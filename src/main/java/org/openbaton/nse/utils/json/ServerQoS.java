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

package org.openbaton.nse.utils.json;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Created by maa on 16.11.15.
 */
public class ServerQoS {

  @SerializedName("hypervisor_id")
  private String hypervisorId;

  @SerializedName("server_id")
  private String serverId;

  private List<InterfaceQoS> interfaces;

  public ServerQoS() {}

  public ServerQoS(String hypervisorId, String serverId, List<InterfaceQoS> interfaces) {
    this.hypervisorId = hypervisorId;
    this.serverId = serverId;
    this.interfaces = interfaces;
  }

  public String getHypervisorId() {
    return hypervisorId;
  }

  public void setHypervisorId(String hypervisorId) {
    this.hypervisorId = hypervisorId;
  }

  public String getServerId() {
    return serverId;
  }

  public void setServerId(String serverId) {
    this.serverId = serverId;
  }

  public List<InterfaceQoS> getInterfaces() {
    return interfaces;
  }

  public void setInterfaces(List<InterfaceQoS> interfaces) {
    this.interfaces = interfaces;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ServerQoS)) return false;

    ServerQoS serverQoS = (ServerQoS) o;

    if (!getServerId().equals(serverQoS.getServerId())) return false;
    return getInterfaces().equals(serverQoS.getInterfaces());
  }

  @Override
  public int hashCode() {
    int result = getServerId().hashCode();
    result = 31 * result + getInterfaces().hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "ServerQoS{"
        + "hypervisorId='"
        + hypervisorId
        + '\''
        + ", serverId='"
        + serverId
        + '\''
        + ", interfaces="
        + interfaces
        + '}';
  }
}
