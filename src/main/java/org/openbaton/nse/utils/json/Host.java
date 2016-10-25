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

package org.openbaton.nse.utils.json;

import java.util.List;

/**
 * Created by maa on 10/11/2015.
 */
public class Host {

  private List<Datacenter> hypervisors;

  public Host(List<Datacenter> hypervisors) {
    this.hypervisors = hypervisors;
  }

  public Host() {}

  public List<Datacenter> getHosts() {
    return hypervisors;
  }

  public void setHosts(List<Datacenter> hypervisors) {
    this.hypervisors = hypervisors;
  }

  public String belongsTo(String serverName) {

    for (Datacenter datacenter : hypervisors) {

      if (datacenter.getServers().contains(serverName)) {

        return datacenter.getName();
      }
    }

    return null;
  }
}
