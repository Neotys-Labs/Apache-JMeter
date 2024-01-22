package com.tricentis.neoload;

import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.samplers.SampleResult;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author lcharlois
 * @since 09/12/2021.
 */
class ResultsAggregatorManager {
    private final Map<String, String> uuidByLabel = new HashMap<>();
    private final Map<String, Integer> objectIdByLabel = new HashMap<>();
    private final BlockingQueue<BenchElement> newElements = new LinkedBlockingQueue<>();
    private final Map<String, ElementAggregator> aggregatorByUuid = new HashMap<>();
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
                if (!uuidByLabel.containsKey(sampleLabel)) {
                    final int objectId = objectIdGenerator.getAndIncrement();
                    newElements.add(BenchElement.newElement(uuid, objectId, sampleLabel, getKind(result)));
                    uuidByLabel.put(sampleLabel, uuid);
                    objectIdByLabel.put(sampleLabel, objectId);
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
            final List<ElementResults> values = aggregatorByUuid.values().stream().map(ElementAggregator::collect).flatMap(Collection::stream).collect(Collectors.toList());
            return new StatisticsBulk(newElementsCopy, values);
        } finally {
            lock.unlock();
        }
    }

    Integer getObjectId(final SampleResult sampleResult) {
        return objectIdByLabel.get(sampleResult.getSampleLabel());
    }

}
