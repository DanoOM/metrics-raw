package org.dsh.metrics.tests;

import java.util.Random;

import org.dsh.metrics.MetricRegistry;
import org.dsh.metrics.listeners.KairosDBListener;
import org.testng.annotations.Test;

public class MultiRegTest {


    private MetricRegistry buildRegistry() {
        return new MetricRegistry.Builder("dsh-metrics","test")
                .addTag("dc", "dataCenter1")
                .addTag("host", "host-1.xyz.org")
                .build();
    }

    @Test
    public void generateCounterData() {
        try {
            MetricRegistry reg1 = new MetricRegistry.Builder("dsh-metrics","test")
                    .addTag("dc", "dataCenter1")
                    .addTag("host", "host-1.xyz.org")
                    .build();
            reg1.addEventListener(new KairosDBListener("http://wdc-tst-masapp-001:8080",
                                        "root",
                                        "root"));


            MetricRegistry reg2 = new MetricRegistry.Builder("dsh-metrics","test")
                    .addTag("dc", "dataCenter2")
                    .addTag("host", "host-1.xyz.org")
                    .build();

            reg2.addEventListener(new KairosDBListener("http://wdc-tst-masapp-001:8080",
                    "root",
                    "root"));

            for (int i = 0; i < 100_000; i++) {
                reg1.counter("test.counter-1").increment();
                reg2.counter("test.counter-1").decrement();
                Thread.sleep(100);
            }
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void generateGaugeData() {
        long runtime = 600_000;
        try {
            MetricRegistry reg1 = new MetricRegistry.Builder("dsh-metrics","test")
                    .addTag("dc", "dataCenter1")
                    .addTag("host", "host-1.xyz.org")
                    .build();
            reg1.addEventListener(new KairosDBListener("http://wdc-tst-masapp-001:8080",
                                        "root",
                                        "root"));


            MetricRegistry reg2 = new MetricRegistry.Builder("dsh-metrics","test")
                    .addTag("dc", "dataCenter2")
                    .addTag("host", "host-1.xyz.org")
                    .build();

            reg2.addEventListener(new KairosDBListener("http://wdc-tst-masapp-001:8080",
                    "root",
                    "root"));

            Random r = new Random();
            reg1.scheduleGauge("test.gauage-1", 1, () -> {return r.nextInt(1000);});
            reg2.scheduleGauge("test.gauage-1", 1, () -> {return r.nextInt(1000);});
            Thread.sleep(runtime);
        }
        catch(Exception e) {
            e.printStackTrace();
        }

    }
}
