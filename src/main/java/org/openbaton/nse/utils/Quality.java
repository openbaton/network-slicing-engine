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
  // 1126 mb/s burst - 1024 mb/s
  PLATINUM("1181116006", "1073741824"),
  // 110 mb/s burst - 100 mb/s
  GOLD("115343360", "104857600"),
  // 11 mb/s burst - 10 mb/s
  SILVER("11534336", "10485760"),
  // 1.1 mb/s burst - 1 mb/s
  BRONZE("1153433", "1048576"),
  // 0.2 mb/s burst - 0.1 mb/s
  COAL("209715", "104857");

  private String max_rate;
  private String min_rate;

  Quality(String max_rate, String min_rate) {
    this.max_rate = max_rate;
    this.min_rate = min_rate;
  }

  public String getMax_rate() {
    return max_rate;
  }

  public void setMax_rate(String max_rate) {
    this.max_rate = max_rate;
  }

  public String getMin_rate() {
    return min_rate;
  }

  public void setMin_rate(String min_rate) {
    this.min_rate = min_rate;
  }
}
