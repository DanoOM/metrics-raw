package org.dshops.metrics.listeners;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.dshops.metrics.Event;
import org.dshops.metrics.EventListener;

/**
 * Echoes out metrics to the console in the Event Format, currently:
 *
 * [Timestamp] [EventName] [Tags] [value]
 *
 * NOTE: tags/value are optional
 * */
public class ConsoleListener extends ThreadedListener implements Runnable {

    private final BlockingQueue<Event> queue;
    private final int batchSize;
    private final long offerTime;   // amount of time we are willing to 'block' before adding an event to our buffer, prior to dropping it.
    private final PrintStream outStream;

    public ConsoleListener(PrintStream outStream) {
        this(outStream, 100);
    }

    public ConsoleListener(PrintStream out, int batchSize) {
        this(out,batchSize, -1);
    }

    public ConsoleListener(PrintStream out, int batchSize, long offerTimeMillis) {
        this.queue = new ArrayBlockingQueue<>(1000);
        if (batchSize > 1) {
            this.batchSize = batchSize;
        }
        else {
            this.batchSize = 100;
        }
        this.outStream = out;
        this.offerTime = offerTimeMillis;
        runThread = new Thread(this);
        runThread.setDaemon(true);
        runThread.start();
    }

    @Override
    public void run() {
        try {
            List<Event> dispatchList = new ArrayList<>(1000);
            do {
                dispatchList.add(queue.take());
                queue.drainTo(dispatchList, batchSize - 1);
                dispatchList.stream().forEach(event -> outStream.println(event));
                dispatchList.clear();
            } while(!stopRequested);
        }
        catch(InterruptedException ie) {
            // assuming system exist.
        }
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

    @Override
    public int eventsBuffered() {
        return queue.size();
    }
}
