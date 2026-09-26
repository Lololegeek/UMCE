package io.umce.core.hardware;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** JVM and host facts that can be read without native dependencies. */
public final class HardwareDetector {
    public HardwareProfile detect() {
        Runtime runtime = Runtime.getRuntime();
        java.lang.management.RuntimeMXBean jvm = ManagementFactory.getRuntimeMXBean();
        List<String> collectors = new ArrayList<String>();
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            collectors.add(collector.getName());
        }
        String cpu = System.getenv("PROCESSOR_IDENTIFIER");
        Optional<String> cpuName = cpu == null || cpu.trim().isEmpty()
                ? Optional.<String>empty() : Optional.of(cpu.trim());

        return new HardwareProfile(
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"),
                jvm.getVmName(),
                jvm.getVmVersion(),
                Math.max(1, runtime.availableProcessors()),
                runtime.maxMemory(),
                detectPhysicalMemory(ManagementFactory.getOperatingSystemMXBean()),
                cpuName,
                collectors,
                "NOT_PROBED");
    }

    private static OptionalLong detectPhysicalMemory(OperatingSystemMXBean bean) {
        if (!(bean instanceof com.sun.management.OperatingSystemMXBean)) return OptionalLong.empty();
        long bytes = ((com.sun.management.OperatingSystemMXBean) bean).getTotalPhysicalMemorySize();
        return bytes > 0 ? OptionalLong.of(bytes) : OptionalLong.empty();
    }
}
