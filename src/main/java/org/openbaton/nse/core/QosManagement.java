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

package org.openbaton.nse.core;

import org.openbaton.nse.beans.connectivitymanageragent.ConnectivityManagerRequestor;
import org.openbaton.nse.interfaces.QosManagementInterface;
import org.openbaton.nse.utils.QoSAllocation;
import org.openbaton.nse.utils.QoSReference;
import org.openbaton.nse.utils.Quality;
import org.openbaton.nse.utils.json.Host;
import org.openbaton.nse.utils.json.InterfaceQoS;
import org.openbaton.nse.utils.json.Qos;
import org.openbaton.nse.utils.json.QosAdd;
import org.openbaton.nse.utils.json.QosQueue;
import org.openbaton.nse.utils.json.QosQueueValues;
import org.openbaton.nse.utils.json.Server;
import org.openbaton.nse.utils.json.ServerQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.PostConstruct;

/**
 * Created by mpa on 05.09.16.
 */
@Service
@Scope("prototype")
public class QosManagement implements QosManagementInterface {

  @Autowired private ConnectivityManagerRequestor requestor;
  private Logger logger;

  @PostConstruct
  private void init() {
    this.logger = LoggerFactory.getLogger(this.getClass());
  }

  public List<Server> createQueues(Host hostMap, List<QoSAllocation> queues, String nsrId) {

    logger.info("[QOS-HANDLER] CREATING queues for " + nsrId + " at time " + new Date().getTime());
    logger.debug("received request for " + queues.toString());

    List<ServerQoS> queuesReq = new ArrayList<>();
    List<Server> servers = new ArrayList<>();

    for (QoSAllocation allocation : queues) {

      String serverName = allocation.getServerName();
      logger.debug("[CREATING QUEUES] get server name " + serverName);
      String hypervisor = hostMap.belongsTo(serverName);
      logger.debug("[CREATING QUEUES] get hypervisor name " + hypervisor);
      Server serverData = requestor.getServerData(hypervisor, serverName);
      logger.debug("[CREATING QUEUES] server data is " + serverData.toString());
      servers.add(serverData);
      ServerQoS serverQoS =
          this.compileServerRequest(serverData, allocation.getIfaces(), hypervisor);
      queuesReq.add(serverQoS);
    }

    QosAdd add = new QosAdd(queuesReq);
    add = requestor.setQoS(add);

    servers = this.updateServers(servers, add);
    logger.info("[QOS-HANDLER] CREATED queues for " + nsrId + " at time " + new Date().getTime());
    return servers;
  }

  private List<Server> updateServers(List<Server> servers, QosAdd add) {

    List<Server> updated = new ArrayList<>();
    for (Server server : servers) {
      ServerQoS qos = add.getQosByServerID(server.getId());
      if (qos != null) {
        server.updateInterfaces(qos.getInterfaces());
      }
      updated.add(server);
    }

    return updated;
  }

  private ServerQoS compileServerRequest(
      Server serverData, List<QoSReference> ifaces, String hypervisor) {

    logger.debug(
        "[COMPILE SERVER REQUEST] Server data: "
            + serverData.toString()
            + " hypervisor "
            + hypervisor);
    ServerQoS res = new ServerQoS();
    res.setHypervisorId(hypervisor);
    res.setServerId(serverData.getId());

    List<InterfaceQoS> ifacesReq = new ArrayList<>();
    for (InterfaceQoS serverIface : serverData.getInterfaces()) {
      for (QoSReference ref : ifaces) {
        if (serverIface.getIp().equals(ref.getIp())) {
          InterfaceQoS iface = this.addQos(serverIface, ref.getQuality());
          ifacesReq.add(iface);
        }
      }
    }
    res.setInterfaces(ifacesReq);

    return res;
  }

  @Override
  public InterfaceQoS addQos(InterfaceQoS serverIface, Quality quality) {

    Qos qos = serverIface.getQos();
    int idNum = qos.getActualID() + 1;
    String id = "" + idNum;
    QosQueue queue = new QosQueue(new QosQueueValues(quality), "", id);
    qos.addQueue(queue);
    serverIface.setQos(qos);
    return serverIface;
  }

  @Override
  public void updateQos() {}

  @Override
  public void removeQos(Host hostMap, List<Server> servers, List<String> serverIds, String nsrId) {

    logger.info("[QOS-HANDLER] REMOVING queues for " + nsrId + " at time " + new Date().getTime());
    for (Server server : servers) {
      if (serverIds.contains(server.getName())) {
        String hypervisor = hostMap.belongsTo(server.getName());
        for (InterfaceQoS iface : server.getInterfaces()) {
          Qos ifaceQoS = iface.getQos();
          requestor.delQos(hypervisor, ifaceQoS.getQos_uuid());
        }
      }
    }
    logger.info("[QOS-HANDLER] REMOVED queues for " + nsrId + " at time " + new Date().getTime());
  }
}
