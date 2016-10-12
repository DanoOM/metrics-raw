package org.dsh.metrics.generators;

import java.util.Random;

import org.dsh.metrics.JvmMetrics;
import org.dsh.metrics.MetricRegistry;
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

    //    reg.addEventListener(new ConsoleListener(System.out));

        new JvmMetrics(reg, 4);
        long time = System.currentTimeMillis();
        String[] tags = new String[2000];
        for (int i = 0 ; i < 2000; i++) {
            tags[i] = "host" + i;
        }
        Random r = new Random();
        int messages = 2000;
        String[] eventNames = {"event1","event2","event3","event4","event5","event6"};
        while(true) {
            time = System.currentTimeMillis() + 1000;
            // aprox 100 tps
            for (int i = 0; i < messages; i++) {
              //  pause(1);// ensure each event occurs in another ms
                reg.event(eventNames[r.nextInt(eventNames.length)],
                          "host", tags[r.nextInt(tags.length)]);
            }
            long ts = System.currentTimeMillis();
            System.out.println(messages+ " sent: " + ts + " sleeping for: " + (time - ts));
            pause(time - ts);
        }
    }

    public void pause(long ms){
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
                                 .addTag("dc", "dataCenter1")
                                 .addTag("host", "host-1.xyz.org")
                                 .build();
    }
}
