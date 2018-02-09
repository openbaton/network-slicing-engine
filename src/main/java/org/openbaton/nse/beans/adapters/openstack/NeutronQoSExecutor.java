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

package org.openbaton.nse.beans.adapters.openstack;

//import com.google.common.base.Function;
//import com.google.common.collect.ImmutableSet;
//import com.google.inject.Key;
//import com.google.inject.Module;
//import com.google.inject.TypeLiteral;
/*
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.domain.Credentials;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.keystone.v2_0.domain.Access;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.neutron.v2.domain.Port;
import org.jclouds.openstack.neutron.v2.domain.Ports;
import org.jclouds.openstack.neutron.v2.features.PortApi;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.v2_0.options.PaginationOptions;
*/
import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.descriptor.InternalVirtualLink;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.nse.utils.DetailedQoSReference;
import org.openbaton.nse.utils.QoSReference;
import org.openbaton.nse.utils.Quality;
import org.openstack4j.api.OSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by lgr on 20/09/16. modified by lgr on 20.07.17
 */
public class NeutronQoSExecutor implements Runnable {

  private Logger logger;
  private Set<VirtualNetworkFunctionRecord> vnfrs;
  private NeutronQoSHandler neutron_handler;
  private VimInstance v;
  private String token;
  private Map<String, String> creds;
  private List<org.openstack4j.model.network.Port> portList;
  private Map<String, String> computeNodeMap;
  private Map<String, String> hostComputeNodeMap;
  private String delimiter_line;

  public NeutronQoSExecutor(
      Set<VirtualNetworkFunctionRecord> vnfrs,
      NeutronQoSHandler handler,
      String token,
      VimInstance v,
      Map<String, String> creds,
      List<org.openstack4j.model.network.Port> portList,
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

  private void init() {
    this.logger = LoggerFactory.getLogger(this.getClass());
  }

  @Override
  public void run() {
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
      // TODO : Add a configuration parameter telling us what to do , ignore the difference or update bandwidth rule
      logger.warn(
          "        The QoS policy "
              + ref.getQuality().name()
              + " on "
              + creds.get("auth")
              + " has been modified in OpenStack Neutron, remove the QoS policy in OpenStack Neutron to get rid of this warning");
    }
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
      response =
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

  // Simple method to check if a set of connection points contain configured bandwidth qualities
  private boolean hasQoS(Map<String, Quality> qualities, Set<VNFDConnectionPoint> ifaces) {
    for (VNFDConnectionPoint cp : ifaces) {
      if (qualities.keySet().contains(cp.getVirtual_link_reference())) return true;
    }
    return false;
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
            for (VNFDConnectionPoint cp : vnfci.getConnection_point()) {
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
}
