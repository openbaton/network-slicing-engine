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
import org.openbaton.nse.interfaces.FlowManagementInterface;
import org.openbaton.nse.utils.FlowAllocation;
import org.openbaton.nse.utils.FlowReference;
import org.openbaton.nse.utils.json.Flow;
import org.openbaton.nse.utils.json.FlowServer;
import org.openbaton.nse.utils.json.Host;
import org.openbaton.nse.utils.json.InterfaceQoS;
import org.openbaton.nse.utils.json.RequestFlows;
import org.openbaton.nse.utils.json.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
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
@Configuration
@Scope("prototype")
public class FlowManagement implements FlowManagementInterface {

  @Autowired private ConnectivityManagerRequestor requestor;
  private Logger logger;
  private String protocol;
  private String priority;

  @PostConstruct
  private void init() {

    this.logger = LoggerFactory.getLogger(this.getClass());
    this.protocol = "tcp";
    this.priority = "2";
  }

  @Override
  public void addFlow(Host host, List<Server> servers, FlowAllocation allocations, String nsrId) {
    logger.info("[FLOW-HANDLER] CREATING flows for " + nsrId + " at time " + new Date().getTime());
    logger.debug("Received Flow allocation " + allocations.toString());
    List<FlowServer> flows = new ArrayList<>();
    for (String vlr : allocations.getAllVlr()) {
      for (FlowReference fr : allocations.getIpsForVlr(vlr)) {
        for (Server server : servers) {
          if (server.getName().equals(fr.getHostname())) {
            FlowServer fs = new FlowServer();
            fs.setHypervisor_id(host.belongsTo(server.getName()));
            fs.setServer_id(server.getName());
            InterfaceQoS iface = server.getFromIp(fr.getIp());
            List<Flow> internalFlows = new ArrayList<>();
            for (String ip : allocations.getAllIpsForVlr(vlr)) {
              if (ip.equals(fr.getIp())) {
                Flow tmp = new Flow();
                // tmp.setDest_ipv4(ip);
                Server vm = this.getServerRefFromIp(servers, fr.getIp());
                tmp.setDest_hyp(host.belongsTo(vm.getName()));
                tmp.setOvs_port_number(vm.getFromIp(ip).getOvs_port_number());
                tmp.setPriority(priority);
                tmp.setProtocol(protocol);
                tmp.setSrc_ipv4(iface.getIp());
                tmp.setQueue_number("" + vm.getFromIp(ip).getQos().getActualID());
                internalFlows.add(tmp);
              }
            }
            fs.setQos_flows(internalFlows);
            flows.add(fs);
          }
        }
      }
    }
    RequestFlows request = new RequestFlows(flows);
    logger.debug("REQUEST is " + request.toString());
    RequestFlows returningFlows = requestor.setFlow(request);
    logger.debug("Returning flows " + returningFlows.toString());
    logger.info("[FLOW-HANDLER] CREATED queues for " + nsrId + " at time " + new Date().getTime());
  }

  @Override
  public void updateFlow(
      Host hostmap, List<String> serversIds, List<Server> servers, String nsrId) {}

  @Override
  public void removeFlow(
      Host hostmap, List<String> serversIds, List<Server> servers, String nsrId) {

    logger.info("[FLOW-HANDLER] REMOVING queues for " + nsrId + " at time " + new Date().getTime());

    for (Server server : servers) {
      if (serversIds.contains(server.getName())) {
        String hypervisor = hostmap.belongsTo(server.getName());
        for (InterfaceQoS iface : server.getInterfaces()) {
          requestor.deleteFlow(hypervisor, protocol, iface.getIp());
        }
      }
    }
    logger.info("[FLOW-HANDLER] REMOVED queues for " + nsrId + " at time " + new Date().getTime());
  }

  private Server getServerRefFromIp(List<Server> servers, String ip) {

    for (Server server : servers) {
      if (server.getFromIp(ip) != null) {
        return server;
      }
    }

    return null;
  }
}
