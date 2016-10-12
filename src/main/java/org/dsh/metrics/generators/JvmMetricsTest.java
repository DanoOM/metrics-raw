package org.dsh.metrics.generators;

import org.dsh.metrics.JvmMetrics;
import org.dsh.metrics.MetricRegistry;
import org.dsh.metrics.listeners.ConsoleListener;
import org.dsh.metrics.listeners.KairosDBListener;


public class JvmMetricsTest {

    public static void main(String args[]) {
        new JvmMetricsTest().testJvmMetrics();
    }

    public void testJvmMetrics() {
        MetricRegistry reg = getRegistry();
        reg.addEventListener(new KairosDBListener("http://wdc-tst-masapp-002:8080",
                                                  "root",
                                                  "root",
                                                  100));

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
        return new MetricRegistry.Builder("dsh-metrics","test")
                                 .withDatacenter("dataCenter1")
                                 .withHost("host-1.xyz.org")
                                 .build();
    }
}
