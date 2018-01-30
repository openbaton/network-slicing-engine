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
import org.openstack4j.model.network.IP;
import org.openstack4j.model.network.Network;
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

  private HashMap<String, Object> getNetworkNames(OSClient os) {
    HashMap<String, Object> networks = new HashMap<String, Object>();
    for (Network n : os.networking().network().list()) {
      networks.put(n.getId(), n.getName());
    }
    return networks;
  }

  private HashMap<String, Object> getPortIps(OSClient os, ArrayList<String> ips) {
    HashMap<String, Object> port_map = new HashMap<String, Object>();
    for (Port p : os.networking().port().list()) {
      ArrayList<String> tmp_ips = new ArrayList<String>();
      for (IP i : p.getFixedIps()) {
        //ArrayList<String> tmp_ips;
        //if (port_map.containsKey(p.getId())) {
        //  tmp_ips = ((ArrayList<String>) port_map.get(p.getId()));
        //  tmp_ips.add(i.getIpAddress());
        //} else {
        //  tmp_ips = new ArrayList<String>();

        // Only add the ports we want to check...
        if (ips.contains(i.getIpAddress())) {
          tmp_ips.add(i.getIpAddress());
          port_map.put(p.getId(), tmp_ips);
        }
        //}
      }
    }
    return port_map;
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
    //ArrayList<Map<String, Object>> project_nsr_map = null;
    //ArrayList<Map<String, Object>> complete_computeNodeMap = new ArrayList<>();

    NFVORequestor nfvo_nsr_req = null;
    NFVORequestor nfvo_default_req = null;

    // Set up a variable which contains the already processed vims, we distinguish via the auth_url + user + tenant here
    // to avoid contacting the same infrastructure used in different projects.
    ArrayList<Integer> processed_vims = new ArrayList<Integer>();
    // Set up a map containing all the vim ids listed to the internal generated hash
    HashMap<Integer, Object> vim_map = new HashMap<Integer, Object>();
    // Set up a map containing all external vim ids together with their names
    HashMap<String, String> vim_name_map = new HashMap<String, String>();
    // Set up a map containing all external vim ids together with their names
    HashMap<String, String> vim_type_map = new HashMap<String, String>();
    // Set up a map containing all internal vim hashs and related node information ( openstack only currently)
    HashMap<Integer, Object> node_map = new HashMap<Integer, Object>();
    // Set up a map containing all projects ids together with their names
    HashMap<String, String> project_id_map = new HashMap<String, String>();
    // Set up a map containing all projects together with a list of nsr ids
    HashMap<String, Object> project_nsr_map = new HashMap<String, Object>();
    // Set up a map containing all projects ids together with their names
    HashMap<String, String> nsr_name_map = new HashMap<String, String>();
    // Set up a map containing all projects together with a list of nsr ids
    HashMap<String, Object> nsr_vnf_map = new HashMap<String, Object>();
    // Set up a map containing the vnfs and their networks from Open Baton side ( virtual link records )
    HashMap<String, Object> vnf_vlr_map = new HashMap<String, Object>();
    // Set up a map containing all vlr ids together with their names
    HashMap<String, String> vlr_name_map = new HashMap<String, String>();
    // Set up a map containing all vlr ids together with their assigned bandwidth qualities
    HashMap<String, String> vlr_quality_map = new HashMap<String, String>();
    // Set up a map containing all vnfs and their vdus
    HashMap<String, Object> vnf_vdu_map = new HashMap<String, Object>();
    // Set up a a map containing the names of each vdu listed by their ids
    HashMap<String, String> vdu_name_map = new HashMap<String, String>();
    // Set up a map containing the vdu and their vnfcis
    HashMap<String, Object> vdu_vnfci_map = new HashMap<String, Object>();
    // Set up a map containing the vnfci names with their ids ( These are the host names in the end )
    HashMap<String, String> vnfci_name_map = new HashMap<String, String>();
    // Set up a map containing the ips of each vnfci
    HashMap<String, Object> vnfci_ip_map = new HashMap<String, Object>();
    // Set up a map containing the names of the networks for each ip
    HashMap<String, String> ip_name_map = new HashMap<String, String>();
    // Set up a map containing the ips of the networks/ip ids
    HashMap<String, String> ip_addresses_map = new HashMap<String, String>();
    // Set up a map containing the vnfci ids together with their vim ids..
    HashMap<String, Integer> vnfci_vim_map = new HashMap<String, Integer>();

    // ###### OpenStack related
    // Set up a map containing the OpenStack port ids listed to the internal hash of the vim
    HashMap<Integer, Object> port_id_map = new HashMap<Integer, Object>();
    // Set up a map containing the OpenStack port ids together with all their ip addresses
    HashMap<String, Object> port_ip_map = new HashMap<String, Object>();
    // A list of ips which have to be checked for ports + subnets + nets ( listed by internal hash..)
    HashMap<Integer, Object> ips_to_be_checked = new HashMap<Integer, Object>();
    // A simple map which saves the reference to the osclients for specific nodes
    // HashMap<Integer, OSClient> os_client_map = new HashMap<Integer, OSClient>();
    // A simple map which saves the reference to the osclients ( via a vim instaces )
    HashMap<Integer, VimInstance> os_vim_map = new HashMap<Integer, VimInstance>();

    // Set up a map containing the OpenStack port ids listed with their parent network id
    HashMap<String, String> port_net_map = new HashMap<String, String>();
    // Set up a map containing the OpenStack network ids listed with their names
    HashMap<String, String> net_name_map = new HashMap<String, String>();
    // Set up a map containing the vnfci ids listed with their related hypervisor/ compute node
    HashMap<String, String> vnfci_hypervisor_map = new HashMap<String, String>();

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
          if (!nsr_list.isEmpty()) {
            project_id_map.put(project.getId(), project.getName());
          }
          // ###################################################
          //logger.debug(String.valueOf(nsr_list));
          for (NetworkServiceRecord nsr : nsr_list) {
            nsr_name_map.put(nsr.getId(), nsr.getName());
            ArrayList<String> tmp_nsrs;
            if (project_nsr_map.containsKey(project.getId())) {
              tmp_nsrs = ((ArrayList<String>) project_nsr_map.get(project.getId()));
              tmp_nsrs.add(nsr.getId());
            } else {
              tmp_nsrs = new ArrayList<String>();
              tmp_nsrs.add(nsr.getId());
              project_nsr_map.put(project.getId(), tmp_nsrs);
            }
            for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
              ArrayList<String> tmp_vnfs;
              if (nsr_vnf_map.containsKey(nsr.getId())) {
                tmp_vnfs = ((ArrayList<String>) nsr_vnf_map.get(nsr.getId()));
                tmp_vnfs.add(vnfr.getId());
              } else {
                tmp_vnfs = new ArrayList<String>();
                tmp_vnfs.add(vnfr.getId());
                nsr_vnf_map.put(nsr.getId(), tmp_vnfs);
              }
              for (InternalVirtualLink vlr : vnfr.getVirtual_link()) {
                ArrayList<String> tmp_vlrs;
                if (vnf_vlr_map.containsKey(vnfr.getId())) {
                  tmp_vlrs = ((ArrayList<String>) vnf_vlr_map.get(vnfr.getId()));
                  tmp_vlrs.add(vlr.getId());
                } else {
                  tmp_vlrs = new ArrayList<String>();
                  tmp_vlrs.add(vlr.getId());
                  vnf_vlr_map.put(vnfr.getId(), tmp_vlrs);
                }
                vlr_name_map.put(vlr.getId(), vlr.getName());
                for (String qosParam : vlr.getQos()) {
                  if (qosParam.contains("minimum")
                      || qosParam.contains("maximum")
                      || qosParam.contains("policy")) {
                    vlr_quality_map.put(vlr.getId(), vlr.getQos().toString());
                  }
                }
              }
              for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                ArrayList<String> tmp_vdus;
                if (vnf_vdu_map.containsKey(vnfr.getId())) {
                  tmp_vdus = ((ArrayList<String>) vnf_vdu_map.get(vnfr.getId()));
                  tmp_vdus.add(vdu.getId());
                } else {
                  tmp_vdus = new ArrayList<String>();
                  tmp_vdus.add(vdu.getId());
                  vnf_vdu_map.put(vnfr.getId(), tmp_vdus);
                }
                vdu_name_map.put(vdu.getId(), vdu.getName());
                for (VNFCInstance vnfci : vdu.getVnfc_instance()) {
                  vnfci_name_map.put(vnfci.getId(), vnfci.getHostname());
                  ArrayList<String> tmp_vnfcis;
                  if (vdu_vnfci_map.containsKey(vdu.getId())) {
                    tmp_vnfcis = ((ArrayList<String>) vdu_vnfci_map.get(vdu.getId()));
                    tmp_vnfcis.add(vnfci.getId());
                  } else {
                    tmp_vnfcis = new ArrayList<String>();
                    tmp_vnfcis.add(vnfci.getId());
                    vdu_vnfci_map.put(vdu.getId(), tmp_vnfcis);
                  }
                  VimInstance tmp_vim = this.getVimInstance(nfvo_nsr_req, vnfci.getVim_id());
                  // Generate an identifier internally to not distinguish vims by their internal id but at other crucial information to avoid contacting the same infrastructure
                  int vim_identifier =
                      (tmp_vim.getAuthUrl() + tmp_vim.getUsername() + tmp_vim.getTenant())
                              .hashCode()
                          & 0xfffffff;
                  if (!vim_name_map.containsKey(tmp_vim.getId())) {
                    vim_name_map.put(tmp_vim.getId(), tmp_vim.getName());
                    vim_type_map.put(tmp_vim.getId(), tmp_vim.getType());
                    for (org.openbaton.catalogue.nfvo.Network n : tmp_vim.getNetworks()) {
                      net_name_map.put(n.getExtId(), n.getName());
                    }
                  }
                  vnfci_vim_map.put(vnfci.getId(), vim_identifier);
                  for (Ip ip : vnfci.getIps()) {
                    ip_name_map.put(ip.getId(), ip.getNetName());
                    ip_addresses_map.put(ip.getId(), ip.getIp());
                    ArrayList<String> tmp_ips;
                    if (vnfci_ip_map.containsKey(vnfci.getId())) {
                      tmp_ips = ((ArrayList<String>) vnfci_ip_map.get(vnfci.getId()));
                      tmp_ips.add(ip.getId());
                    } else {
                      tmp_ips = new ArrayList<String>();
                      tmp_ips.add(ip.getId());
                      vnfci_ip_map.put(vnfci.getId(), tmp_ips);
                    }
                    ArrayList<String> tmp_ip_list;
                    if (ips_to_be_checked.containsKey(vim_identifier)) {
                      tmp_ip_list = ((ArrayList<String>) ips_to_be_checked.get(vim_identifier));
                      tmp_ip_list.add(ip.getIp());
                    } else {
                      tmp_ip_list = new ArrayList<String>();
                      tmp_ip_list.add(ip.getIp());
                      ips_to_be_checked.put(vim_identifier, tmp_ip_list);
                    }
                  }
                  if (!processed_vims.contains(vim_identifier)) {
                    processed_vims.add(vim_identifier);
                    ArrayList<String> tmp_vim_ids = new ArrayList<String>();
                    tmp_vim_ids.add(tmp_vim.getId());
                    vim_map.put(vim_identifier, tmp_vim_ids);

                    if (tmp_vim.getType().equals("openstack")) {
                      OSClient tmp_os = getOSClient(tmp_vim);
                      //if (!os_client_map.containsKey(vim_identifier)) {
                      //  os_client_map.put(vim_identifier, tmp_os);
                      //}
                      if (!os_vim_map.containsKey(vim_identifier)) {
                        os_vim_map.put(vim_identifier, tmp_vim);
                      }
                      Map<String, String> tmp_computeNodeMap = getComputeNodeMap(tmp_os);
                      if (tmp_computeNodeMap != null) {
                        // We collect all involved compute nodes
                        ArrayList<String> tmp_node_names = new ArrayList<String>();
                        for (String key : tmp_computeNodeMap.keySet()) {

                          tmp_node_names.add(key);
                        }
                        node_map.put(vim_identifier, tmp_node_names);
                      }
                    }
                  } else {
                    // in this case we already found the vim via the internal generated hash and only need to append the vim id to the hash in the map
                    ArrayList<String> vim_ids = ((ArrayList<String>) vim_map.get(vim_identifier));
                    if (!vim_ids.contains(tmp_vim.getId())) {
                      vim_ids.add(tmp_vim.getId());
                    }
                  }
                }
              }
            }
          }
        }
      }
      this.osOverview.setVims(vim_map);
      this.osOverview.setVim_names(vim_name_map);
      this.osOverview.setVim_types(vim_type_map);
      this.osOverview.setOs_nodes(node_map);
      this.osOverview.setProjects(project_id_map);
      this.osOverview.setNsrs(project_nsr_map);
      this.osOverview.setNsr_names(nsr_name_map);
      this.osOverview.setNsr_vnfs(nsr_vnf_map);
      this.osOverview.setVnf_vlrs(vnf_vlr_map);
      this.osOverview.setVlr_names(vlr_name_map);
      this.osOverview.setVlr_qualities(vlr_quality_map);
      this.osOverview.setVnf_vdus(vnf_vdu_map);
      this.osOverview.setVdu_names(vdu_name_map);
      this.osOverview.setVdu_vnfcis(vdu_vnfci_map);
      this.osOverview.setVnfci_names(vnfci_name_map);
      this.osOverview.setVnfci_ips(vnfci_ip_map);
      this.osOverview.setIp_names(ip_name_map);
      this.osOverview.setIp_addresses(ip_addresses_map);
      this.osOverview.setVnfci_vims(vnfci_vim_map);

      // TODO : Switch to threads to collect information of the infrastructure ( should become way faster )
      for (Integer i : node_map.keySet()) {
        OSClient os_client = getOSClient(os_vim_map.get(i));
        HashMap<String, Object> tmp_portMap =
            getPortIps(os_client, (ArrayList<String>) ips_to_be_checked.get(i));
        if (tmp_portMap != null) {
          for (String p_id : tmp_portMap.keySet()) {
            ArrayList<String> tmp_port_ids;
            if (port_id_map.containsKey(i)) {
              tmp_port_ids = ((ArrayList<String>) port_id_map.get(i));
              if (!tmp_port_ids.contains(p_id)) {
                tmp_port_ids.add(p_id);
              }
            } else {
              tmp_port_ids = new ArrayList<String>();
              tmp_port_ids.add(p_id);
              port_id_map.put(i, tmp_port_ids);
            }
          }
        }
        port_ip_map = tmp_portMap;
        // Collect information about the compute nodes...
        for (Server s : os_client.compute().servers().list()) {
          for (String vnfci_id : vnfci_name_map.keySet()) {
            if (vnfci_name_map.get(vnfci_id).equals(s.getName())) {
              //vnf_host_compute_map.put(vnfr.getName(), s.getHypervisorHostname());
              vnfci_hypervisor_map.put(s.getName(), s.getHypervisorHostname());
            }
          }
        }
      }
      // TODO : collect information about the os networks, to be able to integrate with the Open Baton view on resources
      for (Integer i : port_id_map.keySet()) {
        OSClient os_client = getOSClient(os_vim_map.get(i));
        for (String p_id : ((ArrayList<String>) port_id_map.get(i))) {
          // TODO : avoid contacting the infrastructure to often, maybe there is a better way of collecting all information in before
          port_net_map.put(p_id, os_client.networking().port().get(p_id).getNetworkId());
        }
        //for(Network n : tmp_os.networking().network().list()){
        //  net_name_map.put(n.getId(),n.getId());
        //}
      }
      // Well we should collect the network names together with their id's

      this.osOverview.setOs_port_ids(port_id_map);
      this.osOverview.setOs_port_ips(port_ip_map);
      this.osOverview.setOs_port_net_map(port_net_map);
      this.osOverview.setOs_net_names(net_name_map);
      this.osOverview.setVnfci_hypervisors(vnfci_hypervisor_map);

      // In the very end add the hosts and hypervisors which did not belong to any NSR
      //this.osOverview.setNodes(complete_computeNodeMap);
      //this.osOverview.setProjects(project_nsr_map);
      //logger.debug("updated overview");
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
