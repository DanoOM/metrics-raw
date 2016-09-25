package org.dsh.metrics;

public interface EventListener {
    // All eventListeners are notified on same (per Registry)
    // as such this method must be performent
    public void onEvent(Event e);
}
