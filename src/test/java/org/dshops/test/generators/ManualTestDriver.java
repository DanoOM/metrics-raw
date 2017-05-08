package org.dshops.test.generators;

import java.util.Random;

import org.dshops.metrics.EventListener;
import org.dshops.metrics.MeterImpl;
import org.dshops.metrics.MetricRegistry;
import org.dshops.metrics.Timer;
import org.dshops.metrics.listeners.ConsoleListener;

public class ManualTestDriver {

    public EventListener getListener(MetricRegistry reg) {
        return new ConsoleListener(System.out);
    }

    public ManualTestDriver(String[] args) {
        MetricRegistry mr = new MetricRegistry.Builder("dshops", "metrics", "test", "DanHost", "danDatacenter").build();
        mr.addEventListener(getListener(mr));
        // basic timer test
        Timer t = mr.timer("testTimer", "tag1", "tagValue1").addTag("tag2", "tagValue2");
        Timer t2 = mr.timer("testTimer", "tag1", "tagValue1").addTag("tag2", "tagValue2");
        Timer t3 = mr.timer("testTimer2", "tag1", "tagValue1").addTag("tag2", "tagValue2");
        sleep(1000);
        t.stop();
        t2.stop();
        sleep(1000);
        t3.stop();

        // Basic event test with value
        mr.event("testEventWholeNumber", 10);
        mr.event("testEventWholeNumber", 20);
        mr.event("testEventWholeNumber", 30);
        mr.event("testEventWholeNumber", 40);
        mr.event("testEventWholeNumber", 50);
        mr.event("testEventDouble", 10.6);
        mr.event("testEventDouble", 10.7);
        mr.event("testEventDouble", 10.8);
        mr.event("testEventDouble", 10.9);
        mr.event("testEventDouble", 11.0);

        mr.event("testEventWholeNumber", 10, "tag", "tagValue");
        mr.event("testEventEventDouble", 10.6, "tag", "tagValue");
        // Gauge test
        Random r = new Random();

        mr.scheduleGauge("testGauage", 1, () -> {
            return r.nextInt(100);
        }, "tag", "tagValue");
        // note cannot have 2 gauges with same name/tagset
        mr.scheduleGauge("testGauage", 5, () -> {
            return r.nextInt(100) + 200;
        }, "tag", "tagValue2");
        // note cannot have 2 gauges with same name/tagset
        mr.scheduleGauge("testGauage2", 1, () -> {
            return r.nextInt(100) + 400;
        }, "tag", "tagValue");

        MeterImpl rg = new MeterImpl();
        mr.scheduleGauge("testRateGauge", 1, rg,"","");
        
        
        

        // counter test
        for (int i = 0; i < 30_000; i++) {
            try {
                Timer pt = mr.percentileTimer("testPercentileTimer");
                Thread.sleep(r.nextInt(5));
                pt.stop();
                rg.mark();
            } catch (Exception e) {}
            mr.counter("testCounter").increment();
        }
        System.out.println("Exiting");

    }

    public static void main(String[] args) {
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {

        }
    }

}
