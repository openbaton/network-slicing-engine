/*
 * Copyright (c) 2015 Technische Universität Berlin
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openbaton.nse.beans.openbaton;

import org.openbaton.nse.core.ConnectivityManagerHandler;
import org.openbaton.nse.utils.Quality;
import org.openbaton.catalogue.mano.descriptor.InternalVirtualLink;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by maa on 18.01.16.
 */
public class RemoveQoSExecutor implements Runnable {

  private ConnectivityManagerHandler connectivityManagerHandler;
  private Logger logger;
  private Set<VirtualNetworkFunctionRecord> vnfrs;
  private String nsrID;

  public RemoveQoSExecutor(
      ConnectivityManagerHandler connectivityManagerHandler,
      Set<VirtualNetworkFunctionRecord> vnfrs,
      String nsrID) {
    this.connectivityManagerHandler = connectivityManagerHandler;
    this.vnfrs = vnfrs;
    this.nsrID = nsrID;
    this.logger = LoggerFactory.getLogger(this.getClass());
  }

  @Override
  public void run() {
    List<String> servers = this.getServersWithQoS(vnfrs);
    logger.debug("remmoving qos for nsr " + nsrID + " with vnfrs: " + vnfrs);
    boolean response = connectivityManagerHandler.removeQos(servers, nsrID);
    logger.debug("Response from handler " + response);
  }

  private List<String> getServersWithQoS(Set<VirtualNetworkFunctionRecord> vnfrs) {
    List<String> res = new ArrayList<>();

    Map<String, Quality> qualities = this.getVlrs(vnfrs);

    for (VirtualNetworkFunctionRecord vnfr : vnfrs) {
      // Check for the vnfr name , not the network name anymore..
      if (qualities.keySet().contains(vnfr.getName())) {
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
          for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
            for (VNFDConnectionPoint connectionPoint : vnfcInstance.getConnection_point()) {
              // this has become obsolete
              //if (qualities.keySet().contains(connectionPoint.getVirtual_link_reference())){
              logger.debug("GETSERVERWITHQOS");
              res.add(vnfcInstance.getHostname());
              //}
            }
          }
        }
      }
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

  private Quality mapValueQuality(String value) {
    logger.debug("MAPPING VALUE-QUALITY: received value " + value);
    String[] qos = value.split(":");
    logger.debug("MAPPING VALUE-QUALITY: quality is " + qos[1]);
    return Quality.valueOf(qos[1]);
  }
}
