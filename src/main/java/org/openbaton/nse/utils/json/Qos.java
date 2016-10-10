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

import java.util.List;

/**
 * Created by maa on 04.11.15.
 */
//@Entity
public class Qos {

  //    @Id
  private String qos_uuid;
  //    @OneToMany(targetEntity = QosQueue.class,cascade = CascadeType.ALL,fetch = FetchType.EAGER)
  private List<QosQueue> queues;

  public Qos() {}

  public Qos(List<QosQueue> queues, String qos_uuid) {
    this.queues = queues;
    this.qos_uuid = qos_uuid;
  }

  public List<QosQueue> getQueues() {
    return queues;
  }

  public void setQueues(List<QosQueue> queues) {
    this.queues = queues;
  }

  public String getQos_uuid() {
    return qos_uuid;
  }

  public void setQos_uuid(String qos_uuid) {
    this.qos_uuid = qos_uuid;
  }

  public int getActualID() {
    return queues.size(); //DO NOT CREATE QUEUE WITH ID 0 ON OVS!!!
  }

  public void addQueue(QosQueue queue) {
    this.queues.add(queue);
  }

  @Override
  public String toString() {
    return "Qos{" + "qos_uuid='" + qos_uuid + '\'' + ", queues=" + queues + '}';
  }
}
