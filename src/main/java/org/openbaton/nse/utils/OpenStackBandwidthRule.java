package org.openbaton.nse.utils;

import org.json.JSONObject;

/**
 * Created by lgr on 2/16/18.
 */
public class OpenStackBandwidthRule {
  private int max_burst_kbps;
  private int max_kbps;
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

  public OpenStackBandwidthRule(int max_burst_kbps, int max_kbps, String id, String type) {
    this.max_burst_kbps = max_burst_kbps;
    this.max_kbps = max_kbps;
    this.id = id;
    this.type = type;
  }

  public OpenStackBandwidthRule(JSONObject json) {
    this.max_burst_kbps = json.getInt("max_burst_kbps");
    this.max_kbps = json.getInt("max_kbps");
    this.id = json.getString("id");
    this.type = json.getString("type");
  }
}
