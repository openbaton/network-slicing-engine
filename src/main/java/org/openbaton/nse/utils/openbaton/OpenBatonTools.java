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

package org.openbaton.nse.utils.openbaton;

import org.openbaton.catalogue.nfvo.viminstances.BaseVimInstance;
import org.openbaton.catalogue.nfvo.viminstances.OpenstackVimInstance;
import org.openbaton.catalogue.security.Project;
import org.openbaton.catalogue.security.ServiceMetadata;
import org.openbaton.nse.properties.NfvoProperties;
import org.openbaton.nse.properties.NseProperties;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.NfvoRequestorBuilder;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by maa on 13.10.15. modified by lgr on 20.07.17
 */
@Configuration
public class OpenBatonTools {

  @SuppressWarnings("unused")
  @Autowired
  private NfvoProperties nfvoProperties;

  @SuppressWarnings("unused")
  @Autowired
  private NseProperties nseProperties;

  private static Logger logger = LoggerFactory.getLogger(OpenBatonTools.class);

  // This bean is actually used to instantiate

  @Bean
  @SuppressWarnings("unused")
  public NFVORequestor getNFVORequestor() throws SDKException, FileNotFoundException {
    if (!isNfvoStarted(nfvoProperties.getIp(), nfvoProperties.getPort())) {
      logger.error("NFVO is not available");
      System.exit(1);
    }
    NFVORequestor nfvoRequestor =
        NfvoRequestorBuilder.create()
            .nfvoIp(nfvoProperties.getIp())
            .nfvoPort(Integer.parseInt(nfvoProperties.getPort()))
            .username(nfvoProperties.getUsername())
            .password(nfvoProperties.getPassword())
            .sslEnabled(nfvoProperties.getSsl().isEnabled())
            .version("1")
            .build();
    logger.info("Starting the Open Baton Manager Bean");
    try {
      logger.info("Finding default project");
      boolean found = false;
      for (Project project : nfvoRequestor.getProjectAgent().findAll()) {
        if (project.getName().equals(nfvoProperties.getProject().getName())) {
          found = true;
          nfvoRequestor.setProjectId(project.getId());
          logger.info("Found default project");
        }
      }
      if (!found) {
        logger.error("Not found project " + nfvoProperties.getProject().getName());
      }
    } catch (SDKException e) {
      throw new SDKException(e);
    }

    return nfvoRequestor;
  }

  // Function to extract credentials directly out of a virtualized infrastructure manager ( VIM )
  public Map<String, String> getDatacenterCredentials(NFVORequestor requestor, String vim_id) {
    Map<String, String> cred = new HashMap<>();
    // What we want to archieve is to list all machines and know to which vim-instance they belong
    BaseVimInstance v = null;
    try {
      //logger.debug("trying to use viminstanceagent");
      //VimInstanceAgent agent = requestor.getVimInstanceAgent();
      //logger.debug("listing all vim-instances");
      //logger.debug(requestor.getVimInstanceAgent().findAll().toString());

      //v = requestor.getVimInstanceAgent().findById(vim_id);
      for (BaseVimInstance vim : requestor.getVimInstanceAgent().findAll()) {
        if (vim.getId().equals(vim_id)) {
          // well we found the correct vim
          v = vim;
        }
      }
      if (v == null) {
        logger.warn("Problem generating the credentials");
        return cred;
      }
      if (OpenstackVimInstance.class.isInstance(v)) {
        OpenstackVimInstance osV = (OpenstackVimInstance) v;
        //logger.debug("        adding identity : " + v.getTenant() + ":" + v.getUsername());
        cred.put("identity", osV.getTenant() + ":" + osV.getUsername());
        //logger.debug("        adding password : " + v.getPassword());
        cred.put("password", osV.getPassword());
        //logger.debug("        adding nova auth url " + v.getAuthUrl());
        cred.put("auth", v.getAuthUrl());
      }

    } catch (Exception e) {
      logger.error("Exception while creating credentials");
      logger.error(e.getMessage());
      logger.error(e.toString());
      e.printStackTrace();
    }
    return cred;
  }

  // Function to directly get a VimInstance by its id
  public BaseVimInstance getVimInstance(NFVORequestor requestor, String vim_id) {
    BaseVimInstance v = null;
    try {
      //logger.debug("listing all vim-instances");
      //logger.debug(requestor.getVimInstanceAgent().findAll().toString());
      //v = requestor.getVimInstanceAgent().findById(vim_id);
      for (BaseVimInstance vim : requestor.getVimInstanceAgent().findAll()) {

        if (vim.getId().equals(vim_id)) {
          // well we found the correct vim
          v = vim;
        }
      }
    } catch (Exception e) {
      logger.error("Exception while creating credentials");
      logger.error(e.getMessage());
      logger.error(e.toString());
      e.printStackTrace();
    }
    return v;
  }

  public NFVORequestor getNFVORequestor(
      String ip, String port, String project_id, String service_key) {
    NFVORequestor requestor = null;
    try {
      if (service_key.isEmpty()) {
        logger.warn("No service key provided. Trying to self register as new service...");
        if (nfvoProperties.getUsername().isEmpty() && nfvoProperties.getPassword().isEmpty()) {
          logger.error("Not found user and/or password to self register as a new service...");
        }
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setName("autoscaling-engine");
        ArrayList<String> roles = new ArrayList<>();
        roles.add("*");
        NFVORequestor tmpNfvoRequestor =
            NfvoRequestorBuilder.create()
                .nfvoIp(nfvoProperties.getIp())
                .nfvoPort(Integer.parseInt(nfvoProperties.getPort()))
                .username(nfvoProperties.getUsername())
                .password(nfvoProperties.getPassword())
                .sslEnabled(nfvoProperties.getSsl().isEnabled())
                .version("1")
                .build();

        String serviceKey = tmpNfvoRequestor.getServiceAgent().create("nse", roles);
        logger.info("Received service key: " + serviceKey);
        NseProperties.Service serv = new NseProperties.Service();
        serv.setKey(serviceKey);
        nseProperties.setService(serv);
      }

      requestor =
          NfvoRequestorBuilder.create()
              .nfvoIp(ip)
              .nfvoPort(Integer.parseInt(port))
              .serviceName("nse")
              .serviceKey(service_key)
              .projectId("*")
              .sslEnabled(nfvoProperties.getSsl().isEnabled())
              .version("1")
              .build();
    } catch (SDKException e) {
      logger.error("Problem instantiating NFVORequestor for project " + project_id);
    }
    return requestor;
  }

  public static boolean isNfvoStarted(String ip, String port) {
    int i = 600;
    logger.info("Testing if NFVO is available...");
    while (!available(ip, port)) {
      logger.warn(
          "NFVO is not available at "
              + ip
              + ":"
              + port
              + ". Waiting for "
              + i
              + "s before terminating the VNFM");
      i--;
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      if (i <= 0) {
        return false;
      }
    }
    logger.info("NFVO is listening at " + ip + ":" + port);
    return true;
  }

  public static boolean available(String ip, String port) {
    try {
      Socket s = new Socket(ip, Integer.parseInt(port));
      s.close();
      return true;
    } catch (IOException ex) {
      // The remote host is not listening on this port
      return false;
    }
  }
}
