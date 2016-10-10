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

package org.openbaton.nse.utils;

/**
 * Created by maa on 18.02.16.
 */
public class VldQuality {

  private String vnfrId;
  private String vlid;
  private Quality quality;

  public VldQuality(String vnfrId, String vlid, Quality quality) {
    this.vnfrId = vnfrId;
    this.vlid = vlid;
    this.quality = quality;
  }

  public VldQuality() {}

  public String getVnfrId() {
    return vnfrId;
  }

  public void setVnfrId(String vnfrId) {
    this.vnfrId = vnfrId;
  }

  public String getVlid() {
    return vlid;
  }

  public void setVlid(String vlid) {
    this.vlid = vlid;
  }

  public Quality getQuality() {
    return quality;
  }

  public void setQuality(Quality quality) {
    this.quality = quality;
  }
}
