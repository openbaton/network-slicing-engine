package org.openbaton.nse.utils.api;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by lgr on 6/6/18.
 */
public class NetworkStatistic {

  // Map containing the item names ( fused with hostname ) together with an array containing their value and unit
  private Map<String, ArrayList<String>> item_value_map;
  private int historySize;

  public NetworkStatistic() {
    this.item_value_map = new HashMap<>();
    this.historySize = 10;
  }

  public NetworkStatistic(int historySize) {
    this.item_value_map = new HashMap<>();
    this.historySize = historySize;
  }

  public synchronized void updateValue(String host, String item, String value, String unit) {
    ArrayList<String> item_value_list;
    String key = host + "_" + item;
    String data = value + "_" + unit;
    if (item_value_map.containsKey(key)) {
      item_value_list = item_value_map.get(key);
      // If the history exceeds the max size, delete the first entry
      if (item_value_list.size() >= historySize) {
        item_value_list.remove(0);
      }
      item_value_list.add(data);
    } else {
      item_value_list = new ArrayList<>();
      item_value_list.add(data);
      item_value_map.put(key, item_value_list);
    }
  }

  public synchronized ArrayList<String> getValue(String host, String item, int historysize) {
    String key = host + "_" + item;
    ArrayList<String> result = new ArrayList<>();
    if (item_value_map.containsKey(key)) {
      if (item_value_map.get(key).size() >= historysize) {
        return item_value_map.get(key);
      }
    }
    return result;
  }
}
