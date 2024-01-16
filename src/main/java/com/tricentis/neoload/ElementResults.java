package com.tricentis.neoload;

import org.apache.jmeter.samplers.SampleResult;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author lcharlois
 * @since 10/12/2021.
 */
class ElementResults {
    private final List<SampleResult> results = new LinkedList<>();

    private final String uuid;

    private final long offset;

    ElementResults(final String uuid, final long offset) {
        this.uuid = uuid;
        this.offset = offset;
    }

    public void add(final SampleResult result) {
        results.add(result);
    }

    public List<SampleResult> getResults() {
        return results;
    }

    public long getOffset() {
        return offset;
    }

    public String getUuid() {
        return uuid;
    }

    public Stream<SampleResult> stream() {
        return results.stream();
    }
}
