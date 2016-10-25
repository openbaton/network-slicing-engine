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

package org.openbaton.nse.beans.openbaton;

import org.openbaton.nse.core.ConnectivityManagerHandler;
import org.openbaton.nse.utils.FlowAllocation;
import org.openbaton.nse.utils.FlowReference;
import org.openbaton.nse.utils.QoSAllocation;
import org.openbaton.nse.utils.QoSReference;
import org.openbaton.nse.utils.Quality;
import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.descriptor.InternalVirtualLink;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by maa on 18.01.16.
 */
public class AddQoSExecutor implements Runnable {

  private ConnectivityManagerHandler networkSlicingEngineHandler;
  private Logger logger;
  private Set<VirtualNetworkFunctionRecord> vnfrs;
  private String nsrID;

  public AddQoSExecutor(
      ConnectivityManagerHandler networkSlicingEngineHandler,
      Set<VirtualNetworkFunctionRecord> vnfrs,
      String nsrID) {
    this.networkSlicingEngineHandler = networkSlicingEngineHandler;
    this.vnfrs = vnfrs;
    this.nsrID = nsrID;
    this.logger = LoggerFactory.getLogger(this.getClass());
  }

  @Override
  public void run() {

    logger.debug("Received vnfrs with qos");

    FlowAllocation flows = this.getSFlows(vnfrs);
    logger.debug("adding flows for " + flows.toString()); //print list@id
    List<QoSAllocation> qoses = this.getQoses(vnfrs);
    logger.debug("adding qoses for " + qoses.toString());

    boolean response = networkSlicingEngineHandler.addQos(qoses, flows, nsrID);
    logger.debug("RESPONSE from Handler " + response);
  }

  private List<QoSAllocation> getQoses(Set<VirtualNetworkFunctionRecord> vnfrs) {

    Map<String, Quality> qualities = this.getVlrs(vnfrs);
    List<QoSAllocation> res = new ArrayList<>();

    for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
      // Check for the vnfr name , not the network name anymore..
      if (qualities.keySet().contains(vnfr.getName())) {
        logger.debug("Found quality for " + vnfr.getName());
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
          for (VNFCInstance vnfci : vdu.getVnfc_instance()) {
            // this has become obsolete
            //if(this.hasQoS(qualities,vnfci.getConnection_point())){
            QoSAllocation tmp = new QoSAllocation();
            tmp.setServerName(vnfci.getHostname());
            List<QoSReference> ifaces = new ArrayList<>();
            for (VNFDConnectionPoint cp : vnfci.getConnection_point()) {
              // aswell this check
              //if(qualities.keySet().contains(cp.getVirtual_link_reference())){
              QoSReference ref = new QoSReference();
              // modified the set Quality to work correctly
              //ref.setQuality(qualities.get(cp.getVirtual_link_reference()));
              ref.setQuality(qualities.get(vnfr.getName()));
              for (Ip ip : vnfci.getIps()) {
                if (ip.getNetName().equals(cp.getVirtual_link_reference())) {
                  ref.setIp(ip.getIp());
                }
              }
              logger.debug("GET QOSES: adding reference to list " + ref.toString());
              ifaces.add(ref);
              //}
            }
            logger.debug("IFACES " + ifaces);
            tmp.setIfaces(ifaces);
            logger.debug("QoSAllocation " + tmp);
            res.add(tmp);
            //}
          }
        }
      } else {
        logger.debug(
            "There are no qualities defined for " + vnfr.getName() + " in " + qualities.toString());
      }
    }
    return res;
  }

  private boolean hasQoS(Map<String, Quality> qualities, Set<VNFDConnectionPoint> ifaces) {

    for (VNFDConnectionPoint cp : ifaces) {
      if (qualities.keySet().contains(cp.getVirtual_link_reference())) return true;
    }
    return false;
  }

  private FlowAllocation getSFlows(Set<VirtualNetworkFunctionRecord> vnfrs) {

    Map<String, Quality> qualities = this.getVlrs(vnfrs);
    FlowAllocation res = new FlowAllocation();

    // Added another variable holding the network name
    String vlr_net_name = null;
    for (String vlr : qualities.keySet()) {
      for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
        if (vlr.equals(vnfr.getName())) {
          for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            for (VNFCInstance vnfci : vdu.getVnfc_instance()) {
              for (VNFDConnectionPoint cp : vnfci.getConnection_point()) {
                vlr_net_name = cp.getVirtual_link_reference();
              }
            }
          }
        }
      }
      List<FlowReference> references = this.findCprFromVirtualLink(vnfrs, vlr);
      res.addVirtualLink(vlr_net_name, references);
    }

    return res;
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

  private List<FlowReference> findCprFromVirtualLink(
      Set<VirtualNetworkFunctionRecord> vnfrs, String vlr) {

    List<FlowReference> res = new ArrayList<>();

    for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
      // Check for the vnfr name , not the network name anymore..
      if (vlr.equals(vnfr.getName())) {
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
          for (VNFCInstance vnfc : vdu.getVnfc_instance()) {
            for (VNFDConnectionPoint cp : vnfc.getConnection_point()) {
              // this has become obsolete
              //if(cp.getVirtual_link_reference().equals(vlr)){
              for (Ip ip : vnfc.getIps()) {
                if (ip.getNetName().equals(cp.getVirtual_link_reference())) {
                  FlowReference tobeAdded = new FlowReference(vnfc.getHostname(), ip.getIp());
                  logger.debug(
                      "FIND CONNECTION POINT RECORD FROM VIRTUAL LINK: adding Flow Reference to list "
                          + tobeAdded.toString());
                  res.add(tobeAdded);
                }
              }
              //}
            }
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
}
