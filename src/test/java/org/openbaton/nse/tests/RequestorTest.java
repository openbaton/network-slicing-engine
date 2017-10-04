package org.openbaton.nse.tests;

import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.catalogue.security.Project;
import org.openbaton.nse.properties.NfvoProperties;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;

import java.io.FileNotFoundException;

/**
 * Created by lgr on 10/4/17.
 */
public class RequestorTest {
  public static void main(String[] args)
      throws SDKException, FileNotFoundException, ClassNotFoundException {
    /*
    NFVORequestor requestor =
        //new NFVORequestor("nse", "6501516f-a1d6-4a5d-9d8a-49c17c34a1cb", "10.147.66.148", "8080", "1", false, "/etc/openbaton/nse.txt");
        new NFVORequestor(
            "nse",
            "id",
            "127.0.0.1",
            "8080",
            "1",
            false,
            "/etc/openbaton/nse-local.txt");
    for (Project p : requestor.getProjectAgent().findAll()) {
      requestor.setProjectId(p.getId());
    }

    //requestor.getVimInstanceAgent().findAll().stream().forEach(System.out::println);
    for (VimInstance v : requestor.getVimInstanceAgent().findAll()) {
      if (v.getId().equals("id")) {
        System.out.println(v.getPassword());
        return;
      }
    }
    //System.out.println(requestor.getVimInstanceAgent().findById("2423b30a-9a83-486f-a4ab-a2e20926988f").getPassword());
      */
  }
}
