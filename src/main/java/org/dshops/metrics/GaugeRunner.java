package org.dshops.metrics;

class GaugeRunner<T extends Number> implements Runnable {
    private final Gauge<T> gauge;
    private final MetricRegistry registry;
    private final MetricKey key;

    public GaugeRunner(MetricKey key, Gauge<T> gauge, MetricRegistry registry) {
        this.gauge = gauge;
        this.registry = registry;
        this.key = key;
    }

    @Override
    public void run() {
        this.registry.postEvent(this.key.name, System.currentTimeMillis(), this.key.tags, gauge.getValue());
    }
}