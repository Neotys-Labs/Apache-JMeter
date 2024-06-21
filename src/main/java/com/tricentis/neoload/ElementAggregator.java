package com.tricentis.neoload;

import org.apache.jmeter.samplers.SampleResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.tricentis.neoload.ThreadGroupNameCache.getThreadGroupName;

/**
 * @author lcharlois
 * @since 09/12/2021.
 */
class ElementAggregator {

    private final String uuid;
    private final long startTime;
    private final int samplingInterval;
    private final Map<Long, ElementResults> statByBucket = new HashMap<>();

    ElementAggregator(final String uuid, final long startTime, final int samplingInterval) {
        this.uuid = uuid;
        this.startTime = startTime;
        this.samplingInterval = samplingInterval;
    }

    void addResult(final SampleResult result) {
        final long offset = result.getStartTime() - startTime;
        final long bucket = offset / samplingInterval;
        statByBucket.computeIfAbsent(bucket, key -> new ElementResults(uuid, bucket * samplingInterval)).add(result);
    }

    List<ElementResults> collect() {
        final List<ElementResults> valuesCopy = new ArrayList<>(statByBucket.values());
        statByBucket.clear();
        return valuesCopy;
    }

}
