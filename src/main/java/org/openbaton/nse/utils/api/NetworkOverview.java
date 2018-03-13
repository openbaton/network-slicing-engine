package org.openbaton.nse.utils.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by lgr on 10/23/17.
 */
public class NetworkOverview {

  private Map<Integer, String> vim_hashes;

  private HashMap<Integer, ArrayList<String>> vims;

  private Map<String, String> vim_names;

  private Map<String, String> vim_types;

  private Map<String, ArrayList<String>> vim_projects;

  private Map<Integer, ArrayList<String>> os_nodes;

  private Map<String, String> projects;

  private Map<String, ArrayList<String>> nsrs;

  private Map<String, String> nsr_names;

  private Map<String, ArrayList<String>> nsr_vnfrs;

  private Map<String, String> vnfr_names;

  private Map<String, ArrayList<String>> vnfr_vlrs;

  private Map<String, String> vlr_names;

  private Map<String, String> vlr_qualities;

  private Map<String, ArrayList<String>> vnfr_vdus;

  private Map<String, String> vdu_names;

  private Map<String, ArrayList<String>> vdu_vnfcis;

  private Map<String, String> vnfci_names;

  private Map<String, String> vnfci_vnfr;

  private Map<String, ArrayList<String>> vnfci_ips;

  private Map<String, String> ip_names;

  private Map<String, String> ip_addresses;

  private Map<String, Integer> vnfci_vims;

  private Map<String, Integer> vdu_scale;

  private HashMap<Integer, ArrayList<String>> os_port_ids;

  private HashMap<String, ArrayList<String>> os_port_ips;

  private HashMap<String, String> os_port_net_map;

  private Map<String, String> os_net_names;

  private HashMap<String, String> vnfci_hypervisors;

  private Map<String, String> vlr_ext_networks;

  public Map<Integer, String> getVim_hashes() {
    return vim_hashes;
  }

  public void setVim_hashes(Map<Integer, String> vim_hashes) {
    this.vim_hashes = vim_hashes;
  }

  public HashMap<Integer, ArrayList<String>> getVims() {
    return vims;
  }

  public void setVims(HashMap<Integer, ArrayList<String>> vims) {
    this.vims = vims;
  }

  public void setVim_names(Map<String, String> vim_names) {
    this.vim_names = vim_names;
  }

  public void setVim_types(Map<String, String> vim_types) {
    this.vim_types = vim_types;
  }

  public void setVim_projects(Map<String, ArrayList<String>> vim_projects) {
    this.vim_projects = vim_projects;
  }

  public void setOs_nodes(Map<Integer, ArrayList<String>> os_nodes) {
    this.os_nodes = os_nodes;
  }

  public void setProjects(Map<String, String> projects) {
    this.projects = projects;
  }

  public void setNsrs(Map<String, ArrayList<String>> nsrs) {
    this.nsrs = nsrs;
  }

  public void setNsr_names(Map<String, String> nsr_names) {
    this.nsr_names = nsr_names;
  }

  public void setNsr_vnfrs(Map<String, ArrayList<String>> nsr_vnfrs) {
    this.nsr_vnfrs = nsr_vnfrs;
  }

  public void setVnfr_names(Map<String, String> vnfr_names) {
    this.vnfr_names = vnfr_names;
  }

  public void setVnfr_vlrs(Map<String, ArrayList<String>> vnfr_vlrs) {
    this.vnfr_vlrs = vnfr_vlrs;
  }

  public void setVlr_names(Map<String, String> vlr_names) {
    this.vlr_names = vlr_names;
  }

  public void setVlr_qualities(Map<String, String> vlr_qualities) {
    this.vlr_qualities = vlr_qualities;
  }

  public void setVnfr_vdus(Map<String, ArrayList<String>> vnfr_vdus) {
    this.vnfr_vdus = vnfr_vdus;
  }

  public void setVdu_names(Map<String, String> vdu_names) {
    this.vdu_names = vdu_names;
  }

  public void setVdu_vnfcis(Map<String, ArrayList<String>> vdu_vnfcis) {
    this.vdu_vnfcis = vdu_vnfcis;
  }

  public void setVnfci_names(Map<String, String> vnfci_names) {
    this.vnfci_names = vnfci_names;
  }

  public void setVnfci_vnfr(Map<String, String> vnfci_vnfr) {
    this.vnfci_vnfr = vnfci_vnfr;
  }

  public void setVnfci_ips(Map<String, ArrayList<String>> vnfci_ips) {
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

  public HashMap<Integer, ArrayList<String>> getOs_port_ids() {
    return os_port_ids;
  }

  public void setOs_port_ids(HashMap<Integer, ArrayList<String>> os_port_ids) {
    this.os_port_ids = os_port_ids;
  }

  public HashMap<String, ArrayList<String>> getOs_port_ips() {
    return os_port_ips;
  }

  public void setOs_port_ips(HashMap<String, ArrayList<String>> os_port_ips) {
    this.os_port_ips = os_port_ips;
  }

  public HashMap<String, String> getOs_port_net_map() {
    return os_port_net_map;
  }

  public void setOs_port_net_map(HashMap<String, String> os_port_net_map) {
    this.os_port_net_map = os_port_net_map;
  }

  public void setOs_net_names(Map<String, String> os_net_names) {
    this.os_net_names = os_net_names;
  }

  public HashMap<String, String> getVnfci_hypervisors() {
    return vnfci_hypervisors;
  }

  public void setVnfci_hypervisors(HashMap<String, String> vnfci_hypervisors) {
    this.vnfci_hypervisors = vnfci_hypervisors;
  }

  public void setVlr_ext_networks(Map<String, String> vlr_ext_networks) {
    this.vlr_ext_networks = vlr_ext_networks;
  }

  public NetworkOverview() {
    //this.current_hash = null;
    this.vim_hashes = new HashMap<>();
    this.vims = new HashMap<>();
    this.vim_names = new HashMap<>();
    this.vim_types = new HashMap<>();
    this.vim_projects = new HashMap<>();
    this.os_nodes = new HashMap<>();
    this.projects = new HashMap<>();
    this.nsrs = new HashMap<>();
    this.nsr_names = new HashMap<>();
    this.vnfr_names = new HashMap<>();
    this.nsr_vnfrs = new HashMap<>();
    this.vnfr_vlrs = new HashMap<>();
    this.vlr_names = new HashMap<>();
    this.vlr_qualities = new HashMap<>();
    this.vnfr_vdus = new HashMap<>();
    this.vdu_names = new HashMap<>();
    this.vdu_vnfcis = new HashMap<>();
    this.vnfci_names = new HashMap<>();
    this.vnfci_vnfr = new HashMap<>();
    this.vnfci_ips = new HashMap<>();
    this.ip_names = new HashMap<>();
    this.ip_addresses = new HashMap<>();
    this.vnfci_vims = new HashMap<>();
    this.vdu_scale = new HashMap<>();
    this.os_port_ids = new HashMap<>();
    this.os_port_ips = new HashMap<>();
    this.os_port_net_map = new HashMap<>();
    this.os_net_names = new HashMap<>();
    this.vnfci_hypervisors = new HashMap<>();
    this.vlr_ext_networks = new HashMap<>();
  }
}
