package org.dshops.metrics;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class PercentileInfo {
    int[] values;
    int valuesCollected = 0;
    private final MetricRegistry registry;

    private static ExecutorService threadPool = Executors.newFixedThreadPool(10, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }
    });
    private final int[] percentilesToReport;
    private final MetricKey key;

    public PercentileInfo(MetricRegistry registry, int buffer, int[] percentilesToReport, MetricKey key) {
        this.registry = registry;
        values = new int[buffer];
        this.percentilesToReport = percentilesToReport;
        this.key = key;
    }

    public void update(long duration) {
        synchronized (this) {
            values[valuesCollected] = (int)duration;
            valuesCollected++;
            if (valuesCollected >= values.length) {                
                int[] tmp = values;
                values = new int[values.length];
                valuesCollected = 0;                
                threadPool.submit(() -> reportPercentiles(tmp));
            }
        }
    }

    public void reportPercentiles(int[] dataValues) {
        Arrays.sort(dataValues);
        long ts = System.currentTimeMillis();
        for (int p : percentilesToReport) {
            long percentileValue = getPercentile(p, dataValues);
            if (registry != null) {
                registry.postEvent(key.getName() + ".p"+p, ts, key.getTags(), percentileValue);
            }
            else{
                System.out.println(key.getName()+ ".p" +p+"=="+ percentileValue);
            }
        }
    }

    private long getPercentile(int percent, int[] dataValues) {
        float percentF = 0;
        if (percent <=99)
            percentF = (float)percent/100;
        else if (percent<=999) {
            percentF = (float)percent/1000;
        }
        else if (percent<=9999) {
            percentF = (float)percent/10000;
        }
        else if (percent<=99999) {
            percentF = (float)percent/100000;
        }
        int index = (int)(percentF * dataValues.length);
        return dataValues[index];
    }
}