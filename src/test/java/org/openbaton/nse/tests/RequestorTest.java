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
package org.openbaton.nse.tests;

/*
import org.openbaton.catalogue.security.Project;
import org.openbaton.nse.properties.NfvoProperties;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.sdk.api.rest.VimInstanceAgent;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.common.Link;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.ext.Hypervisor;
import org.openstack4j.openstack.OSFactory;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
*/

/**
 * Created by lgr on 10/4/17.
 */
public class RequestorTest {
  public static void main(String[] args) {}
  /*
      throws SDKException, FileNotFoundException, ClassNotFoundException {


    NFVORequestor requestor =
        new NFVORequestor("nse", "project_id", "127.0.0.1", "8080", "1", false, "service-key");
    for (Project p : requestor.getProjectAgent().findAll()) {
      requestor.setProjectId(p.getId());
    }
    Map<String, String> hypervisor_ip_map = new HashMap<String, String>();
    for (VimInstance v : requestor.getVimInstanceAgent().findAll()) {
      if (v.getId().equals("vim_id")) {
        OSClient os = getOSClient(v);
        os.senlin().node().list();
        System.out.println("##########################################");
        for (Hypervisor h : os.compute().hypervisors().list()) {
          hypervisor_ip_map.put(h.getHypervisorHostname(), h.getHostIP());
          //          System.out.println("------------------------------------------");
          //          System.out.println(
          //              "Hypervisor : " + h.getHypervisorHostname() + " with IP : " + h.getHostIP());
          //          System.out.println("------------------------------------------");
          //          System.out.println(
          //              "CPU info : using "
          //                  + h.getVirtualUsedCPU()
          //                  + " of "
          //                  + h.getVirtualCPU()
          //                  + " available ");
          //          System.out.println("------------------------------------------");
          //          System.out.println("------------------------------------------");
        }
        System.out.println("###############################################");
        for (Server s : os.compute().servers().list()) {
          System.out.println(
              "Server : "
                  + s.getName()
                  + " is running on compute node : "
                  + s.getHypervisorHostname()
                  + " available at : "
                  + hypervisor_ip_map.get(s.getHypervisorHostname()));
          System.out.println(s.getHost());
        }
      }

      //System.out.println(v.getPassword());
      return;
    }
  }
  //System.out.println(requestor.getVimInstanceAgent().findById("2423b30a-9a83-486f-a4ab-a2e20926988f").getPassword());

  private static OSClient getOSClient(VimInstance vimInstance) {
    OSClient os = null;
    try {
      if (isV3API(vimInstance)) {
        Identifier domain = Identifier.byName("Default");
        Identifier project = Identifier.byId(vimInstance.getTenant());
        os =
            OSFactory.builderV3()
                .endpoint(vimInstance.getAuthUrl())
                .scopeToProject(project)
                .credentials(vimInstance.getUsername(), vimInstance.getPassword(), domain)
                .authenticate();
        if (vimInstance.getLocation() != null
            && vimInstance.getLocation().getName() != null
            && !vimInstance.getLocation().getName().isEmpty()) {
          try {
            org.openstack4j.model.identity.v3.Region region =
                ((OSClient.OSClientV3) os)
                    .identity()
                    .regions()
                    .get(vimInstance.getLocation().getName());

            if (region != null) {
              ((OSClient.OSClientV3) os).useRegion(vimInstance.getLocation().getName());
            }
          } catch (Exception ignored) {
            System.out.println(
                "Not found region '"
                    + vimInstance.getLocation().getName()
                    + "'. Use default one...");
            return os;
          }
        }
      } else {
        os =
            OSFactory.builderV2()
                .endpoint(vimInstance.getAuthUrl())
                .credentials(vimInstance.getUsername(), vimInstance.getPassword())
                .tenantName(vimInstance.getTenant())
                .authenticate();
        if (vimInstance.getLocation() != null
            && vimInstance.getLocation().getName() != null
            && !vimInstance.getLocation().getName().isEmpty()) {
          try {
            ((OSClient.OSClientV2) os).useRegion(vimInstance.getLocation().getName());
            ((OSClient.OSClientV2) os).identity().listTokenEndpoints();
          } catch (Exception e) {
            System.out.println(
                "Not found region '"
                    + vimInstance.getLocation().getName()
                    + "'. Use default one...");
            ((OSClient.OSClientV2) os).removeRegion();
          }
        }
      }
    } catch (AuthenticationException e) {
      System.out.println("Authentification error");
      e.printStackTrace();
    }
    return os;
  }

  private static boolean isV3API(VimInstance vimInstance) {
    return vimInstance.getAuthUrl().endsWith("/v3") || vimInstance.getAuthUrl().endsWith("/v3.0");
  }
  */
}
