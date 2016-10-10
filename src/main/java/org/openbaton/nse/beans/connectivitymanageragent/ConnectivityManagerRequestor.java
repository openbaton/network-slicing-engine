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

package org.openbaton.nse.beans.connectivitymanageragent;

import com.google.gson.Gson;

import org.openbaton.nse.properties.NetworkSlicingEngineProperties;
import org.openbaton.nse.utils.json.AddQueue;
import org.openbaton.nse.utils.json.Host;
import org.openbaton.nse.utils.json.QosAdd;
import org.openbaton.nse.utils.json.RequestFlows;
import org.openbaton.nse.utils.json.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import javax.annotation.PostConstruct;

/**
 * Created by Carlo on 10/11/2015.
 */
@Service
@Scope("prototype")
public class ConnectivityManagerRequestor {

  @Autowired private Gson mapper;
  @Autowired private NetworkSlicingEngineProperties configuration;
  private Logger logger;
  private RestTemplate template;

  @PostConstruct
  private void init() throws IOException {
    this.logger = LoggerFactory.getLogger(this.getClass());
    this.template = new RestTemplate();
  }

  public Host getHost() {
    String url = configuration.getBaseUrl() + "/hosts";
    HttpHeaders headers = new HttpHeaders();
    HttpEntity<String> getEntity = new HttpEntity<>(headers);
    logger.debug("REQUESTING HOSTS to " + url);
    ResponseEntity<String> hosts = template.exchange(url, HttpMethod.GET, getEntity, String.class);

    logger.debug("hosts " + hosts.getBody());

    if (!hosts.getStatusCode().is2xxSuccessful()) {
      return null;
    } else {
      return mapper.fromJson(hosts.getBody(), Host.class);
    }
  }

  public QosAdd setQoS(QosAdd qosRequest) {

    logger.debug("SENDING REQUEST FOR " + mapper.toJson(qosRequest, QosAdd.class));
    String url = configuration.getBaseUrl() + "/qoses";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> setEntity =
        new HttpEntity<>(mapper.toJson(qosRequest, QosAdd.class), headers);
    logger.debug("SENDING QOS " + mapper.toJson(qosRequest, QosAdd.class));
    ResponseEntity<String> insert =
        template.exchange(url, HttpMethod.POST, setEntity, String.class);

    logger.debug(
        "Setting of QoS has produced http status:"
            + insert.getStatusCode()
            + " with body: "
            + insert.getBody());

    if (!insert.getStatusCode().is2xxSuccessful()) {
      return null;
    } else {
      QosAdd result = mapper.fromJson(insert.getBody(), QosAdd.class);
      logger.debug(
          "RESULT IS "
              + insert.getStatusCode()
              + " with body "
              + mapper.toJson(result, QosAdd.class));
      return result;
    }
  }

  public Server getServerData(String hypervisorName, String serverName) {

    logger.debug("Getting data for server " + serverName + " that belong to " + hypervisorName);
    String url = configuration.getBaseUrl() + "/server/" + hypervisorName + "/" + serverName;
    HttpHeaders headers = new HttpHeaders();
    HttpEntity<String> getEntity = new HttpEntity<>(headers);
    ResponseEntity<String> server = template.exchange(url, HttpMethod.GET, getEntity, String.class);

    logger.debug(
        "Setting of QoS has produced http status:"
            + server.getStatusCode()
            + " with body: "
            + server.getBody());

    if (!server.getStatusCode().is2xxSuccessful()) {
      return null;
    } else {
      Server result = mapper.fromJson(server.getBody(), Server.class);
      logger.debug(
          "Request produced "
              + server.getStatusCode()
              + " with data "
              + mapper.toJson(result, Server.class));
      return result;
    }
  }

  public HttpStatus delQos(String hypervisorName, String qosId) {

    String url = configuration.getBaseUrl() + "/qoses/" + hypervisorName + "/" + qosId;
    HttpHeaders headers = new HttpHeaders();
    HttpEntity<String> delentity = new HttpEntity<>(headers);
    ResponseEntity<String> delete =
        template.exchange(url, HttpMethod.DELETE, delentity, String.class);

    if (delete.getStatusCode().is5xxServerError()) {
      logger.debug(
          "The port is still here, returned "
              + delete.getStatusCode()
              + " with body "
              + delete.getBody());
      return delete.getStatusCode();
    }

    logger.debug("deleting qos " + qosId + " has returned " + delete.getStatusCode());

    return delete.getStatusCode();
  }

  public HttpStatus delQueue(
      String hypervisorName, String qosId, String queueId, String queueNumber) {

    String url =
        configuration.getBaseUrl()
            + "/queue/"
            + hypervisorName
            + "/"
            + queueId
            + "/"
            + queueNumber
            + "/"
            + qosId;
    HttpHeaders headers = new HttpHeaders();
    HttpEntity<String> delQueueEntity = new HttpEntity<>(headers);
    ResponseEntity<String> delete =
        template.exchange(url, HttpMethod.DELETE, delQueueEntity, String.class);

    logger.debug(
        "deleting queue "
            + queueId
            + " with qosID "
            + qosId
            + " has returned "
            + delete.getStatusCode());

    return delete.getStatusCode();
  }

  public AddQueue addQueue(AddQueue add) {

    String url = configuration.getBaseUrl() + "/queue";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> postEntity = new HttpEntity<>(mapper.toJson(add, AddQueue.class), headers);
    ResponseEntity<String> addResp =
        template.exchange(url, HttpMethod.POST, postEntity, String.class);

    logger.debug("posted " + add.toString() + " and returned " + addResp.getBody());

    if (!addResp.getStatusCode().is2xxSuccessful()) {
      return null;
    } else {
      return mapper.fromJson(addResp.getBody(), AddQueue.class);
    }
  }

  public RequestFlows setFlow(RequestFlows flow) {

    String url = configuration.getBaseUrl() + "/flow";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> flowEntity =
        new HttpEntity<>(mapper.toJson(flow, RequestFlows.class), headers);
    logger.debug("SENDING FLOWS " + mapper.toJson(flow, RequestFlows.class));
    ResponseEntity<String> addFlow =
        template.exchange(url, HttpMethod.POST, flowEntity, String.class);

    logger.debug(
        "FLOW RESPONSE: sent flow configuration "
            + flow.toString()
            + " and received "
            + addFlow.getBody());

    if (!addFlow.getStatusCode().is2xxSuccessful()) {
      logger.debug("Status code is " + addFlow.getStatusCode());
      return null;
    } else {
      return mapper.fromJson(addFlow.getBody(), RequestFlows.class);
    }
  }

  public HttpStatus deleteFlow(String hypervisor_name, String flow_protocol, String flow_ip) {

    String url =
        configuration.getBaseUrl()
            + "/flow/"
            + hypervisor_name
            + "/"
            + flow_protocol
            + "/"
            + flow_ip;
    HttpHeaders headers = new HttpHeaders();
    HttpEntity<String> deleteFlowEntity = new HttpEntity<>(headers);
    ResponseEntity<String> deleteResponse =
        template.exchange(url, HttpMethod.DELETE, deleteFlowEntity, String.class);

    logger.debug("Deleted flow with result " + deleteResponse.getStatusCode());

    return deleteResponse.getStatusCode();
  }
}
