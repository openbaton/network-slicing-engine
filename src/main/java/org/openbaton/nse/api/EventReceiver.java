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

package org.openbaton.nse.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import org.openbaton.catalogue.mano.descriptor.InternalVirtualLink;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.EndpointType;
import org.openbaton.catalogue.nfvo.EventEndpoint;
import org.openbaton.nse.beans.openbaton.QoSAllocator;
import org.openbaton.nse.properties.RabbitMQProperties;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import javax.annotation.PreDestroy;

/**
 * Created by maa on 11.11.15.
 */
@Service
public class EventReceiver implements CommandLineRunner {

  @Autowired private NFVORequestor requestor;
  @Autowired private RabbitMQProperties rabbitMQProperties;
  @Autowired private QoSAllocator creator;
  @Autowired private Gson mapper;

  private Logger logger = LoggerFactory.getLogger(this.getClass());
  private List<String> eventIds;

  public static final String queueName_eventInstantiateFinish = "openbaton.nse.nsr.create";
  public static final String queueName_eventError = "openbaton.nse.nsr.error";
  public static final String queueName_eventScale = "openbaton.nse.nsr.scale";

  private void init() throws SDKException, IOException {

    this.eventIds = new ArrayList<>();

    EventEndpoint eventEndpointCreation = new EventEndpoint();
    eventEndpointCreation.setType(EndpointType.RABBIT);
    eventEndpointCreation.setEvent(Action.INSTANTIATE_FINISH);
    eventEndpointCreation.setEndpoint(queueName_eventInstantiateFinish);
    eventEndpointCreation.setName("eventNsrInstantiateFinish");
    eventEndpointCreation = requestor.getEventAgent().create(eventEndpointCreation);

    EventEndpoint eventEndpointDeletion = new EventEndpoint();
    eventEndpointDeletion.setType(EndpointType.RABBIT);
    eventEndpointDeletion.setEvent(Action.RELEASE_RESOURCES_FINISH);
    eventEndpointDeletion.setEndpoint(queueName_eventError);
    eventEndpointDeletion.setName("eventNsrReleaseFinish");
    eventEndpointDeletion = requestor.getEventAgent().create(eventEndpointDeletion);

    EventEndpoint eventEndpointScale = new EventEndpoint();
    eventEndpointScale.setType(EndpointType.RABBIT);
    eventEndpointScale.setEvent(Action.SCALED);
    eventEndpointScale.setEndpoint(queueName_eventScale);
    eventEndpointScale.setName("eventNsrScaleFinish");
    eventEndpointScale = requestor.getEventAgent().create(eventEndpointScale);

    this.eventIds.add(eventEndpointCreation.getId());
    this.eventIds.add(eventEndpointDeletion.getId());
    this.eventIds.add(eventEndpointScale.getId());
  }

  public void receiveConfiguration(String message) {
    logger.debug("received new event " + message);
    Action action;
    NetworkServiceRecord nsr;

    try {
      logger.debug("Trying to deserialize it");
      JsonParser jsonParser = new JsonParser();
      JsonObject json = jsonParser.parse(message).getAsJsonObject();
      action = mapper.fromJson(json.get("action"), Action.class);
      nsr = mapper.fromJson(json.get("payload"), NetworkServiceRecord.class);
    } catch (JsonParseException e) {
      if (logger.isDebugEnabled() || logger.isTraceEnabled())
        logger.warn("Error in payload, expected NSR ", e);
      else logger.warn("Error in payload, expected NSR " + e.getMessage());
      return;
    }

    logger.info(
        "[OPENBATON-EVENT-SUBSCRIPTION] received new NSR "
            + nsr.getId()
            + "for slice allocation at time "
            + new Date().getTime());
    logger.debug("ACTION: " + action + " PAYLOAD: " + nsr.toString());

    for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
      logger.debug("VNFR: " + vnfr.toString());
      for (InternalVirtualLink vlr : vnfr.getVirtual_link()) {
        logger.debug("VLR: " + vlr.toString());
        if (!vlr.getQos().isEmpty()) {
          for (String qosAttr : vlr.getQos()) {
            logger.debug("QoS Attribute: " + qosAttr);
            if (qosAttr.contains("minimum_bandwith")) {
              logger.info(
                  "[OPENBATON-EVENT-SUBSCRIPTION] sending the NSR "
                      + nsr.getId()
                      + "for slice allocation to nsr handler at time "
                      + new Date().getTime());
              creator.addQos(nsr.getVnfr(), nsr.getId());
            }
          }
        }
      }
    }
    logger.info(
        "[OPENBATON-EVENT-SUBSCRIPTION] Ended message callback function at "
            + new Date().getTime());
  }

  public void deleteConfiguration(String message) {

    logger.debug("received new event " + message);
    Action action;
    NetworkServiceRecord nsr;

    try {
      logger.debug("Trying to deserialize it");
      JsonParser jsonParser = new JsonParser();
      JsonObject json = jsonParser.parse(message).getAsJsonObject();
      action = mapper.fromJson(json.get("action"), Action.class);
      nsr = mapper.fromJson(json.get("payload"), NetworkServiceRecord.class);
    } catch (JsonParseException e) {
      if (logger.isDebugEnabled() || logger.isTraceEnabled())
        logger.warn("Error in payload, expected NSR ", e);
      else logger.warn("Error in payload, expected NSR " + e.getMessage());
      return;
    }

    logger.info(
        "[OPENBATON-EVENT-SUBSCRIPTION] received new NSR "
            + nsr.getId()
            + " for slice removal at time "
            + new Date().getTime());
    logger.debug("ACTION: " + action + " PAYLOAD " + nsr.toString());

    for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
      logger.debug("VNFR: " + vnfr.toString());
      for (InternalVirtualLink vlr : vnfr.getVirtual_link()) {
        logger.debug("VLR: " + vlr.toString());
        if (!vlr.getQos().isEmpty()) {
          for (String qosAttr : vlr.getQos()) {
            if (qosAttr.contains("minimum_bandwith")) {
              logger.info(
                  "[OPENBATON-EVENT-SUBSCRIPTION] sending the NSR "
                      + nsr.getId()
                      + "for slice removal to nsr handler at time "
                      + new Date().getTime());
              creator.removeQos(nsr.getVnfr(), nsr.getId());
            }
          }
        }
      }
    }
  }

  public void scaleConfiguration(String message) {

    logger.debug("received new event " + message);
    Action action;
    VirtualNetworkFunctionRecord vnfr;

    try {
      logger.debug("Trying to deserialize it");
      JsonParser jsonParser = new JsonParser();
      JsonObject json = jsonParser.parse(message).getAsJsonObject();
      action = mapper.fromJson(json.get("action"), Action.class);
      vnfr = mapper.fromJson(json.get("payload"), VirtualNetworkFunctionRecord.class);
    } catch (JsonParseException e) {
      if (logger.isDebugEnabled() || logger.isTraceEnabled())
        logger.warn("Error in payload, expected NSR ", e);
      else logger.warn("Error in payload, expected NSR " + e.getMessage());
      return;
    }

    logger.info(
        "[OPENBATON-EVENT-SUBSCRIPTION] received new VNFR "
            + vnfr.getId()
            + " for scaled slice at time "
            + new Date().getTime());
    logger.debug("ACTION: " + action + " PAYLOAD " + vnfr.toString());

    for (InternalVirtualLink vlr : vnfr.getVirtual_link()) {
      logger.debug("VLR: " + vlr.toString());
      if (!vlr.getQos().isEmpty()) {
        for (String qosAttr : vlr.getQos()) {
          if (qosAttr.contains("minimum_bandwith")) {
            logger.info(
                "[OPENBATON-EVENT-SUBSCRIPTION] sending the VNFR "
                    + vnfr.getId()
                    + "for slice scale to nsr handler at time "
                    + new Date().getTime());
            creator.addQos(
                new HashSet<VirtualNetworkFunctionRecord>(Arrays.asList(vnfr)),
                vnfr.getParent_ns_id());
          }
        }
      }
    }
  }

  @PreDestroy
  private void dispose() throws SDKException {
    for (String id : this.eventIds) {
      requestor.getEventAgent().delete(id);
    }
  }

  @Bean
  public ConnectionFactory getConnectionFactory(Environment env) {
    logger.debug("Created ConnectionFactory");
    CachingConnectionFactory factory = new CachingConnectionFactory(rabbitMQProperties.getHost());
    factory.setPassword(rabbitMQProperties.getPassword());
    factory.setUsername(rabbitMQProperties.getUsername());
    return factory;
  }

  @Bean
  public TopicExchange getTopic() {
    logger.debug("Created Topic Exchange");
    return new TopicExchange("openbaton-exchange");
  }

  @Bean
  public Queue getCreationQueue() {
    logger.debug("Created Queue for NSR Create event");
    return new Queue(queueName_eventInstantiateFinish, false, false, true);
  }

  @Bean
  public Queue getErrorQueue() {
    logger.debug("Created Queue for NSR error event");
    return new Queue(queueName_eventError, false, false, true);
  }

  @Bean
  public Queue getScaleQueue() {
    logger.debug("Created Queue for NSR scale event");
    return new Queue(queueName_eventScale, false, false, true);
  }

  @Bean
  public Binding setCreationBinding(
      @Qualifier("getCreationQueue") Queue queue, TopicExchange topicExchange) {
    logger.debug("Created Binding for NSR Creation event");
    return BindingBuilder.bind(queue).to(topicExchange).with("ns-creation");
  }

  @Bean
  public Binding setErrorBinding(
      @Qualifier("getErrorQueue") Queue queue, TopicExchange topicExchange) {
    logger.debug("Created Binding for NSR error event");
    return BindingBuilder.bind(queue).to(topicExchange).with("ns-error");
  }

  @Bean
  public Binding setScaleBinding(
      @Qualifier("getScaleQueue") Queue queue, TopicExchange topicExchange) {
    logger.debug("Created Binding for NSR scale event");
    return BindingBuilder.bind(queue).to(topicExchange).with("ns-scale");
  }

  @Bean
  public MessageListenerAdapter setCreationMessageListenerAdapter() {
    return new MessageListenerAdapter(this, "receiveConfiguration");
  }

  @Bean
  public MessageListenerAdapter setErrorMessageListenerAdapter() {
    return new MessageListenerAdapter(this, "deleteConfiguration");
  }

  @Bean
  public MessageListenerAdapter setScaleMessageListenerAdapter() {
    return new MessageListenerAdapter(this, "scaleConfiguration");
  }

  @Bean
  public SimpleMessageListenerContainer setCreationMessageContainer(
      ConnectionFactory connectionFactory,
      @Qualifier("getCreationQueue") Queue queue,
      @Qualifier("setCreationMessageListenerAdapter") MessageListenerAdapter adapter) {
    logger.debug("Created MessageContainer for NSR Creation event");
    SimpleMessageListenerContainer res = new SimpleMessageListenerContainer();
    res.setConnectionFactory(connectionFactory);
    res.setQueues(queue);
    res.setMessageListener(adapter);
    return res;
  }

  @Bean
  public SimpleMessageListenerContainer setErrorMessageContainer(
      ConnectionFactory connectionFactory,
      @Qualifier("getErrorQueue") Queue queue,
      @Qualifier("setErrorMessageListenerAdapter") MessageListenerAdapter adapter) {
    logger.debug("Created MessageContainer for NSR error event");
    SimpleMessageListenerContainer res = new SimpleMessageListenerContainer();
    res.setConnectionFactory(connectionFactory);
    res.setQueues(queue);
    res.setMessageListener(adapter);
    return res;
  }

  @Bean
  public SimpleMessageListenerContainer setScaleMessageContainer(
      ConnectionFactory connectionFactory,
      @Qualifier("getScaleQueue") Queue queue,
      @Qualifier("setScaleMessageListenerAdapter") MessageListenerAdapter adapter) {
    logger.debug("Created MessageContainer for NSR scale event");
    SimpleMessageListenerContainer res = new SimpleMessageListenerContainer();
    res.setConnectionFactory(connectionFactory);
    res.setQueues(queue);
    res.setMessageListener(adapter);
    return res;
  }

  public void run(String... args) throws Exception {
    init();
  }
}
