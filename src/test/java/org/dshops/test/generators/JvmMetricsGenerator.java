package org.dshops.test.generators;

import org.dshops.metrics.EventListener;
import org.dshops.metrics.JvmMetrics;
import org.dshops.metrics.MetricRegistry;
import org.dshops.metrics.listeners.ConsoleListener;

/**
 * Generate metrics based on the JvmMetrics Package provided with Metrics-RAW
 * */
abstract public class JvmMetricsGenerator implements DynamicListener {

    @Override
    public EventListener getListener(MetricRegistry reg) {
        return new ConsoleListener(System.out);
    }

    public void testJvmMetrics() {
        MetricRegistry reg = getRegistry();
        reg.addEventListener(getListener(reg));
        reg.addEventListener(new ConsoleListener(System.out));
        JvmMetrics.addMetrics(reg, 4);
        while(true) {
            pause(1000);
        }
    }

    public void pause(long ms) {
        try {
            if (ms < 0){
                ms = 1;
            }
            Thread.sleep(ms);
        }
        catch(Exception e) {
            // swallow
        }
    }


    public MetricRegistry getRegistry() {
        return new MetricRegistry.Builder("dshops", "metrics", "test", "host-1.xyz.org", "datacenter1")
                                 .build();
    }
}
