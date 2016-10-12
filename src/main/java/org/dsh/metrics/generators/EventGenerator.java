package org.dsh.metrics.generators;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.dsh.metrics.EventImpl;
import org.dsh.metrics.MetricRegistry;

class EventGenerator extends Thread implements Runnable {
    final String hostname;
    final AtomicBoolean exitFlag;
    final AtomicInteger counter;
    final MetricRegistry mr;
    final String[] eventNames;
    final int tagValueCount;
    final int possibleTags;
    final int tps;

    public EventGenerator(String host,
                          int eventSignatures,
                          AtomicBoolean exitFlag,
                          AtomicInteger counter,
                          MetricRegistry mr,
                          int tagCount,
                          int tagValueCount,
                          int tps) {
        this.tps = tps;
        this.hostname = host;
        this.exitFlag = exitFlag;
        this.counter = counter;
        this.mr = mr;
        eventNames = new String[eventSignatures];
        for (int i = 0; i < eventSignatures; i++) {
            eventNames[i] = "event"+i;
        }
        this.tagValueCount = tagValueCount;
        this.possibleTags = tagCount;

    }

    @Override
    public void run() {
        Random r = new Random();
        System.out.println("Host: " + hostname + " started Target TPS:"+tps);
        while (!this.exitFlag.get()) {
            long time = System.currentTimeMillis() + 1000;
            for (int j = 0; j < tps ; j++) {
                if (tps < 1000) {
                    long sleepTime = 1000 / tps;
                    pause(r.nextInt((int)sleepTime)); // since tps target is less then 1000, we will sleep 1ms for each event.
                }
                int tagCount = r.nextInt(possibleTags); // random number of tags to generate
                if (tagCount > 0) {
                    EventImpl.Builder eb = mr.eventWithTags(eventNames[r.nextInt(eventNames.length)]);
                    for (int i = 0 ; i < tagCount; i++){
                        eb.addTag("tag" + r.nextInt(tagCount), "value"+ r.nextInt(tagValueCount));
                    }
                    eb.build();
                }
                else {
                    mr.event(eventNames[r.nextInt(eventNames.length)]);
                }
                this.counter.incrementAndGet();
            }
            long sleepTime = time - System.currentTimeMillis();
            pause(sleepTime);
        }
        System.out.println("Host: " + hostname + " exit");
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
}
