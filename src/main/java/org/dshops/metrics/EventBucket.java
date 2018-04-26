package org.dshops.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** EventBuckets represents n number of events, by default when eventBuckets
 *  are emitted to the metric store, they will be 100 events.
 *  EventBuckets when emitted to the metric store can emit the following datapoints.
 *  
 *  count - always emitted.
 *  min   -  optional
 *  max   -  optional
 *  ave   -  optional
 *  std   -  optional
 *  percentiles (configurable) - optiional
 *  
 *  Users can use initBucketDataToReport(..) methods to tweak what
 *  is emitted, as well as the sample size  
 * */
public class EventBucket extends MetricBase {
    private static Map<MetricKey,EventPercentileInfo> percentilesInfos = new ConcurrentHashMap<>();
	private static int[] percentilesToReport = {};
	private static int buffer = 100;

	public static final int STAT_MIN = 1;
    public static final int STAT_MAX = 2;
	public static final int STAT_STD = 4;
	public static final int STAT_AVE = 8;
	private static int stat_funcs = 0;

	public static void initBucketDataToReport(int sampleSize, int[] percentiles) {
	    initBucketDataToReport(sampleSize, percentiles, 0);
	}

	public static void initBucketDataToReport(int sampleSize, int[] percentiles, int STAT_FUNCTIONS) {
	    stat_funcs = STAT_FUNCTIONS;
        if (!percentilesInfos.isEmpty()) {
            throw new RuntimeException("percentiles must be set prior to using a PercentileTimer!");
        }
        buffer = sampleSize;
        percentilesToReport = percentiles;

        for (int i = 0; i  < percentiles.length; i++) {
            if (percentiles[i] < 0 || percentiles[i] > 1000) {
                throw new RuntimeException("Illegal perentile!, just be >=0 && < 1000");
            }
            double x = Math.round((sampleSize / 100d) * percentiles[i]);
            if (x >= sampleSize) {
                throw new RuntimeException("The specified sample Size is too small to meet your percentile requirements!");
            }
        }
    }

	/**
	 * Construct an EventBucket, where percentiles is the list of percentiles to report (i.e. 90, 99, etc)
	 * */
	EventBucket(String name, MetricRegistry registry) {
        super(name, registry, null);
    }

	EventBucket(String name, MetricRegistry registry, Map<String,String> customTags) {
        super(name, registry, customTags);
    }

    public void update(Number n) {
        collectData(n);
    }

    public void update(Number n, String... customTags) {
        update(n, Util.buildTags(customTags));
    }

    public void update(Number n, Map<String,String> customTags) {
        if (this.tags == null) {
            this.tags = new HashMap<>();
        }
        this.tags.putAll(customTags);
        collectData(n);
    }

    private void collectData(Number value) {
        MetricKey key = new MetricKey(name, tags);
    	EventPercentileInfo p = percentilesInfos.get(key);
    	if (p == null) {
    	    synchronized (percentilesInfos) {
                p = percentilesInfos.get(key);
                if (p == null) {
                    p = new EventPercentileInfo(registry, value, buffer, percentilesToReport, stat_funcs, key);
                    percentilesInfos.put(key, p);
                }
            }
    	}
    	p.update(value);
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
