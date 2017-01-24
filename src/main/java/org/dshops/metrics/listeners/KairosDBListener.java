package org.dshops.metrics.listeners;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.dshops.metrics.DoubleEvent;
import org.dshops.metrics.Event;
import org.dshops.metrics.EventListener;
import org.dshops.metrics.LongEvent;
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

    public KairosDBListener(String connectString,
                            String un,
                            String pd) {
        this(connectString, un, pd, 100);
    }

    public KairosDBListener(String connectString,
                            String un,
                            String pd,
                            int batchSize) {
        this(connectString,un,pd,batchSize,-1);
    }

    public KairosDBListener(String connectString,
                            String un,
                            String pd,
                            int batchSize,
                            long offerTimeMillis) {
        this.queue = new ArrayBlockingQueue<>(5000);
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
        long lastResetTime = 0;
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

                metricCount += dispatchList.size();
                Response r = kairosDb.pushMetrics(buildPayload(dispatchList));
                httpCalls++;
                if (r.getStatusCode() != 204 ){
                    lastError = r;
                    errorCount++;
                }
                // every 5 minutes log a stat
                if (System.currentTimeMillis() - lastResetTime > 300_000) {
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
                        else {
                            log.info("Http calls:{} Dispatch count: {} errorCount:{} lastError.status:{} lastErrorDetails:{}", httpCalls, metricCount, errorCount, lastError.getStatusCode(), sb.toString());
                        }
                        lastError = null;
                    }
                    else {
                        log.info("Http calls: Dispatch count: ", httpCalls, metricCount);
                    }
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
                    log.error("Unexpected Exception (only 1 exception log per minute)", ex);
                }
                exceptionTime = System.currentTimeMillis();
            }
            finally {
                dispatchList.clear();
            }
        } while(true);
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
                queue.offer(e, offerTime, TimeUnit.MILLISECONDS);
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
