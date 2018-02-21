package org.openbaton.nse.utils;

import org.json.JSONObject;

/**
 * Created by lgr on 2/16/18.
 */
public class OpenStackBandwidthRule {
  private Integer max_burst_kbps;
  private Integer max_kbps;
  private String id;
  private String type;

  public void setMax_burst_kbps(int max_burst_kbps) {
    this.max_burst_kbps = max_burst_kbps;
  }

  public void setMax_kbps(int max_kbps) {
    this.max_kbps = max_kbps;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Integer getMax_burst_kbps() {
    return max_burst_kbps;
  }

  public Integer getMax_kbps() {
    return max_kbps;
  }

  public String getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public OpenStackBandwidthRule() {}

  public OpenStackBandwidthRule(int max_burst_kbps, int max_kbps, String type) {
    this.max_burst_kbps = max_burst_kbps;
    this.max_kbps = max_kbps;
    this.type = type;
  }

  public OpenStackBandwidthRule(String max_burst_kbps, String max_kbps, String type) {
    this.max_burst_kbps = new Integer(max_burst_kbps);
    this.max_kbps = new Integer(max_kbps);
    this.type = type;
  }

  public OpenStackBandwidthRule(JSONObject json) {
    this.max_burst_kbps = json.getInt("max_burst_kbps");
    this.max_kbps = json.getInt("max_kbps");
    this.id = json.getString("id");
    this.type = json.getString("type");
  }
}
