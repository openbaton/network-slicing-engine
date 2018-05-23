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

package org.openbaton.nse.adapters.openstack;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.descriptor.InternalVirtualLink;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.nse.utils.*;
import org.openbaton.nse.utils.DetailedQoSReference;
import org.openbaton.nse.utils.QoSReference;
import org.openbaton.nse.utils.openstack.OpenStackBandwidthRule;
import org.openbaton.nse.utils.openstack.OpenStackNetwork;
import org.openbaton.nse.utils.openstack.OpenStackPort;
import org.openbaton.nse.utils.openstack.OpenStackQoSPolicy;
import org.openstack4j.model.network.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Created by lgr on 20/09/16. modified by lgr on 20.07.17
 */
@Service
public class NeutronQoSExecutor implements Runnable, Callable {

  @SuppressWarnings("unused")
  public NeutronQoSExecutor() {}

  private Logger logger = LoggerFactory.getLogger(NeutronQoSExecutor.class);

  private Set<VirtualNetworkFunctionRecord> vnfrs;
  private NeutronQoSHandler neutron_handler;
  private VimInstance v;
  private String token;
  private Map<String, String> creds;
  private List<? extends Port> portList;
  private Map<String, String> computeNodeMap;
  private Map<String, String> hostComputeNodeMap;

  public NeutronQoSExecutor(
      Set<VirtualNetworkFunctionRecord> vnfrs,
      NeutronQoSHandler handler,
      String token,
      VimInstance v,
      Map<String, String> creds,
      List<? extends Port> portList,
      Map<String, String> computeNodeMap,
      Map<String, String> hostComputeNodeMap) {
    this.vnfrs = vnfrs;
    this.logger = LoggerFactory.getLogger(this.getClass());
    this.neutron_handler = handler;
    this.token = token;
    this.v = v;
    this.creds = creds;
    this.portList = portList;
    this.computeNodeMap = computeNodeMap;
    this.hostComputeNodeMap = hostComputeNodeMap;
  }

  public NeutronQoSExecutor(
      NeutronQoSHandler handler, String token, VimInstance v, Map<String, String> creds) {
    this.neutron_handler = handler;
    this.token = token;
    this.v = v;
    this.creds = creds;
  }

  @Override
  public void run() {
    String delimiter_line;
    List<DetailedQoSReference> qoses = this.getDetailedQosesRefs(vnfrs);
    String heading = "  # Will work on VIM with name : " + v.getName() + " and id : " + v.getId();
    delimiter_line = "  #";
    for (int n = 0; n < heading.length(); n++) {
      delimiter_line = delimiter_line + "#";
    }
    heading += "  #";
    logger.debug(delimiter_line);
    logger.debug(heading);
    logger.debug(delimiter_line);
    for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
      for (DetailedQoSReference r : qoses) {
        if (vnfr.getName().equals(r.getHostname())) {
          try {
            logger.debug(
                "    # "
                    + r.getHostname()
                    + " -> "
                    + r.getIp()
                    + " -> "
                    + r.getQuality()
                    + " -> "
                    + hostComputeNodeMap.get(vnfr.getName())
                    + " -> "
                    + computeNodeMap.get(hostComputeNodeMap.get(vnfr.getName())));
          } catch (Exception e) {
            logger.warn("  Problem checking on compute node for " + r.getIp());
            logger.debug("/////////////////////////////////////");
            logger.debug(hostComputeNodeMap.toString());
            logger.debug("/////////////////////////////////////");
            logger.debug(computeNodeMap.toString());
          }
        }
      }
      logger.debug("  " + delimiter_line);
      for (DetailedQoSReference r : qoses) {
        if (vnfr.getName().equals(r.getHostname())) {
          logger.debug("    Setting " + r.getIp() + " bandwidth quality to " + r.getQuality());
          Map<String, String> qos_map = getNeutronQoSPolicies(neutron_handler, creds, token);
          if (qos_map == null) {
            // in this case the NFVI (OpenStack) does not seem to support the operation, probably wrong neutron version
            logger.warn(
                "    OpenStack Neutron does not seem to support QoS operations for VIM : "
                    + v.getName()
                    + " with id : "
                    + v.getId());
            continue;
          }
          logger.debug("    Iterating over OpenStack Neutron Ports");
          //List<org.openstack4j.model.network.Port> portList = this.getNeutronPorts(os);
          for (org.openstack4j.model.network.Port p : portList) {
            // Take care here since we need to check if we have different qualities on different networks!
            String ips = p.getFixedIps().toString();
            //for (DetailedQoSReference ref : qoses) {
            // Check for our ip addresses, we simply use a string check here so we do not need to parse the input
            if (ips.contains(r.getIp())) {
              logger.debug(
                  "    Port with the ip : "
                      + r.getIp()
                      + " and id : "
                      + p.getId()
                      + " will get QoS policy : "
                      + r.getQuality().name());
              // Natively metering the bandwidth in bytes per second, openstack uses kilo bytes per second
              String bandwidth =
                  String.valueOf(Integer.parseInt(r.getQuality().getMax_rate()) / 1024);
              // if the quality is missing, we should CREATE IT
              logger.debug(
                  "    Checking if QoS policy : " + r.getQuality().name() + " exists in OpenStack");
              if (qos_map.get(r.getQuality().name()) == null) {
                logger.debug("    Did not found QoS policy with name : " + r.getQuality().name());
                logger.debug("    Will create QoS policy : " + r.getQuality().name());
                // Creating the not existing QoS policy
                String created_pol_id = this.createQoSPolicy(creds, neutron_handler, r, token);
                // Since we now have the correct id of the policy , lets create the bandwidth rule
                createBandwidthRule(creds, created_pol_id, neutron_handler, bandwidth, token);
              } else {
                // At least print a warning here if the policy bandwidth rule differs from the one we wanted to create,
                // this means a user touched it already
                logger.debug("    Found QoS policy with name : " + r.getQuality().name());
                String qos_id = qos_map.get(r.getQuality().name());
                checkBandwidthRule(bandwidth, qos_id, neutron_handler, creds, r, token);
              }
              // Check which QoS policies are available now and update the qos_map
              qos_map = getNeutronQoSPolicies(neutron_handler, creds, token);
              logger.debug("    Updating QoS policy list of OpenStack Neutron");
              //logger.debug(qos_map.toString());
              // At this point we can be sure the policy exists
              logger.debug("    Associated QoS policy is " + qos_map.get(r.getQuality().name()));
              // Check if the port already got the correct qos-policy assigned
              checkAndFixAssignedPolicies(neutron_handler, p, creds, qos_map, r, token);
            }
          }
        }
      }
      logger.debug(delimiter_line);
    }
  }

  public void deleteBandwidthRule(String rule_id, String policy_id) {
    String response =
        neutron_handler.neutron_http_connection(
            creds.get("neutron")
                + "/qos/policies/"
                + policy_id
                + "/bandwidth_limit_rules/"
                + rule_id,
            "DELETE",
            token,
            null);
    if (response == null) {
      logger.error(
          "Error trying to delete bandwidth rule :"
              + rule_id
              + " belonging to QoS policy "
              + policy_id);
    }
  }

  public void deleteQoSPolicy(String id) {
    String response =
        neutron_handler.neutron_http_connection(
            creds.get("neutron") + "/qos/policies/" + id, "DELETE", token, null);
    if (response == null) {
      logger.error("Error trying to delete QoS policy :" + id + " are there still VMs using it?");
    }
  }

  // method to create a non existing QoS policy in Openstack
  private String createQoSPolicy(
      Map<String, String> creds,
      NeutronQoSHandler neutron_handler,
      QoSReference ref,
      Object access) {
    String response =
        neutron_handler.neutron_http_connection(
            creds.get("neutron") + "/qos/policies",
            "POST",
            access,
            neutron_handler.createPolicyPayload(ref.getQuality().name()));
    if (response == null) {
      logger.error("Error trying to create QoS policy :" + ref.getQuality().name());
      return null;
    }
    logger.debug("    Created missing QoS policy");
    return neutron_handler.parsePolicyId(response);
  }

  // method to create a non existing QoS policy in Openstack
  private void createBandwidthRule(
      Map<String, String> creds,
      String created_pol_id,
      NeutronQoSHandler neutron_handler,
      String bandwidth,
      Object access) {
    String response =
        neutron_handler.neutron_http_connection(
            creds.get("neutron") + "/qos/policies/" + created_pol_id + "/bandwidth_limit_rules",
            "POST",
            access,
            neutron_handler.createBandwidthLimitRulePayload(bandwidth));
    if (response == null) {
      logger.error("Error trying to create bandwidth rule for QoS policy");
      return;
    }
    logger.debug("    Created bandwidth limitation rule for new QoS policy : " + created_pol_id);
  }

  // method to check existing QoS policy and its bandwidth rules for changes
  private void checkBandwidthRule(
      String bandwidth,
      String qos_id,
      NeutronQoSHandler neutron_handler,
      Map<String, String> creds,
      QoSReference ref,
      Object access) {
    String response =
        neutron_handler.neutron_http_connection(
            creds.get("neutron") + "/qos/policies/" + qos_id, "GET", access, null);
    if (response == null) {
      logger.error(
          "Error trying to show information for existing QoS policy : " + ref.getQuality().name());
      return;
    }
    //logger.debug(response);
    if (neutron_handler.checkForBandwidthRule(response, bandwidth)) {
      logger.debug(
          "    QoS policy : "
              + ref.getQuality().name()
              + " is the same as defined in Openstack Neutron");
    } else {
      // TODO : Add a openbaton parameter telling us what to do , ignore the difference or update bandwidth rule
      logger.warn(
          "        The QoS policy "
              + ref.getQuality().name()
              + " on "
              + creds.get("auth")
              + " has been modified in OpenStack Neutron, remove the QoS policy in OpenStack Neutron to get rid of this warning");
    }
  }

  public void createBandwidthRule(OpenStackBandwidthRule rule, String id) {
    String response =
        neutron_handler.neutron_http_connection(
            creds.get("neutron") + "/qos/policies/" + id + "/bandwidth_limit_rules",
            "POST",
            token,
            neutron_handler.createBandwidthLimitRulePayload(
                rule.getType(),
                rule.getMax_kbps().toString(),
                rule.getMax_burst_kbps().toString()));
    if (response == null) {
      logger.error("Error trying to create bandwidth rule for QoS policy");
      return;
    }
    logger.debug("    Created bandwidth limitation rule for new QoS policy : " + id);
  }

  public void createQoSPolicy(OpenStackQoSPolicy policy) {
    String response =
        neutron_handler.neutron_http_connection(
            creds.get("neutron") + "/qos/policies",
            "POST",
            token,
            neutron_handler.createPolicyPayload(policy.getName()));
    if (response == null) {
      logger.error("Error trying to create QoS policy :" + policy.getName());
    }
    String policy_id = neutron_handler.parsePolicyId(response);
    policy.setId(policy_id);
    for (OpenStackBandwidthRule rule : policy.getRules()) {
      response =
          neutron_handler.neutron_http_connection(
              creds.get("neutron") + "/qos/policies/" + policy_id + "/bandwidth_limit_rules",
              "POST",
              token,
              neutron_handler.createBandwidthLimitRulePayload(
                  rule.getType(),
                  rule.getMax_kbps().toString(),
                  rule.getMax_burst_kbps().toString()));
      if (response == null) {
        logger.error("Error trying to create bandwidth rule for QoS policy");
        return;
      }
    }
  }

  public ArrayList<OpenStackPort> listPorts() {
    ArrayList<OpenStackPort> port_list = new ArrayList<>();
    String response =
        neutron_handler.neutron_http_connection(
            creds.get("neutron") + "/ports", "GET", token, null);
    JSONObject ans = new JSONObject(response);
    JSONArray net = ans.getJSONArray("ports");
    for (int i = 0; i < net.length(); i++) {
      JSONObject o = net.getJSONObject(i);
      OpenStackPort tmp_port = new OpenStackPort(o);
      port_list.add(tmp_port);
    }
    return port_list;
  }

  public ArrayList<OpenStackNetwork> listNetworks() {
    ArrayList<OpenStackNetwork> net_list = new ArrayList<>();
    String response =
        neutron_handler.neutron_http_connection(
            creds.get("neutron") + "/networks", "GET", token, null);
    JSONObject ans = new JSONObject(response);
    JSONArray net = ans.getJSONArray("networks");
    for (int i = 0; i < net.length(); i++) {
      JSONObject o = net.getJSONObject(i);
      OpenStackNetwork tmp_net = new OpenStackNetwork(o);
      net_list.add(tmp_net);
    }
    return net_list;
  }

  public ArrayList<OpenStackQoSPolicy> getNeutronQosRules() {
    ArrayList<OpenStackQoSPolicy> policy_list = new ArrayList<>();
    String response =
        neutron_handler.neutron_http_connection(
            creds.get("neutron") + "/qos/policies", "GET", token, null);
    if (response == null) {
      logger.warn("    Can not list existing QoS policies for OpenStack Neutron");
      return null;
    }
    // Save the already configured policies in a hash map for later usage
    JSONObject ans = new JSONObject(response);
    JSONArray qos_p = ans.getJSONArray("policies");
    for (int i = 0; i < qos_p.length(); i++) {
      JSONObject o = qos_p.getJSONObject(i);
      OpenStackQoSPolicy tmp_policy = new OpenStackQoSPolicy(o);
      policy_list.add(tmp_policy);
    }
    return policy_list;
  }

  public void assignQoSPolicyToNetwork(String net, String policy) {
    neutron_handler.neutron_http_connection(
        creds.get("neutron") + "/networks/" + net + ".json",
        "PUT",
        token,
        neutron_handler.createPolicyUpdatePayload(policy));
  }

  public void assignQoSPolicyToPort(String port, String policy) {
    neutron_handler.neutron_http_connection(
        creds.get("neutron") + "/ports/" + port + ".json",
        "PUT",
        token,
        neutron_handler.createPolicyUpdatePayload(policy));
  }

  // method to list all QoS policies configured in neutron
  private Map<String, String> getNeutronQoSPolicies(
      NeutronQoSHandler neutron_handler, Map<String, String> creds, Object access) {
    String response =
        neutron_handler.neutron_http_connection(
            creds.get("neutron") + "/qos/policies", "GET", access, null);
    if (response == null) {
      logger.warn("    Can not list existing QoS policies for OpenStack Neutron");
      return null;
    }
    // Save the already configured policies in a hash map for later usage
    return neutron_handler.parsePolicyMap(response);
  }

  // method to check and modify a neutron port for defined policies compared to the ones from the virtual network function record
  private void checkAndFixAssignedPolicies(
      NeutronQoSHandler neutron_handler,
      Object p,
      Map<String, String> creds,
      Map<String, String> qos_map,
      QoSReference ref,
      Object access) {

    org.openstack4j.model.network.Port np = (org.openstack4j.model.network.Port) p;
    String response =
        neutron_handler.neutron_http_connection(
            creds.get("neutron") + "/ports/" + np.getId() + ".json", "GET", access, null);
    // if port already got the qos_policy we want to add, abort ( to avoid sending more traffic )
    if (!neutron_handler.checkPortQoSPolicy(response, qos_map.get(ref.getQuality().name()))) {
      //logger.debug("Neutron port information before updating : " + response);
      // update port
      neutron_handler.neutron_http_connection(
          creds.get("neutron") + "/ports/" + np.getId() + ".json",
          "PUT",
          access,
          neutron_handler.createPolicyUpdatePayload(qos_map.get(ref.getQuality().name())));
      logger.info(
          "        Finished assigning QoS policy "
              + ref.getQuality().name()
              + " to ip "
              + ref.getIp()
              + " connected to port "
              + np.getId());
    } else {
      logger.info(
          "        Port with the ip : "
              + ref.getIp()
              + " and id : "
              + np.getId()
              + " already got the QoS policy "
              + ref.getQuality().name()
              + " assigned");
    }
    //logger.debug(delimiter_line);
  }

  // Method reform a set of virtual network functions to a map containing the name of the record and its quality
  private Map<String, Quality> getVlrs(Set<VirtualNetworkFunctionRecord> vnfrs) {
    Map<String, Quality> res = new LinkedHashMap<>();
    //logger.debug("GETTING VLRS");
    for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
      for (InternalVirtualLink vlr : vnfr.getVirtual_link()) {
        for (String qosParam : vlr.getQos()) {
          if (qosParam.contains("minimum")
              || qosParam.contains("maximum")
              || qosParam.contains("policy")) {
            // TODO : once openstack neutron supports minimum bandwidth, implement support
            if (qosParam.contains("minimum")) {
              //logger.warn(
              //    "Minimum Bandwidth rules are not supported yet by Openstack neutron, will apply maximum bandwidth");
            }
            Quality quality = this.mapValueQuality(qosParam);
            //res.put(vlr.getName(), quality);
            res.put(vnfr.getName(), quality);
            //logger.debug("GET VIRTUAL LINK RECORD: insert in map vlr name " + vlr.getName() + " with quality " + quality);
            //logger.debug("Task : " + vnfr.getName() + " needs to get QoS policy " + quality+ " on net "
            //        + vlr.getName());
          }
        }
      }
    }
    return res;
  }

  // Method the map the qualities defined in the virtual network function record to the values defined in the quality class
  private Quality mapValueQuality(String value) {
    //logger.debug("MAPPING VALUE-QUALITY: received value " + value);
    String[] qos = value.split(":");
    //logger.debug("Found QoS quality defined to : " + qos[1]);
    return Quality.valueOf(qos[1]);
  }

  // Function to extract information about : (quality,vim_id,ip) from a set of virtual network function records
  // Thus we collect all data we need to know : which network service uses which virtualized infrastructure manager so
  // we know to which neutronApi to connect to ( Since we may use a multi_datacenter deployment here ) to set the
  // defined quality
  private List<DetailedQoSReference> getDetailedQosesRefs(Set<VirtualNetworkFunctionRecord> vnfrs) {
    Boolean dup;
    // Get a list of virtual network function record names and their assigned qualities
    Map<String, Quality> qualities = this.getVlrs(vnfrs);
    List<DetailedQoSReference> res = new ArrayList<>();
    for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
      if (qualities.keySet().contains(vnfr.getName())) {
        //logger.debug(
        //"Checking for related IPv4 address for applying QoS policy on " + vnfr.getName());
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
          //logger.debug(
          //    "Checking virtual deployment unit " + vdu.getName() + " of " + vnfr.getName());
          for (VNFCInstance vnfci : vdu.getVnfc_instance()) {
            //logger.debug(
            //"Checking virtual network function component "
            //    + vnfci.getHostname()
            //    + " of virtual deployment unit "
            //    + vdu.getName()
            //    + " of "
            //    + vnfr.getName());
            //logger.debug(
            //    "Checking connection point "
            //        + cp.getVirtual_link_reference()
            //        + " of virtual network function component "
            //        + vnfci.getHostname()
            //        + " of virtual deployment unit "
            //        + vdu.getName()
            //        + " of "
            //        + vnfr.getName());
            //logger.debug("Creating new QoSReference");
            // We modified the check here to go over the vnfr name
            Map<String, Quality> netQualities = this.getNetQualityMap(vnfrs, vnfr.getName());
            //logger.debug("    Map qualities : " + netQualities);

            //logger.debug("Following QoS policies are to applied " + netQualities.toString());
            for (Ip ip : vnfci.getIps()) {
              //logger.debug(
              //    "Checking "
              //        + vnfci.getHostname()
              //        + " - "
              //        + ip.getNetName()
              //        + " - "
              //        + ip.getIp());
              String net = ip.getNetName();
              if (netQualities.keySet().contains(net)) {
                // Avoid duplicate entries
                dup = false;
                for (DetailedQoSReference t : res) {
                  if (t.getIp().equals(ip.getIp())) {
                    dup = true;
                  }
                }
                if (!dup) {
                  DetailedQoSReference ref = new DetailedQoSReference();
                  //ref.setQuality(qualities.get(vnfr.getName()));
                  if (netQualities.get(net) != null) {
                    ref.setQuality(netQualities.get(net));
                    ref.setVim_id(vnfci.getVim_id());
                    ref.setIp(ip.getIp());
                    ref.setHostname(vnfr.getName());
                    ref.setVnfr_id(vnfr.getId());
                    ref.setVdu_id(vdu.getId());
                    ref.setVnfci_id(vnfci.getId());
                    ref.setNsr_id(vnfr.getParent_ns_id());
                    //logger.debug("    Adding " + ref.toString());
                    res.add(ref);
                  }
                }
              }
            }
          }
        }
      }
      //      else {
      //        logger.debug(
      //            "  There are no bandwidth limitations defined for " + vnfr.getName() + " in " + qualities.toString());
      //      }
    }
    return res;
  }

  // method to create a map of virtual network function names combined with their configured qualities
  private Map<String, Quality> getNetQualityMap(
      Set<VirtualNetworkFunctionRecord> vnfrs, String vnfrName) {
    Map<String, Quality> res = new LinkedHashMap<>();
    //logger.debug("GETTING VLRS");
    for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
      if (vnfr.getName().equals(vnfrName)) {
        for (InternalVirtualLink vlr : vnfr.getVirtual_link()) {
          for (String qosParam : vlr.getQos()) {
            if (qosParam.contains("minimum")
                || qosParam.contains("maximum")
                || qosParam.contains("policy")) {
              // TODO : once openstack neutron supports minimum bandwidth, implement support
              if (qosParam.contains("minimum")) {
                logger.warn(
                    "Minimum bandwidth rules are not supported yet by Openstack Neutron, will apply maximum bandwidth limitation instead");
              }
              Quality quality = this.mapValueQuality(qosParam);
              //res.put(vlr.getName(), quality);
              res.put(vlr.getName(), quality);
              //logger.debug("GET VIRTUAL LINK RECORD: insert in map vlr name " + vlr.getName() + " with quality " + quality);
              //logger.debug("Task : "+ vnfr.getName()+ " needs to get QoS policy "+ quality+ " on net "+ vlr.getName());
            }
          }
        }
      }
    }
    return res;
  }

  @Override
  public Object call() throws Exception {
    return null;
  }
}
