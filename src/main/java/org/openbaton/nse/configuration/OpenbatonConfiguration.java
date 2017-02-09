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

package org.openbaton.nse.configuration;

import org.openbaton.catalogue.security.Project;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.nse.properties.NfvoProperties;
import org.openbaton.nse.utils.Utils;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Created by maa on 13.10.15.
 */
@Configuration
@ComponentScan("org.openbaton.nse")
public class OpenbatonConfiguration {

  @Autowired private NfvoProperties nfvoProperties;

  private static Logger logger = LoggerFactory.getLogger(OpenbatonConfiguration.class);

  @Bean
  public NFVORequestor getNFVORequestor() throws SDKException, NotFoundException {
    if (!Utils.isNfvoStarted(nfvoProperties.getIp(), nfvoProperties.getPort())) {
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
    } catch (SDKException | ClassNotFoundException e) {
      throw new SDKException(e);
    }
    return nfvoRequestor;
  }
}
