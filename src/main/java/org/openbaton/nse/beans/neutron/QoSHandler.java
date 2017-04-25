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

package org.openbaton.nse.beans.neutron;

import org.jclouds.openstack.keystone.v2_0.domain.Access;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by lgr on 9/21/16.
 */
public class QoSHandler {

  private Logger logger;

  public QoSHandler() {
    this.logger = LoggerFactory.getLogger(this.getClass());
  };

  /**
   * Builds up a simple http_connection to the neutron rest api
   *
   * @return response.toString()
   *
   * if something gone bad, you will receive null
   */
  public String neutron_http_connection(
      String t_url, String method, Object access, JSONObject payload) {
    logger.debug("Building up neutron http connection : " + t_url + " method : " + method);
    HttpURLConnection connection = null;
    URL url = null;
    try {
      url = new URL(t_url);
      connection = (HttpURLConnection) url.openConnection();
      if (method.equals("PUT")) {
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
      } else if (method.equals("POST")) {

      } else if (method.equals("GET")) {
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
      } else {
        logger.error("No method defined for http request while contacting neutron");
        return null;
      }
      connection.setDoOutput(true);
      if (access instanceof Access) {
        connection.setRequestProperty("X-Auth-Token", ((Access) access).getToken().getId());
      } else if (access instanceof String) {
        connection.setRequestProperty("X-Auth-Token", ((String) access));
      } else {
        logger.error("Access object was neither String or Access");
        return null;
      }

      connection.setRequestProperty("User-Agent", "python-neutronclient");
      if (payload != null) {
        OutputStreamWriter output = new OutputStreamWriter(connection.getOutputStream());
        output.write(payload.toString());
        output.flush();
        output.close();
      }
      InputStream is = connection.getInputStream();
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = rd.readLine()) != null) {
        response.append(line);
        response.append('\r');
      }
      rd.close();
      connection.disconnect();
      //logger.debug("Response of final request is: " + response.toString());
      logger.debug("Got response from neutron : " + response.toString());
      return response.toString();
    } catch (Exception e) {
      logger.error("Problem contacting openstack-neutron");
      logger.error(e.getMessage());
      logger.error(e.toString());
      e.printStackTrace();
      // If we have found a problem, we should probably end this thread
      return null;
    }
  }

  /**
   * Parses a policy list response from neutron
   *
   * @return a Hash Map containing the policy names and their ids
   */
  public Map<String, String> parsePolicyMap(String response) {
    Map<String, String> tmp = new HashMap<String, String>();
    try {
      logger.debug("Parsing existing policies");
      // TODO : catch null objects
      JSONObject ans = new JSONObject(response);
      //logger.debug("Created JSON-Object : "+ans.toString());
      JSONArray qos_p = ans.getJSONArray("policies");
      //logger.debug("Created JSON-Array : "+qos_p.toString());
      logger.debug("There are " + qos_p.length() + " qos-policies available");
      for (int i = 0; i < qos_p.length(); i++) {
        JSONObject o = qos_p.getJSONObject(i);
        //logger.debug(o.toString());
        tmp.put(o.getString("name"), o.getString("id"));
      }
    } catch (Exception e) {
      logger.error(e.getMessage());
      logger.error(e.toString());
    }
    return tmp;
  }

  public boolean checkForBandwidthRule(String response, String max_kbps) {
    try {
      logger.debug("Parsing policy");
      int bw = Integer.parseInt(max_kbps);
      JSONObject policy = new JSONObject(response).getJSONObject("policy");
      // Check if the policy is shared
      if (policy.getBoolean("shared")) {
        JSONArray pol_rules = policy.getJSONArray("rules");
        for (int i = 0; i < pol_rules.length(); i++) {
          JSONObject o = pol_rules.getJSONObject(i);
          // Check if the policy got the right max_kbs assigned
          if (o.getInt("max_kbps") == bw) {
            return true;
          }
        }
      }
    } catch (Exception e) {
      logger.error(e.getMessage());
      logger.error(e.toString());
    }
    return false;
  }

  public String parseNeutronEndpoint(Access access) {
    try {
      String json = access.toString();
      // Unfortunaltely we are not working on valid JSON here...
      /*
      // get rid of the beginning to have valid json...
      json = json.substring(6);
      logger.debug(json);
      JSONObject ac = new JSONObject(json);
      JSONArray services = ac.getJSONArray("serviceCatalog");
      for (int i = 0; i < services.length(); i++) {
        JSONObject s = services.getJSONObject(i);
        logger.debug("Iterating over Service : " + s.getString("name"));
        // If the name is neutron we want to get the public URL
        if (s.getString("name").equals("neutron")) {
          JSONArray endpoints = s.getJSONArray("endpoints");
          // Well actually this should only be a 1 entry array here
          for (int x = 0; x < endpoints.length(); x++) {
            JSONObject e = endpoints.getJSONObject(x);
            return e.getString("publicURL");
          }
        }
      }
      */
      // Cut all from the beginning until we are at the serviceCatalog
      json = json.substring(json.indexOf("serviceCatalog"));
      // Now cut all until we find our neutron service
      json = json.substring(json.indexOf("neutron"));
      // Now advance to the publicURL
      json = json.substring(json.indexOf("publicURL"));
      // Next cut off the rest after the ","
      json = json.substring(0, json.indexOf(","));
      return json.substring(json.indexOf("=") + 1);
    } catch (Exception e) {
      logger.error(e.getMessage());
      logger.error(e.toString());
    }
    logger.error("Did not found neutron endpoint");
    return null;
  }

  public boolean checkPortQoSPolicy(String response, String id) {
    String port_qos_id = null;
    try {
      Object o = new JSONObject(response).getJSONObject("port").get("qos_policy_id");
      if (o == null) {
        return false;
      } else {
        port_qos_id = (String) o;
        if (port_qos_id.equals(id)) {
          // we do not need a update of the port
          return true;
        }
      }
    } catch (ClassCastException e) {
      logger.debug("port does not have a qos policy assigned");
    }
    // We need a update of the port
    return false;
  }

  public JSONObject createPolicyPayload(String name) {
    // TODO : catch null objects
    JSONObject tmp =
        new JSONObject(
            "{\"policy\":{\"name\":\""
                + name
                + "\",\"description\":\"generated by connectivity manager\",\"shared\":\"true\"}}");
    logger.debug("Created policy payload : " + tmp.toString());
    return tmp;
  }

  public JSONObject createPolicyUpdatePayload(String id) {
    // TODO : catch null objects
    JSONObject tmp = new JSONObject("{\"port\":{\"qos_policy_id\":\"" + id + "\"}}");
    logger.debug("Created policy update payload : " + tmp.toString());
    return tmp;
  }

  public JSONObject createBandwidthLimitRulePayload(String max_rate) {
    // TODO : catch null objects
    JSONObject tmp =
        new JSONObject("{\"bandwidth_limit_rule\":{\"max_kbps\":\"" + max_rate + "\"}}");
    logger.debug("Created bandwidth_limit_rule payload : " + tmp.toString());
    return tmp;
  }

  public String parsePolicyId(String response) {
    // TODO : catch null objects
    JSONObject pol = new JSONObject(response);
    String tmp = pol.getJSONObject("policy").getString("id");
    return tmp;
  }

  public String get_service_endpoint(String t_url, String access, String service_id) {
    logger.debug("Building up nova http connection : " + t_url);
    HttpURLConnection connection = null;
    URL url = null;
    try {
      url = new URL(t_url);
      connection = (HttpURLConnection) url.openConnection();
      connection.setDoOutput(true);
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("X-Auth-Token", access);
      connection.setRequestProperty("User-Agent", "python-novaclient");
      InputStream is = connection.getInputStream();
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = rd.readLine()) != null) {
        response.append(line);
        response.append('\r');
      }
      rd.close();
      connection.disconnect();
      if (response != null) {
        logger.debug("Got response from nova : " + response.toString());
      } else {
        logger.debug("Got response from nova : null ");
      }
      JSONObject tmp = new JSONObject(response.toString());
      JSONArray endpoints = tmp.getJSONArray("endpoints");
      logger.debug("There are " + endpoints.length() + " endpoints listed");
      for (int i = 0; i < endpoints.length(); i++) {
        JSONObject o = endpoints.getJSONObject(i);
        if (o.getString("service_id").equals(service_id)) {
          if (o.getString("interface").equals("public")) {
            return o.getString("url");
          }
        }
      }
      logger.error("Did not found endpoint");
      return null;

    } catch (Exception e) {
      logger.error("Problem contacting openstack-nova");
      logger.error(e.getMessage());
      logger.error(e.toString());
      e.printStackTrace();
      // If we have found a problem, we should probably end this thread
      return null;
    }
  }
}
