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

import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.descriptor.InternalVirtualLink;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.catalogue.security.Project;
import org.openbaton.nse.beans.adapters.openstack.NeutronQoSExecutor;
import org.openbaton.nse.beans.adapters.openstack.NeutronQoSHandler;
import org.openbaton.nse.utils.OpenStackOverview;
import org.openbaton.nse.properties.NseProperties;
import org.openbaton.nse.properties.NfvoProperties;
import org.openbaton.nse.utils.Quality;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.sdk.api.rest.VimInstanceAgent;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.ext.Hypervisor;
import org.openstack4j.model.network.Port;
import org.openstack4j.openstack.OSFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileNotFoundException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

/**
 * Created by maa on 02.12.15. modified by lgr on 20.07.17
 */
@Service
@RestController
public class CoreModule {

  //@Autowired private CmHandler handler;
  private Logger logger;
  private PriorityQueue<String> nsr_id_queue = new PriorityQueue<String>();
  private Map<String, Set<VirtualNetworkFunctionRecord>> nsr_vnfrs_map =
      new HashMap<String, Set<VirtualNetworkFunctionRecord>>();
  private NFVORequestor requestor;
  private final ScheduledExecutorService qtScheduler = Executors.newScheduledThreadPool(1);
  private NeutronQoSHandler neutron_handler = new NeutronQoSHandler();

  private OpenStackOverview osOverview = new OpenStackOverview();

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
                  nse_configuration.getService().getKey());
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
      //v = requestor.getVimInstanceAgent().findById(vim_id);
      for (VimInstance vim : requestor.getVimInstanceAgent().findAll()) {
        if (vim.getId().equals(vim_id)) {
          // well we found the correct vim
          v = vim;
        }
      }
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
                "    Not found region '"
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
                "    Not found region '"
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
      //logger.debug("    Using OpenStack Nova API v3 to obtain x auth token");
      token = ((OSClient.OSClientV3) os).getToken().getId();
    } else {
      //logger.debug("    Using OpenStack Nova API v2 to obtain x auth token");
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

  private Map<String, String> getComputeNodeMap(OSClient os) {
    Map<String, String> computenode_ip_map = new HashMap<String, String>();
    for (Hypervisor h : os.compute().hypervisors().list()) {
      computenode_ip_map.put(h.getHypervisorHostname(), h.getHostIP());
    }
    return computenode_ip_map;
  }

  private Map<String, String> getVnfHostNameComputeNodeMap(
      OSClient os, Set<VirtualNetworkFunctionRecord> vnfrs) {
    Map<String, String> vnf_host_compute_map = new HashMap<String, String>();
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

  private Map<String, String> getRestHostNameComputeNodeMap(OSClient os) {
    Map<String, String> host_compute_map = new HashMap<String, String>();
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
      List<org.openstack4j.model.network.Port> portList) {
    NeutronQoSExecutor aqe =
        new NeutronQoSExecutor(
            vim_vnfrs_map.get(key),
            neutron_handler,
            token,
            v,
            creds,
            portList,
            this.getComputeNodeMap(os),
            this.getVnfHostNameComputeNodeMap(os, vim_vnfrs_map.get(key)));
    qtScheduler.schedule(aqe, 1, TimeUnit.SECONDS);
  }

  private void updateOpenStackOverview() {
    this.osOverview = new OpenStackOverview();
    ArrayList<Map<String, Object>> project_nsr_map = null;
    ArrayList<Map<String, Object>> complete_computeNodeMap = new ArrayList<>();

    NFVORequestor nfvo_nsr_req = null;
    NFVORequestor nfvo_default_req = null;
    try {
      nfvo_default_req =
          new NFVORequestor(
              nfvo_configuration.getUsername(),
              nfvo_configuration.getPassword(),
              "*",
              false,
              nfvo_configuration.getIp(),
              nfvo_configuration.getPort(),
              "1");
      // Iterate over all projects and collect all NSRs
      for (Project project : nfvo_default_req.getProjectAgent().findAll()) {
        //logger.debug("Checking project : " + project.getName());
        nfvo_nsr_req =
            new NFVORequestor(
                "nse",
                project.getId(),
                nfvo_configuration.getIp(),
                nfvo_configuration.getPort(),
                "1",
                false,
                nse_configuration.getService().getKey());
        if (nfvo_nsr_req != null) {
          List<NetworkServiceRecord> nsr_list =
              nfvo_nsr_req.getNetworkServiceRecordAgent().findAll();

          if (project_nsr_map == null) {
            //project_nsr_map = new HashMap<String, ArrayList<HashMap<String, String>>>();

            project_nsr_map = new ArrayList<>();
          }
          for (NetworkServiceRecord nsr : nsr_list) {
            ArrayList<HashMap<String, String>> tmp_nsr_list =
                new ArrayList<HashMap<String, String>>();
            //logger.debug("Checking NSR : " + nsr.getName());
            boolean found_project = false;
            for (int n = 0; n < project_nsr_map.size(); n++) {
              Map<String, Object> tmp = project_nsr_map.get(n);
              if (tmp.get("name").equals(project.getName())) {
                found_project = true;
              }
            }
            if (!found_project) {
              HashMap<String, Object> tmp_project_entry = new HashMap<String, Object>();
              HashMap<String, String> tmp_nsr_entry = new HashMap<String, String>();
              tmp_nsr_entry.put("name", nsr.getName());
              tmp_nsr_entry.put("id", nsr.getId());
              tmp_nsr_list.add(tmp_nsr_entry);
              tmp_project_entry.put("nsrs", tmp_nsr_list);
              tmp_project_entry.put("name", project.getName());
              project_nsr_map.add(tmp_project_entry);
            } else {
              //logger.debug("Already found project " + project.getName() + " in the hashmap");
            }
            for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
              //logger.debug("Checking VNFR : " + vnfr.getName());
              for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                for (VNFCInstance vnfci : vdu.getVnfc_instance()) {
                  VimInstance tmp_vim = this.getVimInstance(nfvo_nsr_req, vnfci.getVim_id());
                  OSClient tmp_os = getOSClient(tmp_vim);
                  Map<String, String> tmp_computeNodeMap = getComputeNodeMap(tmp_os);
                  if (tmp_computeNodeMap != null) {
                    // We collect all involved compute nodes
                    for (String key : tmp_computeNodeMap.keySet()) {
                      boolean node_found = false;
                      for (int i = 0; i < complete_computeNodeMap.size(); i++) {
                        if (complete_computeNodeMap.get(i).get("name").equals(key)) {
                          node_found = true;
                        }
                      }
                      if (!node_found) {
                        HashMap<String, Object> tmp_node_entry = new HashMap<String, Object>();
                        //tmp_node_entry.put(key, tmp_computeNodeMap.get(key));
                        tmp_node_entry.put("vnfs", new ArrayList<HashMap<String, String>>());
                        tmp_node_entry.put("node_ip", tmp_computeNodeMap.get(key));
                        tmp_node_entry.put("name", key);
                        tmp_node_entry.put("no_vnfs", new ArrayList<HashMap<String, String>>());
                        tmp_node_entry.put("networks", new ArrayList<HashMap<String, String>>());
                        complete_computeNodeMap.add(tmp_node_entry);
                      }
                    }
                  }
                  // get all units together with their compute node...
                  Map<String, String> tmp_vnf_computeNodeMap =
                      getVnfHostNameComputeNodeMap(tmp_os, nsr.getVnfr());
                  // get all hosts of the related testbed
                  Map<String, String> tmp_host_computeNodeMap =
                      getRestHostNameComputeNodeMap(tmp_os);
                  // remove the vnfs from the other host entries
                  for (String key : tmp_vnf_computeNodeMap.keySet()) {
                    tmp_host_computeNodeMap.remove(key);
                  }

                  if (tmp_host_computeNodeMap != null) {
                    boolean host_node_found = false;
                    for (String key : tmp_host_computeNodeMap.keySet()) {
                      String current_compute_node = tmp_host_computeNodeMap.get(key);
                      for (int i = 0; i < complete_computeNodeMap.size(); i++) {
                        if (complete_computeNodeMap
                            .get(i)
                            .get("name")
                            .equals(current_compute_node)) {
                          ArrayList<HashMap<String, String>> already_contained_hosts =
                              (ArrayList<HashMap<String, String>>)
                                  complete_computeNodeMap.get(i).get("no_vnfs");
                          boolean found_host = false;
                          for (int y = 0; y < already_contained_hosts.size(); y++) {
                            HashMap<String, String> tmp_host = already_contained_hosts.get(y);
                            if (tmp_host.get("hostname").equals(key)) {
                              found_host = true;
                            }
                          }
                          if (found_host) {
                            //logger.debug(
                            //    "Entry "
                            //        + vnfci.getHostname()
                            //        + " already added to "
                            //        + current_compute_node);
                          } else {
                            //logger.debug(
                            //    "adding " + vnfci.getHostname() + " to " + current_compute_node);
                            HashMap<String, String> tmp_host_info = new HashMap<String, String>();
                            tmp_host_info.put("hostname", key);
                            already_contained_hosts.add(tmp_host_info);
                          }
                        }
                      }
                    }
                  }

                  //logger.debug(tmp_vnf_computeNodeMap.toString());
                  if (tmp_vnf_computeNodeMap != null) {
                    boolean vnf_node_found = false;
                    // {bt=node05ob100.maas, chess=node06ob100.maas, mme=node03ob100.maas, sgwupgwugw=node07ob100.maas, bind9=node02ob100.maas}
                    for (String key : tmp_vnf_computeNodeMap.keySet()) {
                      if (key.equals(vnfci.getHostname())) {
                        //if (key.equals(vnfr.getName())) {
                        String current_compute_node = tmp_vnf_computeNodeMap.get(key);
                        //logger.debug("Checking " + key + " of " + nsr.getName());
                        for (int i = 0; i < complete_computeNodeMap.size(); i++) {
                          if (complete_computeNodeMap
                              .get(i)
                              .get("name")
                              .equals(current_compute_node)) {
                            //logger.debug("found compute node " + current_compute_node);
                            ArrayList<HashMap<String, Object>> already_contained_vnfs =
                                (ArrayList<HashMap<String, Object>>)
                                    complete_computeNodeMap.get(i).get("vnfs");
                            boolean found_vnf = false;
                            for (int y = 0; y < already_contained_vnfs.size(); y++) {
                              HashMap<String, Object> tmp_vnf = already_contained_vnfs.get(y);
                              if (tmp_vnf.get("hostname").equals(vnfci.getHostname())) {
                                found_vnf = true;
                              }
                            }
                            if (found_vnf) {
                              //logger.debug(
                              //    "Entry "
                              //        + vnfci.getHostname()
                              //        + " already added to "
                              //        + current_compute_node);
                            } else {
                              //logger.debug(
                              //    "adding " + vnfci.getHostname() + " to " + current_compute_node);
                              HashMap<String, Object> tmp_vnf_info = new HashMap<String, Object>();
                              ArrayList<Map<String, String>> vnf_net_map = new ArrayList<>();
                              //ArrayList<String> vnf_net_map = new ArrayList<>();
                              tmp_vnf_info.put("hostname", vnfci.getHostname());
                              tmp_vnf_info.put("nsr_name", nsr.getName());
                              tmp_vnf_info.put("nsr_id", nsr.getId());
                              tmp_vnf_info.put("vim_id", tmp_vim.getId());
                              tmp_vnf_info.put("vim_name", tmp_vim.getName());
                              tmp_vnf_info.put("project_id", nsr.getProjectId());
                              for (InternalVirtualLink vlr : vnfr.getVirtual_link()) {
                                for (String qosParam : vlr.getQos()) {
                                  if (qosParam.contains("minimum")
                                      || qosParam.contains("maximum")
                                      || qosParam.contains("policy")) {
                                    HashMap<String, String> tmp_qos_net =
                                        new HashMap<String, String>();
                                    tmp_qos_net.put(vlr.getName(), vlr.getQos().toString());
                                    vnf_net_map.add(tmp_qos_net);
                                    //logger.debug("Added " + vlr.getName() + " with QOS");
                                  }
                                }
                              }
                              for (Ip ip : vnfci.getIps()) {
                                boolean found_net = false;
                                // Check if we already got the entry..
                                //logger.debug("Will now check for existing entries ");
                                for (Map<String, String> entry : vnf_net_map) {
                                  //logger.debug("Checking " + entry.toString());
                                  if (entry.containsKey(ip.getNetName())) {
                                    //logger.debug("Found existing net.. will not add");
                                    found_net = true;
                                  }
                                }
                                if (!found_net) {
                                  //logger.debug("Adding not existing net");
                                  HashMap<String, String> tmp_qos_net =
                                      new HashMap<String, String>();
                                  tmp_qos_net.put(ip.getNetName(), "none");
                                  vnf_net_map.add(tmp_qos_net);
                                }
                                //vnf_net_map.add(ip.getNetName());
                                //}
                              }
                              tmp_vnf_info.put("networks", vnf_net_map);
                              already_contained_vnfs.add(tmp_vnf_info);
                              // Also check for the networks of the vnf
                              ArrayList<HashMap<String, String>> already_contained_networks =
                                  (ArrayList<HashMap<String, String>>)
                                      complete_computeNodeMap.get(i).get("networks");
                              if (already_contained_networks.isEmpty()) {
                                // If the list is empty we can directly add the networks of this VNF..
                                for (Ip ip : vnfci.getIps()) {
                                  //logger.debug("Adding " + ip.getNetName());
                                  HashMap<String, String> tmp_net_info =
                                      new HashMap<String, String>();
                                  tmp_net_info.put("name", ip.getNetName());
                                  already_contained_networks.add(tmp_net_info);
                                }

                              } else {
                                for (int x = 0; x < already_contained_networks.size(); x++) {
                                  boolean found_network = false;
                                  HashMap<String, String> tmp_net =
                                      already_contained_networks.get(x);
                                  // Check each IP address...
                                  for (Ip ip : vnfci.getIps()) {
                                    //logger.debug("Checking " + ip.getNetName());
                                    if (tmp_net.get("name").equals(ip.getNetName())) {
                                      found_network = true;
                                    }
                                  }
                                  if (found_network) {
                                    //logger.debug(
                                    //    "Network " + tmp_net.get("name") + " already exists");
                                  } else {
                                    HashMap<String, String> tmp_net_info =
                                        new HashMap<String, String>();
                                    tmp_net_info.put("name", tmp_net.get("name"));
                                    already_contained_networks.add(tmp_net_info);
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
        // In the very end add the hosts and hypervisors which did not belong to any NSR
        this.osOverview.setNodes(complete_computeNodeMap);
        this.osOverview.setProjects(project_nsr_map);
        logger.debug("updated overview");
      }
    } catch (SDKException e) {
      logger.error("Problem instantiating NFVORequestor with project id null");
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  //@CrossOrigin(origins = "*")
  //@RequestMapping("/overview")
  //public OpenStackOverview getOverview(
  //    @RequestParam(value = "name", defaultValue = "World") String name) {
  //  updateOpenStackOverview();
  //  return this.osOverview;
  //}
}
