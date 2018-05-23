/*
 *
 *  * Copyright (c) 2016 Open Baton (http://www.openbaton.org)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.openbaton.nse.core;

import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.nse.adapters.openstack.NeutronQoSExecutor;
import org.openbaton.nse.adapters.openstack.NeutronQoSHandler;
import org.openbaton.nse.api.Api;
import org.openbaton.nse.utils.openstack.OpenStackTools;
import org.openbaton.nse.utils.openbaton.OpenBatonTools;
import org.openbaton.nse.properties.NseProperties;
import org.openbaton.nse.properties.NfvoProperties;
import org.openbaton.sdk.NFVORequestor;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.network.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Created by maa on 02.12.15. modified by lgr on 20.07.17
 */
@Service
public class CoreModule {

  private static Logger logger = LoggerFactory.getLogger(CoreModule.class);

  private PriorityQueue<String> nsr_id_queue = new PriorityQueue<>();
  private Map<String, Set<VirtualNetworkFunctionRecord>> nsr_vnfrs_map = new HashMap<>();
  private NFVORequestor requestor;
  private final ScheduledExecutorService qtScheduler = Executors.newScheduledThreadPool(1);

  @SuppressWarnings("unused")
  @Autowired
  private NeutronQoSHandler neutron_handler;

  @SuppressWarnings("unused")
  @Autowired
  private OpenStackTools osTools;

  @SuppressWarnings("unused")
  @Autowired
  private OpenBatonTools obTools;

  @SuppressWarnings("unused")
  @Autowired
  private Api api;

  @SuppressWarnings("unused")
  @Autowired
  private NfvoProperties nfvo_configuration;

  @SuppressWarnings("unused")
  @Autowired
  private NseProperties nse_configuration;

  public void notifyChange(String vim_id) {
    api.notifyChange(vim_id);
  }

  /*
   * To allow different types of NFVI besides OpenStack
   * it will be necessary to split up the set of VNFRs
   * here to then create thread for each type of VNFI
   * instead of pushing everything to the thead
   * responsible for OpenStack Neutron..
   */

  public void addQos(Set<VirtualNetworkFunctionRecord> vnfrs, String nsrId) {
    ArrayList<VirtualNetworkFunctionRecord> vnfr_list = api.getVnfr_list();
    ArrayList<VimInstance> vim_list = api.getVim_list();
    // Lets put the received vnfrs into a dictionary and the nsr ids into a queue
    // to avoid running multiple times the same tasks
    logger.debug("There are currently " + nsr_id_queue.size() + " NSRs in the processing queue");
    if (!nsr_id_queue.contains(nsrId) && !nsr_vnfrs_map.containsKey(nsrId)) {
      nsr_vnfrs_map.put(nsrId, vnfrs);
      nsr_id_queue.add(nsrId);
    } else {
      //logger.warn("NSR with id " + nsrId + " was already added to the processing queue");
      return;
    }

    Map<String, Set<VirtualNetworkFunctionRecord>> vim_vnfrs_map = new HashMap<>();
    // simple check each nsr in the queue
    Iterator itr = nsr_id_queue.iterator();
    while (itr.hasNext()) {
      String tmp_nsr_id = (String) itr.next();
      logger.debug("Processing NSR with id " + tmp_nsr_id);
      // Check the set of vnfrs attached to the specific nsr...
      Set<VirtualNetworkFunctionRecord> curr_vnfrs = nsr_vnfrs_map.get(tmp_nsr_id);

      for (VirtualNetworkFunctionRecord vnfr : curr_vnfrs) {
        // Remove all occurences matching the old id
        for (int x = 0; x < vnfr_list.size(); x++) {
          VirtualNetworkFunctionRecord int_vnfr = vnfr_list.get(x);
          if (int_vnfr.getId().equals(vnfr.getId())) {
            vnfr_list.remove(int_vnfr);
          }
        }
        vnfr_list.add(vnfr);

        // First step is now to sort the VNFRs according to their vim
        this.requestor =
            obTools.getNFVORequestor(
                nfvo_configuration.getIp(),
                nfvo_configuration.getPort(),
                vnfr.getProjectId(),
                nse_configuration.getService().getKey());

        if (requestor == null) {
          logger.error(
              "Did not receive NFVO requestor for OpenBaton project : " + vnfr.getProjectId());
          return;
        }

        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
          for (VNFCInstance vnfci : vdu.getVnfc_instance()) {
            if (vim_vnfrs_map.containsKey(vnfci.getVim_id())) {
              // add to the existing vim entry
              vim_vnfrs_map.get(vnfci.getVim_id()).add(vnfr);
            } else {
              // create the new set accordingly
              Set<VirtualNetworkFunctionRecord> tmp_set = new HashSet<>();
              tmp_set.add(vnfr);
              vim_vnfrs_map.put(vnfci.getVim_id(), tmp_set);
              // create a new vim entry
            }
          }
        }
      }
    }
    // At this point we have a map containing all vnfrs sorted by their related vims..
    // thus we should clear the queue as well as the hash map containg the entries\
    // after a small timeout
    Timer timer = new Timer();
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            nsr_id_queue.clear();
            nsr_vnfrs_map.clear();
          }
        },
        3000);
    // Now we collect the credential information for each vim and pass over the
    // set of vnfrs to be processed
    logger.debug(
        "    The NSR with id :"
            + nsrId
            + " is built of "
            + vim_vnfrs_map.keySet().size()
            + " VIM(s)");
    for (String key : vim_vnfrs_map.keySet()) {
      VimInstance v = obTools.getVimInstance(requestor, key);
      logger.debug(" Working on : " + v.getAuthUrl());

      // Remove all occurences matching the old id
      for (int x = 0; x < vim_list.size(); x++) {
        VimInstance vim = vim_list.get(x);
        if (vim.getId().equals(v.getId())) {
          vim_list.remove(vim);
        }
      }
      vim_list.add(v);
      //if (!vim_list.contains(v)) {
      //  vim_list.add(v);
      //}
      if (v.getType().equals("openstack")) {
        openstackNeutronQoS(key, v, vim_vnfrs_map);
      } else {
        logger.warn("VIM type " + v.getType() + " not supported yet");
      }
    }
  }

  @SuppressWarnings("unused")
  public void removeQos(Set<VirtualNetworkFunctionRecord> vnfrs, String nsrId) {
    //logger.debug("Creating REMOVE Thread");
    //logger.debug("Neutron does delete the ports and the applied QoS on machine deletion, will not create REMOVE Thread");
  }

  private void openstackNeutronQoS(
      String key, VimInstance v, Map<String, Set<VirtualNetworkFunctionRecord>> vim_vnfrs_map) {
    Map<String, String> creds = obTools.getDatacenterCredentials(requestor, key);
    OSClient os = osTools.getOSClient(v);
    String token = osTools.getAuthToken(os, v);
    String neutron_access = osTools.getNeutronEndpoint(v);
    // Add the neutron related information into our credential map
    creds.put("neutron", neutron_access);
    //logger.debug("    Collecting OpenStack Neutron Ports");
    List<? extends Port> portList = osTools.getNeutronPorts(os);
    logger.debug(
        "    Starting thread to handle VNFRs using VIM : "
            + v.getName()
            + " with id : "
            + v.getId());
    startOpenStackNeutronQoSTask(key, vim_vnfrs_map, os, token, v, creds, portList);
  }

  private Map<String, String> getVnfHostNameComputeNodeMap(
      OSClient os, Set<VirtualNetworkFunctionRecord> vnfrs) {
    Map<String, String> vnf_host_compute_map = new HashMap<>();
    for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
      for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
        for (VNFCInstance vnfci : vdu.getVnfc_instance()) {
          for (Server s : os.compute().servers().list()) {
            if (vnfci.getHostname().equals(s.getName())) {
              //vnf_host_compute_map.put(vnfr.getName(), s.getHypervisorHostname());
              vnf_host_compute_map.put(vnfci.getHostname(), s.getHypervisorHostname());
            }
          }
        }
      }
    }
    return vnf_host_compute_map;
  }

  @SuppressWarnings("unused")
  private Map<String, String> getRestHostNameComputeNodeMap(OSClient os) {
    Map<String, String> host_compute_map = new HashMap<>();
    for (Server s : os.compute().servers().list()) {
      host_compute_map.put(s.getName(), s.getHypervisorHostname());
    }

    return host_compute_map;
  }

  private void startOpenStackNeutronQoSTask(
      String key,
      Map<String, Set<VirtualNetworkFunctionRecord>> vim_vnfrs_map,
      OSClient os,
      String token,
      VimInstance v,
      Map<String, String> creds,
      List<? extends Port> portList) {
    Callable<NeutronQoSExecutor> aqe =
        new NeutronQoSExecutor(
            vim_vnfrs_map.get(key),
            neutron_handler,
            token,
            v,
            creds,
            portList,
            osTools.getComputeNodeMap(os),
            this.getVnfHostNameComputeNodeMap(os, vim_vnfrs_map.get(key)));
    Collection<Callable<NeutronQoSExecutor>> tasklist = new ArrayList<>();
    tasklist.add(aqe);
    try {
      qtScheduler.schedule(aqe, 2, TimeUnit.SECONDS);
      qtScheduler.invokeAll(tasklist);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
