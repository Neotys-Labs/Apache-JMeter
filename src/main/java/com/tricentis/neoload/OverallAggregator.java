package com.tricentis.neoload;

import com.google.common.collect.ImmutableMap;
import com.neotys.nlweb.bench.definition.common.model.BenchStatistics;
import com.neotys.web.data.ValueNumber;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.threads.JMeterContextService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author lcharlois
 * @since 08/12/2021.
 * <p>
 * OverallAggregator is in charge of forwarding the global metrics displayed on NeoLoadWeb test result overview page.
 * <p>
 * Element properly mapped (attribute <-> NeoLoadWeb indicator):
 * - transactionCountSuccess <-> TOTAL_TRANSACTION_COUNT_SUCCESS (Total Transaction passed)
 * - transactionCountFailure <-> TOTAL_TRANSACTION_COUNT_FAILURE (Total Transaction failed)
 * - transactionDurationSum / (transactionCountSuccess + transactionCountFailure) <-> TOTAL_TRANSACTION_DURATION_AVERAGE (Average Transaction time (s))
 * - downloadedBytesSum <-> TOTAL_GLOBAL_DOWNLOADED_BYTES (Total throughput Mb)
 * - downloadedBytesSum / offet <-> TOTAL_GLOBAL_DOWNLOADED_BYTES_PER_SECOND (Average throughput Mb/s)
 * - requestCountSuccess <-> TOTAL_REQUEST_COUNT_SUCCESS (Total request passed)
 * - requestCountFailure <-> TOTAL_REQUEST_COUNT_FAILURE (Total request failed)
 * - requestDurationSum / (requestCountSuccess + transactionCountFailure) <-> TOTAL_REQUEST_DURATION_AVERAGE (Average request response time (s))
 * - (transactionCountSuccess + transactionCountFailure) / offset <-> TOTAL_TRANSACTION_COUNT_PER_SECOND (Total Transactions/s)
 * - (requestCountSuccess + requestCountFailure) / offset  <-> TOTAL_REQUEST_COUNT_PER_SECOND (Total requests/s)
 * - lastVirtualUserCount <-> LAST_VIRTUAL_USER_COUNT (Users curve on overview 1st graph)
 * - lastRequestCount / relativeOffset <-> LAST_REQUEST_COUNT_PER_SECOND (Requests/s curve on overview 2nd graph)
 * - lastRequestDurationSum / lastRequestCount <-> LAST_REQUEST_DURATION_AVERAGE (Response time (s) curve on overview 2nd graph)
 * - lastTransactionDurationSum / lastTransactionCount <-> LAST_TRANSACTION_DURATION_AVERAGE (?????????????)
 * - lastErrorCount <-> LAST_ERROR_COUNT (Errors curve on overview 1st graph)
 * <p>
 * Jmeter statistic not mapped
 * - SampleResult.getSentBytes() -> this is the total number of bytes uploaded. NeoLoadWeb do not display this information because the indicator "Throughput is only for downloaded bytes"
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
    private long lastStartTime = 0L;

    OverallAggregator() {
    }

    void start(final long startTime) {
        this.startTime = startTime;
        this.lastStartTime = startTime;
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
            // As the documentation claim this is only the average number of requests containing error, we may want to filter and increment the last error count only for request.
            // && !TransactionController.isFromTransactionController(result)
            lastErrorCount.incrementAndGet();
        }
        requestDurationSum.addAndGet(requestResult.getTime());
        downloadedBytesSum.addAndGet(requestResult.getBytesAsLong());
        lastRequestDurationSum.addAndGet(requestResult.getTime());
        lastRequestCount.incrementAndGet();
    }

    Map<BenchStatistics.Stat, ValueNumber> getStats() {
        lock.lock();
        final long now = System.currentTimeMillis();
        try {
            final long offset = now - startTime;
            final long relativeOffset = now - lastStartTime;
            final long lastVirtualUserCount = JMeterContextService.getThreadCounts().activeThreads;
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

    private double getRate(final long count, final long offset) {
        return (1.0D * count) / (1.0D * offset / 1000);
    }

    private ValueNumber nanSafe(final double value) {
        if (Double.isNaN(value)) {
            return ValueNumber.of(0.0D);
        }
        return ValueNumber.of(value);
    }

}
