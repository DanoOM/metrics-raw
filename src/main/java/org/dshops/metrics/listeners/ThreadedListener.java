package org.dshops.metrics.listeners;

import org.dshops.metrics.EventListener;

public abstract class ThreadedListener implements EventListener {
    protected Thread runThread;
    protected boolean stopRequested = false;

    @Override
    public void stop() {
        if(runThread != null && runThread.isAlive()) {
            stopRequested = true;
            long startWait = System.currentTimeMillis();
            try {
                do {
                    Thread.sleep(100);
                } while(runThread.isAlive() && System.currentTimeMillis() - startWait < 2000);
                if(runThread.isAlive()) {
                    runThread.interrupt();
                }
            }
            catch (Exception e) {}
        }
    }

}
