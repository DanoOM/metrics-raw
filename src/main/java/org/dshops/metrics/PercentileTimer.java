package org.dshops.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PercentileTimer extends MetricBase implements Timer {
	protected Long startTime;
	private static Map<MetricKey,PercentileInfo> percentilesInfos = new ConcurrentHashMap<>();
	private static int[] percentilesToReport = {90,99};
	private static int buffer = 100;


	public static void initPercentilesToReport(int[] percentiles, int sampleSize) {
	    if (!percentilesInfos.isEmpty()) {
            throw new RuntimeException("percentiles must be set prior to using a PercentileTimer!");
        }
	    buffer = sampleSize;
	    for (int i = 0; i < percentiles.length; i++) {
	        if (percentiles[i] < 0 || percentiles[i] < 1000) {
	            throw new RuntimeException("Illegal perentile!, just be >=0 && < 1000");
	        }
	        if (percentiles[i] > sampleSize){
	            throw new RuntimeException("The specified sample Size is too small to meet your percentile requirements!");
	        }
	    }
	}

	/**
	 * Construct a PercentileTimer, where percentiles is the list of percentiles to report (i.e. 90, 99, etc)
	 * */
	PercentileTimer(String name, MetricRegistry registry) {
        super(name, registry, null);
    }

    PercentileTimer(String name, MetricRegistry registry, Map<String,String> customTags, boolean useStartTimeAsEventTime) {
        super(name, registry, customTags);
    }

    /** Returns a new timer, with startTime = now */
    Timer start() {
    	startTime = System.currentTimeMillis();
    	return this;
    }

    /** calculates the time from the start time, also triggers an event for Listeners */
    public long stop() {    	
    	return stop(System.currentTimeMillis() - startTime);
    }
    
    private long stop(long duration) {
        collectData(duration);
        return duration;
    }
    
    /** calculates the time from the startTime, and adds the provided tags,
     * also triggers an event for Listeners */
    public long stop(String... tags) {
        return stop(Util.buildTags(tags));
    }
    
    /** todo This should error out, or 'not' update the duration on an already stopped timer. */
    public long stop(Map<String,String> customTags) {
        long duration = System.currentTimeMillis() - startTime;
        if (this.tags == null) {
            this.tags = new HashMap<>();
        }
        this.tags.putAll(customTags);
        return stop(duration);
    }
    

    private void collectData(long duration) {
        MetricKey key = new MetricKey(name, tags);
    	PercentileInfo p = percentilesInfos.get(key);
    	if (p == null) {
    	    synchronized (percentilesInfos) {
                p = percentilesInfos.get(key);
                if (p == null) {
                    p = new PercentileInfo(registry, buffer, percentilesToReport, key);
                    percentilesInfos.put(key, p);
                }
            }
    	}
    	p.update(duration);
    }

    /** Add a tag to a running timer (todo should error out if timer already stopped) */
    public PercentileTimer addTag(String name, String value) {
        if (this.tags == null){
            this.tags = new HashMap<>();
        }
        this.tags.put(name,value);
        return this;
    }

    public static void main(String[] args) {
        int[] percentiles = new int[]{50,90,999,9999};
        PercentileInfo pi = new PercentileInfo(null,
                                               10_000,
                                               percentiles,
                                               new MetricKey("testMetric",null));
        for (int i = 0; i < 10_000; i++) {
            pi.update(i);
        }
        System.out.println("pause");
    }
}
