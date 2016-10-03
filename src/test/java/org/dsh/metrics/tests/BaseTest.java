package org.dsh.metrics.tests;

import org.dsh.metrics.MetricRegistry;

public class BaseTest {
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
