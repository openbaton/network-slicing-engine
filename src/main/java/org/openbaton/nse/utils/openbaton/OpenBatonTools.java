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

import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.catalogue.security.Project;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.nse.properties.NfvoProperties;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
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

  private static Logger logger = LoggerFactory.getLogger(OpenBatonTools.class);

  // This bean is actually used to instantiate

  @Bean
  @SuppressWarnings("unused")
  public NFVORequestor getNFVORequestor()
      throws SDKException, NotFoundException, FileNotFoundException {
    if (!isNfvoStarted(nfvoProperties.getIp(), nfvoProperties.getPort())) {
      logger.error("NFVO is not available");
      System.exit(1);
    }
    NFVORequestor nfvoRequestor =
        new NFVORequestor(
            nfvoProperties.getUsername(),
            nfvoProperties.getPassword(),
            "*",
            false,
            nfvoProperties.getIp(),
            nfvoProperties.getPort(),
            "1");
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
        throw new NotFoundException("Not found project " + nfvoProperties.getProject().getName());
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
    VimInstance v = null;
    try {
      //logger.debug("trying to use viminstanceagent");
      //VimInstanceAgent agent = requestor.getVimInstanceAgent();
      //logger.debug("listing all vim-instances");
      //logger.debug(requestor.getVimInstanceAgent().findAll().toString());

      //v = requestor.getVimInstanceAgent().findById(vim_id);
      for (VimInstance vim : requestor.getVimInstanceAgent().findAll()) {
        if (vim.getId().equals(vim_id)) {
          // well we found the correct vim
          v = vim;
        }
      }
      if (v == null) {
        logger.warn("Problem generating the credentials");
        return cred;
      }
      //logger.debug("        adding identity : " + v.getTenant() + ":" + v.getUsername());
      cred.put("identity", v.getTenant() + ":" + v.getUsername());
      //logger.debug("        adding password : " + v.getPassword());
      cred.put("password", v.getPassword());
      //logger.debug("        adding nova auth url " + v.getAuthUrl());
      cred.put("auth", v.getAuthUrl());
    } catch (Exception e) {
      logger.error("Exception while creating credentials");
      logger.error(e.getMessage());
      logger.error(e.toString());
      e.printStackTrace();
    }
    return cred;
  }

  // Function to directly get a VimInstance by its id
  public VimInstance getVimInstance(NFVORequestor requestor, String vim_id) {
    VimInstance v = null;
    try {
      //logger.debug("listing all vim-instances");
      //logger.debug(requestor.getVimInstanceAgent().findAll().toString());
      //v = requestor.getVimInstanceAgent().findById(vim_id);
      for (VimInstance vim : requestor.getVimInstanceAgent().findAll()) {
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
      // TODO : instead of false check for enabled SSL
      // openbaton.getSsl().isEnabled()
      requestor = new NFVORequestor("nse", project_id, ip, port, "1", false, service_key);
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
