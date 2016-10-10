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

package org.openbaton.nse.core;

import org.openbaton.nse.beans.connectivitymanageragent.ConnectivityManagerRequestor;
import org.openbaton.nse.utils.FlowAllocation;
import org.openbaton.nse.utils.QoSAllocation;
import org.openbaton.nse.utils.json.Host;
import org.openbaton.nse.utils.json.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

/**
 * Created by maa on 03.12.15.
 */
@Service
@Scope("prototype")
public class ConnectivityManagerHandler {

  @Autowired private QosManagement queueHandler;
  @Autowired private FlowManagement flowsHandler;
  @Autowired private ConnectivityManagerRequestor requestor;
  private Logger logger;
  private Map<String, List<Server>> internalData;
  private Host hostMap;

  @PostConstruct
  private void init() {
    this.logger = LoggerFactory.getLogger(this.getClass());
    this.internalData = new LinkedHashMap<>();
    this.hostMap = new Host();
  }

  public boolean addQos(List<QoSAllocation> queues, FlowAllocation flows, String nsrId) {
    logger.info(
        "[CONNECTIVITY-MANAGER-HANDLER] allocating slice for "
            + nsrId
            + " at time "
            + new Date().getTime());
    logger.debug(
        "Start creating QOS for "
            + nsrId
            + " with queues "
            + queues.toString()
            + " and flows "
            + flows.toString());
    this.updateHost();
    List<Server> servers = queueHandler.createQueues(hostMap, queues, nsrId);
    internalData.put(nsrId, servers);
    logger.debug("MAP VALUE IS " + nsrId + " -> " + servers.toString());

    flowsHandler.addFlow(hostMap, servers, flows, nsrId);
    logger.info(
        "[CONNECTIVITY-MANAGER-HANDLER] allocated slice for "
            + nsrId
            + " at time "
            + new Date().getTime());
    return true;
  }

  public boolean updateQos() {
    return false;
  }

  private void updateHost() {
    this.hostMap = requestor.getHost();
  }

  public boolean removeQos(List<String> servers, String nsrID) {

    List<Server> serversList;
    logger.info(
        "[CONNECTIVITY-MANAGER-HANDLER] removing slice for "
            + nsrID
            + " at time "
            + new Date().getTime());
    try {
      serversList = internalData.get(nsrID);
      logger.info("SERVER LIST FOR DELETING IS " + serversList.toString());
    } catch (NullPointerException e) {
      logger.debug("Servers for " + nsrID + " not found");
      return false;
    }

    queueHandler.removeQos(hostMap, serversList, servers, nsrID);
    flowsHandler.removeFlow(hostMap, servers, internalData.get(nsrID), nsrID);
    internalData.remove(nsrID);
    logger.info(
        "[CONNECTIVITY-MANAGER-HANDLER] removed slice for "
            + nsrID
            + " at time "
            + new Date().getTime());
    return true;
  }
}
