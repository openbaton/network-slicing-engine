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
  //#############################################################
  // ~ 500 mb/s - Maximum Bandwidth for outgoing traffic
  PLATINUM("524288000", "0", "egress", "bandwidth_limit_rule"),
  // ~ 200 mb/s - Maximum Bandwidth for outgoing traffic
  GOLD("209715200", "0", "egress", "bandwidth_limit_rule"),
  // ~ 100 mb/s - Maximum Bandwidth for outgoing traffic
  SILVER("104857600", "0", "egress", "bandwidth_limit_rule"),
  // ~ 50 mb/s - Maximum Bandwidth for outgoing traffic
  BRONZE("52428800", "0", "egress", "bandwidth_limit_rule"),
  // ~ 5 mb/s - Maximum Bandwidth for outgoing traffic
  COAL("5242880", "0", "egress", "bandwidth_limit_rule"),
  //#############################################################
  // ~ 500 mb/s - Maximum Bandwidth for incoming traffic
  INGRESS_PLATINUM("524288000", "0", "ingress", "bandwidth_limit_rule"),
  // ~ 200 mb/s - Maximum Bandwidth for incoming traffic
  INGRESS_GOLD("209715200", "0", "ingress", "bandwidth_limit_rule"),
  // ~ 100 mb/s - Maximum Bandwidth for incoming traffic
  INGRESS_SILVER("104857600", "0", "ingress", "bandwidth_limit_rule"),
  // ~ 50 mb/s - Maximum Bandwidth for incoming traffic
  INGRESS_BRONZE("52428800", "0", "ingress", "bandwidth_limit_rule"),
  // ~ 5 mb/s - Maximum Bandwidth for incoming traffic
  INGRESS_COAL("5242880", "0", "ingress", "bandwidth_limit_rule"),
  //#############################################################
  // ~ 500 mb/s - Minimum Bandwidth for outgoing traffic
  MINIMUM_PLATINUM("524288000", "0", "egress", "minimum_bandwidth_rule"),
  // ~ 200 mb/s - Minimum Bandwidth for outgoing traffic
  MINIMUM_GOLD("209715200", "0", "egress", "minimum_bandwidth_rule"),
  // ~ 100 mb/s - Minimum Bandwidth for outgoing traffic
  MINIMUM_SILVER("104857600", "0", "egress", "minimum_bandwidth_rule"),
  // ~ 50 mb/s - Minimum Bandwidth for outgoing traffic
  MINIMUM_BRONZE("52428800", "0", "egress", "minimum_bandwidth_rule"),
  // ~ 5 mb/s - Minimum Bandwidth for outgoing traffic
  MINIMUM_COAL("5242880", "0", "egress", "minimum_bandwidth_rule"),
  //#############################################################
  // ~ 500 mb/s - Minimum Bandwidth for incoming traffic
  MINIMUM_INGRESS_PLATINUM("524288000", "0", "ingress", "minimum_bandwidth_rule"),
  // ~ 200 mb/s - Minimum Bandwidth for incoming traffic
  MINIMUM_INGRESS_GOLD("209715200", "0", "ingress", "minimum_bandwidth_rule"),
  // ~ 100 mb/s - Minimum Bandwidth for incoming traffic
  MINIMUM_INGRESS_SILVER("104857600", "0", "ingress", "minimum_bandwidth_rule"),
  // ~ 50 mb/s - Minimum Bandwidth for incoming traffic
  MINIMUM_INGRESS_BRONZE("52428800", "0", "ingress", "minimum_bandwidth_rule"),
  // ~ 5 mb/s - Minimum Bandwidth for incoming traffic
  MINIMUM_INGRESS_COAL("5242880", "0", "ingress", "minimum_bandwidth_rule");
  //#############################################################

  private String rate;
  private String burst;
  // egress / ingress
  private String direction;
  private String type;

  Quality(String rate, String burst, String direction, String type) {
    this.rate = rate;
    this.burst = burst;
    this.direction = direction;
    this.type = type;
  }

  public String getRate() {
    return rate;
  }

  public void setRate(String rate) {
    this.rate = rate;
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
