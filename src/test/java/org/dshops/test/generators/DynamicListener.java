package org.dshops.test.generators;

import org.dshops.metrics.EventListener;
import org.dshops.metrics.MetricRegistry;

public interface DynamicListener {
    public EventListener getListener(MetricRegistry reg);
}
