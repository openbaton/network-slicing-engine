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

package org.openbaton.nse.beans.core;

import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.nse.beans.adapters.openstack.NeutronQoSExecutor;
import org.openbaton.nse.beans.adapters.openstack.NeutronQoSHandler;
import org.openbaton.nse.properties.NseProperties;
import org.openbaton.nse.properties.NfvoProperties;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.sdk.api.rest.VimInstanceAgent;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.network.Port;
import org.openstack4j.openstack.OSFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

/**
 * Created by maa on 02.12.15. modified by lgr on 20.07.17
 */
@Service
public class CoreModule {

  //@Autowired private CmHandler handler;
  private Logger logger;
  private PriorityQueue<String> nsr_id_queue = new PriorityQueue<String>();
  private Map<String, Set<VirtualNetworkFunctionRecord>> nsr_vnfrs_map =
      new HashMap<String, Set<VirtualNetworkFunctionRecord>>();
  private NFVORequestor requestor;
  private final ScheduledExecutorService qtScheduler = Executors.newScheduledThreadPool(1);
  private NeutronQoSHandler neutron_handler = new NeutronQoSHandler();

  @Autowired private NfvoProperties nfvo_configuration;
  @Autowired private NseProperties nse_configuration;

  @PostConstruct
  private void init() {
    this.logger = LoggerFactory.getLogger(this.getClass());
  }

  /*
   * To allow different types of NFVI besides OpenStack
   * it will be necessary to split up the set of VNFRs
   * here to then create thread for each type of VNFI
   * instead of pushing everything to the thead
   * responsible for OpenStack Neutron..
   */

  public void addQos(Set<VirtualNetworkFunctionRecord> vnfrs, String nsrId) {
    // Lets put the received vnfrs into a dictionary and the nsr ids into a queue
    // to avoid running multiple times the same tasks
    logger.debug("There are currently " + nsr_id_queue.size() + " NSRs in the processing queue");
    if (!nsr_id_queue.contains(nsrId) && !nsr_vnfrs_map.containsKey(nsrId)) {
      nsr_vnfrs_map.put(nsrId, vnfrs);
      nsr_id_queue.add(nsrId);
    } else {
      logger.warn("NSR with id " + nsrId + " was already added to the processing queue");
      return;
    }

    Map<String, Set<VirtualNetworkFunctionRecord>> vim_vnfrs_map =
        new HashMap<String, Set<VirtualNetworkFunctionRecord>>();
    // simple check each nsr in the queue
    Iterator itr = nsr_id_queue.iterator();
    while (itr.hasNext()) {
      String tmp_nsr_id = (String) itr.next();
      logger.debug("Processing NSR with id " + tmp_nsr_id);
      // Check the set of vnfrs attached to the specific nsr...
      Set<VirtualNetworkFunctionRecord> curr_vnfrs = nsr_vnfrs_map.get(tmp_nsr_id);
      for (VirtualNetworkFunctionRecord vnfr : curr_vnfrs) {
        // First step is now to sort the VNFRs according to their vim
        try {
          // TODO : instead of false check for enabled SSL
          // configuration.getSsl().isEnabled()
          this.requestor =
              new NFVORequestor(
                  "nse",
                  vnfr.getProjectId(),
                  nfvo_configuration.getIp(),
                  nfvo_configuration.getPort(),
                  "1",
                  false,
                  nse_configuration.getKey());
        } catch (SDKException e) {
          logger.error(
              "Problem instantiating NFVORequestor for VNFR "
                  + vnfr.getName()
                  + " contained in NSR "
                  + tmp_nsr_id);
        }
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
          for (VNFCInstance vnfci : vdu.getVnfc_instance()) {
            if (vim_vnfrs_map.containsKey(vnfci.getVim_id())) {
              // add to the existing vim entry
              vim_vnfrs_map.get(vnfci.getVim_id()).add(vnfr);
            } else {
              // create the new set accordingly
              Set<VirtualNetworkFunctionRecord> tmp_set =
                  new HashSet<VirtualNetworkFunctionRecord>();
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
    Map<String, VimInstance> vim_cred_map = new HashMap<String, VimInstance>();
    logger.debug(
        "    The NSR with id :"
            + nsrId
            + " is built of "
            + vim_vnfrs_map.keySet().size()
            + " VIM(s)");
    for (String key : vim_vnfrs_map.keySet()) {
      VimInstance v = getVimInstance(requestor, key);
      if (v.getType().equals("openstack")) {
        openstackNeutronQoS(key, v, vim_vnfrs_map);
      } else {
        logger.warn("VIM type " + v.getType() + " not supported yet");
      }
    }
  }

  public void removeQos(Set<VirtualNetworkFunctionRecord> vnfrs, String nsrId) {
    //logger.debug("Creating REMOVE Thread");
    //logger.debug("Neutron does delete the ports and the applied QoS on machine deletion, will not create REMOVE Thread");
  }

  private void openstackNeutronQoS(
      String key, VimInstance v, Map<String, Set<VirtualNetworkFunctionRecord>> vim_vnfrs_map) {
    Map<String, String> creds = getDatacenterCredentials(requestor, key);
    OSClient os = getOSClient(v);
    String token = getAuthToken(os, v);
    String neutron_access = getNeutronEndpoint(os, v, token);
    // Add the neutron related information into our credential map
    creds.put("neutron", neutron_access);
    logger.debug("    Collecting OpenStack Neutron Ports");
    List<org.openstack4j.model.network.Port> portList = getNeutronPorts(os);
    logger.debug(
        "    Starting thread to handle VNFRs using VIM : "
            + v.getName()
            + " with id : "
            + v.getId());
    startOpenStackNeutronQoSTask(key, vim_vnfrs_map, os, token, v, creds, portList);
  }

  // Function to directly get a VimInstance by its id
  private VimInstance getVimInstance(NFVORequestor requestor, String vim_id) {
    VimInstance v = null;
    try {
      //logger.debug("listing all vim-instances");
      //logger.debug(requestor.getVimInstanceAgent().findAll().toString());
      for (VimInstance vim : requestor.getVimInstanceAgent().findAll()) {
        if (vim.getId().equals(vim_id)) {
          // well we found the correct vim
          v = vim;
        }
      }
      //v = requestor.getVimInstanceAgent().findById(vim_id);

    } catch (Exception e) {
      logger.error("Exception while creating credentials");
      logger.error(e.getMessage());
      logger.error(e.toString());
      e.printStackTrace();
    }
    return v;
  }

  // Function to get the correct OSClient to communicate directly to openstack, it depends on the vim
  // if you get a v3 or v2 type
  private OSClient getOSClient(VimInstance vimInstance) {
    OSClient os = null;
    try {
      if (isV3API(vimInstance)) {

        Identifier domain = Identifier.byName("Default");
        Identifier project = Identifier.byId(vimInstance.getTenant());

        //logger.debug("Domain id: " + domain.getId());
        //logger.debug("Project id: " + project.getId());
        //logger.debug("Password: " + vimInstance.getPassword());
        os =
            OSFactory.builderV3()
                .endpoint(vimInstance.getAuthUrl())
                .scopeToProject(project)
                .credentials(vimInstance.getUsername(), vimInstance.getPassword(), domain)
                .authenticate();
        if (vimInstance.getLocation() != null
            && vimInstance.getLocation().getName() != null
            && !vimInstance.getLocation().getName().isEmpty()) {
          try {
            org.openstack4j.model.identity.v3.Region region =
                ((OSClient.OSClientV3) os)
                    .identity()
                    .regions()
                    .get(vimInstance.getLocation().getName());

            if (region != null) {
              ((OSClient.OSClientV3) os).useRegion(vimInstance.getLocation().getName());
            }
          } catch (Exception ignored) {
            logger.warn(
                "Not found region '"
                    + vimInstance.getLocation().getName()
                    + "'. Use default one...");
            return os;
          }
        }
      } else {
        os =
            OSFactory.builderV2()
                .endpoint(vimInstance.getAuthUrl())
                .credentials(vimInstance.getUsername(), vimInstance.getPassword())
                .tenantName(vimInstance.getTenant())
                .authenticate();

        if (vimInstance.getLocation() != null
            && vimInstance.getLocation().getName() != null
            && !vimInstance.getLocation().getName().isEmpty()) {
          try {
            ((OSClient.OSClientV2) os).useRegion(vimInstance.getLocation().getName());
            ((OSClient.OSClientV2) os).identity().listTokenEndpoints();
          } catch (Exception e) {
            logger.warn(
                "Not found region '"
                    + vimInstance.getLocation().getName()
                    + "'. Use default one...");
            ((OSClient.OSClientV2) os).removeRegion();
          }
        }
      }
    } catch (AuthenticationException e) {
      logger.error("Authentification error");
      e.printStackTrace();
    }
    return os;
  }

  // Function to extract credentials directly out of a virtualized infrastructure manager ( VIM )
  private Map<String, String> getDatacenterCredentials(NFVORequestor requestor, String vim_id) {
    Map<String, String> cred = new HashMap<String, String>();
    // What we want to archieve is to list all machines and know to which vim-instance they belong
    VimInstance v = null;
    try {
      //logger.debug("trying to use viminstanceagent");
      VimInstanceAgent agent = requestor.getVimInstanceAgent();
      //logger.debug("listing all vim-instances");
      //logger.debug(requestor.getVimInstanceAgent().findAll().toString());

      //v = requestor.getVimInstanceAgent().findById(vim_id);
      for (VimInstance vim : requestor.getVimInstanceAgent().findAll()) {
        if (vim.getId().equals(vim_id)) {
          // well we found the correct vim
          v = vim;
        }
      }
      //logger.debug("        adding identity : " + v.getTenant() + ":" + v.getUsername());
      cred.put("identity", v.getTenant() + ":" + v.getUsername());
      //logger.debug("        adding password : " + v.getPassword());
      cred.put("password", v.getPassword());
      //logger.debug("        adding nova auth url " + v.getAuthUrl());
      cred.put("auth", v.getAuthUrl());
    } catch (Exception e) {
      logger.error("Exception while creating credentials");
      logger.error(e.getMessage());
      logger.error(e.toString());
      e.printStackTrace();
    }
    return cred;
  }

  // Check for nova api v2 or nova api v3, necessary to be able to know which OSClient to use if you choose
  // to use openstack4j
  private boolean isV3API(VimInstance vimInstance) {
    return vimInstance.getAuthUrl().endsWith("/v3") || vimInstance.getAuthUrl().endsWith("/v3.0");
  }

  // Method to get a x auth token using the openstack4j libraries
  private String getAuthToken(OSClient os, VimInstance vimInstance) {
    String token = null;
    if (isV3API(vimInstance)) {
      logger.debug("    Using OpenStack Nova API v3 to obtain x auth token");
      token = ((OSClient.OSClientV3) os).getToken().getId();
    } else {
      logger.debug("    Using OpenStack Nova API v2 to obtain x auth token");
      token = ((OSClient.OSClientV2) os).getAccess().getToken().getId();
    }
    //logger.debug("Received token : " + token);
    return token;
  }
  // Function to list all neutron ports via the OSClient for a specific vim
  // We need to know the port ids to know where to push the QoS rules on
  private List<Port> getNeutronPorts(OSClient os) {
    List<org.openstack4j.model.network.Port> lp =
        (List<org.openstack4j.model.network.Port>) os.networking().port().list();
    return lp;
  }

  // Method to get the correct neutron url to communicate with
  private String getNeutronEndpoint(OSClient os, VimInstance vimInstance, String token) {
    // For nova api v3 we check the public neutron public api via the token catalog
    if (isV3API(vimInstance)) {
      logger.debug("    Trying to get OpenStack Neutron endpoint url via Nova API v3");
      List<? extends org.openstack4j.model.identity.v3.Service> service_list =
          ((OSClient.OSClientV3) os).getToken().getCatalog();
      for (org.openstack4j.model.identity.v3.Service s : service_list) {
        //logger.debug("      Checking service " + s.getName());
        if (s.getName().equals("neutron")) {
          List<? extends org.openstack4j.model.identity.v3.Endpoint> e_list = s.getEndpoints();
          for (org.openstack4j.model.identity.v3.Endpoint e : e_list) {
            logger.debug(
                "    Found OpenStack Neutron endpoint with type : " + e.getIface().value());
            if (e.getIface().value().equals("public")) {
              logger.debug("    Using API version 2 for communication with OpenStack Neutron");
              return e.getUrl().toString() + "/v2.0";
            }
          }
        }
      }
    } else {
      // For nova api v2 we check the public neutron public api via the identity token endpoints
      logger.debug("    Trying to get OpenStack Neutron endpoint url via Nova API v2");
      // Well a way to get the desired values is to use the tokenEndpoints
      List<? extends org.openstack4j.model.identity.v2.Endpoint> endpoint_list =
          ((OSClient.OSClientV2) os).identity().listTokenEndpoints();
      for (org.openstack4j.model.identity.v2.Endpoint e : endpoint_list) {
        //logger.debug("Checking endpoint : " + e.getName());
        if (e.getName().equals("neutron")) {
          logger.debug("    Found OpenStack Neutron endpoint with type : " + e.getType());
          if (e.getType().equals("network")) {
            logger.debug("    Neutron is available at " + e.getPublicURL());
            logger.debug("    Using API version 2 for communication with OpenStack Neutron");
            return e.getPublicURL() + "/v2.0";
          }
        }
      }
    }
    logger.error("    Did not found any OpenStack Neutron endpoint url");
    return null;
  }

  private void startOpenStackNeutronQoSTask(
      String key,
      Map<String, Set<VirtualNetworkFunctionRecord>> vim_vnfrs_map,
      OSClient os,
      String token,
      VimInstance v,
      Map<String, String> creds,
      List<org.openstack4j.model.network.Port> portList) {
    NeutronQoSExecutor aqe =
        new NeutronQoSExecutor(
            vim_vnfrs_map.get(key), neutron_handler, os, token, v, creds, portList);
    qtScheduler.schedule(aqe, 1, TimeUnit.SECONDS);
  }
}
