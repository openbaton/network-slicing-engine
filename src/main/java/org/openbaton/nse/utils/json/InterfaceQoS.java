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

/**
 * Created by maa on 24.11.15.
 */
//@Entity
public class InterfaceQoS {

  //    @Id
  //    @Expose(serialize = false, deserialize = false)
  //    private String id;
  private String ip;
  private String ovs_port_number;
  private Qos qos;

  public InterfaceQoS(String ip, String ovs_port_number, Qos qos) {
    this.ip = ip;
    this.ovs_port_number = ovs_port_number;
    this.qos = qos;
  }

  public InterfaceQoS() {}

  //    public String getId() {
  //        return id;
  //    }
  //
  //    public void setId(String id) {
  //        this.id = id;
  //    }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public String getOvs_port_number() {
    return ovs_port_number;
  }

  public void setOvs_port_number(String ovs_port_number) {
    this.ovs_port_number = ovs_port_number;
  }

  public Qos getQos() {
    return qos;
  }

  public void setQos(Qos qos) {
    this.qos = qos;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof InterfaceQoS)) return false;

    InterfaceQoS that = (InterfaceQoS) o;

    if (this.getIp().equals(that.getIp())) return true;
    return false;
  }

  @Override
  public int hashCode() {
    int result = getIp().hashCode();
    result = 31 * result + getOvs_port_number().hashCode();
    result = 31 * result + getQos().hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "InterfaceQoS{"
        + "ip='"
        + ip
        + '\''
        + ", ovs_port_number='"
        + ovs_port_number
        + '\''
        + ", qos="
        + qos
        + '}';
  }
}
