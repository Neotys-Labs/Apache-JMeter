package com.tricentis.neoload;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.neotys.nlweb.bench.definition.common.FamilyName;
import com.neotys.nlweb.bench.definition.common.Statistics;
import com.neotys.nlweb.bench.definition.storage.model.element.Element;
import com.neotys.nlweb.bench.definition.storage.model.element.ElementBuilder;
import com.neotys.nlweb.bench.result.im.data.point.ImCounterPoint;
import com.neotys.web.data.ValueNumber;
import org.apache.jmeter.threads.JMeterContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author srichert
 * @since 18/05/2022.
 */
public enum Monitor {

    USER_LOAD("User Load", "35d6fa81-be88-4b24-b426-805d68bd769d", Statistics.USER_LOAD_NAME) {
        @Override
        public Optional<ValueNumber> getValue() {
            return Optional.of(ValueNumber.of(JMeterContextService.getNumberOfThreads()));
        }
    },
    MEMORY_USED("Memory Used", "cb573624-7647-40ac-9dbd-c032669f016a", "%") {
        @Override
        public Optional<ValueNumber> getValue() {
            final Runtime runtime = Runtime.getRuntime();
            return Optional.of(ValueNumber.of((((double) (runtime.totalMemory() - runtime.freeMemory())) / runtime.totalMemory()) * 100));
        }
    },
    THREAD_COUNT("Thread Count", "f1b0994b-3724-4472-b83e-1d0b9b26e4b6", "") {
        @Override
        public Optional<ValueNumber> getValue() {
            return Optional.of(ValueNumber.of(ManagementFactory.getThreadMXBean().getThreadCount()));
        }
    },
    CPU_LOAD("CPU Load", "dc728726-b0ee-4333-947d-52d78626b5bc", "%") {
        @Override
        public Optional<ValueNumber> getValue() {
            try {
                final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                final ObjectName name = ObjectName.getInstance("java.lang:type=OperatingSystem");
                final AttributeList list = mbs.getAttributes(name, new String[]{"ProcessCpuLoad"});
                if (list.isEmpty()) {
                    return Optional.empty();
                }
                final Attribute attribute = (Attribute) list.get(0);
                return Optional.of(ValueNumber.of((Double) attribute.getValue() * 100));
            } catch (final Exception e) {
                LOGGER.error("Error while computing CPU Load", e);
                return Optional.empty();
            }
        }
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(Monitor.class);

    private final String displayName;
    private final String counterId;
    private final String unit;

    Monitor(final String displayName, final String counterId, final String unit) {
        this.displayName = displayName;
        this.counterId = counterId;
        this.unit = unit;
    }

    abstract public Optional<ValueNumber> getValue();

    public Element toElement() {
        return ElementBuilder.builder()
                .name(displayName)
                .id(counterId)
                .familyId(FamilyName.MONITOR.name())
                .addSpecificStatisticId(unit)
                .build();
    }

    public Optional<ImCounterPoint> toImCounterPoint(final long startTime) {
        return getValue().map(vn -> ImCounterPoint.of(counterId, (int) (System.currentTimeMillis() - startTime), vn));
    }

    public static ListMultimap getCountersByGroupId(final String groupId) {
        final ListMultimap<String, String> counterByGroupId = ArrayListMultimap.create();
        counterByGroupId.putAll(groupId, Arrays.stream(Monitor.values()).map(monitor -> monitor.counterId).collect(Collectors.toSet()));
        return counterByGroupId;
    }
}








