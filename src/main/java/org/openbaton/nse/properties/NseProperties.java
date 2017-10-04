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

  //  private String baseUrl;
  //
  //  private String driver;

  private String library_type;

  private String key;

  private Logger logger = LoggerFactory.getLogger(this.getClass());

  //  public String getBaseUrl() {
  //    return baseUrl;
  //  }
  //
  //  public void setBaseUrl(String baseUrl) {
  //    this.baseUrl = baseUrl;
  //  }

  //  public String getDriver() {
  //    return driver;
  //  }
  //
  //  public void setDriver(String driver) {
  //    this.driver = driver;
  //  }

  public String getLibrary_type() {
    return library_type;
  }

  public void setLibrary_type(String library_type) {
    this.library_type = library_type;
  }

  //  public Key getKey() {
  //    return key;
  //  }
  //
  //  public void setKey(Key key) {
  //    this.key = key;
  //  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  //  @PostConstruct
  //  private void init() {
  //
  //    logger.debug("Agent baseurl is: " + baseUrl);
  //  }

  //  public static class Key {
  //    private File file;
  //
  //    public File getFile() {
  //      return file;
  //    }
  //
  //    public void setFile(File file) {
  //      this.file = file;
  //    }
  //
  //    @Override
  //    public String toString() {
  //      return "Key{" + "file=" + file + '}';
  //    }
  //  }
  //
  //  public static class File {
  //    private String path;
  //
  //    public String getPath() {
  //      return path;
  //    }
  //
  //    public void setPath(String path) {
  //      this.path = path;
  //    }
  //
  //    @Override
  //    public String toString() {
  //      return "File{" + "path='" + path + '\'' + '}';
  //    }
  //  }

}
