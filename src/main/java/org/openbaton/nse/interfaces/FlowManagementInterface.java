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

package org.openbaton.nse.interfaces;

import org.openbaton.nse.utils.FlowAllocation;
import org.openbaton.nse.utils.json.Host;
import org.openbaton.nse.utils.json.Server;

import java.util.List;

/**
 * Created by mpa on 05.09.16.
 */
public interface FlowManagementInterface {

  void addFlow(Host host, List<Server> servers, FlowAllocation allocations, String nsrId);

  void updateFlow(Host hostmap, List<String> serversIds, List<Server> servers, String nsrId);

  void removeFlow(Host hostmap, List<String> serversIds, List<Server> servers, String nsrId);
}
