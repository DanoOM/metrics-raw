package org.dsh.metrics.listeners;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.dsh.metrics.DoubleEvent;
import org.dsh.metrics.Event;
import org.dsh.metrics.EventListener;
import org.dsh.metrics.LongEvent;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.MetricBuilder;

public class KairosDBListener implements EventListener, Runnable {

    private BlockingQueue<Event> queue = new ArrayBlockingQueue<>(1000);
    private final int batchSize;
    private final long offerTime;   // amount of time we are willing to 'block' before adding an event to our buffer, prior to dropping it.
    private Thread runThread;
    private final HttpClient kairosDb;


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
        try {
            final List<Event> dispatchList = new ArrayList<>(100);
            do {
                if (0 == queue.drainTo(dispatchList, batchSize - 1)) {
                    Event e = queue.take();
                    dispatchList.add(e);
                }
                MetricBuilder payload = buildPayload(dispatchList);
                kairosDb.pushMetrics(payload);
            } while(true);
        }
        catch(IOException | URISyntaxException ex) {
            // swallow..
        }
        catch(InterruptedException ie) {
            // assuming system exist.
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
