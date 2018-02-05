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

  private String vim_id;
  private String hostname;
  private String vnfr_id;
  private String vdu_id;
  private String vnfci_id;
  private String nsr_id;

  public String getVim_id() {
    return vim_id;
  }

  public void setVim_id(String vim_id) {
    this.vim_id = vim_id;
  }

  public String getHostname() {
    return hostname;
  }

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  public String getVnfci_id() {
    return vnfci_id;
  }

  public void setVnfci_id(String vnfci_id) {
    this.vnfci_id = vnfci_id;
  }

  public String getVnfr_id() {
    return vnfr_id;
  }

  public void setVnfr_id(String vnfr_id) {
    this.vnfr_id = vnfr_id;
  }

  public String getVdu_id() {
    return vdu_id;
  }

  public void setVdu_id(String vdu_id) {
    this.vdu_id = vdu_id;
  }

  public String getNsr_id() {
    return nsr_id;
  }

  public void setNsr_id(String nsr_id) {
    this.nsr_id = nsr_id;
  }

  public DetailedQoSReference(
      String ip,
      Quality quality,
      String vim_id,
      String hostname,
      String vnfr_id,
      String vdu_id,
      String vnfci_id,
      String nsr_id) {
    super();
    this.vim_id = vim_id;
    this.hostname = hostname;
    this.vnfr_id = vnfr_id;
    this.vdu_id = vdu_id;
    this.vnfci_id = vnfci_id;
    this.nsr_id = nsr_id;
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
        + ", vnfr_name="
        + this.hostname
        + ", vnfr_id="
        + this.vnfr_id
        + ", vdu_id="
        + this.vdu_id
        + ", vnfci_id="
        + this.vnfci_id
        + ", nsr_id="
        + this.nsr_id
        + '}';
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      if (obj.getClass().equals(this.getClass())) {
        if (DetailedQoSReference.class.isAssignableFrom(obj.getClass())) {
          DetailedQoSReference tmp = (DetailedQoSReference) obj;
          if (((DetailedQoSReference) obj).getHostname().equals(this.getHostname())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return this.getHostname().hashCode();
  }
}
