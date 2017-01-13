package org.dshops.metrics.listeners;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.dshops.metrics.Event;
import org.dshops.metrics.EventListener;
import org.dshops.metrics.LongEvent;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reporter;
import com.codahale.metrics.Timer;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;

public class DropWizardListener implements EventListener, Runnable {

    private Map<String, String> mapping = new ConcurrentHashMap<>();
    MetricRegistry registry;
    Reporter reporter;
    private Map<String,Number> gauges = new ConcurrentHashMap<>();

    public DropWizardListener(String host, int port, int reportIntervalInSeconds) {
        registry = new MetricRegistry();
        Graphite graphite = new Graphite(new InetSocketAddress(host, port));
        GraphiteReporter.forRegistry(registry)
                        .convertRatesTo(TimeUnit.SECONDS)
                        .convertDurationsTo(TimeUnit.MILLISECONDS)
                        .build(graphite).start(reportIntervalInSeconds, TimeUnit.SECONDS);
    }

    public void addMap(String metricName, String dropWizardName) {
        mapping.put(metricName, dropWizardName);
    }

    @Override
    public void onEvent(Event e) {
        if(mapping.get(e.getName()) == null) {
            return; // metric is not mapped.
        }
        // get the 'graphite name'
        String name = getName(e, mapping.get(e.getName()));
        switch(e.getType()) {
            case Timer:
                Timer t = registry.timer(name);
                t.update(e.getLongValue(),TimeUnit.MILLISECONDS);
            break;
            case Guage:
                Number n = gauges.get(name);
                if (n == null) {
                    synchronized (this) {
                        n = gauges.get(name);
                        if (n == null) {
                            if (e instanceof LongEvent) {
                                gauges.put(name, e.getLongValue());
                                registry.register(name,
                                        new Gauge<Long>() {
                                              @Override
                                              public Long getValue() {
                                                  return gauges.get(name).longValue();
                                              }
                                      });
                            }
                            else {
                                gauges.put(name, e.getDoubleValue());

                                registry.register(name,
                                                  new Gauge<Double>() {
                                                        @Override
                                                        public Double getValue() {
                                                            return gauges.get(name).doubleValue();
                                                        }
                                                });
                            }
                        }
                    }
                }
                else {
                    if (e instanceof LongEvent)
                        gauges.put(name,e.getLongValue());
                    else
                        gauges.put(name,e.getDoubleValue());
                }
            break;
            case Event:
                registry.meter(name).mark();
            break;
            case Counter:
                long c = registry.counter(name).getCount();
                if (c < e.getLongValue()) {
                    registry.counter(name).inc(e.getLongValue() - c);
                }
                else if (c > e.getLongValue()) {
                    registry.counter(name).inc(c - e.getLongValue());
                }
            break;
        }
    }

    public String getName(Event e, String map) {
        StringBuilder sb = new StringBuilder();
        String[] path = map.split("\\.");

        for (String entry : path) {
            if (entry.startsWith("{") && entry.endsWith("}")) {
                sb.append(e.getTags().get(entry.substring(1, entry.length()-1)));
            }
            else {
                sb.append(entry);
            }
            sb.append(".");
        }
        return sb.toString().substring(0, sb.length() -1);
    }

    @Override
    public int eventsBuffered() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub

    }

}
