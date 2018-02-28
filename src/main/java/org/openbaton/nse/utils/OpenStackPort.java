package org.openbaton.nse.utils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by lgr on 2/28/18.
 */
public class OpenStackPort {

  private String id;
  private String qosPolicy;

  public String getQosPolicy() {
    return qosPolicy;
  }

  public void setQosPolicy(String qosPolicy) {
    this.qosPolicy = qosPolicy;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public OpenStackPort(String id, String qosPolicy) {
    this.id = id;
    this.qosPolicy = qosPolicy;
  }

  public OpenStackPort() {}

  public OpenStackPort(JSONObject json) {
    this.id = json.getString("id");
    try {
      this.qosPolicy = json.getString("qos_policy_id");
    } catch (JSONException e) {
      this.qosPolicy = "no-qos-policy";
    }
  }
}
