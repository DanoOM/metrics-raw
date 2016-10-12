package org.dsh.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;

public class JvmMetrics {

    public static void addMetrics(MetricRegistry registry, int intervalInSeconds) {
        final OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        registry.scheduleGauge("jvm.processCPU", intervalInSeconds, () -> {return osBean.getProcessCpuLoad();});
        registry.scheduleGauge("jvm.systemCPU", intervalInSeconds, () -> {return osBean.getSystemCpuLoad();});
        
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        registry.scheduleGauge("jvm.heapUsed",intervalInSeconds, () -> {return memoryBean.getHeapMemoryUsage().getUsed();});
        registry.scheduleGauge("jvm.nonHeapUsed",intervalInSeconds, () -> {return memoryBean.getNonHeapMemoryUsage().getUsed();});
        
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        registry.scheduleGauge("jvm.threads",intervalInSeconds, () -> {return threadBean.getThreadCount();});
    }
}
