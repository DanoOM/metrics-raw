package org.dshops;

import java.util.Map;

public interface Event {
    public Map<String,String> getTags();
    public String getName();
    public long getTimestamp();
    public EventType getType();

    default public long getLongValue(){ return 1; }
        default public double getDoubleValue(){ return 0;}


}
