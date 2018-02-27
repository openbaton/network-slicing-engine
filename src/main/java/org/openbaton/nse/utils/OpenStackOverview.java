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

  private String current_hash;

  private Map<Integer, Object> vims;

  private Map<String, String> vim_names;

  private Map<String, String> vim_types;

  private Map<String, Object> vim_projects;

  private Map<Integer, Object> os_nodes;

  private Map<String, String> projects;

  private Map<String, Object> nsrs;

  private Map<String, String> nsr_names;

  private Map<String, Object> nsr_vnfrs;

  private Map<String, String> vnfr_names;

  private Map<String, Object> vnfr_vlrs;

  private Map<String, String> vlr_names;

  private Map<String, String> vlr_qualities;

  private Map<String, Object> vnfr_vdus;

  private Map<String, String> vdu_names;

  private Map<String, Object> vdu_vnfcis;

  private Map<String, String> vnfci_names;

  private Map<String, String> vnfci_vnfr;

  private Map<String, Object> vnfci_ips;

  private Map<String, String> ip_names;

  private Map<String, String> ip_addresses;

  private Map<String, Integer> vnfci_vims;

  private Map<String, Integer> vdu_scale;

  private Map<Integer, Object> os_port_ids;

  private Map<String, Object> os_port_ips;

  private Map<String, String> os_port_net_map;

  private Map<String, String> os_net_names;

  private Map<String, String> vnfci_hypervisors;

  private Map<String, String> vlr_ext_networks;

  public String getCurrent_hash() {
    return current_hash;
  }

  public void setCurrent_hash(String current_hash) {
    this.current_hash = current_hash;
  }

  public void setVims(Map<Integer, Object> vims) {
    this.vims = vims;
  }

  public void setVim_names(Map<String, String> vim_names) {
    this.vim_names = vim_names;
  }

  public void setVim_types(Map<String, String> vim_types) {
    this.vim_types = vim_types;
  }

  public void setVim_projects(Map<String, Object> vim_projects) {
    this.vim_projects = vim_projects;
  }

  public void setOs_nodes(Map<Integer, Object> os_nodes) {
    this.os_nodes = os_nodes;
  }

  public void setProjects(Map<String, String> projects) {
    this.projects = projects;
  }

  public void setNsrs(Map<String, Object> nsrs) {
    this.nsrs = nsrs;
  }

  public void setNsr_names(Map<String, String> nsr_names) {
    this.nsr_names = nsr_names;
  }

  public void setNsr_vnfrs(Map<String, Object> nsr_vnfrs) {
    this.nsr_vnfrs = nsr_vnfrs;
  }

  public void setVnfr_names(Map<String, String> vnfr_names) {
    this.vnfr_names = vnfr_names;
  }

  public void setVnfr_vlrs(Map<String, Object> vnfr_vlrs) {
    this.vnfr_vlrs = vnfr_vlrs;
  }

  public void setVlr_names(Map<String, String> vlr_names) {
    this.vlr_names = vlr_names;
  }

  public void setVlr_qualities(Map<String, String> vlr_qualities) {
    this.vlr_qualities = vlr_qualities;
  }

  public void setVnfr_vdus(Map<String, Object> vnfr_vdus) {
    this.vnfr_vdus = vnfr_vdus;
  }

  public void setVdu_names(Map<String, String> vdu_names) {
    this.vdu_names = vdu_names;
  }

  public void setVdu_vnfcis(Map<String, Object> vdu_vnfcis) {
    this.vdu_vnfcis = vdu_vnfcis;
  }

  public void setVnfci_names(Map<String, String> vnfci_names) {
    this.vnfci_names = vnfci_names;
  }

  public void setVnfci_vnfr(Map<String, String> vnfci_vnfr) {
    this.vnfci_vnfr = vnfci_vnfr;
  }

  public void setVnfci_ips(Map<String, Object> vnfci_ips) {
    this.vnfci_ips = vnfci_ips;
  }

  public void setIp_names(Map<String, String> ip_names) {
    this.ip_names = ip_names;
  }

  public void setIp_addresses(Map<String, String> ip_addresses) {
    this.ip_addresses = ip_addresses;
  }

  public void setVnfci_vims(Map<String, Integer> vnfci_vims) {
    this.vnfci_vims = vnfci_vims;
  }

  public void setVdu_scale(Map<String, Integer> vdu_scale) {
    this.vdu_scale = vdu_scale;
  }

  public void setOs_port_ids(Map<Integer, Object> os_port_ids) {
    this.os_port_ids = os_port_ids;
  }

  public void setOs_port_ips(Map<String, Object> os_port_ips) {
    this.os_port_ips = os_port_ips;
  }

  public void setOs_port_net_map(Map<String, String> os_port_net_map) {
    this.os_port_net_map = os_port_net_map;
  }

  public void setOs_net_names(Map<String, String> os_net_names) {
    this.os_net_names = os_net_names;
  }

  public void setVnfci_hypervisors(Map<String, String> vnfci_hypervisors) {
    this.vnfci_hypervisors = vnfci_hypervisors;
  }

  public void setVlr_ext_networks(Map<String, String> vlr_ext_networks) {
    this.vlr_ext_networks = vlr_ext_networks;
  }

  public OpenStackOverview() {
    //this.current_hash = null;
    this.vims = new HashMap<Integer, Object>();
    this.vim_names = new HashMap<String, String>();
    this.vim_types = new HashMap<String, String>();
    this.vim_projects = new HashMap<String, Object>();
    this.os_nodes = new HashMap<Integer, Object>();
    this.projects = new HashMap<String, String>();
    this.nsrs = new HashMap<String, Object>();
    this.nsr_names = new HashMap<String, String>();
    this.vnfr_names = new HashMap<String, String>();
    this.nsr_vnfrs = new HashMap<String, Object>();
    this.vnfr_vlrs = new HashMap<String, Object>();
    this.vlr_names = new HashMap<String, String>();
    this.vlr_qualities = new HashMap<String, String>();
    this.vnfr_vdus = new HashMap<String, Object>();
    this.vdu_names = new HashMap<String, String>();
    this.vdu_vnfcis = new HashMap<String, Object>();
    this.vnfci_names = new HashMap<String, String>();
    this.vnfci_vnfr = new HashMap<String, String>();
    this.vnfci_ips = new HashMap<String, Object>();
    this.ip_names = new HashMap<String, String>();
    this.ip_addresses = new HashMap<String, String>();
    this.vnfci_vims = new HashMap<String, Integer>();
    this.vdu_scale = new HashMap<String, Integer>();
    this.os_port_ids = new HashMap<Integer, Object>();
    this.os_port_ips = new HashMap<String, Object>();
    this.os_port_net_map = new HashMap<String, String>();
    this.os_net_names = new HashMap<String, String>();
    this.vnfci_hypervisors = new HashMap<String, String>();
    this.vlr_ext_networks = new HashMap<String, String>();
  }
}
