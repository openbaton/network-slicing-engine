package org.openbaton.nse.utils;

import org.openstack4j.model.network.Port;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by lgr on 10/23/17.
 */
public class OpenStackOverview {

  private ArrayList<Map<String, Object>> nodes;
  private ArrayList<Map<String, Object>> projects;

  public ArrayList<Map<String, Object>> getNodes() {
    return nodes;
  }

  public void setNodes(ArrayList<Map<String, Object>> nodes) {
    this.nodes = nodes;
  }

  public ArrayList<Map<String, Object>> getProjects() {
    return projects;
  }

  public void setProjects(ArrayList<Map<String, Object>> projects) {
    this.projects = projects;
  }

  public OpenStackOverview() {
    this.nodes = new ArrayList<>();
    this.projects = new ArrayList<>();
  }
}
