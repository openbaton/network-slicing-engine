package org.openbaton.nse.api;

import org.apache.tomcat.util.bcel.classfile.Constant;
import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.descriptor.InternalVirtualLink;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.viminstances.BaseVimInstance;
import org.openbaton.catalogue.nfvo.viminstances.OpenstackVimInstance;
import org.openbaton.catalogue.nfvo.networks.BaseNetwork;
import org.openbaton.catalogue.security.Project;
import org.openbaton.nse.adapters.openstack.NeutronQoSExecutor;
import org.openbaton.nse.adapters.openstack.NeutronQoSHandler;
import org.openbaton.nse.monitoring.ZabbixChecker;
import org.openbaton.nse.monitoring.ZabbixPluginCaller;
import org.openbaton.nse.utils.api.NetworkStatistic;
import org.openbaton.nse.utils.openstack.OpenStackTools;
import org.openbaton.nse.utils.openbaton.OpenBatonTools;
import org.openbaton.nse.properties.NfvoProperties;
import org.openbaton.nse.properties.NseProperties;
import org.openbaton.nse.utils.api.NetworkOverview;
import org.openbaton.nse.utils.openstack.OpenStackBandwidthRule;
import org.openbaton.nse.utils.openstack.OpenStackNetwork;
import org.openbaton.nse.utils.openstack.OpenStackPort;
import org.openbaton.nse.utils.openstack.OpenStackQoSPolicy;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.NfvoRequestorBuilder;
import org.openbaton.sdk.api.exception.SDKException;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.network.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileNotFoundException;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Created by lgr on 3/1/18.
 */
@RestController
public class Api {
  private static Logger logger = LoggerFactory.getLogger(Api.class);
  private NetworkOverview osOverview = new NetworkOverview();
  private NetworkStatistic osStatistic = new NetworkStatistic();
  //private ArrayList<VimInstance> vim_list = new ArrayList<>();
  private final List<BaseVimInstance> vim_list = Collections.synchronizedList(new ArrayList<>());
  //private ArrayList<VirtualNetworkFunctionRecord> vnfr_list = new ArrayList<>();
  private final List<VirtualNetworkFunctionRecord> vnfr_list =
      Collections.synchronizedList(new ArrayList<>());

  @SuppressWarnings("unused")
  @Autowired
  private ZabbixChecker zabbixChecker;

  @SuppressWarnings("unused")
  @Autowired
  private NeutronQoSHandler neutron_handler;

  @SuppressWarnings("unused")
  @Autowired
  private OpenStackTools osTools;

  @SuppressWarnings("unused")
  @Autowired
  private OpenBatonTools obTools;

  private HashMap<Integer, String> vim_hashes = new HashMap<>();
  private HashMap<Integer, ArrayList<String>> int_vim_map = new HashMap<>();

  @SuppressWarnings("unused")
  @Autowired
  private NfvoProperties nfvo_configuration;

  @SuppressWarnings("unused")
  @Autowired
  private NseProperties nse_configuration;

  public List<VirtualNetworkFunctionRecord> getVnfr_list() {
    return vnfr_list;
  }

  public List<BaseVimInstance> getVim_list() {
    return vim_list;
  }

  // Function so signal the API that there is the need to pull latest data from a VIM instance
  public void notifyChange(String external_vim_id) {
    for (int int_vim_id : int_vim_map.keySet()) {
      ArrayList<String> ext_vims = int_vim_map.get(int_vim_id);
      if (ext_vims.contains(external_vim_id)) {
        //logger.debug("Generating new hash for : " + int_vim_id);
        vim_hashes.put(int_vim_id, UUID.randomUUID().toString());
        this.updateNetworkOverview();
        return;
      }
    }
    //logger.warn("Did not found " + external_vim_id + " in : " + int_vim_map.toString());
  }

  @CrossOrigin(origins = "*")
  @RequestMapping("/overview")
  @SuppressWarnings("unused")
  public NetworkOverview getOverview() {
    updateNetworkOverview();
    return this.osOverview;
  }

  @CrossOrigin(origins = "*")
  @RequestMapping("/statistics")
  @SuppressWarnings("unused")
  public NetworkStatistic getStatistic(
      @RequestParam(value = "history", defaultValue = "1") String history) {
    Integer length = Integer.parseInt(history);
    return zabbixChecker.getStatistic();
  }

  // Method to be called by the NSE-GUI to apply bandwidth limitations directly on a whole network
  @CrossOrigin(origins = "*")
  @RequestMapping("/assign-net-policy")
  @SuppressWarnings("unused")
  public void assignNetPolicy(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim,
      @RequestParam(value = "net", defaultValue = "net_id") String net,
      @RequestParam(value = "policy", defaultValue = "no_policy") String policy) {
    logger.debug(
        "Received assign QoS policy request for vim : "
            + vim
            + " in project "
            + project
            + " network : "
            + net
            + " policy : "
            + policy);
    synchronized (vim_list) {
      for (BaseVimInstance v : vim_list) {
        if (v.getId().equals(vim)) {
          if (OpenstackVimInstance.class.isInstance(v)) {
            OpenstackVimInstance osV = (OpenstackVimInstance) v;
            NFVORequestor nfvoRequestor =
                obTools.getNFVORequestor(
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    project,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(osV);
            String token = osTools.getAuthToken(tmp_os, osV);
            String neutron_access = osTools.getNeutronEndpoint(osV);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.assignQoSPolicyToNetwork(net, policy);
          } else {
            logger.warn("VIM type " + v.getType() + " not supported yet");
          }
        }
      }
      this.notifyChange(vim);
    }
  }

  // Method to be called by the NSE-GUI to apply bandwidth limitations directly on a port
  @CrossOrigin(origins = "*")
  @RequestMapping("/assign-port-policy")
  @SuppressWarnings("unused")
  public void assignPortPolicy(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim,
      @RequestParam(value = "port", defaultValue = "port_id") String port,
      @RequestParam(value = "policy", defaultValue = "no_policy") String policy) {
    logger.debug(
        "Received assign QoS policy request for vim : "
            + vim
            + " in project "
            + project
            + " port : "
            + port
            + " policy : "
            + policy);
    synchronized (vim_list) {
      for (BaseVimInstance v : vim_list) {
        if (v.getId().equals(vim)) {
          if (OpenstackVimInstance.class.isInstance(v)) {
            OpenstackVimInstance osV = (OpenstackVimInstance) v;
            NFVORequestor nfvoRequestor =
                obTools.getNFVORequestor(
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    project,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(osV);
            String token = osTools.getAuthToken(tmp_os, osV);
            String neutron_access = osTools.getNeutronEndpoint(osV);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.assignQoSPolicyToPort(port, policy);
          } else {
            logger.warn("VIM type " + v.getType() + " not supported yet");
          }
        }
      }
      this.notifyChange(vim);
    }
  }

  // Method to be called by the NSE-GUI to delete QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/policy-delete")
  @SuppressWarnings("unused")
  public void deletePolicy(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "id", defaultValue = "policy_id") String id,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug(
        "Received delete QoS policy request for vim : "
            + vim
            + " in project "
            + project
            + " policy : "
            + id);
    synchronized (vim_list) {
      for (BaseVimInstance v : vim_list) {
        if (v.getId().equals(vim)) {
          if (OpenstackVimInstance.class.isInstance(v)) {
            OpenstackVimInstance osV = (OpenstackVimInstance) v;
            NFVORequestor nfvoRequestor =
                obTools.getNFVORequestor(
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    project,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(osV);
            String token = osTools.getAuthToken(tmp_os, osV);
            String neutron_access = osTools.getNeutronEndpoint(osV);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.deleteQoSPolicy(id);
          } else {
            logger.warn("VIM type " + v.getType() + " not supported yet");
          }
        }
      }
      this.notifyChange(vim);
    }
  }

  // Method to be called by the NSE-GUI to delete QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/bandwidth-rule-delete")
  @SuppressWarnings("unused")
  public void deleteBandwidthRule(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "policy_id", defaultValue = "policy_id") String policy_id,
      @RequestParam(value = "rule_id", defaultValue = "rule_id") String rule_id,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug(
        "Received delete bandwidth rule request for vim : "
            + vim
            + " in project "
            + project
            + " policy : "
            + policy_id
            + " rule : "
            + rule_id);
    synchronized (vim_list) {
      for (BaseVimInstance v : vim_list) {
        if (v.getId().equals(vim)) {
          if (OpenstackVimInstance.class.isInstance(v)) {
            OpenstackVimInstance osV = (OpenstackVimInstance) v;
            NFVORequestor nfvoRequestor =
                obTools.getNFVORequestor(
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    project,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(osV);
            String token = osTools.getAuthToken(tmp_os, osV);
            String neutron_access = osTools.getNeutronEndpoint(osV);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.deleteBandwidthRule(rule_id, policy_id);
          } else {
            logger.warn("VIM type " + v.getType() + " not supported yet");
          }
        }
      }
      this.notifyChange(vim);
    }
  }

  // Method to be called by the NSE-GUI to create QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/bandwidth-rule-create")
  @SuppressWarnings("unused")
  public void createBandwidthRule(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "id", defaultValue = "policy_id") String id,
      @RequestParam(value = "type", defaultValue = "bandwidth_limit_rule") String type,
      @RequestParam(value = "burst", defaultValue = "0") String burst,
      @RequestParam(value = "kbps", defaultValue = "0") String kbps,
      @RequestParam(value = "direction", defaultValue = "egress") String direction,
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
        "Received create bandwidth rule request for vim : " + vim + " in project " + project);
    logger.debug(
        "Policy id : " + id + " type : " + type + " max_kbps : " + kbps + " burst : " + burst);
    OpenStackBandwidthRule rule = new OpenStackBandwidthRule();
    rule.setMax_burst_kbps(new Integer(burst));
    rule.setType(type);
    rule.setMax_kbps(new Integer(kbps));
    rule.setDirection(direction);
    synchronized (vim_list) {
      for (BaseVimInstance v : vim_list) {
        if (v.getId().equals(vim)) {
          if (OpenstackVimInstance.class.isInstance(v)) {
            OpenstackVimInstance osV = (OpenstackVimInstance) v;
            NFVORequestor nfvoRequestor =
                obTools.getNFVORequestor(
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    project,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(osV);
            String token = osTools.getAuthToken(tmp_os, osV);
            String neutron_access = osTools.getNeutronEndpoint(osV);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.createBandwidthRule(rule, id);
          } else {
            logger.warn("VIM type " + v.getType() + " not supported yet");
          }
        }
      }
      this.notifyChange(vim);
    }
  }

  // Method to be called by the NSE-GUI to create QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/policy-create")
  @SuppressWarnings("unused")
  public void createPolicy(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "name", defaultValue = "name") String name,
      @RequestParam(value = "type", defaultValue = "bandwidth_limit_rule") String type,
      @RequestParam(value = "burst", defaultValue = "0") String burst,
      @RequestParam(value = "kbps", defaultValue = "0") String kbps,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    if (!burst.matches("[0-9]+")) {
      logger.error(
          "Cannot create QoS policy with max_burst_kbps : \""
              + burst
              + "\" please enter a valid number");
      return;
    }
    if (!kbps.matches("[0-9]+")) {
      logger.error(
          "Cannot create QoS policy with max_kbps : \"" + kbps + "\" please enter a valid number");
      return;
    }
    logger.debug("Received create QoS policy request for vim : " + vim + " in project " + project);
    logger.debug(
        "Name : " + name + " type : " + type + " max_kbps : " + kbps + " burst : " + burst);
    OpenStackQoSPolicy policy = new OpenStackQoSPolicy();
    policy.setName(name);
    ArrayList<OpenStackBandwidthRule> rules = new ArrayList<>();
    rules.add(new OpenStackBandwidthRule(burst, kbps, type));
    policy.setRules(rules);
    synchronized (vim_list) {
      for (BaseVimInstance v : vim_list) {
        if (v.getId().equals(vim)) {
          if (OpenstackVimInstance.class.isInstance(v)) {
            OpenstackVimInstance osV = (OpenstackVimInstance) v;
            NFVORequestor nfvoRequestor =
                obTools.getNFVORequestor(
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    project,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(osV);
            String token = osTools.getAuthToken(tmp_os, osV);
            String neutron_access = osTools.getNeutronEndpoint(osV);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.createQoSPolicy(policy);
          } else {
            logger.warn("VIM type " + v.getType() + " not supported yet");
          }
        }
      }
      this.notifyChange(vim);
    }
  }

  // Method to be called by the NSE-GUI to list networks
  @CrossOrigin(origins = "*")
  @RequestMapping("/list-ports")
  @SuppressWarnings("unused")
  public ArrayList<OpenStackPort> listPorts(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug("Received port list request for vim : " + vim + " in project " + project);
    ArrayList<OpenStackPort> port_list = new ArrayList<>();
    synchronized (vim_list) {
      for (BaseVimInstance v : vim_list) {
        if (v.getId().equals(vim)) {
          if (OpenstackVimInstance.class.isInstance(v)) {
            OpenstackVimInstance osV = (OpenstackVimInstance) v;
            NFVORequestor nfvoRequestor =
                obTools.getNFVORequestor(
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    project,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(osV);
            String token = osTools.getAuthToken(tmp_os, osV);
            String neutron_access = osTools.getNeutronEndpoint(osV);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            port_list = neutron_executor.listPorts();
          } else {
            logger.warn("VIM type " + v.getType() + " not supported yet");
          }
        }
      }
      return port_list;
    }
  }

  // Method to be called by the NSE-GUI to list networks
  @CrossOrigin(origins = "*")
  @RequestMapping("/list-networks")
  @SuppressWarnings("unused")
  public ArrayList<OpenStackNetwork> listNetworks(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug("Received network list request for vim : " + vim + " in project " + project);
    ArrayList<OpenStackNetwork> net_list = new ArrayList<>();
    synchronized (vim_list) {
      for (BaseVimInstance v : vim_list) {
        if (v.getId().equals(vim)) {
          if (OpenstackVimInstance.class.isInstance(v)) {
            OpenstackVimInstance osV = (OpenstackVimInstance) v;
            NFVORequestor nfvoRequestor =
                obTools.getNFVORequestor(
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    project,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(osV);
            String token = osTools.getAuthToken(tmp_os, osV);
            String neutron_access = osTools.getNeutronEndpoint(osV);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            net_list = neutron_executor.listNetworks();
          } else {
            logger.warn("VIM type " + v.getType() + " not supported yet");
          }
        }
      }
      return net_list;
    }
  }

  // Method to be called by the NSE-GUI to list QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/list-qos-policies")
  @SuppressWarnings("unused")
  public ArrayList<OpenStackQoSPolicy> listQoSPolicies(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug("Received list QoS policy list for vim : " + vim + " in project " + project);
    ArrayList<OpenStackQoSPolicy> qos_policy_list = new ArrayList<>();
    synchronized (vim_list) {
      for (BaseVimInstance v : vim_list) {
        if (v.getId().equals(vim)) {
          if (OpenstackVimInstance.class.isInstance(v)) {
            OpenstackVimInstance osV = (OpenstackVimInstance) v;
            NFVORequestor nfvoRequestor =
                obTools.getNFVORequestor(
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    project,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(osV);
            String token = osTools.getAuthToken(tmp_os, osV);
            String neutron_access = osTools.getNeutronEndpoint(osV);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            qos_policy_list = neutron_executor.getNeutronQosRules();
          } else {
            logger.warn("VIM type " + v.getType() + " not supported yet");
          }
        }
      }
      return qos_policy_list;
    }
  }

  // Method to be called by the NSE-GUI to scale out
  @CrossOrigin(origins = "*")
  @RequestMapping("/scale-out")
  @SuppressWarnings("unused")
  public void scaleOut(
      @RequestParam(value = "project", defaultValue = "project_id") String project_id,
      @RequestParam(value = "vnfr", defaultValue = "vnfr_id") String vnfr_id) {
    logger.debug(
        "Received SCALE out request for vnfr " + vnfr_id + " belonging to project " + project_id);
    try {
      NFVORequestor nfvoRequestor =
          obTools.getNFVORequestor(
              nfvo_configuration.getIp(),
              nfvo_configuration.getPort(),
              project_id,
              nse_configuration.getService().getKey());
      synchronized (vnfr_list) {
        for (VirtualNetworkFunctionRecord vnfr : vnfr_list) {
          if (vnfr.getId().equals(vnfr_id)) {
            boolean scaled = false;
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
              if (scaled) break;
              if (vdu.getVnfc_instance().size() < vdu.getScale_in_out()
                  && (vdu.getVnfc().iterator().hasNext())) {
                VNFComponent vnfComponent = vdu.getVnfc().iterator().next();
                nfvoRequestor
                    .getNetworkServiceRecordAgent()
                    .createVNFCInstance(
                        vnfr.getParent_ns_id(),
                        vnfr.getId(),
                        vnfComponent,
                        new ArrayList<>(vdu.getVimInstanceName()));
                scaled = true;
              }
            }
            return;
          }
        }
      }
    } catch (SDKException e) {
      logger.error("Problem instantiating NFVORequestor");
    } //catch (FileNotFoundException e) {
    //logger.error("Problem scaling");
    //}
  }

  // Method to be called by the NSE-GUI to scale in
  @CrossOrigin(origins = "*")
  @RequestMapping("/scale-in")
  @SuppressWarnings("unused")
  public void scaleIn(
      @RequestParam(value = "project", defaultValue = "project_id") String project_id,
      @RequestParam(value = "vnfci", defaultValue = "vnfci_hostname") String vnfci_hostname,
      @RequestParam(value = "vnfr", defaultValue = "vnfr_id") String vnfr_id) {
    logger.debug(
        "Received SCALE in request for vnfr " + vnfr_id + " belonging to project " + project_id);
    try {
      NFVORequestor nfvoRequestor =
          obTools.getNFVORequestor(
              nfvo_configuration.getIp(),
              nfvo_configuration.getPort(),
              project_id,
              nse_configuration.getService().getKey());
      synchronized (vnfr_list) {
        for (VirtualNetworkFunctionRecord vnfr : vnfr_list) {
          if (vnfr.getId().equals(vnfr_id)) {
            boolean scaled = false;
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
              if (scaled) break;
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
                notifyChange(vnfcInstance_remove.getVim_id());
              }
            }
            return;
          }
        }
      }

    } catch (SDKException e) {
      logger.error("Problem instantiating NFVORequestor");
    } //catch (FileNotFoundException e) {
    //logger.error("Problem scaling");
    // }
  }

  private void updateNetworkOverview() {
    NFVORequestor nfvo_nsr_req;
    NFVORequestor nfvo_default_req;
    // Check if there were changes on the VIM ( machines scaled in-out, new nsrs or deleted nsrs )
    ArrayList<Integer> vims_no_update = new ArrayList<>();
    if (this.osOverview != null) {
      if (this.osOverview.getVim_hashes() != null) {
        for (Integer int_vim_id : osOverview.getVim_hashes().keySet()) {
          if (vim_hashes.containsKey(int_vim_id)) {
            // Check for differences
            if (osOverview.getVim_hashes().get(int_vim_id).equals(vim_hashes.get(int_vim_id))) {
              vims_no_update.add(int_vim_id);
            }
          }
        }
      }
    }
    // Set up a variable which contains the already processed vims, we distinguish via the auth_url + user + tenant here
    // to avoid contacting the same infrastructure used in different projects.
    ArrayList<Integer> processed_vims = new ArrayList<>();
    // Set up a map containing all the vim ids listed to the internal generated hash, therefor we use the latest
    // network overview
    HashMap<Integer, ArrayList<String>> vim_map = osOverview.getVims();
    // if its empty, just use an empty HashMap
    if (vim_map == null) {
      vim_map = new HashMap<>();
    }
    // Set up a map containing all external vim ids together with their names
    HashMap<String, String> vim_name_map = new HashMap<>();
    // Set up a map containing all external vim ids together with their names
    HashMap<String, String> vim_type_map = new HashMap<>();
    // Set up a map containing all external vim ids together with their projects in which they are used
    HashMap<String, ArrayList<String>> vim_project_map = new HashMap<>();
    // Set up a map containing all internal vim hashs and related node information ( openstack only currently)
    HashMap<Integer, ArrayList<String>> node_map = new HashMap<>();
    // Set up a map containing all projects ids together with their names
    HashMap<String, String> project_id_map = new HashMap<>();
    // Set up a map containing all projects together with a list of nsr ids
    HashMap<String, ArrayList<String>> project_nsr_map = new HashMap<>();
    // Set up a map containing all projects ids together with their names
    HashMap<String, String> nsr_name_map = new HashMap<>();
    // Set up a map containing all projects together with a list of nsr ids
    HashMap<String, ArrayList<String>> nsr_vnfr_map = new HashMap<>();
    // Set up a map containing all vnf names with their ids
    HashMap<String, String> vnfr_name_map = new HashMap<>();
    // Set up a map containing the vnfs and their networks from Open Baton side ( virtual link records )
    HashMap<String, ArrayList<String>> vnfr_vlr_map = new HashMap<>();
    // Set up a map containing all vlr ids together with their names
    HashMap<String, String> vlr_name_map = new HashMap<>();
    // Set up a map containing all vlr ids together with their assigned bandwidth qualities
    HashMap<String, String> vlr_quality_map = new HashMap<>();
    // Set up a map containing all vnfs and their vdus
    HashMap<String, ArrayList<String>> vnfr_vdu_map = new HashMap<>();
    // Set up a a map containing the names of each vdu listed by their ids
    HashMap<String, String> vdu_name_map = new HashMap<>();
    // Set up a map containing the vdu and their vnfcis
    HashMap<String, ArrayList<String>> vdu_vnfci_map = new HashMap<>();
    // Set up a map containing the vnfci names with their ids ( These are the host names in the end )
    HashMap<String, String> vnfci_name_map = new HashMap<>();
    // Set up a map containing all vnfci hostnames with their related vnf id
    HashMap<String, String> vnfci_vnfr_map = new HashMap<>();
    // Set up a map containing the ips of each vnfci
    HashMap<String, ArrayList<String>> vnfci_ip_map = new HashMap<>();
    // Set up a map containing the names of the networks for each ip
    HashMap<String, String> ip_name_map = new HashMap<>();
    // Set up a map containing the ips of the networks/ip ids
    HashMap<String, String> ip_addresses_map = new HashMap<>();
    // Set up a map containing the vnfci ids together with their vim ids..
    HashMap<String, Integer> vnfci_vim_map = new HashMap<>();
    // Set up a map containing the vdu id together with the maximum number of vnfc instances
    HashMap<String, Integer> vdu_scale_map = new HashMap<>();
    // ###### OpenStack related
    // Set up a map containing the OpenStack port ids listed to the internal hash of the vim
    HashMap<Integer, ArrayList<String>> port_id_map = osOverview.getOs_port_ids();
    if (port_id_map == null) {
      port_id_map = new HashMap<>();
    }
    // Set up a map containing the OpenStack port ids together with all their ip addresses
    HashMap<String, ArrayList<String>> port_ip_map = osOverview.getOs_port_ips();
    if (port_ip_map == null) {
      port_ip_map = new HashMap<>();
    }
    // A list of ips which have to be checked for ports + subnets + nets ( listed by internal hash..)
    HashMap<Integer, ArrayList<String>> ips_to_be_checked = new HashMap<>();
    // A simple map which saves the reference to the osclients ( via a vim instaces )
    HashMap<Integer, BaseVimInstance> os_vim_map = new HashMap<>();
    // Set up a map containing the OpenStack port ids listed with their parent network id
    HashMap<String, String> port_net_map = this.osOverview.getOs_port_net_map();
    if (port_net_map == null) {
      port_net_map = new HashMap<>();
    }
    // Set up a map containing the OpenStack network ids listed with their names
    HashMap<String, String> net_name_map = new HashMap<>();
    // Set up a map containing the vnfci ids listed with their related hypervisor/ compute node
    HashMap<String, String> vnfci_hypervisor_map = this.osOverview.getVnfci_hypervisors();
    if (vnfci_hypervisor_map == null) {
      vnfci_hypervisor_map = new HashMap<>();
    }
    // Set up a map containing the vlr id together with the external network id
    HashMap<String, String> vlr_ext_net_map = new HashMap<>();
    // Set up a map containing the internal vim identifier and a hash
    HashMap<Integer, String> vim_hash_map = new HashMap<>();
    this.osOverview = new NetworkOverview();
    try {
      nfvo_default_req =
          NfvoRequestorBuilder.create()
              .nfvoIp(nfvo_configuration.getIp())
              .nfvoPort(Integer.parseInt(nfvo_configuration.getPort()))
              .username(nfvo_configuration.getUsername())
              .password(nfvo_configuration.getPassword())
              .sslEnabled(nfvo_configuration.getSsl().isEnabled())
              .version("1")
              .build();
      // Iterate over all projects and collect all NSRs
      for (Project project : nfvo_default_req.getProjectAgent().findAll()) {
        //logger.debug("Checking project : " + project.getName());
        nfvo_nsr_req =
            obTools.getNFVORequestor(
                nfvo_configuration.getIp(),
                nfvo_configuration.getPort(),
                project.getId(),
                nse_configuration.getService().getKey());
        List<NetworkServiceRecord> nsr_list = nfvo_nsr_req.getNetworkServiceRecordAgent().findAll();
        if (!nsr_list.isEmpty()) {
          project_id_map.put(project.getId(), project.getName());
        }
        // ###################################################
        for (NetworkServiceRecord nsr : nsr_list) {
          nsr_name_map.put(nsr.getId(), nsr.getName());
          ArrayList<String> tmp_nsrs;
          if (project_nsr_map.containsKey(project.getId())) {
            tmp_nsrs = project_nsr_map.get(project.getId());
            tmp_nsrs.add(nsr.getId());
          } else {
            tmp_nsrs = new ArrayList<>();
            tmp_nsrs.add(nsr.getId());
            project_nsr_map.put(project.getId(), tmp_nsrs);
          }
          for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            // Remove all occurences matching the old id
            synchronized (vnfr_list) {
              for (int x = 0; x < vnfr_list.size(); x++) {
                VirtualNetworkFunctionRecord int_vnfr = vnfr_list.get(x);
                if (int_vnfr.getId().equals(vnfr.getId())) {
                  vnfr_list.remove(int_vnfr);
                }
              }
              vnfr_list.add(vnfr);
            }
            vnfr_name_map.put(vnfr.getId(), vnfr.getName());
            ArrayList<String> tmp_vnfs;
            if (nsr_vnfr_map.containsKey(nsr.getId())) {
              tmp_vnfs = nsr_vnfr_map.get(nsr.getId());
              tmp_vnfs.add(vnfr.getId());
            } else {
              tmp_vnfs = new ArrayList<>();
              tmp_vnfs.add(vnfr.getId());
              nsr_vnfr_map.put(nsr.getId(), tmp_vnfs);
            }
            for (InternalVirtualLink vlr : vnfr.getVirtual_link()) {
              ArrayList<String> tmp_vlrs;
              if (vnfr_vlr_map.containsKey(vnfr.getId())) {
                tmp_vlrs = vnfr_vlr_map.get(vnfr.getId());
                tmp_vlrs.add(vlr.getId());
              } else {
                tmp_vlrs = new ArrayList<>();
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
                tmp_vdus = vnfr_vdu_map.get(vnfr.getId());
                tmp_vdus.add(vdu.getId());
              } else {
                tmp_vdus = new ArrayList<>();
                tmp_vdus.add(vdu.getId());
                vnfr_vdu_map.put(vnfr.getId(), tmp_vdus);
              }
              vdu_name_map.put(vdu.getId(), vdu.getName());
              for (VNFCInstance vnfci : vdu.getVnfc_instance()) {
                vnfci_name_map.put(vnfci.getId(), vnfci.getHostname());
                vnfci_vnfr_map.put(vnfci.getHostname(), vnfr.getId());
                ArrayList<String> tmp_vnfcis;
                if (vdu_vnfci_map.containsKey(vdu.getId())) {
                  tmp_vnfcis = vdu_vnfci_map.get(vdu.getId());
                  tmp_vnfcis.add(vnfci.getId());
                } else {
                  tmp_vnfcis = new ArrayList<>();
                  tmp_vnfcis.add(vnfci.getId());
                  vdu_vnfci_map.put(vdu.getId(), tmp_vnfcis);
                }
                BaseVimInstance tmp_vim = obTools.getVimInstance(nfvo_nsr_req, vnfci.getVim_id());
                if (OpenstackVimInstance.class.isInstance(tmp_vim)) {
                  OpenstackVimInstance tmp_os_vim = (OpenstackVimInstance) tmp_vim;

                  for (int x = 0; x < vim_list.size(); x++) {
                    BaseVimInstance vim = vim_list.get(x);
                    if (vim.getId().equals(tmp_vim.getId())) {
                      vim_list.remove(vim);
                    }
                  }
                  vim_list.add(tmp_vim);
                  ArrayList<String> tmp_list;
                  if (vim_project_map.containsKey(tmp_vim.getId())) {
                    tmp_list = vim_project_map.get(tmp_vim.getId());
                    if (!tmp_list.contains(project.getId())) {
                      tmp_list.add(project.getId());
                    }
                  } else {
                    tmp_list = new ArrayList<>();
                    tmp_list.add(project.getId());
                    vim_project_map.put(tmp_vim.getId(), tmp_list);
                  }
                  // Generate an identifier internally to not distinguish vims by their internal id but at other crucial information to avoid contacting the same infrastructure
                  int vim_identifier =
                      (tmp_vim.getAuthUrl() + tmp_os_vim.getUsername() + tmp_os_vim.getTenant())
                              .hashCode()
                          & 0xfffffff;
                  if (!vim_name_map.containsKey(tmp_vim.getId())) {
                    vim_name_map.put(tmp_vim.getId(), tmp_vim.getName());
                    vim_type_map.put(tmp_vim.getId(), tmp_vim.getType());
                    for (BaseNetwork n : tmp_os_vim.getNetworks()) {
                      net_name_map.put(n.getExtId(), n.getName());
                    }
                  }
                  ArrayList<String> vlrs = vnfr_vlr_map.get(vnfr.getId());
                  for (BaseNetwork n : tmp_vim.getNetworks()) {
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
                      tmp_ips = vnfci_ip_map.get(vnfci.getId());
                      tmp_ips.add(ip.getId());
                    } else {
                      tmp_ips = new ArrayList<>();
                      tmp_ips.add(ip.getId());
                      vnfci_ip_map.put(vnfci.getId(), tmp_ips);
                    }
                    ArrayList<String> tmp_ip_list;
                    if (ips_to_be_checked.containsKey(vim_identifier)) {
                      tmp_ip_list = ips_to_be_checked.get(vim_identifier);
                      tmp_ip_list.add(ip.getIp());
                    } else {
                      tmp_ip_list = new ArrayList<>();
                      tmp_ip_list.add(ip.getIp());
                      ips_to_be_checked.put(vim_identifier, tmp_ip_list);
                    }
                  }
                  if (!processed_vims.contains(vim_identifier)) {
                    processed_vims.add(vim_identifier);
                    ArrayList<String> tmp_vim_ids = new ArrayList<>();
                    tmp_vim_ids.add(tmp_vim.getId());
                    vim_map.put(vim_identifier, tmp_vim_ids);
                    int_vim_map.put(vim_identifier, tmp_vim_ids);

                    if (tmp_vim.getType().equals("openstack")) {
                      OSClient tmp_os = osTools.getOSClient(tmp_os_vim);
                      //if (!os_client_map.containsKey(vim_identifier)) {
                      //  os_client_map.put(vim_identifier, tmp_os);
                      //}
                      if (!os_vim_map.containsKey(vim_identifier)) {
                        os_vim_map.put(vim_identifier, tmp_vim);
                      }
                      Map<String, String> tmp_computeNodeMap = osTools.getComputeNodeMap(tmp_os);
                      if (tmp_computeNodeMap != null) {
                        // We collect all involved compute nodes
                        ArrayList<String> tmp_node_names = new ArrayList<>();
                        for (String key : tmp_computeNodeMap.keySet()) {

                          tmp_node_names.add(key);
                        }
                        node_map.put(vim_identifier, tmp_node_names);
                      }
                    }
                  } else {
                    // in this case we already found the vim via the internal generated hash and only need to append the vim id to the hash in the map
                    ArrayList<String> vim_ids = vim_map.get(vim_identifier);
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
      //this.osOverview.setCurrent_hash(new String(this.curr_hash));
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
        if (!vims_no_update.contains(i)) {
          // clear old entries
          //String new_hash = UUID.randomUUID().toString();
          //vim_hash_map.put(i, new_hash);
          //vim_hashes.put(i, new_hash);
          if (os_vim_map.containsKey(i)) {
            if (OpenstackVimInstance.class.isInstance(os_vim_map.get(i))) {
              OpenstackVimInstance osV = (OpenstackVimInstance) os_vim_map.get(i);
              OSClient os_client = osTools.getOSClient(osV);
              HashMap<String, ArrayList<String>> tmp_portMap =
                  osTools.getPortIps(os_client, ips_to_be_checked.get(i));
              //logger.debug(tmp_portMap.toString());
              // remove all old entries
              if (port_id_map.containsKey(i)) {
                for (String key : port_id_map.get(i)) {
                  port_ip_map.remove(key);
                  //net_name_map.remove(port_net_map.get(key));
                  port_net_map.remove(key);
                }
                // remove entry itself
                port_id_map.remove(i);
              }
              if (tmp_portMap != null) {
                for (String p_id : tmp_portMap.keySet()) {
                  ArrayList<String> tmp_port_ids;
                  if (port_id_map.containsKey(i)) {
                    tmp_port_ids = port_id_map.get(i);
                    if (!tmp_port_ids.contains(p_id)) {
                      tmp_port_ids.add(p_id);
                    }
                  } else {
                    tmp_port_ids = new ArrayList<>();
                    tmp_port_ids.add(p_id);
                    port_id_map.put(i, tmp_port_ids);
                  }
                }
              }
              for (String key : tmp_portMap.keySet()) {
                port_ip_map.put(key, tmp_portMap.get(key));
              }
              //port_ip_map = tmp_portMap;
              // Collect information about the compute nodes...
              // TODO : Clear the vnfci_hypervisor map as done for the port ip + port id + port net maps
              for (Server s : os_client.compute().servers().list()) {
                for (String vnfci_id : vnfci_name_map.keySet()) {
                  if (vnfci_name_map.get(vnfci_id).equals(s.getName())) {
                    //vnf_host_compute_map.put(vnfr.getName(), s.getHypervisorHostname());
                    vnfci_hypervisor_map.put(s.getName(), s.getHypervisorHostname());
                  }
                }
              }
            }
          }
        } else {
          vim_hash_map.put(i, vim_hashes.get(i));
        }
      }
      // TODO : collect information about the os networks, to be able to integrate with the Open Baton view on resources
      for (Integer i : port_id_map.keySet()) {
        if (!vims_no_update.contains(i)) {
          if (os_vim_map.containsKey(i)) {
            if (OpenstackVimInstance.class.isInstance(os_vim_map.get(i))) {
              OpenstackVimInstance osV = (OpenstackVimInstance) os_vim_map.get(i);
              OSClient os_client = osTools.getOSClient(osV);
              for (String p_id : port_id_map.get(i)) {
                // TODO : avoid contacting the infrastructure to often, maybe there is a better way of collecting all information in before
                Port p = os_client.networking().port().get(p_id);
                if (p != null) {
                  port_net_map.put(p_id, p.getNetworkId());
                }
              }
            }
          }
          //for(Network n : tmp_os.networking().network().list()){
          //  net_name_map.put(n.getId(),n.getId());
          //}
        }
      }
      // Well we should collect the network names together with their id's

      //this.osOverview.setVim_hashes(vim_hash_map);

      this.osOverview.setOs_port_ids(port_id_map);
      this.osOverview.setOs_port_ips(port_ip_map);
      this.osOverview.setOs_port_net_map(port_net_map);
      this.osOverview.setOs_net_names(net_name_map);
      this.osOverview.setVnfci_hypervisors(vnfci_hypervisor_map);
      this.osOverview.setVlr_ext_networks(vlr_ext_net_map);

      for (Integer i : node_map.keySet()) {
        if (!vims_no_update.contains(i)) {
          // clear old entries
          String new_hash = UUID.randomUUID().toString();
          vim_hash_map.put(i, new_hash);
          vim_hashes.put(i, new_hash);
        }
      }

      this.osOverview.setVim_hashes(vim_hash_map);

      if (nse_configuration.getZabbix()) {
        for (String host : vnfci_name_map.values()) {
          zabbixChecker.addHost(host);
        }
      }

      //zabbix.startPolling(hosts, metrics);
      //zabbix.pollValues(hosts, metrics);

      //this.int_vim_map = vim_map;

      // In the very end add the hosts and hypervisors which did not belong to any NSR
      //this.osOverview.setNodes(complete_computeNodeMap);
      //this.osOverview.setProjects(project_nsr_map);
      //logger.debug("updated overview");

    } catch (SDKException e) {
      logger.error("Problem instantiating NFVORequestor");
    } //catch (FileNotFoundException e) {
    //e.printStackTrace();
    //}
  }
}
