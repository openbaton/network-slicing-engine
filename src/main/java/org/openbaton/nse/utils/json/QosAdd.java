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
 * Created by maa on 09/11/2015.
 */
public class QosAdd {

  private List<ServerQoS> values;

  public QosAdd(List<ServerQoS> values) {
    this.values = values;
  }

  public QosAdd() {}

  public List<ServerQoS> getValues() {
    return values;
  }

  public void setValues(List<ServerQoS> values) {
    this.values = values;
  }

  public ServerQoS getQosByServerID(String serverId) {

    for (ServerQoS qos : values) {
      if (qos.getServerId().equals(serverId)) return qos;
    }

    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof QosAdd)) return false;

    QosAdd request = (QosAdd) o;

    return getValues().equals(request.getValues());
  }

  @Override
  public int hashCode() {
    return getValues().hashCode();
  }
}
