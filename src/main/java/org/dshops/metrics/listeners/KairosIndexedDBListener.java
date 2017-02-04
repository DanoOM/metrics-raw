package org.dshops.metrics.listeners;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.dshops.metrics.DoubleEvent;
import org.dshops.metrics.Event;
import org.dshops.metrics.EventListener;
import org.dshops.metrics.LongEvent;
import org.dshops.metrics.MetricRegistry;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.MetricBuilder;
import org.kairosdb.client.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Will be moved library metric-raw-kairosdb in the future.
 * Notes: while all constructors take username/password, these are currently not used.
 *
 * Upload strategy:
 * All events will be uploaded in batch: batchSize (default: 100), or every 1 second, whichever comes first.
 * A status update will be logged at level info, once every 5 minutes, indicating the number of the number http calls (dispatchCount), as well
 * the number of metric datapoints, the number or errors (with details on the last error that occured).
 *
 *
 * */
public class KairosIndexedDBListener implements EventListener, Runnable {

    //private final BlockingQueue<Event> queue;
    private final ConcurrentSkipListMap<Long,MetricTimeSlot> metricBuffer = new ConcurrentSkipListMap<>(); // time/
    private final int batchSize;
    private final long offerTime;   // amount of time we are willing to 'block' before adding an event to our buffer, prior to dropping it.
    private Thread runThread;
    private final HttpClient kairosDb;
    private final static Logger log = LoggerFactory.getLogger(KairosIndexedDBListener.class);
    private final MetricRegistry registry;
    private final AtomicInteger droppedEvents = new AtomicInteger();
    private final String serviceTeam;
    private final String app;
    private final String appType;
    private final int bufferSize;

    public KairosIndexedDBListener(String connectString,
                            String un,
                            String pd,
                            MetricRegistry registry) {
        this(connectString, un, pd, registry, 100);
    }

    public KairosIndexedDBListener(String connectString,
                            String un,
                            String pd,
                            MetricRegistry registry,
                            int batchSize) {
        this(connectString, un, pd, registry, batchSize, 5000, -1);
    }

    public KairosIndexedDBListener(String connectString,
                            String un,
                            String pd,
                            MetricRegistry registry,
                            int batchSize,
                            int bufferSize,
                            long offerTimeMillis) {
    	this.registry = registry;
    	String[] prefix = registry.getPrefix().split("\\.");
    	this.serviceTeam = prefix[0];
    	this.app = prefix[1];
    	this.appType = prefix[2];
    	this.bufferSize = bufferSize;

        if (batchSize > 1) {
            this.batchSize = batchSize;
        }
        else {
            this.batchSize = 100;
        }

        this.offerTime = offerTimeMillis;
        try {
            kairosDb = new HttpClient(connectString);
        }
        catch(MalformedURLException mue) {
            throw new RuntimeException("Malformed Url:"+connectString+" "+mue.getMessage());
        }
        runThread = new Thread(this);
        runThread.setName("kairosDbListener");
        runThread.setDaemon(true);
        runThread.start();
    }

    @Override
    public void run() {
        final List<Event> dispatchList = new ArrayList<>(batchSize);
        long lastResetTime = System.currentTimeMillis();
        long httpCalls = 0;
        long metricCount = 0;
        long errorCount = 0;
        long exceptionTime = 0;
        Response lastError = null;
        do {
            try {
                addNewEvents(dispatchList);
                if(!dispatchList.isEmpty()) {
                    // @todo - consider bucketing..we may get multiple datapoints for the same ms, with the same name/tagSet, we will lose data with this approach atm.
                    metricCount += dispatchList.size();
                    Response r = kairosDb.pushMetrics(buildPayload(dispatchList));
                    httpCalls++;
                    if (r.getStatusCode() != 204 ) {
                        lastError = r;
                        errorCount++;
                    }
                    // every 5 minutes log a stat
                    if (System.currentTimeMillis() - lastResetTime > 60_000) {
                    	sendMetricStats(metricCount, errorCount, httpCalls);
                        if (lastError != null) {
                            StringBuilder sb = new StringBuilder();
                            for (String s : lastError.getErrors()) {
                                sb.append("[");
                                sb.append(s);
                                sb.append("]");
                            }
                            if (lastError != null) {
                                log.error("Http calls:{} Dispatch count: {} errorCount:{} lastError.status:{} lastErrorDetails:{}", httpCalls, metricCount, errorCount, lastError.getStatusCode(), sb.toString());
                            }
                            lastError = null;
                        }
                        droppedEvents.set(0);
                        httpCalls = 0;
                        errorCount = 0;
                        metricCount = 0;
                        lastResetTime = System.currentTimeMillis();
                    }
                }
            }
            catch(InterruptedException ie) {
                break;
            }
            catch(Exception ex) {
                errorCount++;
                if (System.currentTimeMillis() - exceptionTime > 60_000) {
                    log.error("Unexpected Exception (only 1 exception logged per minute)", ex);
                }
                exceptionTime = System.currentTimeMillis();
            }
            finally {
                dispatchList.clear();
            }
        } while(true);
    }

    // move events from the buffer to the dispatchList, exists if over 1000 second of processing
    // or we have exceeded batchSize.  (any timeSlot started, will result in all events from that
    // timeslot being added to dispatchList.
    private void addNewEvents(final List<Event> dispatchList) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        do {
            Long ts = null;
            Map.Entry<Long,MetricTimeSlot> entry = metricBuffer.firstEntry();
            if (entry != null) {
                ts = entry.getKey();
                if (ts != null && ts < System.currentTimeMillis() - 100) {
                    MetricTimeSlot timeSlot = metricBuffer.remove(ts);
                    // TODO: consider: this will process all events of all metricTypes, regardless of batchSize
                    // ie. we could send more then 'batchSize'
                    for (Map.Entry<String, ConcurrentLinkedQueue<Event>> metricEvents : timeSlot.metricMap.entrySet()){
                        int i = 0;
                        for (Event event: metricEvents.getValue()) {
                            event.addTag("index",i+"");
                            dispatchList.add(event);
                            i++;
                        }
                    }
                    // send what we have if we have exceeded batchSize, or have gone over 1000 second.
                    if (dispatchList.size() > batchSize || - System.currentTimeMillis() - startTime < 1000) {
                        break;
                    }
                }
            }
            else {
                Thread.sleep(10);
            }
        } while(true);
    }



    private void sendMetricStats(long metricCount, long errorCount, long httpCalls) throws Exception {
    	try{
	    	MetricBuilder mb = MetricBuilder.getInstance();
	    	mb.addMetric("metricsraw.stats.data.count")
	    	  .addTags(registry.getTags())
	    	  .addTag("serviceTeam",serviceTeam)
	    	  .addTag("app",app)
	    	  .addTag("appType",appType)
	    	  .addDataPoint(metricCount);
	    	mb.addMetric("metricsraw.stats.http.errors")
	    	.addTags(registry.getTags())
            .addTag("serviceTeam",serviceTeam)
            .addTag("app",app)
            .addTag("appType",appType)
	    	  .addDataPoint(errorCount);
	    	mb.addMetric("metricsraw.stats.http.count")
	    	.addTags(registry.getTags())
            .addTag("serviceTeam",serviceTeam)
            .addTag("app",app)
            .addTag("appType",appType)
	    	  .addDataPoint(httpCalls);
	    	mb.addMetric("metricsraw.stats.data.dropped")
	    	.addTags(registry.getTags())
            .addTag("serviceTeam",serviceTeam)
            .addTag("app",app)
            .addTag("appType",appType)
	    	  .addDataPoint(droppedEvents.longValue());
	    	 Response r = kairosDb.pushMetrics(mb);

             if (r.getStatusCode() != 204 ) {
                 log.warn("failed to send metric statistics!", r.getStatusCode());
             }
    	}
    	catch(Exception e) {
    		log.warn("failed to send metric statistis to server! {} ", e.getMessage());
    	}
    }

    private MetricBuilder buildPayload(List<Event> events) {
        MetricBuilder mb = MetricBuilder.getInstance();
        //@todo time-deduping when same event occurs in same millis
        for (Event e: events) {
            if (e instanceof LongEvent) {
                mb.addMetric(e.getName()).addTags(e.getTags()).addDataPoint(e.getTimestamp(), e.getLongValue());
            }
            else if (e instanceof DoubleEvent) {
                mb.addMetric(e.getName()).addTags(e.getTags()).addDataPoint(e.getTimestamp(), e.getDoubleValue());
            }
            else {
                // this is a pure event, value has no meaning
                mb.addMetric(e.getName()).addTags(e.getTags()).addDataPoint(e.getTimestamp(), 1);
            }
        }
        return mb;
    }

    @Override
    public int eventsBuffered() {
        return metricBuffer.size();
    }

    @Override
    public void onEvent(Event e) {
        // TODO: bufferLimit and offerTime..
        MetricTimeSlot timeSlot = metricBuffer.get(e.getTimestamp());
        if (timeSlot == null) {
            timeSlot = new MetricTimeSlot();
            metricBuffer.put(e.getTimestamp(), timeSlot);
        }
        ConcurrentLinkedQueue<Event> events = timeSlot.metricMap.get(e.getName());
        if (events == null) {
            events = new ConcurrentLinkedQueue<>();
            timeSlot.metricMap.put(e.getName() + e.getPrimaryTag(), events);
        }
        events.add(e);
    }
}


class MetricTimeSlot {
    Map<String,ConcurrentLinkedQueue<Event>> metricMap = new ConcurrentHashMap<>();
}
