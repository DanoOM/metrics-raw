package org.dshops.metrics.listeners;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
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
public class KairosDBListener implements EventListener, Runnable {

    private final BlockingQueue<Event> queue;
    private final int batchSize;
    private final long offerTime;   // amount of time we are willing to 'block' before adding an event to our buffer, prior to dropping it.
    private Thread runThread;
    private final HttpClient kairosDb;
    private final static Logger log = LoggerFactory.getLogger(KairosDBListener.class);
    private final MetricRegistry registry;
    private final AtomicInteger droppedEvents = new AtomicInteger();
    private final String serviceTeam;
    private final String app;
    private final String appType;

    public KairosDBListener(String connectString,
                            String un,
                            String pd,
                            MetricRegistry registry) {
        this(connectString, un, pd, registry, 100);
    }

    public KairosDBListener(String connectString,
                            String un,
                            String pd,
                            MetricRegistry registry,
                            int batchSize) {
        this(connectString, un, pd, registry, batchSize, 5000, -1);
    }

    public KairosDBListener(String connectString,
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

        this.queue = new ArrayBlockingQueue<>(bufferSize);
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
                // block until we have at least 1 metric
                dispatchList.add(queue.take());

                // try to always send a minimum of dispatchSize datapoints per call.
                long takeTime = System.currentTimeMillis();
                do {
                    Event e = queue.poll(10, TimeUnit.MILLISECONDS);
                    if (e != null) {
                        dispatchList.add(e);
                    }
                    // flush every second or until we have seen batchSize Events
                } while(dispatchList.size() < batchSize && (System.currentTimeMillis() - takeTime < 1000));

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
            catch(InterruptedException ie) {
                break;
            }
            catch(Exception ex) {
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

    private void sendMetricStats(long metricCount, long errorCount, long httpCalls) throws Exception {
    	try{
	    	MetricBuilder mb = MetricBuilder.getInstance();
	    	mb.addMetric("stats.count")
	    	  .addTags(registry.getTags())
	    	  .addTag("serviceTeam",serviceTeam)
	    	  .addTag("app",app)
	    	  .addTag("appType",appType)
	    	  .addDataPoint(metricCount);
	    	mb.addMetric("stats.errors")
	    	.addTags(registry.getTags())
            .addTag("serviceTeam",serviceTeam)
            .addTag("app",app)
            .addTag("appType",appType)
	    	  .addDataPoint(errorCount);
	    	mb.addMetric("stats.httpCalls")
	    	.addTags(registry.getTags())
            .addTag("serviceTeam",serviceTeam)
            .addTag("app",app)
            .addTag("appType",appType)
	    	  .addDataPoint(httpCalls);
	    	mb.addMetric("stats.dropped")
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
        return queue.size();
    }

    @Override
    public void onEvent(Event e) {
        if (offerTime > 0) {
            try {
                if (!queue.offer(e, offerTime, TimeUnit.MILLISECONDS)){
                	droppedEvents.incrementAndGet();
                }
            }
            catch(InterruptedException ie) {
                // swallow
            }
        }
        else {
            queue.offer(e);
        }
    }
}
