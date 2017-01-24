package org.dshops.metrics.generators;

import org.dshops.metrics.MetricRegistry;
import org.dshops.metrics.listeners.ConsoleListener;
import org.dshops.metrics.listeners.KairosDBListener;

public class Test {

    public static void main(String[] args) {
        MetricRegistry mr = new MetricRegistry.Builder("pst","test").withHost("DanHost").build();
        mr.addEventListener(new ConsoleListener(System.out));
        mr.addEventListener(new KairosDBListener("http://wdc-tst-masapp-001:8080", "root", "root"));
        mr.event("testEvent", 22);
        mr.counter("testCounter").increment();
        mr.counter("testCounter").increment();
        mr.timer("", "","").addTag("", "").stop("");
    }

}
