package org.dsh.metrics;

import java.util.Map;

public class MetricImpl {
    protected final String name;
    protected final Map<String,String> tags;
    protected final MetricRegistry registry;

    public MetricImpl(String name, MetricRegistry registry, Map<String,String> tags) {
    	this.name = name;
    	this.registry = registry;
        this.tags = tags;
    }

    public MetricRegistry getMetricRegistry() {
        return registry;
    }
}
