package org.dshops.metrics;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class EventPercentileInfo<T extends Number> {
    T[] values;
    int valuesCollected = 0;
    private final MetricRegistry registry;
    private final int statFuncs;

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

    public EventPercentileInfo(MetricRegistry registry, Number type, int buffer, int[] percentilesToReport, int statFuncs, MetricKey key) {
        this.registry = registry;
        this.statFuncs = statFuncs;
        values = (T[])Array.newInstance(type.getClass(), buffer);
        this.percentilesToReport = percentilesToReport;
        this.key = key;
    }

    public void update(T duration) {
        synchronized (this) {
            values[valuesCollected] = duration;
            valuesCollected++;
            if (valuesCollected >= values.length) {
                T[] tmp = values;
                values =  (T[])Array.newInstance(values.getClass().getComponentType(), values.length);   //new values.getClass().get [values.length];
                valuesCollected = 0;
                threadPool.submit(() -> reportMetrics(tmp));
            }
        }
    }

    public void reportMetrics(T[] dataValues) {
        Arrays.sort(dataValues);
        long ts = System.currentTimeMillis();
        // percentiles
        for (int p : percentilesToReport) {
            Number percentileValue = getPercentile(p, dataValues);
            if (registry != null) {
                registry.postEvent(key.getName() + ".p"+p, ts, key.getTags(), percentileValue);
            }
            else{
                System.out.println(key.getName()+ ".p" +p+"=="+ percentileValue);
            }
        }
        // additional functions (@todo consider optimizing into 1 loop?)
        if ((statFuncs & EventBucket.STAT_MIN) == EventBucket.STAT_MIN) {
            if (registry != null) {
                registry.postEvent(key.getName() + ".min", ts, key.getTags(), dataValues[0]);
            }
        }
        if ((statFuncs & EventBucket.STAT_MAX) == EventBucket.STAT_MAX) {
            if (registry != null) {
                registry.postEvent(key.getName() + ".max", ts, key.getTags(), dataValues[dataValues.length - 1]);
            }
        }
        double[] stdDevAndMean = null;
        if ((statFuncs & EventBucket.STAT_STD) == EventBucket.STAT_STD) {
            if (registry != null) {
                stdDevAndMean = getStdDevAndMean(dataValues);
                registry.postEvent(key.getName() + ".std", ts, key.getTags(), stdDevAndMean[0]);
                if ((statFuncs & EventBucket.STAT_AVE) == EventBucket.STAT_AVE) {
                    registry.postEvent(key.getName() + ".ave", ts, key.getTags(), stdDevAndMean[1]);
                }
            }
        }
        if (stdDevAndMean == null && ((statFuncs & EventBucket.STAT_AVE) == EventBucket.STAT_AVE)) {
            if (registry != null) {
                double mean = getMean(dataValues);
                registry.postEvent(key.getName() + ".ave", ts, key.getTags(), mean);
            }
        }
    }
    private double getMean(final T[] dataValues) {
        double sum = 0.0;
        for (int i = 0; i < dataValues.length; i++) {
            sum += dataValues[i].doubleValue();
        }
        return sum / dataValues.length;
    }
    
    private double[] getStdDevAndMean(final T[] dataValues) {
        double meanSum = dataValues[0].doubleValue();
        double stdDevSum = 0.0;
        double sum = dataValues[0].doubleValue();
        for (int i = 1; i< dataValues.length; i++) {
            double stepSum = dataValues[i].doubleValue() - meanSum;
            double stepMean = ((i - 1)) * stepSum / i;
            meanSum += stepMean;
            stdDevSum +=stepMean * stepSum;
            sum+=dataValues[i].doubleValue();
        }
        double[] results = new double[2];
        results[0] = Math.sqrt(stdDevSum / (dataValues.length - 1));
        results[1] = sum / dataValues.length;        
        return results;        
    }

    private Number getPercentile(int percent, T[] dataValues) {
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