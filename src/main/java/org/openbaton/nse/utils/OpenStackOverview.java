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

  private Map<Integer, Object> vims;

  private Map<String, String> vim_names;

  private Map<String, String> vim_types;

  private Map<Integer, Object> os_nodes;

  private Map<String, String> projects;

  private Map<String, Object> nsrs;

  private Map<String, String> nsr_names;

  private Map<String, Object> nsr_vnfs;

  private Map<String, Object> vnf_vlrs;

  private Map<String, String> vlr_names;

  private Map<String, String> vlr_qualities;

  private Map<String, Object> vnf_vdus;

  private Map<String, String> vdu_names;

  private Map<String, Object> vdu_vnfcis;

  private Map<String, String> vnfci_names;

  private Map<String, Object> vnfci_ips;

  private Map<String, String> ip_names;

  private Map<String, String> ip_addresses;

  private Map<Integer, Object> os_port_ids;

  private Map<String, Object> os_port_ips;

  private Map<String, String> os_port_net_map;

  public void setVims(Map<Integer, Object> vims) {
    this.vims = vims;
  }

  public void setVim_names(Map<String, String> vim_names) {
    this.vim_names = vim_names;
  }

  public void setVim_types(Map<String, String> vim_types) {
    this.vim_types = vim_types;
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

  public void setNsr_vnfs(Map<String, Object> nsr_vnfs) {
    this.nsr_vnfs = nsr_vnfs;
  }

  public void setVnf_vlrs(Map<String, Object> vnf_vlrs) {
    this.vnf_vlrs = vnf_vlrs;
  }

  public void setVlr_names(Map<String, String> vlr_names) {
    this.vlr_names = vlr_names;
  }

  public void setVlr_qualities(Map<String, String> vlr_qualities) {
    this.vlr_qualities = vlr_qualities;
  }

  public void setVnf_vdus(Map<String, Object> vnf_vdus) {
    this.vnf_vdus = vnf_vdus;
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

  public void setVnfci_ips(Map<String, Object> vnfci_ips) {
    this.vnfci_ips = vnfci_ips;
  }

  public void setIp_names(Map<String, String> ip_names) {
    this.ip_names = ip_names;
  }

  public void setIp_addresses(Map<String, String> ip_addresses) {
    this.ip_addresses = ip_addresses;
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

  public OpenStackOverview() {
    this.vims = new HashMap<Integer, Object>();
    this.vim_names = new HashMap<String, String>();
    this.vim_types = new HashMap<String, String>();
    this.os_nodes = new HashMap<Integer, Object>();
    this.projects = new HashMap<String, String>();
    this.nsrs = new HashMap<String, Object>();
    this.nsr_names = new HashMap<String, String>();
    this.nsr_vnfs = new HashMap<String, Object>();
    this.vnf_vlrs = new HashMap<String, Object>();
    this.vlr_names = new HashMap<String, String>();
    this.vlr_qualities = new HashMap<String, String>();
    this.vnf_vdus = new HashMap<String, Object>();
    this.vdu_names = new HashMap<String, String>();
    this.vdu_vnfcis = new HashMap<String, Object>();
    this.vnfci_names = new HashMap<String, String>();
    this.vnfci_ips = new HashMap<String, Object>();
    this.ip_names = new HashMap<String, String>();
    this.ip_addresses = new HashMap<String, String>();
    this.os_port_ids = new HashMap<Integer, Object>();
    this.os_port_ips = new HashMap<String, Object>();
    this.os_port_net_map = new HashMap<String, String>();
  }
}
