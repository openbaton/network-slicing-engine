package org.openbaton.nse.utils.openstack;


import org.json.JSONObject;

/**
 * Created by lgr on 28.01.19.
 */
public class OpenStackMinimumBandwidthRule {

    private Integer min_kbps;
    private String id;
    // type is usually "minimum_bandwidth_rule"
    private String type;
    // egress / ingress
    private String direction;

    public Integer getMin_kbps() { return min_kbps; }

    public void setMin_kbps(Integer min_kbps) { this.min_kbps = min_kbps; }

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getType() { return type; }

    public void setType(String type) { this.type = type; }

    public String getDirection() { return direction; }

    public void setDirection(String direction) { this.direction = direction; }

    public OpenStackMinimumBandwidthRule(){};

    public OpenStackMinimumBandwidthRule(int min_kbps, String type) {
        this.min_kbps = min_kbps;
        this.type = type;
        this.direction = "egress";
    }

    public OpenStackMinimumBandwidthRule(int min_kbps, String type, String direction) {
        this.min_kbps = min_kbps;
        this.type = type;
        this.direction = direction;
    }

    public OpenStackMinimumBandwidthRule(JSONObject json) {
        this.min_kbps = json.getInt("min_kbps");
        this.id = json.getString("id");
        this.type = json.getString("type");
        // Default rule direction is egress
        this.direction = json.optString("direction","egress");
    }
}
