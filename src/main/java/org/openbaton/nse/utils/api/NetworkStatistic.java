package org.openbaton.nse.utils.api;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by lgr on 6/6/18.
 */
public class NetworkStatistic {

  // Map containing the item names ( fused with hostname ) together with their value and unit
  private Map<String, String> item_value_list;

  public NetworkStatistic() {
    this.item_value_list = new HashMap<>();
  }

  public synchronized void updateValue(String host, String item, String value, String unit) {
    item_value_list.put(host + "_" + item, value + "_" + unit);
  }

  public synchronized String getValue(String host, String item) {
    return item_value_list.get(host + "_" + item);
  }
}
