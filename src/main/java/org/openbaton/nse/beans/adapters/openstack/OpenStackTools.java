package org.openbaton.nse.beans.adapters.openstack;

import org.openbaton.catalogue.nfvo.VimInstance;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.compute.ext.Hypervisor;
import org.openstack4j.model.network.IP;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Port;
import org.openstack4j.openstack.OSFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by lgr on 3/1/18.
 */
@Configuration
public class OpenStackTools {

  private static Logger logger = LoggerFactory.getLogger(OpenStackTools.class);

  // Function to get the correct OSClient to communicate directly to openstack, it depends on the vim
  // if you get a v3 or v2 type
  public OSClient getOSClient(String tenant, String user, String pw, String loc, String url) {
    OSClient os = null;
    try {
      if (isV3API(url)) {
        Identifier domain = Identifier.byName("Default");
        Identifier project = Identifier.byId(tenant);
        os =
            OSFactory.builderV3()
                .endpoint(url)
                .scopeToProject(project)
                .credentials(user, pw, domain)
                //.credentials(vimInstance.getUsername(), vimInstance.getPassword())
                .authenticate();
        if (loc != null && !loc.isEmpty()) {
          try {
            org.openstack4j.model.identity.v3.Region region =
                ((OSClient.OSClientV3) os).identity().regions().get(loc);

            if (region != null) {
              ((OSClient.OSClientV3) os).useRegion(loc);
            }
          } catch (Exception ignored) {
            logger.warn("    Not found region '" + loc + "'. Use default one...");
            return os;
          }
        }
      } else {
        os =
            OSFactory.builderV2()
                .endpoint(url)
                .credentials(user, pw)
                .tenantName(tenant)
                .authenticate();
        if (loc != null && !loc.isEmpty()) {
          try {
            ((OSClient.OSClientV2) os).useRegion(loc);
            ((OSClient.OSClientV2) os).identity().listTokenEndpoints();
          } catch (Exception e) {
            logger.warn("    Not found region '" + loc + "'. Use default one...");
            ((OSClient.OSClientV2) os).removeRegion();
          }
        }
      }
    } catch (AuthenticationException e) {
      logger.error("Authentification error");
      e.printStackTrace();
    }
    return os;
  }

  public OSClient getOSClient(VimInstance v) {
    return this.getOSClient(
        v.getTenant(), v.getUsername(), v.getPassword(), v.getLocation().getName(), v.getAuthUrl());
  }

  // Check for nova api v2 or nova api v3, necessary to be able to know which OSClient to use if you choose
  // to use openstack4j
  private boolean isV3API(VimInstance vimInstance) {
    return vimInstance.getAuthUrl().endsWith("/v3") || vimInstance.getAuthUrl().endsWith("/v3.0");
  }

  // Check for nova api v2 or nova api v3, necessary to be able to know which OSClient to use if you choose
  // to use openstack4j
  private boolean isV3API(String url) {
    return url.endsWith("/v3") || url.endsWith("/v3.0");
  }

  // Method to get a x auth token using the openstack4j libraries
  public String getAuthToken(OSClient os, VimInstance vimInstance) {
    logger.debug(vimInstance.getAuthUrl());
    String token = null;
    if (isV3API(vimInstance)) {
      //logger.debug("    Using OpenStack Nova API v3 to obtain x auth token");
      token = ((OSClient.OSClientV3) os).getToken().getId();
    } else {
      //logger.debug("    Using OpenStack Nova API v2 to obtain x auth token");
      token = ((OSClient.OSClientV2) os).getAccess().getToken().getId();
    }
    //logger.debug("Received token : " + token);
    return token;
  }
  // Function to list all neutron ports via the OSClient for a specific vim
  // We need to know the port ids to know where to push the QoS rules on
  public List<Port> getNeutronPorts(OSClient os) {

    List<org.openstack4j.model.network.Port> lp =
        (List<org.openstack4j.model.network.Port>) os.networking().port().list();
    return lp;
  }

  // Method to get the correct neutron url to communicate with
  public String getNeutronEndpoint(VimInstance vimInstance, String token) {
    //OSClient os = getOSClient(vimInstance);
    OSClient os = getOSClient(vimInstance);

    // For nova api v3 we check the public neutron public api via the token catalog
    if (isV3API(vimInstance)) {
      logger.debug("    Trying to get OpenStack Neutron endpoint url via Nova API v3");
      List<? extends org.openstack4j.model.identity.v3.Service> service_list =
          ((OSClient.OSClientV3) os).getToken().getCatalog();
      for (org.openstack4j.model.identity.v3.Service s : service_list) {
        //logger.debug("      Checking service " + s.getName());
        if (s.getName().equals("neutron")) {
          List<? extends org.openstack4j.model.identity.v3.Endpoint> e_list = s.getEndpoints();
          for (org.openstack4j.model.identity.v3.Endpoint e : e_list) {
            logger.debug(
                "    Found OpenStack Neutron endpoint with type : " + e.getIface().value());
            if (e.getIface().value().equals("public")) {
              logger.debug("    Using API version 2 for communication with OpenStack Neutron");
              return e.getUrl().toString() + "/v2.0";
            }
          }
        }
      }
    } else {
      // For nova api v2 we check the public neutron public api via the identity token endpoints
      logger.debug("    Trying to get OpenStack Neutron endpoint url via Nova API v2");
      // Well a way to get the desired values is to use the tokenEndpoints
      List<? extends org.openstack4j.model.identity.v2.Endpoint> endpoint_list =
          ((OSClient.OSClientV2) os).identity().listTokenEndpoints();
      for (org.openstack4j.model.identity.v2.Endpoint e : endpoint_list) {
        //logger.debug("Checking endpoint : " + e.getName());
        if (e.getName().equals("neutron")) {
          logger.debug("    Found OpenStack Neutron endpoint with type : " + e.getType());
          if (e.getType().equals("network")) {
            logger.debug("    Neutron is available at " + e.getPublicURL());
            logger.debug("    Using API version 2 for communication with OpenStack Neutron");
            return e.getPublicURL() + "/v2.0";
          }
        }
      }
    }
    logger.error("    Did not found any OpenStack Neutron endpoint url");
    return null;
  }

  public Map<String, String> getComputeNodeMap(OSClient os) {
    Map<String, String> computenode_ip_map = new HashMap<String, String>();
    for (Hypervisor h : os.compute().hypervisors().list()) {
      computenode_ip_map.put(h.getHypervisorHostname(), h.getHostIP());
    }
    return computenode_ip_map;
  }

  public HashMap<String, Object> getNetworkNames(OSClient os) {
    HashMap<String, Object> networks = new HashMap<String, Object>();
    for (Network n : os.networking().network().list()) {
      networks.put(n.getId(), n.getName());
    }
    return networks;
  }

  public HashMap<String, Object> getPortIps(OSClient os, ArrayList<String> ips) {
    HashMap<String, Object> port_map = new HashMap<String, Object>();
    for (Port p : os.networking().port().list()) {
      ArrayList<String> tmp_ips = new ArrayList<String>();
      for (IP i : p.getFixedIps()) {
        //ArrayList<String> tmp_ips;
        //if (port_map.containsKey(p.getId())) {
        //  tmp_ips = ((ArrayList<String>) port_map.get(p.getId()));
        //  tmp_ips.add(i.getIpAddress());
        //} else {
        //  tmp_ips = new ArrayList<String>();

        // Only add the ports we want to check...
        if (ips.contains(i.getIpAddress())) {
          tmp_ips.add(i.getIpAddress());
          port_map.put(p.getId(), tmp_ips);
        }
        //}
      }
    }
    return port_map;
  }
}
