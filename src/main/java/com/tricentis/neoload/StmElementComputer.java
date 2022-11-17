package com.tricentis.neoload;

import com.google.common.base.Preconditions;
import org.apache.jmeter.samplers.SampleResult;

import java.util.List;
import java.util.Optional;

/**
 * @author lcharlois
 * @since 10/12/2021.
 */
class StmElementComputer {
	final long count;
	final long sumDuration;
	final long minDuration;
	final long maxDuration;
	final long sumTtfb;
	final long minTtfb;
	final long maxTtfb;
	final long sumDownloadedBytes;

	private StmElementComputer(final List<SampleResult> results) {
		Preconditions.checkArgument(results.size() > 0);
		count = results.size();
		sumDuration = results.stream().mapToLong(SampleResult::getTime).sum();
		minDuration = results.stream().mapToLong(SampleResult::getTime).min().getAsLong();
		maxDuration = results.stream().mapToLong(SampleResult::getTime).max().getAsLong();
		sumTtfb = results.stream().mapToLong(SampleResult::getLatency).sum();
		minTtfb = results.stream().mapToLong(SampleResult::getLatency).min().getAsLong();
		maxTtfb = results.stream().mapToLong(SampleResult::getLatency).max().getAsLong();
		sumDownloadedBytes = results.stream().mapToLong(SampleResult::getBytesAsLong).sum();
	}

	static Optional<StmElementComputer> fromResults(final List<SampleResult> results) {
		if(results == null || results.isEmpty()) return Optional.empty();
		return Optional.of(new StmElementComputer(results));
	}
}
