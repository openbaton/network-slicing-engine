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

import com.google.gson.annotations.SerializedName;

import org.openbaton.nse.utils.Quality;

/**
 * Created by maa on 04.11.15.
 */
public class QosQueueValues {

  @SerializedName("min-rate")
  private String min_bitrate;

  @SerializedName("max-rate")
  private String max_bitrate;

  public QosQueueValues() {}

  public QosQueueValues(String min_bitrate, String max_bitrate) {
    this.min_bitrate = min_bitrate;
    this.max_bitrate = max_bitrate;
  }

  public QosQueueValues(Quality quality) {

    this.min_bitrate = quality.getMin_rate();
    this.max_bitrate = quality.getMax_rate();
  }

  public String getMin_bitrate() {
    return min_bitrate;
  }

  public void setMin_bitrate(String min_bitrate) {
    this.min_bitrate = min_bitrate;
  }

  public String getMax_bitrate() {
    return max_bitrate;
  }

  public void setMax_bitrate(String max_bitrate) {
    this.max_bitrate = max_bitrate;
  }

  //    public Quality getQuality (){
  //        Quality quality ;
  //    }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof QosQueueValues)) return false;

    QosQueueValues that = (QosQueueValues) o;

    if (!getMin_bitrate().equals(that.getMin_bitrate())) return false;
    return getMax_bitrate().equals(that.getMax_bitrate());
  }

  @Override
  public int hashCode() {
    int result = getMin_bitrate().hashCode();
    result = 31 * result + getMax_bitrate().hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "QosQueueValues{"
        + "min_bitrate='"
        + min_bitrate
        + '\''
        + ", max_bitrate='"
        + max_bitrate
        + '\''
        + '}';
  }
}
