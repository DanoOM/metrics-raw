package org.dshops.metrics.listeners;

import org.dshops.metrics.EventListener;

/**
 * Base class that can be used for EventListener that dispatch events on a secondary thread.
 * The recommend approach, as onEvent should be as fast as possible.
 * */
public abstract class ThreadedListener implements EventListener {
    protected Thread runThread = null;
    protected volatile boolean stopRequested = false;

    @Override
    public void stop() {
        if (runThread != null) {
            stopRequested = true;
            long startWait = System.currentTimeMillis();
            try {
                do {
                    Thread.sleep(100);
                } while(runThread.isAlive() && System.currentTimeMillis() - startWait < 2000);

                runThread.interrupt();
            }
            catch (Exception e) {}
        }
    }

}
