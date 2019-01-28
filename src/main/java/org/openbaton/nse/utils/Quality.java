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
 * Created by maa on 11.11.15.
 */
public enum Quality {
  // 1126 mb/s burst - 500 mb/s
  PLATINUM("524288000", "0", "egress", "bandwidth_limit_rule"),
  // 110 mb/s burst - 200 mb/s
  GOLD("209715200", "0", "egress", "bandwidth_limit_rule"),
  // 11 mb/s burst - 100 mb/s
  SILVER("104857600", "0", "egress", "bandwidth_limit_rule"),
  // 1.1 mb/s burst - 50 mb/s
  BRONZE("52428800", "0", "egress", "bandwidth_limit_rule"),
  // 0.2 mb/s burst - 5 mb/s
  COAL("5242880", "0", "egress", "bandwidth_limit_rule");

  private String max_rate;
  private String burst;
  // egress / ingress
  private String direction;
  private String type;

  Quality(String max_rate, String burst, String direction, String type) {
    this.max_rate = max_rate;
    this.burst = burst;
    this.direction = direction;
    this.type = type;
  }

  public String getMax_rate() {
    return max_rate;
  }

  public void setMax_rate(String max_rate) {
    this.max_rate = max_rate;
  }

  public String getBurst() {
    return burst;
  }

  public void setBurst(String min_rate) { this.burst = min_rate; }

  public String getDirection() { return direction; }

  public void setDirection(String direction) { this.direction = direction; }

  public String getType() { return type; }

  public void setType(String type) { this.type = type; }

}
