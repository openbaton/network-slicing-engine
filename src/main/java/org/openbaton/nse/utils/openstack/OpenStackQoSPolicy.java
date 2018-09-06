package org.openbaton.nse.utils.openstack;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by lgr on 2/16/18.
 */
public class OpenStackQoSPolicy {

  private String id;
  private String name;
  private String description;
  private ArrayList<OpenStackBandwidthRule> rules;

  public void setId(String id) {
    this.id = id;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setRules(ArrayList<OpenStackBandwidthRule> rules) {
    this.rules = rules;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public ArrayList<OpenStackBandwidthRule> getRules() {
    return rules;
  }

  public OpenStackQoSPolicy() {}

  public OpenStackQoSPolicy(
      String id, String name, String description, ArrayList<OpenStackBandwidthRule> rules) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.rules = rules;
  }

  public OpenStackQoSPolicy(JSONObject json) {
    this.id = json.getString("id");
    this.name = json.getString("name");
    this.description = json.getString("description");

    ArrayList<OpenStackBandwidthRule> rules = new ArrayList<OpenStackBandwidthRule>();
    JSONArray tmp_rules = json.getJSONArray("rules");
    if (rules != null) {
      for (int x = 0; x < tmp_rules.length(); x++) {
        OpenStackBandwidthRule tmp_rule = new OpenStackBandwidthRule(tmp_rules.getJSONObject(x));
        rules.add(tmp_rule);
      }
    }
    this.rules = rules;
  }
}
