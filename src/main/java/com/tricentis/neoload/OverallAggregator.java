package com.tricentis.neoload;

import com.google.common.collect.ImmutableMap;
import com.neotys.nlweb.bench.definition.common.model.BenchStatistics;
import com.neotys.web.data.ValueNumber;
import org.apache.jmeter.samplers.SampleResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.jmeter.threads.JMeterContextService.getThreadCounts;

/**
 * @author lcharlois
 * @since 08/12/2021.
 *
 * OverallAggregator is in charge of forwarding the global metrics displayed on NeoLoadWeb test result overview page.
 *
 * Element properly mapped (attribute <-> NeoLoadWeb indicator):
 *	- transactionCountSuccess <-> TOTAL_TRANSACTION_COUNT_SUCCESS (Total Transaction passed)
 *	- transactionCountFailure <-> TOTAL_TRANSACTION_COUNT_FAILURE (Total Transaction failed)
 *  - transactionDurationSum / (transactionCountSuccess + transactionCountFailure) <-> TOTAL_TRANSACTION_DURATION_AVERAGE (Average Transaction time (s))
 *  - downloadedBytesSum <-> TOTAL_GLOBAL_DOWNLOADED_BYTES (Total throughput Mb)
 *  - downloadedBytesSum / offet <-> TOTAL_GLOBAL_DOWNLOADED_BYTES_PER_SECOND (Average throughput Mb/s)
 *  - requestCountSuccess <-> TOTAL_REQUEST_COUNT_SUCCESS (Total request passed)
 *  - requestCountFailure <-> TOTAL_REQUEST_COUNT_FAILURE (Total request failed)
 *  - requestDurationSum / (requestCountSuccess + transactionCountFailure) <-> TOTAL_REQUEST_DURATION_AVERAGE (Average request response time (s))
 *  - (transactionCountSuccess + transactionCountFailure) / offset <-> TOTAL_TRANSACTION_COUNT_PER_SECOND (Total Transactions/s)
 *  - (requestCountSuccess + requestCountFailure) / offset  <-> TOTAL_REQUEST_COUNT_PER_SECOND (Total requests/s)
 *  - lastVirtualUserCount <-> LAST_VIRTUAL_USER_COUNT (Users curve on overview 1st graph)
 *  - lastRequestCount / relativeOffset <-> LAST_REQUEST_COUNT_PER_SECOND (Requests/s curve on overview 2nd graph)
 *  - lastRequestDurationSum / lastRequestCount <-> LAST_REQUEST_DURATION_AVERAGE (Response time (s) curve on overview 2nd graph)
 *  - lastTransactionDurationSum / lastTransactionCount <-> LAST_TRANSACTION_DURATION_AVERAGE (?????????????)
 *  - lastErrorCount <-> LAST_ERROR_COUNT (Errors curve on overview 1st graph)
 *
 * Jmeter statistic not mapped
 *  - SampleResult.getSentBytes() -> this is the total number of bytes uploaded. NeoLoadWeb do not display this information because the indicator "Throughput is only for downloaded bytes"
 *
 *
 *
 *
 * TODO:
 * 1/ The last error count is mapped to NLWeb LAST_ERROR_COUNT which see to be for the Error graph on the Overview page.
 * As the documentation clain this is only the average number of requests containing error, we may want to filter and increment the last error count only for request.
 *
 * 2/ How to handle TOTAL_ITERATION_COUNT_SUCCESS & TOTAL_ITERATION_COUNT_FAILURE & TOTAL_GLOBAL_COUNT_FAILURE ?
 * This info are needed by NLWeb, but not provided by JMeter.
 *
 * 3/ nanSafe() method. How to handle the not-a-number (division by 0...). So far, value just set to 0 for display.
 *
 */
final class OverallAggregator {
	private final ReentrantLock lock = new ReentrantLock();
	//Overall
	private final AtomicLong transactionCountSuccess = new AtomicLong(0);
	private final AtomicLong transactionCountFailure = new AtomicLong(0);
	private final AtomicLong transactionDurationSum = new AtomicLong(0);
	private final AtomicLong downloadedBytesSum = new AtomicLong(0);
	private final AtomicLong requestCountSuccess = new AtomicLong(0);
	private final AtomicLong requestCountFailure = new AtomicLong(0);
	private final AtomicLong requestDurationSum = new AtomicLong(0);
	private long startTime = 0L;

	//Last
	private final AtomicLong lastRequestCount = new AtomicLong(0);
	private final AtomicLong lastRequestDurationSum = new AtomicLong(0);
	private final AtomicLong lastTransactionCount = new AtomicLong(0);
	private final AtomicLong lastTransactionDurationSum = new AtomicLong(0);
	private final AtomicLong lastErrorCount = new AtomicLong(0);
	private final AtomicReference<VULoad> vuLoad = new AtomicReference<>(new VULoad(0,0,0));

	private long lastStartTime = 0L;

	OverallAggregator() {
	}

	void start(final long startTime) {
		this.startTime = startTime;
		this.lastStartTime = startTime;
		this.vuLoad.set(new VULoad(0,0,0));
	}

	void addResults(final List<SampleResult> results) {
		lock.lock();
		try {
			for (final SampleResult result : results) {
				if (result.getSubResults() != null && result.getSubResults().length > 0) {
					addTransactionResult(result);
				} else {
					addRequestResult(result);
				}
			}
		} finally {
			lock.unlock();
		}
	}

	private void addTransactionResult(final SampleResult transactionResult) {
		if (transactionResult.isSuccessful()) {
			transactionCountSuccess.incrementAndGet();
		} else {
			transactionCountFailure.incrementAndGet();
		}
		transactionDurationSum.addAndGet(transactionResult.getTime());
		lastTransactionDurationSum.addAndGet(transactionResult.getTime());
		lastTransactionCount.incrementAndGet();
	}

	private void addRequestResult(final SampleResult requestResult) {
		if (requestResult.isSuccessful()) {
			requestCountSuccess.incrementAndGet();
		} else {
			requestCountFailure.incrementAndGet();
			// TODO : the last error count is mapped to NLWeb LAST_ERROR_COUNT which see to be for the Error graph on the Overview page.
			// As the documentation clain this is only the average number of requests containing error, we may want to filter and increment the last error count only for request.
			// && !TransactionController.isFromTransactionController(result)
			lastErrorCount.incrementAndGet();
		}
		requestDurationSum.addAndGet(requestResult.getTime());
		downloadedBytesSum.addAndGet(requestResult.getBytesAsLong());
		lastRequestDurationSum.addAndGet(requestResult.getTime());
		lastRequestCount.incrementAndGet();
	}

	class VULoad {
		int activeVU;
		int finishedVU;
		int startedVU;

		public VULoad(int activeVU, int finishedVU, int start) {
			this.activeVU = activeVU;
			this.finishedVU = finishedVU;
			this.startedVU = start;
		}

		@Override
		public String toString() {
			return "a=>"+activeVU+" s=>"+startedVU+" f=>"+finishedVU;
		}

		public int computeVuLoadFrom(VULoad vuLoad) {
			return this.activeVU + (this.finishedVU - vuLoad.finishedVU);
		}
	}
	private VULoad vuSnapshot() {
		return new VULoad(
				getThreadCounts().activeThreads,
				getThreadCounts().finishedThreads,
				getThreadCounts().startedThreads);
	}
	private int getVULoadFromLastSnapshotAndReplace() {
		VULoad newVULoad = vuSnapshot();
		VULoad lastVULoad = vuLoad.get();
		vuLoad.set(newVULoad);
		return newVULoad.computeVuLoadFrom(lastVULoad);
	}
	Map<BenchStatistics.Stat, ValueNumber> getStats() {
		lock.lock();
		final long now = System.currentTimeMillis();
		try {
			final int lastVirtualUserCount = getVULoadFromLastSnapshotAndReplace();
			System.out.println("VU LOAD => "+vuLoad+" FINAL VALUE => "+lastVirtualUserCount);
			final long offset = now - startTime;
			final long relativeOffset = now - lastStartTime;
			return ImmutableMap.<BenchStatistics.Stat, ValueNumber>builder()
					.put(BenchStatistics.Stat.TIMESTAMP, ValueNumber.of(now))
					.put(BenchStatistics.Stat.OFFSET, ValueNumber.of(offset))
					.put(BenchStatistics.Stat.TOTAL_TRANSACTION_COUNT_SUCCESS, ValueNumber.of(transactionCountSuccess.longValue()))
					.put(BenchStatistics.Stat.TOTAL_TRANSACTION_COUNT_FAILURE, ValueNumber.of(transactionCountFailure.longValue()))
					.put(BenchStatistics.Stat.TOTAL_TRANSACTION_COUNT_PER_SECOND, ValueNumber.of(nanSafe(getRate(transactionCountSuccess.longValue() + transactionCountFailure.longValue(), offset))))
					.put(BenchStatistics.Stat.TOTAL_TRANSACTION_DURATION_AVERAGE, ValueNumber.of(nanSafe(getAverage(transactionCountSuccess.longValue() + transactionCountFailure.longValue(), transactionDurationSum.longValue()))))
					.put(BenchStatistics.Stat.TOTAL_GLOBAL_DOWNLOADED_BYTES, ValueNumber.of(downloadedBytesSum.longValue()))
					.put(BenchStatistics.Stat.TOTAL_GLOBAL_DOWNLOADED_BYTES_PER_SECOND, ValueNumber.of(nanSafe(getRate(downloadedBytesSum.longValue(), offset))))
					.put(BenchStatistics.Stat.TOTAL_REQUEST_COUNT_SUCCESS, ValueNumber.of(requestCountSuccess.longValue()))
					.put(BenchStatistics.Stat.TOTAL_REQUEST_COUNT_FAILURE, ValueNumber.of(requestCountFailure.longValue()))
					.put(BenchStatistics.Stat.TOTAL_GLOBAL_COUNT_FAILURE, ValueNumber.of(requestCountFailure.longValue()))
					.put(BenchStatistics.Stat.TOTAL_REQUEST_COUNT_PER_SECOND, ValueNumber.of(nanSafe(getRate(requestCountSuccess.longValue() + requestCountFailure.longValue(), offset))))
					.put(BenchStatistics.Stat.TOTAL_REQUEST_DURATION_AVERAGE, ValueNumber.of(nanSafe(getAverage(requestCountSuccess.longValue() + requestCountFailure.longValue(), requestDurationSum.longValue()))))
					.put(BenchStatistics.Stat.LAST_VIRTUAL_USER_COUNT, ValueNumber.of(lastVirtualUserCount))
					.put(BenchStatistics.Stat.LAST_REQUEST_COUNT_PER_SECOND, ValueNumber.of(nanSafe(getRate(lastRequestCount.longValue(), relativeOffset))))
					.put(BenchStatistics.Stat.LAST_REQUEST_DURATION_AVERAGE, ValueNumber.of(nanSafe(getAverage(lastRequestCount.longValue(), lastRequestDurationSum.longValue()))))
					.put(BenchStatistics.Stat.LAST_TRANSACTION_DURATION_AVERAGE, ValueNumber.of(nanSafe(getAverage(lastTransactionCount.longValue(), lastTransactionDurationSum.longValue()))))
					.put(BenchStatistics.Stat.LAST_ERROR_COUNT, ValueNumber.of(lastErrorCount.longValue()))
					.build();
		} finally {
			lastStartTime = now;
			lastRequestCount.set(0);
			lastRequestDurationSum.set(0);
			lastTransactionCount.set(0);
			lastTransactionDurationSum.set(0);
			lastErrorCount.set(0);
			lock.unlock();
		}
	}

	private double getAverage(final long count, final long sum) {
		return (1.0D * sum) / count;
	}

	private double  getRate(final long count, final long offset) {
		return (1.0D * count) / (1.0D * offset / 1000);
	}

	//FIXME this is ugly !
	private ValueNumber nanSafe(final double value) {
		if (Double.isNaN(value)) {
			return ValueNumber.of(0.0D);
		}
		return ValueNumber.of(value);
	}

}
