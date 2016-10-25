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
 * Created by maa on 02.12.15.
 */
public class FlowServer {

  private String hypervisor_id;
  private String server_id;
  private List<Flow> qos_flows;

  public FlowServer(String server_id, List<Flow> qos_flows) {
    this.server_id = server_id;
    this.qos_flows = qos_flows;
  }

  public FlowServer() {}

  public String getHypervisor_id() {
    return hypervisor_id;
  }

  public void setHypervisor_id(String hypervisor_id) {
    this.hypervisor_id = hypervisor_id;
  }

  public String getServer_id() {
    return server_id;
  }

  public void setServer_id(String server_id) {
    this.server_id = server_id;
  }

  public List<Flow> getQos_flows() {
    return qos_flows;
  }

  public void setQos_flows(List<Flow> qos_flows) {
    this.qos_flows = qos_flows;
  }

  @Override
  public String toString() {
    return "FlowServer{"
        + "hypervisor_id='"
        + hypervisor_id
        + '\''
        + ", server_id='"
        + server_id
        + '\''
        + ", qos_flows="
        + qos_flows
        + '}';
  }
}
