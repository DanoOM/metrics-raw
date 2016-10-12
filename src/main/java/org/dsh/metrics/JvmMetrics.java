package org.dsh.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;

import com.sun.management.OperatingSystemMXBean;

public class JvmMetrics {

    public JvmMetrics(MetricRegistry registry, int intervalInSeconds) {
        final OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        registry.scheduleGauge("jvm.processCPU", intervalInSeconds, () -> {return osBean.getProcessCpuLoad();});
        registry.scheduleGauge("jvm.systemCPU", intervalInSeconds, () -> {return osBean.getSystemCpuLoad();});

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        registry.scheduleGauge("jvm.heapUsed",intervalInSeconds, () -> {return memoryBean.getHeapMemoryUsage().getUsed();});
        registry.scheduleGauge("jvm.nonHeapUsed",intervalInSeconds, () -> {return memoryBean.getNonHeapMemoryUsage().getUsed();});

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        registry.scheduleGauge("jvm.threads",intervalInSeconds, () -> {return threadBean.getThreadCount();});
        RuntimeMXBean rbean = ManagementFactory.getRuntimeMXBean();
        registry.scheduleGauge("jvm.uptime",intervalInSeconds, () -> {return rbean.getUptime()/60_000;});
    }
}
