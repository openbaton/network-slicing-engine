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
 * Created by maa on 04.11.15.
 */
//@Entity
public class Server {

  //    @Id
  private String id;
  private String name;
  //    @OneToMany(targetEntity = InterfaceQoS.class, cascade = CascadeType.ALL, fetch = FetchType.EAGER,mappedBy = "server",orphanRemoval = true)
  private List<InterfaceQoS> interfaces;

  public Server() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public List<InterfaceQoS> getInterfaces() {
    return interfaces;
  }

  public void setInterfaces(List<InterfaceQoS> interfaces) {
    this.interfaces = interfaces;
  }

  public InterfaceQoS getFromIp(String ip) {

    for (InterfaceQoS iface : interfaces) {
      if (iface.getIp().equals(ip)) return iface;
    }
    return null;
  }

  public void updateInterfaces(List<InterfaceQoS> ifaces) {

    for (InterfaceQoS updatedIface : ifaces) {
      this.updateInterface(updatedIface);
    }
  }

  private void updateInterface(InterfaceQoS iface) {

    for (InterfaceQoS oldIface : interfaces) {

      if (oldIface.getIp().equals(iface.getIp())) {
        interfaces.remove(oldIface);
        interfaces.add(iface);
      }
    }
  }

  @Override
  public String toString() {
    return "Server{"
        + "id='"
        + id
        + '\''
        + ", name='"
        + name
        + '\''
        + ", interfaces="
        + interfaces
        + '}';
  }
}
