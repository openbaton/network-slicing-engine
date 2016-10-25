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
//the method requires an array
public class AddQueue {

  private String hypervisor_id;
  private List<Qos> values;

  public AddQueue() {}

  public AddQueue(String hypervisor_id, List<Qos> values) {
    this.hypervisor_id = hypervisor_id;
    this.values = values;
  }

  public String getHypervisor_id() {
    return hypervisor_id;
  }

  public void setHypervisor_id(String hypervisor_id) {
    this.hypervisor_id = hypervisor_id;
  }

  public List<Qos> getValues() {
    return values;
  }

  public void setValues(List<Qos> values) {
    this.values = values;
  }
}
