package org.dsh.metrics.tests;

import org.dsh.metrics.EventListener;
import org.dsh.metrics.listeners.ConsoleListener;


public class ConsoleListenerTest extends BaseListenerTest {



    @Override
    public EventListener getListener() {
        return new ConsoleListener(System.out, 100, 500);
    }
}
