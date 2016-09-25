package org.dsh.metrics.tests;

import org.dsh.metrics.EventListener;
import org.dsh.metrics.listeners.KairosDBListener;

public class KairosListenerTest extends BaseListenerTest {

    @Override
    public EventListener getListener() {
        return new KairosDBListener("http://localhost:8080",
                                    "root",
                                    "root");
    }
}
