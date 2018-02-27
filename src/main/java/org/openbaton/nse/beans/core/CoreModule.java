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

import org.json.JSONArray;
import org.json.JSONObject;
import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.descriptor.InternalVirtualLink;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.catalogue.security.Project;
import org.openbaton.nse.beans.adapters.openstack.NeutronQoSExecutor;
import org.openbaton.nse.beans.adapters.openstack.NeutronQoSHandler;
import org.openbaton.nse.utils.*;
import org.openbaton.nse.properties.NseProperties;
import org.openbaton.nse.properties.NfvoProperties;
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

  private String curr_hash = UUID.randomUUID().toString();

  private ArrayList<VirtualNetworkFunctionRecord> vnfr_list =
      new ArrayList<VirtualNetworkFunctionRecord>();

  private ArrayList<VimInstance> vim_list = new ArrayList<VimInstance>();

  private OpenStackOverview osOverview = new OpenStackOverview();

  @Autowired private NfvoProperties nfvo_configuration;
  @Autowired private NseProperties nse_configuration;

  @PostConstruct
  private void init() {
    this.logger = LoggerFactory.getLogger(this.getClass());
  }

  // Function to be called if there a NSR has been scaled, deleted or instantiated
  public void notifyChange() {
    curr_hash = UUID.randomUUID().toString();
  }

  /*
   * To allow different types of NFVI besides OpenStack
   * it will be necessary to split up the set of VNFRs
   * here to then create thread for each type of VNFI
   * instead of pushing everything to the thead
   * responsible for OpenStack Neutron..
   */

  public void addQos(Set<VirtualNetworkFunctionRecord> vnfrs, String nsrId) {
    // Generate a new hash
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
        // Remove all occurences matching the old id
        for (int x = 0; x < vnfr_list.size(); x++) {
          VirtualNetworkFunctionRecord int_vnfr = vnfr_list.get(x);
          if (int_vnfr.getId().equals(vnfr.getId())) {
            vnfr_list.remove(int_vnfr);
          }
        }
        vnfr_list.add(vnfr);

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
      if (v == null) {
        logger.warn("Problem generating the credentials");
        return cred;
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
    // Check if the there were any addQos / removeQos tasks in the meanwhile ( relates to any scale, error or instantiate events )
    // to avoid running unnecessary tasks if there were no changes at all...
    if (this.osOverview != null) {
      if (this.osOverview.getCurrent_hash() != null) {
        if (this.osOverview.getCurrent_hash().equals(this.curr_hash)) {
          return;
        }
      }
    }

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
    // Set up a map containing all external vim ids together with their projects in which they are used
    HashMap<String, Object> vim_project_map = new HashMap<String, Object>();
    // Set up a map containing all internal vim hashs and related node information ( openstack only currently)
    HashMap<Integer, Object> node_map = new HashMap<Integer, Object>();
    // Set up a map containing all projects ids together with their names
    HashMap<String, String> project_id_map = new HashMap<String, String>();
    // Set up a map containing all projects together with a list of nsr ids
    HashMap<String, Object> project_nsr_map = new HashMap<String, Object>();
    // Set up a map containing all projects ids together with their names
    HashMap<String, String> nsr_name_map = new HashMap<String, String>();
    // Set up a map containing all projects together with a list of nsr ids
    HashMap<String, Object> nsr_vnfr_map = new HashMap<String, Object>();
    // Set up a map containing all vnf names with their ids
    HashMap<String, String> vnfr_name_map = new HashMap<String, String>();
    // Set up a map containing the vnfs and their networks from Open Baton side ( virtual link records )
    HashMap<String, Object> vnfr_vlr_map = new HashMap<String, Object>();
    // Set up a map containing all vlr ids together with their names
    HashMap<String, String> vlr_name_map = new HashMap<String, String>();
    // Set up a map containing all vlr ids together with their assigned bandwidth qualities
    HashMap<String, String> vlr_quality_map = new HashMap<String, String>();
    // Set up a map containing all vnfs and their vdus
    HashMap<String, Object> vnfr_vdu_map = new HashMap<String, Object>();
    // Set up a a map containing the names of each vdu listed by their ids
    HashMap<String, String> vdu_name_map = new HashMap<String, String>();
    // Set up a map containing the vdu and their vnfcis
    HashMap<String, Object> vdu_vnfci_map = new HashMap<String, Object>();
    // Set up a map containing the vnfci names with their ids ( These are the host names in the end )
    HashMap<String, String> vnfci_name_map = new HashMap<String, String>();
    // Set up a map containing all vnfci hostnames with their related vnf id
    HashMap<String, String> vnfci_vnfr_map = new HashMap<String, String>();
    // Set up a map containing the ips of each vnfci
    HashMap<String, Object> vnfci_ip_map = new HashMap<String, Object>();
    // Set up a map containing the names of the networks for each ip
    HashMap<String, String> ip_name_map = new HashMap<String, String>();
    // Set up a map containing the ips of the networks/ip ids
    HashMap<String, String> ip_addresses_map = new HashMap<String, String>();
    // Set up a map containing the vnfci ids together with their vim ids..
    HashMap<String, Integer> vnfci_vim_map = new HashMap<String, Integer>();
    // Set up a map containing the vdu id together with the maximum number of vnfc instances
    HashMap<String, Integer> vdu_scale_map = new HashMap<String, Integer>();

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

    // Set up a map containing the vlr id together with the external network id
    HashMap<String, String> vlr_ext_net_map = new HashMap<String, String>();

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
              // Remove all occurences matching the old id
              for (int x = 0; x < vnfr_list.size(); x++) {
                VirtualNetworkFunctionRecord int_vnfr = vnfr_list.get(x);
                if (int_vnfr.getId().equals(vnfr.getId())) {
                  vnfr_list.remove(int_vnfr);
                }
              }
              vnfr_list.add(vnfr);
              vnfr_name_map.put(vnfr.getId(), vnfr.getName());
              ArrayList<String> tmp_vnfs;
              if (nsr_vnfr_map.containsKey(nsr.getId())) {
                tmp_vnfs = ((ArrayList<String>) nsr_vnfr_map.get(nsr.getId()));
                tmp_vnfs.add(vnfr.getId());
              } else {
                tmp_vnfs = new ArrayList<String>();
                tmp_vnfs.add(vnfr.getId());
                nsr_vnfr_map.put(nsr.getId(), tmp_vnfs);
              }
              for (InternalVirtualLink vlr : vnfr.getVirtual_link()) {
                ArrayList<String> tmp_vlrs;
                if (vnfr_vlr_map.containsKey(vnfr.getId())) {
                  tmp_vlrs = ((ArrayList<String>) vnfr_vlr_map.get(vnfr.getId()));
                  tmp_vlrs.add(vlr.getId());
                } else {
                  tmp_vlrs = new ArrayList<String>();
                  tmp_vlrs.add(vlr.getId());
                  vnfr_vlr_map.put(vnfr.getId(), tmp_vlrs);
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
                vdu_scale_map.put(vdu.getId(), vdu.getScale_in_out());
                ArrayList<String> tmp_vdus;
                if (vnfr_vdu_map.containsKey(vnfr.getId())) {
                  tmp_vdus = ((ArrayList<String>) vnfr_vdu_map.get(vnfr.getId()));
                  tmp_vdus.add(vdu.getId());
                } else {
                  tmp_vdus = new ArrayList<String>();
                  tmp_vdus.add(vdu.getId());
                  vnfr_vdu_map.put(vnfr.getId(), tmp_vdus);
                }
                vdu_name_map.put(vdu.getId(), vdu.getName());
                for (VNFCInstance vnfci : vdu.getVnfc_instance()) {
                  vnfci_name_map.put(vnfci.getId(), vnfci.getHostname());
                  vnfci_vnfr_map.put(vnfci.getHostname(), vnfr.getId());
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
                  //if (!vim_list.contains(tmp_vim)) {
                  //  vim_list.add(tmp_vim);
                  //}
                  // Remove all occurences matching the old id
                  for (int x = 0; x < vim_list.size(); x++) {
                    VimInstance vim = vim_list.get(x);
                    if (vim.getId().equals(tmp_vim.getId())) {
                      vim_list.remove(vim);
                    }
                  }
                  vim_list.add(tmp_vim);
                  ArrayList<String> tmp_list;
                  if (vim_project_map.containsKey(tmp_vim.getId())) {
                    tmp_list = (ArrayList<String>) vim_project_map.get(tmp_vim.getId());
                    if (!tmp_list.contains(project.getId())) {
                      tmp_list.add(project.getId());
                    }
                  } else {
                    tmp_list = new ArrayList<String>();
                    tmp_list.add(project.getId());
                    vim_project_map.put(tmp_vim.getId(), tmp_list);
                  }
                  // Generate an identifier internally to not distinguish vims by their internal id but at other crucial information to avoid contacting the same infrastructure
                  int vim_identifier =
                      (tmp_vim.getAuthUrl() + tmp_vim.getUsername() + tmp_vim.getTenant())
                              .hashCode()
                          & 0xfffffff;
                  if (!vim_name_map.containsKey(tmp_vim.getId())) {
                    vim_name_map.put(tmp_vim.getId(), tmp_vim.getName());
                    vim_type_map.put(tmp_vim.getId(), tmp_vim.getType());
                    //ArrayList<String> vlrs = (ArrayList<String>) vnfr_vlr_map.get(vnfr.getId());
                    for (org.openbaton.catalogue.nfvo.Network n : tmp_vim.getNetworks()) {
                      net_name_map.put(n.getExtId(), n.getName());
                      //for (String vlr_id : vlrs) {
                      //  if (vlr_name_map.get(vlr_id).equals(n.getName())) {
                      //    vlr_ext_net_map.put(vlr_id, n.getExtId());
                      //  }
                      //}
                    }
                  }
                  ArrayList<String> vlrs = (ArrayList<String>) vnfr_vlr_map.get(vnfr.getId());
                  for (org.openbaton.catalogue.nfvo.Network n : tmp_vim.getNetworks()) {
                    for (String vlr_id : vlrs) {
                      if (vlr_name_map.get(vlr_id).equals(n.getName())) {
                        vlr_ext_net_map.put(vlr_id, n.getExtId());
                      }
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
      this.osOverview.setCurrent_hash(new String(this.curr_hash));
      this.osOverview.setVims(vim_map);
      this.osOverview.setVim_names(vim_name_map);
      this.osOverview.setVim_types(vim_type_map);
      this.osOverview.setVim_projects(vim_project_map);
      this.osOverview.setOs_nodes(node_map);
      this.osOverview.setProjects(project_id_map);
      this.osOverview.setNsrs(project_nsr_map);
      this.osOverview.setNsr_names(nsr_name_map);
      this.osOverview.setVnfr_names(vnfr_name_map);
      this.osOverview.setNsr_vnfrs(nsr_vnfr_map);
      this.osOverview.setVnfr_vlrs(vnfr_vlr_map);
      this.osOverview.setVlr_names(vlr_name_map);
      this.osOverview.setVlr_qualities(vlr_quality_map);
      this.osOverview.setVnfr_vdus(vnfr_vdu_map);
      this.osOverview.setVdu_names(vdu_name_map);
      this.osOverview.setVdu_vnfcis(vdu_vnfci_map);
      this.osOverview.setVnfci_names(vnfci_name_map);
      this.osOverview.setVnfci_vnfr(vnfci_vnfr_map);
      this.osOverview.setVnfci_ips(vnfci_ip_map);
      this.osOverview.setVdu_scale(vdu_scale_map);
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
      this.osOverview.setVlr_ext_networks(vlr_ext_net_map);

      //logger.debug(vnfr_list.toString());

      // In the very end add the hosts and hypervisors which did not belong to any NSR
      //this.osOverview.setNodes(complete_computeNodeMap);
      //this.osOverview.setProjects(project_nsr_map);
      //logger.debug("updated overview");
    } catch (SDKException e) {
      logger.error("Problem instantiating NFVORequestor");
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  @CrossOrigin(origins = "*")
  @RequestMapping("/overview")
  public OpenStackOverview getOverview() {
    updateOpenStackOverview();
    return this.osOverview;
  }

  // Method to be called by the NSE-GUI to apply bandwidth limitations directly
  @CrossOrigin(origins = "*")
  @RequestMapping("/assign-policy")
  public void assignPolicy(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim,
      @RequestParam(value = "port", defaultValue = "port_id") String port,
      @RequestParam(value = "policy", defaultValue = "no-qos-policy") String policy) {
    logger.debug(
        "Received assign policy request for vim : "
            + vim
            + " in project "
            + project
            + " port : "
            + port
            + " policy : "
            + policy);
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {
          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = getOSClient(v);
            logger.debug("Found OSclient");
            String token = getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = getNeutronEndpoint(tmp_os, v, token);
            Map<String, String> creds = getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.assignQoSPolicyToPort(port, policy);
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    this.notifyChange();
  }

  // Method to be called by the NSE-GUI to delete QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/policy-delete")
  public void deletePolicy(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "id", defaultValue = "policy_id") String id,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug(
        "Received QoS policy delete request for vim : "
            + vim
            + " in project "
            + project
            + " policy : "
            + id);
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {

          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = getOSClient(v);
            logger.debug("Found OSclient");
            String token = getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = getNeutronEndpoint(tmp_os, v, token);
            Map<String, String> creds = getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.deleteQoSPolicy(id);
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    this.notifyChange();
  }

  // Method to be called by the NSE-GUI to delete QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/bandwidth-rule-delete")
  public void deleteBandwidthRule(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "policy_id", defaultValue = "policy_id") String policy_id,
      @RequestParam(value = "rule_id", defaultValue = "rule_id") String rule_id,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug(
        "Received bandwidth rule delete request for vim : "
            + vim
            + " in project "
            + project
            + " policy : "
            + policy_id
            + " rule : "
            + rule_id);
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {

          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = getOSClient(v);
            logger.debug("Found OSclient");
            String token = getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = getNeutronEndpoint(tmp_os, v, token);
            Map<String, String> creds = getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.deleteBandwidthRule(rule_id, policy_id);
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    this.notifyChange();
  }

  // Method to be called by the NSE-GUI to create QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/bandwidth-rule-create")
  public void createBandwidthRule(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "id", defaultValue = "policy_id") String id,
      @RequestParam(value = "type", defaultValue = "bandwidth_limit_rule") String type,
      @RequestParam(value = "burst", defaultValue = "0") String burst,
      @RequestParam(value = "kbps", defaultValue = "0") String kbps,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    if (!burst.matches("[0-9]+")) {
      logger.error(
          "Cannot create bandwidth rule with max_kbps : \""
              + burst
              + "\" please enter a valid number");
      return;
    }
    if (!kbps.matches("[0-9]+")) {
      logger.error(
          "Cannot create bandwidth rule with max_kbps : \""
              + kbps
              + "\" please enter a valid number");
      return;
    }
    logger.debug(
        "Received bandwidth rule create request for vim : " + vim + " in project " + project);
    logger.debug(
        "Policy id : " + id + " type : " + type + " max_kbps : " + kbps + " burst : " + burst);
    OpenStackBandwidthRule rule = new OpenStackBandwidthRule();
    rule.setMax_burst_kbps(new Integer(burst));
    rule.setType(type);
    rule.setMax_kbps(new Integer(kbps));
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {

          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = getOSClient(v);
            logger.debug("Found OSclient");
            String token = getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = getNeutronEndpoint(tmp_os, v, token);
            Map<String, String> creds = getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.createBandwidthRule(rule, id);
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    this.notifyChange();
  }

  // Method to be called by the NSE-GUI to create QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/policy-create")
  public void createPolicy(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "name", defaultValue = "name") String name,
      @RequestParam(value = "type", defaultValue = "bandwidth_limit_rule") String type,
      @RequestParam(value = "burst", defaultValue = "0") String burst,
      @RequestParam(value = "kbps", defaultValue = "0") String kbps,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    if (!burst.matches("[0-9]+")) {
      logger.error(
          "Cannot create policy with max_burst_kbps : \""
              + burst
              + "\" please enter a valid number");
      return;
    }
    if (!kbps.matches("[0-9]+")) {
      logger.error(
          "Cannot create policy with max_kbps : \"" + kbps + "\" please enter a valid number");
      return;
    }
    logger.debug("Received QoS policy create request for vim : " + vim + " in project " + project);
    logger.debug(
        "Name : " + name + " type : " + type + " max_kbps : " + kbps + " burst : " + burst);
    OpenStackQoSPolicy policy = new OpenStackQoSPolicy();
    policy.setName(name);
    ArrayList<OpenStackBandwidthRule> rules = new ArrayList<OpenStackBandwidthRule>();
    rules.add(new OpenStackBandwidthRule(burst, kbps, type));
    policy.setRules(rules);
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {

          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = getOSClient(v);
            logger.debug("Found OSclient");
            String token = getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = getNeutronEndpoint(tmp_os, v, token);
            Map<String, String> creds = getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.createQoSPolicy(policy);
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    this.notifyChange();
  }

  // Method to be called by the NSE-GUI to apply bandwidth limitations directly
  @CrossOrigin(origins = "*")
  @RequestMapping("/list")
  public ArrayList<OpenStackQoSPolicy> list(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug("Received QoS policy list request for vim : " + vim + " in project " + project);
    //String qos_rules = "[]";
    //JSONArray qos_rules = new JSONArray();
    ArrayList<OpenStackQoSPolicy> qos_policy_list = new ArrayList<OpenStackQoSPolicy>();
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {

          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());

            OSClient tmp_os = getOSClient(v);
            logger.debug("Found OSclient");
            String token = getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = getNeutronEndpoint(tmp_os, v, token);
            Map<String, String> creds = getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            qos_policy_list = neutron_executor.getNeutronQosRules();
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    return qos_policy_list;
  }

  // Method to be called by the NSE-GUI to scale out
  @CrossOrigin(origins = "*")
  @RequestMapping("/scale-out")
  public void scaleOut(
      @RequestParam(value = "project", defaultValue = "project_id") String project_id,
      @RequestParam(value = "vnfr", defaultValue = "vnfr_id") String vnfr_id) {
    logger.debug(
        "Received SCALE out operation for vnfr " + vnfr_id + " belonging to project " + project_id);
    try {
      NFVORequestor nfvoRequestor =
          new NFVORequestor(
              "nse",
              project_id,
              nfvo_configuration.getIp(),
              nfvo_configuration.getPort(),
              "1",
              false,
              nse_configuration.getService().getKey());
      for (VirtualNetworkFunctionRecord vnfr : vnfr_list) {
        if (vnfr.getId().equals(vnfr_id)) {
          boolean scaled = false;
          for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            if (scaled == true) break;
            if (vdu.getVnfc_instance().size() < vdu.getScale_in_out()
                && (vdu.getVnfc().iterator().hasNext())) {
              VNFComponent vnfComponent = vdu.getVnfc().iterator().next();
              nfvoRequestor
                  .getNetworkServiceRecordAgent()
                  .createVNFCInstance(
                      vnfr.getParent_ns_id(),
                      vnfr.getId(),
                      vnfComponent,
                      new ArrayList<String>(vdu.getVimInstanceName()));
              scaled = true;
            }
          }
          return;
        }
      }
    } catch (SDKException e) {
      logger.error("Problem instantiating NFVORequestor");
    } catch (FileNotFoundException e) {
      logger.error("Problem scaling");
    }
  }

  // Method to be called by the NSE-GUI to scale in
  @CrossOrigin(origins = "*")
  @RequestMapping("/scale-in")
  public void scaleIn(
      @RequestParam(value = "project", defaultValue = "project_id") String project_id,
      @RequestParam(value = "vnfci", defaultValue = "vnfci_hostname") String vnfci_hostname,
      @RequestParam(value = "vnfr", defaultValue = "vnfr_id") String vnfr_id) {
    logger.debug(
        "Received SCALE in operation for vnfr " + vnfr_id + " belonging to project " + project_id);
    try {
      NFVORequestor nfvoRequestor =
          new NFVORequestor(
              "nse",
              project_id,
              nfvo_configuration.getIp(),
              nfvo_configuration.getPort(),
              "1",
              false,
              nse_configuration.getService().getKey());
      for (VirtualNetworkFunctionRecord vnfr : vnfr_list) {
        if (vnfr.getId().equals(vnfr_id)) {
          boolean scaled = false;
          for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            if (scaled == true) break;
            Set<VNFCInstance> vnfcInstancesToRemove = new HashSet<>();
            for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
              if (vnfcInstance.getState() == null
                  || vnfcInstance.getState().toLowerCase().equals("active")) {
                vnfcInstancesToRemove.add(vnfcInstance);
              }
            }
            if (vnfcInstancesToRemove.size() > 1 && vnfcInstancesToRemove.iterator().hasNext()) {
              // If no specific vnfci id to remove has been set use the default way and remove a random one..
              VNFCInstance vnfcInstance_remove = null;
              if (vnfci_hostname.equals("vnfci_hostname")) {
                vnfcInstance_remove = vnfcInstancesToRemove.iterator().next();
              } else {
                for (VNFCInstance currVnfci : vnfcInstancesToRemove) {
                  if (currVnfci.getHostname().equals(vnfci_hostname)) {
                    vnfcInstance_remove = currVnfci;
                  }
                }
              }
              if (vnfcInstance_remove == null) {
                logger.warn(
                    "Not found VNFCInstance in VDU " + vdu.getId() + " that could be removed");
                break;
              }
              nfvoRequestor
                  .getNetworkServiceRecordAgent()
                  .deleteVNFCInstance(
                      vnfr.getParent_ns_id(),
                      vnfr.getId(),
                      vdu.getId(),
                      vnfcInstance_remove.getId());
              scaled = true;
            }
          }
          return;
        }
      }
    } catch (SDKException e) {
      logger.error("Problem instantiating NFVORequestor");
    } catch (FileNotFoundException e) {
      logger.error("Problem scaling");
    }
    // Currently the NSE does not receive any SCALE-In events ( A VNF needs to have a relation to itself therefor ), thus we have a workaround here
    finally {
      notifyChange();
    }
  }
}
