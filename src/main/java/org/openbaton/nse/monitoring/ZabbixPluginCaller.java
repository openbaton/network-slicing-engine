package org.openbaton.nse.monitoring;

import org.openbaton.catalogue.mano.common.monitoring.ObjectSelection;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.exceptions.MonitoringException;
import org.openbaton.monitoring.interfaces.MonitoringPluginCaller;
import org.openbaton.nse.properties.RabbitMQProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lgr on 5/23/18.
 */
@Service
public class ZabbixPluginCaller {

  private static Logger logger = LoggerFactory.getLogger(ZabbixPluginCaller.class);

  @SuppressWarnings("unused")
  @Autowired
  private RabbitMQProperties rabbitMQProperties;

  private MonitoringPluginCaller monitor;

  private void initMonitor() {
    if (monitor == null) {
      try {
        monitor =
            new MonitoringPluginCaller(
                rabbitMQProperties.getHost(),
                rabbitMQProperties.getUsername(),
                rabbitMQProperties.getPassword(),
                5672,
                "/",
                "zabbix-plugin",
                "zabbix",
                "15672",
                120000);
      } catch (Exception e) {
        //e.printStackTrace();
        logger.warn("Problem contacting the monitoring plugin");
        logger.warn(e.getMessage());
      }
    }
  }

  public String startPolling(String host, String metric) {
    ArrayList<String> hosts = new ArrayList<>();
    hosts.add(host);
    ArrayList<String> metrics = new ArrayList<>();
    metrics.add(metric);
    return startPolling(hosts, metrics);
  }

  public String startPolling(String host, List<String> metrics) {
    ArrayList<String> hosts = new ArrayList<>();
    hosts.add(host);
    return startPolling(hosts, metrics);
  }

  public String startPolling(List<String> hosts, List<String> metrics) {
    initMonitor();
    ObjectSelection selection = new ObjectSelection();
    String pmJobId = null;
    for (String entry : hosts) {
      selection.addObjectInstanceId(entry);
      //logger.debug("Adding : " + entry);
    }
    try {
      //logger.debug(metrics.toString());
      pmJobId = monitor.createPMJob(selection, metrics, new ArrayList<>(), 5, 5);
      //logger.debug("PmJobId is: " + pmJobId);
    } catch (MonitoringException e) {
      //logger.warn("Problem with monitoring");
      //logger.warn(e.getMessage());
      //e.printStackTrace();
    }
    return pmJobId;
  }

  public List<Item> pollValues(List<String> hosts, List<String> metrics) {
    initMonitor();
    List<Item> itemlist = new ArrayList<>();
    try {
      itemlist = monitor.queryPMJob(hosts, metrics, "5");
    } catch (MonitoringException e) {
      logger.warn("Problem with monitoring");
      logger.warn(e.getMessage());
      e.printStackTrace();
    }
    return itemlist;
  }

  // Checks if a metric ( item in zabbix ) is available for a specific host
  // This is realized by using the Zabbix Plugin
  public boolean metricExists(String host, String metric) {
    initMonitor();
    ObjectSelection selection = new ObjectSelection();
    selection.addObjectInstanceId(host);
    List<String> hosts = new ArrayList<>();
    hosts.add(host);
    ArrayList<String> metrics = new ArrayList<>();
    metrics.add(metric);
    ArrayList<String> ids = new ArrayList<>();
    try {
      List<Item> itemlist;
      String id = monitor.createPMJob(selection, metrics, new ArrayList<>(), 1, 1);
      //logger.debug("Created PMJob with id " + id);
      ids.add(id);
      itemlist = monitor.queryPMJob(hosts, metrics, "1");
      if (itemlist.isEmpty()) {
        return false;
      } else {
        for (Item i : itemlist) {
          // wildcard
          if (i.getValue() != null) {
            if (i.getValue().equals(i.getLastValue()) && i.getValue().equals("0.0")) {
              return false;
            }
          } else {
            // wildcard case, still need to add normal value..
            if (i.getLastValue().equals("0.0")) {
              return false;
            }
          }
        }
      }
    } catch (MonitoringException e) {
      // TODO : This happens when we scale out for example.. Maybe we should wait for the VM to come up / the NSR to be ACTIVE
      logger.warn("Problem polling for item id " + metric + " on " + host);
      return false;
    } //finally {
    //try {
    //monitor.deletePMJob(ids);
    //} catch (MonitoringException e) {
    //}
    //}
    return true;
  }
}
