package io.umce.core.hardware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public final class HardwareProfile {
    private final String operatingSystem;
    private final String architecture;
    private final String jvmName;
    private final String jvmVersion;
    private final int logicalProcessors;
    private final long maxHeapBytes;
    private final OptionalLong physicalMemoryBytes;
    private final Optional<String> cpuModelName;
    private final List<String> garbageCollectors;
    private final String gpuComputeProbe;

    HardwareProfile(String operatingSystem, String architecture, String jvmName, String jvmVersion,
                    int logicalProcessors, long maxHeapBytes, OptionalLong physicalMemoryBytes,
                    Optional<String> cpuModelName, List<String> garbageCollectors, String gpuComputeProbe) {
        this.operatingSystem = operatingSystem;
        this.architecture = architecture;
        this.jvmName = jvmName;
        this.jvmVersion = jvmVersion;
        this.logicalProcessors = logicalProcessors;
        this.maxHeapBytes = maxHeapBytes;
        this.physicalMemoryBytes = physicalMemoryBytes;
        this.cpuModelName = cpuModelName;
        this.garbageCollectors = Collections.unmodifiableList(new ArrayList<String>(garbageCollectors));
        this.gpuComputeProbe = gpuComputeProbe;
    }

    public String getOperatingSystem() { return operatingSystem; }
    public String getArchitecture() { return architecture; }
    public String getJvmName() { return jvmName; }
    public String getJvmVersion() { return jvmVersion; }
    public int getLogicalProcessors() { return logicalProcessors; }
    public long getMaxHeapBytes() { return maxHeapBytes; }
    public OptionalLong getPhysicalMemoryBytes() { return physicalMemoryBytes; }
    public Optional<String> getCpuModelName() { return cpuModelName; }
    public List<String> getGarbageCollectors() { return garbageCollectors; }

    /** Currently reports NOT_PROBED; no native GPU backend is included yet. */
    public String getGpuComputeProbe() { return gpuComputeProbe; }
}
