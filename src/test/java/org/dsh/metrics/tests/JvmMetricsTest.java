package org.dsh.metrics.tests;

import org.dsh.metrics.JvmMetrics;
import org.dsh.metrics.MetricRegistry;
import org.dsh.metrics.listeners.KairosDBListener;
import org.testng.annotations.Test;

public class JvmMetricsTest extends BaseTest {

    public void main(String args[]) {
        testJvmMetrics();
    }
    @Test
    public void testJvmMetrics() {
        MetricRegistry reg = getRegistry();
        reg.addEventListener(new KairosDBListener("http://wdc-tst-masapp-001:8080",
                                                  "root",
                                                  "root"));

        JvmMetrics.addMetrics(reg, 1);
        while(true) { pause(1000); }
    }
}
