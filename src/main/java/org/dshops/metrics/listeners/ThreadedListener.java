package org.dshops.metrics.listeners;

import org.dshops.metrics.EventListener;

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
