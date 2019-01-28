package org.openbaton.nse.utils.openstack;

import org.json.JSONObject;

/**
 * Created by lgr on 2/16/18.
 */
public class OpenStackBandwidthRule {
  private Integer burst;
  private Integer max_kbps;
  private String id;
  // type is usually "bandwidth_limit_rule"
  private String type;
  // egress / ingress
  private String direction;

  public void setMax_burst_kbps(int max_burst_kbps) {
    this.burst = max_burst_kbps;
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
    return burst;
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

  public String getDirection() { return direction; }

  public void setDirection(String direction) { this.direction = direction; }

  public OpenStackBandwidthRule() {}

  public OpenStackBandwidthRule(int burst, int max_kbps, String type) {
    this.burst =burst;
    this.max_kbps = max_kbps;
    this.type = type;
    this.direction = "egress";
  }

  public OpenStackBandwidthRule(String burst, String max_kbps, String type) {
    this.burst = new Integer(burst);
    this.max_kbps = new Integer(max_kbps);
    this.type = type;
    this.direction = "egress";
  }

  public OpenStackBandwidthRule(int max_burst_kbps, int max_kbps, String type, String direction) {
    this.burst = max_burst_kbps;
    this.max_kbps = max_kbps;
    this.type = type;
    this.direction = direction;
  }

  public OpenStackBandwidthRule(String max_burst_kbps, String max_kbps, String type, String direction) {
    this.burst = new Integer(max_burst_kbps);
    this.max_kbps = new Integer(max_kbps);
    this.type = type;
    this.direction = direction;
  }

  public OpenStackBandwidthRule(JSONObject json) {
    this.burst = json.getInt("max_burst_kbps");
    this.max_kbps = json.getInt("max_kbps");
    this.id = json.getString("id");
    this.type = json.getString("type");
    // Default rule direction is egress
    this.direction = json.optString("direction","egress");
  }
}
