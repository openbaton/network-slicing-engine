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
 * Created by maa on 02.12.15.
 */
public class Flow {

  private String ovs_port_number;
  private String src_ipv4;
  private String dest_ipv4;
  private String dest_hyp;
  private String protocol; //could be tcp/udp
  private String priority;
  private String queue_number;

  public Flow(
      String ovs_port_number,
      String src_ipv4,
      String dest_ipv4,
      String dest_hyp,
      String protocol,
      String priority,
      String queue_number) {
    this.ovs_port_number = ovs_port_number;
    this.src_ipv4 = src_ipv4;
    this.dest_ipv4 = dest_ipv4;
    this.dest_hyp = dest_hyp;
    this.protocol = protocol;
    this.priority = priority;
    this.queue_number = queue_number;
  }

  public Flow() {}

  public String getOvs_port_number() {
    return ovs_port_number;
  }

  public void setOvs_port_number(String ovs_port_number) {
    this.ovs_port_number = ovs_port_number;
  }

  public String getSrc_ipv4() {
    return src_ipv4;
  }

  public void setSrc_ipv4(String src_ipv4) {
    this.src_ipv4 = src_ipv4;
  }

  public String getDest_ipv4() {
    return dest_ipv4;
  }

  public void setDest_ipv4(String dest_ipv4) {
    this.dest_ipv4 = dest_ipv4;
  }

  public String getProtocol() {
    return protocol;
  }

  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }

  public String getPriority() {
    return priority;
  }

  public void setPriority(String priority) {
    this.priority = priority;
  }

  public String getQueue_number() {
    return queue_number;
  }

  public void setQueue_number(String queue_number) {
    this.queue_number = queue_number;
  }

  public String getDest_hyp() {
    return dest_hyp;
  }

  public void setDest_hyp(String dest_hyp) {
    this.dest_hyp = dest_hyp;
  }

  @Override
  public String toString() {
    return "Flow{"
        + "ovs_port='"
        + ovs_port_number
        + '\''
        + ", src_ipv4='"
        + src_ipv4
        + '\''
        + ", dest_ipv4='"
        + dest_ipv4
        + '\''
        + ", dest_hyp='"
        + dest_hyp
        + '\''
        + ", protocol='"
        + protocol
        + '\''
        + ", priority='"
        + priority
        + '\''
        + ", queue_number='"
        + queue_number
        + '\''
        + '}';
  }
}
