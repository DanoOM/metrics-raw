package org.dshops.metrics;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

class DaemonThreadFactory implements ThreadFactory {
    private final AtomicInteger counter = new AtomicInteger();
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setName("metric-raw-" + (counter.incrementAndGet()));
        t.setDaemon(true);
        return t;
    }
}