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

package org.openbaton.nse.utils;

/**
 * Created by maa on 09.12.15.
 */
public class DetailedQoSReference extends QoSReference {

  public String getVim_id() {
    return vim_id;
  }

  public void setVim_id(String vim_id) {
    this.vim_id = vim_id;
  }

  private String vim_id;

  public DetailedQoSReference(String ip, Quality quality, String vim_id) {
    super();
    this.vim_id = vim_id;
  }

  public DetailedQoSReference() {}

  @Override
  public String toString() {
    return "QoSReference{"
        + "ip='"
        + super.getIp()
        + '\''
        + ", quality="
        + super.getQuality()
        + ", vim_id="
        + this.vim_id
        + '}';
  }
}
