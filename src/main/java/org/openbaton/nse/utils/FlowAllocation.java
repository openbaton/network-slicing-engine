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

package org.openbaton.nse.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by maa on 09.12.15.
 */
public class FlowAllocation {

  private Map<String, List<FlowReference>> virtualLinkRecord;

  public FlowAllocation(Map<String, List<FlowReference>> virtualLinkRecord) {
    this.virtualLinkRecord = virtualLinkRecord;
  }

  public FlowAllocation() {
    this.virtualLinkRecord = new LinkedHashMap<>();
  }

  public Map<String, List<FlowReference>> getVirtualLinkRecord() {
    return virtualLinkRecord;
  }

  public void setVirtualLinkRecord(Map<String, List<FlowReference>> virtualLinkRecord) {
    this.virtualLinkRecord = virtualLinkRecord;
  }

  public void addVirtualLink(String virtualLink, List<FlowReference> ips) {
    this.virtualLinkRecord.put(virtualLink, ips);
  }

  public Set<String> getAllVlr() {
    return this.virtualLinkRecord.keySet();
  }

  public List<FlowReference> getIpsForVlr(String vlr) {
    return this.virtualLinkRecord.get(vlr);
  }

  public List<String> getAllIpsForVlr(String vlr) {
    List<String> res = new ArrayList<>();
    for (FlowReference ref : this.getIpsForVlr(vlr)) {
      res.add(ref.getIp());
    }
    return res;
  }

  @Override
  public String toString() {
    String res = "";

    for (String vlrid : virtualLinkRecord.keySet()) {
      res += vlrid + ": {\n";
      for (FlowReference allocation : virtualLinkRecord.get(vlrid)) {
        res += "\tserver:" + allocation.getHostname() + "\n" + "ip:" + allocation.getIp() + "\n";
      }
    }

    return res;
  }
}
