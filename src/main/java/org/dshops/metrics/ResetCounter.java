package org.dshops.metrics;

import java.util.concurrent.atomic.AtomicInteger;

//used to 'sub-index' on milli
class ResetCounter {
 public AtomicInteger counter = new AtomicInteger();
 public long ts;
 public int incrementAndGet() {
     int count = counter.incrementAndGet();
     if (System.currentTimeMillis() - ts  > 1) {
         synchronized (this) {
             ts = System.currentTimeMillis();
             counter.set(0);
         }
     }
     return count;
 }
}