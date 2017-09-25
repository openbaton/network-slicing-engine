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

package org.openbaton.nse.properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * Created by maa on 01.02.16. modified by lgr on 20.07.17
 */
@Service
@ConfigurationProperties(prefix = "nse")
public class NseProperties {

  private String baseUrl;

  private String driver;

  private String library_type;

  private Logger logger = LoggerFactory.getLogger(this.getClass());

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getDriver() {
    return driver;
  }

  public void setDriver(String driver) {
    this.driver = driver;
  }

  public String getLibrary_type() {
    return library_type;
  }

  public void setLibrary_type(String library_type) {
    this.library_type = library_type;
  }

  @PostConstruct
  private void init() {

    logger.debug("Agent baseurl is: " + baseUrl);
  }
}
