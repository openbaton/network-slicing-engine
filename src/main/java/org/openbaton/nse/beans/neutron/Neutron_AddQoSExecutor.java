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

package org.openbaton.nse.beans.neutron;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;

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
import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.descriptor.InternalVirtualLink;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.nse.properties.NfvoProperties;
import org.openbaton.nse.utils.DetailedQoSReference;
import org.openbaton.nse.utils.QoSReference;
import org.openbaton.nse.utils.Quality;
import org.openbaton.sdk.NFVORequestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by lgr on 20/09/16.
 */
public class Neutron_AddQoSExecutor implements Runnable {

  private Logger logger;
  private Set<VirtualNetworkFunctionRecord> vnfrs;
  //private OpenstackConfiguration configuration;
  private NfvoProperties configuration;
  private NFVORequestor requestor;
  private QoSHandler neutron_handler;

  private Access pacc;

  public Neutron_AddQoSExecutor(
      Set<VirtualNetworkFunctionRecord> vnfrs, NfvoProperties configuration, QoSHandler handler) {
    this.vnfrs = vnfrs;
    this.logger = LoggerFactory.getLogger(this.getClass());
    this.configuration = configuration;
    this.neutron_handler = handler;
  }

  private void init() {
    this.logger = LoggerFactory.getLogger(this.getClass());
  }

  @Override
  public void run() {
    // TODO : if security is disabled : username , password need to be empty strings
    this.requestor =
        new NFVORequestor(
            configuration.getUsername(),
            configuration.getPassword(),
            "*",
            false,
            configuration.getIp(),
            configuration.getPort(),
            "1");
    // get the project id from the vnfrs
    for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
      requestor.setProjectId(vnfr.getProjectId());
      logger.debug("Setting project id to : " + vnfr.getProjectId());
    }
    String nova_provider = "openstack-nova";
    NovaApi novaApi;
    String neutron_provider = "openstack-neutron";
    NeutronApi neutronApi;
    String response;
    Set<String> regions;
    Map<String, String> qos_map;
    //List<QoSReference> qoses = this.getQosesRefs(vnfrs);
    List<DetailedQoSReference> qoses = this.getDetailedQosesRefs(vnfrs);
    // first of all get the datacenter we want to work on!
    for (DetailedQoSReference r : qoses) {
      //logger.debug("Checking for PoP "+r.getVim_id());
      Map<String, String> creds = this.getDatacenterCredentials(requestor, r.getVim_id());
      //logger.debug("Finished receiving credentials");
      logger.debug("adding qoses for " + qoses.toString());
      // Use nova to list our endpoints at a later stage
      Iterable<Module> modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());
      novaApi =
          ContextBuilder.newBuilder(nova_provider)
              .credentials(creds.get("identity"), creds.get("password"))
              .endpoint(creds.get("auth"))
              .modules(modules)
              .buildApi(NovaApi.class);

      // We will steal the x-auth token here, to be able to directly communicate with the neutron qos rest api
      Access access =
          this.getAccess(
              creds.get("identity"), nova_provider, creds.get("password"), creds.get("auth"));

      // For a multi node environment we also need to steal the neutron-ip, therefor we work with the acess object directly
      String neutron_access = neutron_handler.parceNeutronEndpoint(access);
      creds.put("neutron", neutron_access + "/v2.0");

      logger.debug("Received auth token");
      // Check which QoS policies are available already
      response =
          neutron_handler.neutron_http_connection(
              creds.get("neutron") + "/qos/policies", "GET", access, null);
      if (response == null) {
        logger.error("Error trying to list existing QoS policies");
        return;
      }
      // Save those in a hash map for later usage
      qos_map = neutron_handler.parsePolicyMap(response);
      logger.debug(qos_map.toString());
      // With neutron we collect the necessary values to know which ports to modify
      neutronApi =
          ContextBuilder.newBuilder(neutron_provider)
              .credentials(creds.get("identity"), creds.get("password"))
              .endpoint(creds.get("auth"))
              .buildApi(NeutronApi.class);
      regions = novaApi.getConfiguredRegions();
      for (String region : regions) {
        PortApi portApi = neutronApi.getPortApi(region);
        Ports ports = portApi.list(PaginationOptions.Builder.limit(2).marker("abcdefg"));
        // For all neutron ports
        logger.debug("Iterarting over neutron ports");
        for (Port p : ports) {
          String ips = p.getFixedIps().toString();
          for (QoSReference ref : qoses) {
            // Check for our ip addresses
            if (ips.contains(ref.getIp())) {
              logger.debug("port id : " + p.getId() + " will get qos " + ref.getQuality().name());
              // The native cm agent metered the bandwidth a bit different
              String bandwidth =
                  String.valueOf(Integer.parseInt(ref.getQuality().getMax_rate()) / 1000);
              // if the quaility is missing, we should CREATE IT
              if (qos_map.get(ref.getQuality().name()) == null) {
                logger.debug("Did not found qos-policy with name " + ref.getQuality().name());
                logger.debug("Will create qos-policy : " + ref.getQuality().name());
                // Creating the not existing QoS policy
                response =
                    neutron_handler.neutron_http_connection(
                        creds.get("neutron") + "/qos/policies",
                        "POST",
                        access,
                        neutron_handler.createPolicyPayload(ref.getQuality().name()));
                if (response == null) {
                  logger.error("Error trying to create QoS policy");
                  return;
                }
                logger.debug("Created policy :" + response);
                String created_pol_id = neutron_handler.parsePolicyId(response);
                // Since we now have the correct id of the policy , lets create the bandwidth rule
                response =
                    neutron_handler.neutron_http_connection(
                        creds.get("neutron")
                            + "/qos/policies/"
                            + created_pol_id
                            + "/bandwidth_limit_rules",
                        "POST",
                        access,
                        neutron_handler.createBandwidthLimitRulePayload(bandwidth));
                if (response == null) {
                  logger.error("Error trying to create bandwidth rule for QoS policy");
                  return;
                }
                logger.debug(
                    "Created bandwidth rule for policy" + created_pol_id + ": " + response);
              } else {
                // At least print a warning here if the policy bandwidth rule differs from the one we wanted to create,
                // this means a user touched it already
                logger.debug("found qos-policy with name " + ref.getQuality().name());
                String qos_id = qos_map.get(ref.getQuality().name());
                response =
                    neutron_handler.neutron_http_connection(
                        creds.get("neutron") + "/qos/policies/" + qos_id, "GET", access, null);
                if (response == null) {
                  logger.error(
                      "Error trying to show information for existing QoS policy : "
                          + ref.getQuality().name());
                  return;
                }
                logger.debug(response);
                if (neutron_handler.checkForBandwidthRule(response, bandwidth)) {
                  logger.debug("policy looks fine");
                } else {
                  // TODO : Add a configuration parameter telling us what to do , ignore the difference or update bandwidth rule
                  logger.warn(
                      "The policy "
                          + ref.getQuality().name()
                          + " on "
                          + creds.get("auth")
                          + " has been touched, remove the QoS policy to get rid of this warning");
                }
              }
              // Check which QoS policies are available now
              response =
                  neutron_handler.neutron_http_connection(
                      creds.get("neutron") + "/qos/policies", "GET", access, null);
              if (response == null) {
                logger.error("Error trying to list existing QoS policies");
                return;
              }
              // update the QoS Policy map
              qos_map = neutron_handler.parsePolicyMap(response);
              logger.debug(qos_map.toString());
              // At this point we can be sure the policy exists
              logger.debug("associated qos_policy is " + qos_map.get(ref.getQuality().name()));
              // Check if the port already got the correct qos-policy assigned
              response =
                  neutron_handler.neutron_http_connection(
                      creds.get("neutron") + "/ports/" + p.getId() + ".json", "GET", access, null);
              // if port already got the qos_policy we want to add, abort ( to avoid sending more traffic )
              if (!neutron_handler.checkPortQoSPolicy(
                  response, qos_map.get(ref.getQuality().name()))) {
                logger.debug("Port information before updating : " + response);
                // update port
                response =
                    neutron_handler.neutron_http_connection(
                        creds.get("neutron") + "/ports/" + p.getId() + ".json",
                        "PUT",
                        access,
                        neutron_handler.createPolicyUpdatePayload(
                            qos_map.get(ref.getQuality().name())));
              } else {
                logger.debug("Port QoS policy does not need to be updated");
              }
            }
          }
          //logger.debug("Finished iterating over QoS references");
        }
        //logger.debug("Finished Iterating over neutron ports");
      }
      // Close the APIs
      try {
        novaApi.close();
        neutronApi.close();
      } catch (IOException e) {
        logger.error("Could not close novaAPI / neutronAPI");
        e.printStackTrace();
      }
    }
  }

  private boolean hasQoS(Map<String, Quality> qualities, Set<VNFDConnectionPoint> ifaces) {
    for (VNFDConnectionPoint cp : ifaces) {
      if (qualities.keySet().contains(cp.getVirtual_link_reference())) return true;
    }
    return false;
  }

  private Map<String, Quality> getVlrs(Set<VirtualNetworkFunctionRecord> vnfrs) {
    Map<String, Quality> res = new LinkedHashMap<>();
    logger.debug("GETTING VLRS");
    for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
      for (InternalVirtualLink vlr : vnfr.getVirtual_link()) {
        for (String qosParam : vlr.getQos()) {
          if (qosParam.contains("minimum_bandwith")) {
            Quality quality = this.mapValueQuality(qosParam);
            //res.put(vlr.getName(), quality);
            res.put(vnfr.getName(), quality);
            //logger.debug("GET VIRTUAL LINK RECORD: insert in map vlr name " + vlr.getName() + " with quality " + quality);
            logger.debug(
                "GET VIRTUAL LINK RECORD: insert in map vlr name "
                    + vnfr.getName()
                    + " with quality "
                    + quality);
          }
        }
      }
    }
    return res;
  }

  private Quality mapValueQuality(String value) {
    logger.debug("MAPPING VALUE-QUALITY: received value " + value);
    String[] qos = value.split(":");
    logger.debug("MAPPING VALUE-QUALITY: quality is " + qos[1]);
    return Quality.valueOf(qos[1]);
  }

  private Access getAccess(
      String identity, String nova_provider, String password, String endpoint) {
    // TODO : Verify the old token "pacc" before just simply passing it ...
    if (pacc == null) {
      ContextBuilder contextBuilder =
          ContextBuilder.newBuilder(nova_provider)
              .credentials(identity, password)
              .endpoint(endpoint);
      ComputeServiceContext context = contextBuilder.buildView(ComputeServiceContext.class);
      Function<Credentials, Access> auth =
          context
              .utils()
              .injector()
              .getInstance(Key.get(new TypeLiteral<Function<Credentials, Access>>() {}));
      Access access =
          auth.apply(
              new Credentials.Builder<Credentials>()
                  .identity(identity)
                  .credential(password)
                  .build());
      // Save the "token"
      this.pacc = access;
      return access;
    }
    return pacc;
  }

  private Map<String, String> getDatacenterCredentials(NFVORequestor requestor, String vim_id) {
    Map<String, String> cred = new HashMap<String, String>();
    // What we want to archieve is to list all machines and know to which vim-instance they belong
    VimInstance v = null;
    try {
      //logger.debug("listing all vim-instances");
      logger.debug(requestor.getVimInstanceAgent().findAll().toString());
      v = requestor.getVimInstanceAgent().findById(vim_id);
      //logger.debug("adding identity");
      cred.put("identity", v.getTenant() + ":" + v.getUsername());
      //logger.debug("adding password");
      cred.put("password", v.getPassword());
      //logger.debug("adding nova auth url "+ v.getAuthUrl());
      cred.put("auth", v.getAuthUrl());
    } catch (Exception e) {
      logger.error("Exception while creating credentials");
      logger.error(e.getMessage());
      logger.error(e.toString());
      e.printStackTrace();
    }
    return cred;
  }

  // Adds additional to the other loop the vim_ids of the related vnfr
  private List<DetailedQoSReference> getDetailedQosesRefs(Set<VirtualNetworkFunctionRecord> vnfrs) {
    Boolean dup;
    Map<String, Quality> qualities = this.getVlrs(vnfrs);
    List<DetailedQoSReference> res = new ArrayList<>();
    for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
      if (qualities.keySet().contains(vnfr.getName())) {
        logger.debug("Found quality for " + vnfr.getName());
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
          for (VNFCInstance vnfci : vdu.getVnfc_instance()) {
            for (VNFDConnectionPoint cp : vnfci.getConnection_point()) {
              //logger.debug("Creating new QoSReference");
              // In the agent, the check goes over the network name, which makes problems
              // if both services are using the same network, but different qualities...
              // We modified the check here to go over the vnfr name
              Map<String, Quality> netQualities = this.getNetQualityMap(vnfrs, vnfr.getName());
              for (Ip ip : vnfci.getIps()) {
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
                    ref.setQuality(qualities.get(vnfr.getName()));
                    ref.setVim_id(vnfci.getVim_id());
                    ref.setIp(ip.getIp());
                    logger.debug("GET QOSES REF: adding reference to list " + ref.toString());
                    res.add(ref);
                  }
                }
              }
            }
          }
        }
      } else {
        logger.debug(
            "There are no qualities defined for " + vnfr.getName() + " in " + qualities.toString());
      }
    }
    return res;
  }

  private Map<String, Quality> getNetQualityMap(
      Set<VirtualNetworkFunctionRecord> vnfrs, String vnfrName) {
    Map<String, Quality> res = new LinkedHashMap<>();
    logger.debug("GETTING VLRS");
    for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
      if (vnfr.getName().equals(vnfrName)) {
        for (InternalVirtualLink vlr : vnfr.getVirtual_link()) {
          for (String qosParam : vlr.getQos()) {
            if (qosParam.contains("minimum_bandwith")) {
              Quality quality = this.mapValueQuality(qosParam);
              //res.put(vlr.getName(), quality);
              res.put(vlr.getName(), quality);
              //logger.debug("GET VIRTUAL LINK RECORD: insert in map vlr name " + vlr.getName() + " with quality " + quality);
              logger.debug(
                  "GET VIRTUAL LINK RECORD: insert in map vlr name "
                      + vnfr.getName()
                      + " with quality "
                      + quality);
            }
          }
        }
      }
    }
    return res;
  }
}
