/*
 * Copyright (c) 2015 Technische Universität Berlin
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openbaton.nse.utils;

import java.util.List;

/**
 * Created by maa on 09.12.15.
 */
public class QoSAllocation {

  private String serverName;
  private List<QoSReference> ifaces;

  public QoSAllocation(String serverName, List<QoSReference> ifaces) {
    this.serverName = serverName;
    this.ifaces = ifaces;
  }

  public QoSAllocation() {}

  public String getServerName() {
    return serverName;
  }

  public void setServerName(String serverName) {
    this.serverName = serverName;
  }

  public List<QoSReference> getIfaces() {
    return ifaces;
  }

  public void setIfaces(List<QoSReference> ifaces) {
    this.ifaces = ifaces;
  }

  @Override
  public String toString() {
    return "QoSAllocation{" + "serverName='" + serverName + '\'' + ", ifaces=" + ifaces + '}';
  }
}
