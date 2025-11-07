package com.tricentis.neoload;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Predicates.not;

/**
 * @author lcharlois
 * @since 02/12/2021.
 */
public class NeoLoadBackend extends AbstractBackendListenerClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(NeoLoadBackend.class);
	private NLWebRuntime nlwebRuntime;
	private int totalCount = 0;
	private long totalOk = 0;
	private long totalKo = 0;
	ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

	@Override
	public void setupTest(final BackendListenerContext context) throws Exception {
		try {
			LOGGER.info("NeoLoad Backend Listener setupTest");
			neoloadSetup(context);
			super.setupTest(context);
		} catch (final Exception e) {
			LOGGER.error("Error during setupTest", e);
			throw e;
		}
	}

	private void neoloadSetup(final BackendListenerContext context) throws Exception {
		nlwebRuntime = new NLWebRuntime(context);
		nlwebRuntime.start();
		executor.scheduleAtFixedRate(this::logCounters, 0, 1, TimeUnit.SECONDS);
	}

	private void logCounters() {
		if (LOGGER.isDebugEnabled())
			LOGGER.debug(String.format("Received requests from JMeter : Total %d | %d OK | %d KO", totalCount, totalOk, totalKo));
	}

	@Override
	public void handleSampleResults(final List<SampleResult> list, final BackendListenerContext backendListenerContext) {
		try {
			final List<SampleResult> effectiveList = expandList(list);
			calcCounts(effectiveList);
			nlwebRuntime.addSamples(effectiveList);
		} catch(final Exception e) {
			LOGGER.error("Error during handleSampleResults", e);
			throw e;
		}
	}


	private List<SampleResult> expandList(List<SampleResult> list) {
		return Stream.concat(
				list.stream(),
				extractRequestsInTransaction(list).stream()
		).collect(Collectors.toList());
	}

	private List<SampleResult> extractRequestsInTransaction(List<SampleResult> list) {
		return list.stream().flatMap(x -> Arrays.stream(x.getSubResults())).collect(Collectors.toList());
	}

	private void calcCounts(List<SampleResult> sampleList) {
		final List<SampleResult> requests = sampleList.stream()
				.filter(not(TransactionController::isFromTransactionController)).collect(Collectors.toList());
		totalCount += requests.size();
		totalOk += requests.stream().filter(SampleResult::isSuccessful).count();
		totalKo += requests.stream().filter(not(SampleResult::isSuccessful)).count();
	}


	@Override
	public void teardownTest(final BackendListenerContext context) throws Exception {
		try {
			executor.awaitTermination(5, TimeUnit.SECONDS);
			logCounters();
			nlwebRuntime.close();
			executor.shutdown();
			nlwebRuntime = null;
		} catch (final Exception e) {
			LOGGER.error("Error during teardown", e);
			throw e;
		} finally {
			super.teardownTest(context);
		}
	}

	@Override
	public Arguments getDefaultParameters() {
		return new Arguments();
	}
}
