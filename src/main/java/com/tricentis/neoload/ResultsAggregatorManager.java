package com.tricentis.neoload;

import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.samplers.SampleResult;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.tricentis.neoload.ThreadGroupNameCache.getThreadGroupName;

/**
 * @author lcharlois
 * @since 09/12/2021.
 */
class ResultsAggregatorManager {
    private final Map<String, Map<String, String>> uuidByLabelByThreadGroupName = new HashMap<>();
    private final Map<String, Map<String, Integer>> objectIdByLabelByThreadGroupName = new HashMap<>();
    private final BlockingQueue<BenchElement> newElements = new LinkedBlockingQueue<>();
    private final Map<String, Map<String, ElementAggregator>> aggregatorByUuidByThreadGroupName = new HashMap<>();
    private long startTime;
    private final int samplingInterval;
    // Must start at 5.
    // 1, 2, 3 & 4 are reserved ????
    // 1 = VU ID
    // 2 = LG ID
    // 3 = zoneID
    // 4 = populationID
    private final AtomicInteger objectIdGenerator = new AtomicInteger(5);
    private final ReentrantLock lock = new ReentrantLock();

    ResultsAggregatorManager(final int samplingInterval) {
        this.samplingInterval = samplingInterval;
    }

    void start(final long startTime) {
        this.startTime = startTime;
    }

    void addResults(final List<SampleResult> sampleResults) {
        lock.lock();
        try {

            for (final SampleResult result : sampleResults) {
                final String sampleLabel = result.getSampleLabel();
                final String uuid = UUID.nameUUIDFromBytes(sampleLabel.getBytes()).toString();
                final String threadGroupName = getThreadGroupName(result);
                Map<String, String> uuidByLabel = uuidByLabelByThreadGroupName.get(threadGroupName);
                if (uuidByLabel == null) {
                    uuidByLabel = new HashMap<>();
                    uuidByLabelByThreadGroupName.put(threadGroupName, uuidByLabel);
                }
                Map<String, Integer> objectIdByLabel = objectIdByLabelByThreadGroupName.get(threadGroupName);
                if (objectIdByLabel == null) {
                    objectIdByLabel = new HashMap<>();
                    objectIdByLabelByThreadGroupName.put(threadGroupName, objectIdByLabel);
                }
                if (!uuidByLabel.containsKey(sampleLabel)) {
                    final int objectId = objectIdGenerator.getAndIncrement();
                    newElements.add(BenchElement.newElement(uuid, objectId, sampleLabel, getKind(result), getThreadGroupName(result)));
                    uuidByLabel.put(sampleLabel, uuid);
                    objectIdByLabel.put(sampleLabel, objectId);
                }
                Map<String, ElementAggregator> aggregatorByUuid = aggregatorByUuidByThreadGroupName.get(threadGroupName);
                if (aggregatorByUuid == null) {
                    aggregatorByUuid = new HashMap<>();
                    aggregatorByUuidByThreadGroupName.put(threadGroupName, aggregatorByUuid);
                }
                final ElementAggregator elementAggregator = aggregatorByUuid.computeIfAbsent(uuid, key -> new ElementAggregator(key, startTime, samplingInterval));
                elementAggregator.addResult(result);
            }
        } finally {
            lock.unlock();
        }
    }

    private BenchElement.Kind getKind(final SampleResult result) {
        return TransactionController.isFromTransactionController(result) ?
                BenchElement.Kind.TRANSACTION :
                BenchElement.Kind.REQUEST;
    }

    StatisticsBulk collect() {
        lock.lock();
        try {
            final List<BenchElement> newElementsCopy = new ArrayList<>();
            newElements.drainTo(newElementsCopy);
            final List<ElementResults> values = aggregatorByUuidByThreadGroupName
                    .values()
                    .stream()
                    .map(Map::values)
                    .flatMap(Collection::stream)
                    .map(ElementAggregator::collect)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
            return new StatisticsBulk(newElementsCopy, values);
        } finally {
            lock.unlock();
        }
    }

    Integer getObjectId(final SampleResult sampleResult) {
        return objectIdByLabelByThreadGroupName.get(getThreadGroupName(sampleResult)).get(sampleResult.getSampleLabel());
    }
}
