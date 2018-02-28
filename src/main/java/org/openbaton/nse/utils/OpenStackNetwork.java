package org.openbaton.nse.utils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by lgr on 2/28/18.
 */
public class OpenStackNetwork {

  private String id;
  private String name;
  private String qosPolicy;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getQosPolicy() {
    return qosPolicy;
  }

  public void setQosPolicy(String qosPolicy) {
    this.qosPolicy = qosPolicy;
  }

  public OpenStackNetwork(String id, String name, String qosPolicy) {
    this.id = id;
    this.name = name;
    this.qosPolicy = qosPolicy;
  }

  public OpenStackNetwork() {}

  public OpenStackNetwork(JSONObject json) {
    this.id = json.getString("id");
    this.name = json.getString("name");
    try {
      this.qosPolicy = json.getString("qos_policy_id");
    } catch (JSONException e) {
      this.qosPolicy = "no-qos-policy";
    }
  }
}
