package org.dshops.metrics;

import java.util.Map;

public interface Event {
    public Map<String,String> getTags();
    public String getName();
    public long getTimestamp();

    default public long getLongValue(){ return 1; }
    default public double getDoubleValue(){ return 0;}
    public String getPrimaryTag();
    public void addTag(String tag, String value);


}
