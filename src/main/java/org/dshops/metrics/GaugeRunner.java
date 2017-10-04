package org.dshops.metrics;

class GaugeRunner<T extends Number> implements Runnable {
    private final Gauge<T> gauge;
    private final MetricRegistry registry;
    private final MetricKey key;
    private final int reportIntervalInMillis;
    private long lastReportTime = 0;
    private T max;

    public GaugeRunner(MetricKey key, Gauge<T> gauge, MetricRegistry registry) {
        this.gauge = gauge;
        this.registry = registry;
        this.key = key;
        this.reportIntervalInMillis = 0;
    }
    
    public GaugeRunner(MetricKey key, Gauge<T> gauge, int reportIntervalInSeconds, MetricRegistry registry) {
        this.gauge = gauge;
        this.registry = registry;
        this.key = key;
        this.reportIntervalInMillis = reportIntervalInSeconds * 1000;
    }

    @Override
    public void run() {
        if (this.reportIntervalInMillis == 0) {
            this.registry.postEvent(this.key.getName(), System.currentTimeMillis(), this.key.getTags(), gauge.getValue());
            return;
        }
        
        if (max == null || gauge.getValue().doubleValue() > max.doubleValue()) {
            max = gauge.getValue();
        }        
        long ts = System.currentTimeMillis();
        if (ts - lastReportTime > this.reportIntervalInMillis) {
            this.registry.postEvent(this.key.getName(), System.currentTimeMillis(), this.key.getTags(), max);
            max = null;
            lastReportTime = System.currentTimeMillis();
        }
    }
}