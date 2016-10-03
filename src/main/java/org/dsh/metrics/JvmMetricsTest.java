package org.dsh.metrics;

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
                                                  "root"));

        reg.addEventListener(new ConsoleListener(System.out));

        //new JvmMetrics(reg, 4);     // 4 mefff
        reg.scheduleGauge("ONE_TPS", 1, () -> {return 1;});
        while(true) { pause(1000); }
    }

    public void pause(long ms){
        try {
            Thread.sleep(ms);
        }
        catch(Exception e) {
            // swallow
        }
    }


    public MetricRegistry getRegistry() {
        return new MetricRegistry.Builder("dsh-metrics","test")
                                 .addTag("dc", "dataCenter1")
                                 .addTag("host", "host-1.xyz.org")
                                 .build();
    }
}
