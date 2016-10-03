package org.dsh.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import com.sun.management.OperatingSystemMXBean;

public class JvmMetrics {

    public JvmMetrics(MetricRegistry registry, int intervalInSeconds) {
        final OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        registry.scheduleGauge("jvm.processCPU", intervalInSeconds, () -> {return osBean.getProcessCpuLoad();});
        registry.scheduleGauge("jvm.systemCPU", intervalInSeconds, () -> {return osBean.getSystemCpuLoad();});
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        registry.scheduleGauge("jvm.heapUsed",intervalInSeconds, () -> {return mem.getHeapMemoryUsage().getUsed();});
        registry.scheduleGauge("jvm.nonHeapUsed",intervalInSeconds, () -> {return mem.getNonHeapMemoryUsage().getUsed();});
    }
}
