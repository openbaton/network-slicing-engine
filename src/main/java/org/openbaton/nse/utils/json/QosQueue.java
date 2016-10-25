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

/**
 * Created by maa on 10.11.15.
 */
//@Entity
public class QosQueue {

  //    @Id
  private String queue_uuid;
  private QosQueueValues rates;
  private String id;

  public QosQueue() {}

  public QosQueue(QosQueueValues rates, String queue_uuid, String id) {
    this.rates = rates;
    this.queue_uuid = queue_uuid;
    this.id = id;
  }

  public QosQueueValues getRates() {
    return rates;
  }

  public void setRates(QosQueueValues rates) {
    this.rates = rates;
  }

  public String getUuid() {
    return queue_uuid;
  }

  public void setUuid(String queue_uuid) {
    this.queue_uuid = queue_uuid;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return "QosQueue{"
        + "queue_uuid='"
        + queue_uuid
        + '\''
        + ", rates="
        + rates
        + ", id='"
        + id
        + '\''
        + '}';
  }
}
