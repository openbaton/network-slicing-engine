package org.openbaton.nse.monitoring;

import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.nse.properties.NseProperties;
import org.openbaton.nse.utils.api.NetworkStatistic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Created by lgr on 5/30/18.
 */
@Service
public class ZabbixChecker {

  private static Logger logger = LoggerFactory.getLogger(ZabbixChecker.class);

  private final List<String> monitoring_list = Collections.synchronizedList(new ArrayList<>());
  // Contains the VNFCI ids together with a list of metrics attached to them
  private final HashMap<String, ArrayList<String>> monitoring_metric_list = new HashMap<>();

  // Default metrics to be checked for
  private static final ArrayList<String> defaultNetMetrics =
      new ArrayList<>(Arrays.asList("net.if.in[eth0]", "net.if.out[eth0]"));

  private NetworkStatistic statistic = new NetworkStatistic();

  @SuppressWarnings("unused")
  @Autowired
  private NseProperties nse_configuration;

  @SuppressWarnings("unused")
  @Autowired
  private ZabbixPluginCaller zabbixCaller;

  public void addHost(String host) {
    //if (metricScheduler == null) {
    //  metricScheduler = new Timer();
    //  metricScheduler.schedule(new UpdateMetricTask(), 0, 5000);
    //}
    synchronized (monitoring_list) {
      if (!monitoring_list.contains(host)) {
        //logger.debug("Adding PMJob for " + host);
        monitoring_list.add(host);
        ArrayList<String> existingMetrics = new ArrayList<>();
        for (String metric : defaultNetMetrics) {
          if (zabbixCaller.metricExists(host, metric)) {
            existingMetrics.add(metric);
            ArrayList<String> entry_metrics;
            if (monitoring_metric_list.containsKey(host)) {
              entry_metrics = monitoring_metric_list.get(host);
              entry_metrics.add(metric);
            } else {
              entry_metrics = new ArrayList<>();
              entry_metrics.add(host);
              monitoring_metric_list.put(host, entry_metrics);
            }
            // TODO : save the jobId for later deletion of the PMJob
            String jobId = zabbixCaller.startPolling(host, existingMetrics);
          }
        }
      }
    }
  }

  @Scheduled(fixedDelay = 5000)
  private void updateMetrics() {
    if (nse_configuration.getZabbix()) {
      synchronized (monitoring_list) {
        if (!monitoring_list.isEmpty()) {
          List<Item> itemlist = zabbixCaller.pollValues(monitoring_list, defaultNetMetrics);
          for (Item i : itemlist) {
            String unit = i.getMetadata().get(i.getMetric());
            Double m;
            if (i.getLastValue().contains("E")) {
              String num = i.getLastValue().substring(0, i.getLastValue().indexOf("E"));
              String exp = i.getLastValue().substring(i.getLastValue().indexOf("E") + 1);
              m = Double.parseDouble(num) * Math.pow(10, Double.parseDouble(exp));
            } else {
              m = Double.parseDouble(i.getLastValue());
            }
            synchronized (statistic) {
              statistic.updateValue(
                  i.getHostname(), i.getMetric(), String.valueOf(m.longValue()), unit);
              //logger.debug(statistic.getValue(i.getHostname(), i.getMetric()));
            }
            //Long m = Long.parseLong(i.getMetric());
            //logger.debug(i.getHostname() + " : " + i.getMetric() + " : " + m + " " + unit);
          }
        }
      }
    }
  }

  public NetworkStatistic getStatistic() {
    synchronized (statistic) {
      return this.statistic;
    }
  }

  //public ZabbixChecker(NetworkStatistic statistic) {
  //  this.statistic = statistic;
  //}
}
