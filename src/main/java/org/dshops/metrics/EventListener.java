package org.dshops.metrics;

public interface EventListener {
    // All eventListeners are notified on same (per Registry)
    // as such this method must be performent
    public void onEvent(Event e);

    /** Should return the number of events
     * this listener has received, but has not processed.
     *
     * Can be used for shutdown, and testing.
     * @return The number of events currently buffered.
     * */
    public int eventsBuffered();
    
    public void stop();
}
