package com.tricentis.neoload;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.neotys.nlweb.apis.gateway.benchdefinition.api.definition.request.DefineNewElementsRequest;
import com.neotys.nlweb.apis.gateway.benchdefinition.api.definition.request.ImmutableDefineNewElementsRequest;
import com.neotys.nlweb.bench.definition.common.BenchStatus;
import com.neotys.nlweb.bench.definition.common.FamilyName;
import com.neotys.nlweb.bench.definition.common.model.BenchStatistics;
import com.neotys.nlweb.bench.definition.common.model.ProductVersion;
import com.neotys.nlweb.bench.definition.storage.model.BenchDefinition;
import com.neotys.nlweb.bench.definition.storage.model.BenchDefinitionBuilder;
import com.neotys.nlweb.bench.definition.storage.model.Project;
import com.neotys.nlweb.bench.definition.storage.model.Scenario;
import com.neotys.nlweb.bench.definition.storage.model.element.Element;
import com.neotys.nlweb.bench.definition.storage.model.element.ElementBuilder;
import com.neotys.nlweb.bench.result.im.data.point.ImCounterPoint;
import com.neotys.nlweb.bench.result.raw.api.data.*;
import com.neotys.nlweb.bench.result.raw.api.definition.request.ImmutableStoreRawMappingRequest;
import com.neotys.nlweb.bench.result.raw.api.definition.request.ImmutableStoreRawPointsRequest;
import com.neotys.nlweb.bench.result.raw.api.definition.request.StoreRawMappingRequest;
import com.neotys.nlweb.bench.result.raw.api.definition.request.StoreRawPointsRequest;
import com.neotys.nlweb.bench.result.stm.api.definition.request.StorePointsRequest;
import com.neotys.nlweb.bench.stm.agg.data.point.STMAggPoint;
import com.neotys.nlweb.benchdefinition.api.definition.result.AddBenchStatisticsResult;
import com.neotys.timeseries.data.Point;
import com.neotys.web.data.ValueNumber;
import com.tricentis.neoload.jmx.JMXProjectParser;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.services.FileServer;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Completable;
import rx.Single;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Optional.empty;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * @author lcharlois
 * @since 03/12/2021.
 */
public class NLWebRuntime implements Closeable {

	private static final Logger LOGGER = LoggerFactory.getLogger(NLWebRuntime.class);
	private static final int STM_SAMPLING_INTERVAL_IN_MILLISECONDS = 1000;
	private static final int MONITORS_SAMPLING_INTERVAL_IN_MILLISECONDS = 5000;

	private static final String POM_PATH = "/META-INF/maven/com.tricentis.neoload/jmeter-listener/pom.xml";
	private static final Triple<Integer, Integer, Integer> VERSION_1_0_0 = ImmutableTriple.of(1, 0, 0);
	public static final int BENCH_STATISTICS_SAMPLING_INTERVAL_IN_MILLISECONDS = 1000;
	private final NLWebAPIClient nlWebAPIClient;

	private final NLWebContext nlWebContext;

	private final Set<Element> userPathElements;
	private final Element monitorsRootElement;
	private final String scriptName;
	private final long startDate;

	// Aggregators / Collectors
	private final OverallAggregator aggregator = new OverallAggregator();

	private final ResultsAggregatorManager aggregatorManager = new ResultsAggregatorManager(STM_SAMPLING_INTERVAL_IN_MILLISECONDS);
	private final EventsCollector eventsCollector = new EventsCollector();

	private final Set<String> requestsElements = new HashSet<>();

	private static final int EXECUTOR_SERVCE_CORE_POOL_SIZE = 3;
	private static final String EXECUTOR_SERVCE_NAMING_PATTERN = "jmeter-nlw";
	private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(EXECUTOR_SERVCE_CORE_POOL_SIZE, new BasicThreadFactory.Builder().namingPattern(EXECUTOR_SERVCE_NAMING_PATTERN).build());

	private final AtomicInteger totalCount = new AtomicInteger(0);
	private final AtomicInteger totalOkCount = new AtomicInteger(0);
	private final AtomicInteger totalKoCount = new AtomicInteger(0);


	NLWebRuntime(final BackendListenerContext context) throws IOException {
		nlWebContext = BackendListenerContextToNLWebContext.INSTANCE.apply(context);
		logInfo("NlWebContext:\n" + nlWebContext.toString());
		nlWebAPIClient = new NLWebAPIClient(nlWebContext);
		final FileServer fileServer = FileServer.getFileServer();
		scriptName = fileServer.getScriptName();
		logInfo("ScriptName: " + scriptName);
		final Path jmx = Paths.get(fileServer.getBaseDir(), scriptName);
		logInfo(String.format("Parsing JMX project %s", jmx));
		final Set<String> threadGroups = JMXProjectParser.extractThreadGroups(jmx);
		if (threadGroups.isEmpty()) {
			// If no thread group is detected, then create a single node with the JMX project name
			threadGroups.add(scriptName);
		}
		logInfo(String.format("Thread groups detected: %s", String.join(", ", threadGroups)));
		startDate = System.currentTimeMillis();
		aggregator.start(startDate);
		aggregatorManager.start(startDate);
		eventsCollector.start(startDate);
		userPathElements = threadGroups.stream().map(threadGroup -> ElementBuilder.builder()
				.id(threadGroup)
				.name(threadGroup)
				.familyId(FamilyName.USER_PATH.name())
				.build()).collect(Collectors.toSet());
		monitorsRootElement = ElementBuilder.builder()
				.name("JMeter")
				.id("f20d1600-8c67-47df-8117-e36bb952c15b")
				.familyId(FamilyName.MONITORED_ZONE.name())
				.addChildren(Arrays.stream(Monitor.values()).map(Monitor::toElement).collect(Collectors.toList()))
				.build();
	}


	public void start() {
		nlWebAPIClient.createBench(createBenchDefinition());
		nlWebAPIClient.storeMapping(nlWebContext.getBenchId(), monitorsRootElement);
		nlWebAPIClient.storeBenchStartedData(nlWebContext.getBenchId(), startDate);
		scheduleExecutorServices();
	}

	private void scheduleExecutorServices() {
		executorService.scheduleAtFixedRate(this::updateStatisticsAsync, BENCH_STATISTICS_SAMPLING_INTERVAL_IN_MILLISECONDS, BENCH_STATISTICS_SAMPLING_INTERVAL_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
		executorService.scheduleAtFixedRate(this::updateStmAndRawAsync, STM_SAMPLING_INTERVAL_IN_MILLISECONDS, STM_SAMPLING_INTERVAL_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
		executorService.scheduleAtFixedRate(this::updateEventsAsync, 5000, 5000, TimeUnit.MILLISECONDS);
		executorService.scheduleAtFixedRate(this::sendMonitorsAsync, MONITORS_SAMPLING_INTERVAL_IN_MILLISECONDS, MONITORS_SAMPLING_INTERVAL_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
	}

	@Override
	public void close() {
		try {
			executorService.shutdown();
			final boolean terminated = executorService.awaitTermination(30, TimeUnit.SECONDS);
			logDebug(String.format("Executor service termination status: %b", terminated));
		} catch (InterruptedException e) {
			logError("Error while closing NLWebRuntime", e);
		}
		this.updateStatisticsBlocking();
		this.updateEventsBlocking();
		this.updateStmAndRawBlocking();
		nlWebAPIClient.stopBench(nlWebContext.getBenchId());
	}

	void addSamples(final List<SampleResult> sampleResults) {
		aggregator.addResults(sampleResults);
		aggregatorManager.addResults(sampleResults);
		eventsCollector.add(sampleResults);
	}

	private void updateEventsAsync() {
		updateEvents().subscribe();
	}

	private void updateEventsBlocking() {
		updateEvents().await();
	}

	private Completable updateEvents() {
		return logCompletable(nlWebAPIClient.storeBenchEvents(nlWebContext.getBenchId(), eventsCollector.collect()), "updateEvents done...");
	}

	private void updateStmAndRawAsync() {
		updateStmAndRaw().subscribe();
	}

	private void updateStmAndRawBlocking() {
		updateStmAndRaw().await();
	}

	private Completable updateStmAndRaw() {
		final StatisticsBulk bulk = aggregatorManager.collect();
		final List<BenchElement> newElements = bulk.getNewElements();
		final List<STMAggPoint> points = bulk.getValues()
				.stream()
				.map(BenchElementMapper::toStmPoint)
				.flatMap(List::stream)
				.collect(toList());

		requestsElements.addAll(newElements.stream().filter(e -> e.getKind() == BenchElement.Kind.REQUEST).map(BenchElement::getUuid).collect(Collectors.toSet()));

		totalCount.addAndGet(points.stream().filter(x -> requestsElements.contains(x.getId()) && x.getSuccessAndFailure().isPresent()).mapToInt(p -> p.getSuccessAndFailure().get().getCount()).sum());
		totalOkCount.addAndGet(points.stream().filter(x -> requestsElements.contains(x.getId()) && x.getSuccess().isPresent()).mapToInt(p -> p.getSuccess().get().getCount()).sum());
		totalKoCount.addAndGet(points.stream().filter(x -> requestsElements.contains(x.getId()) && x.getFailure().isPresent()).mapToInt(p -> p.getFailure().get().getCount()).sum());


		final StorePointsRequest storePointsRequest = StorePointsRequest.createRequest(nlWebContext.getBenchId(), points);

		final Completable sendStmPoints = points.isEmpty() ? Completable.complete() : nlWebAPIClient.storeSTMPoints(storePointsRequest);

		final List<RawPoint> rawPoints = bulk.getValues().stream().flatMap(ElementResults::stream).map(this::toRawPoint).collect(toList());
		final StoreRawPointsRequest storeRawPointsRequest = ImmutableStoreRawPointsRequest.builder()
				.benchId(nlWebContext.getBenchId())
				.bucketId(UUID.randomUUID().toString())
				.addAllPoints(rawPoints)
				.build();
		final Completable storeRawPointsCompletable = rawPoints.isEmpty() ? Completable.complete() : nlWebAPIClient.storeRawPoints(storeRawPointsRequest);

		Completable completableStoreMappingAndRawData;
		if (!newElements.isEmpty()) {
			final DefineNewElementsRequest defineNewElementsRequest = buildNewElementsRequest(nlWebContext.getBenchId(), newElements);
			final StoreRawMappingRequest storeRawMappingRequest = buildStoreRawMappingRequest(nlWebContext.getBenchId(), newElements);
			completableStoreMappingAndRawData = Completable.merge(
					nlWebAPIClient.defineNewElements(defineNewElementsRequest),
					nlWebAPIClient.storeRawMapping(storeRawMappingRequest)
			).andThen(Completable.merge(sendStmPoints, storeRawPointsCompletable));
		} else {
			completableStoreMappingAndRawData = Completable.merge(sendStmPoints, storeRawPointsCompletable);
		}
		return logCompletable(completableStoreMappingAndRawData, "storeMappingAndRawData done...");
	}

	private ImmutableDefineNewElementsRequest buildNewElementsRequest(final String benchId, final List<BenchElement> newElements) {
		final Map<String, List<BenchElement>> newElementsPerThreadGroupName = newElements.stream().collect(Collectors.groupingBy(BenchElement::getThreadGroupName));
		final List<Element> elements = newElementsPerThreadGroupName.entrySet().stream().map(entry -> buildNewElement(entry.getKey(), entry.getValue())).collect(toList());
		return ImmutableDefineNewElementsRequest.builder()
				.benchId(benchId)
				.addAllCounters(elements)
				.build();
	}

	private Element buildNewElement(final String threadGroupName, final List<BenchElement> newElements) {
		final Element userPathElement = userPathElements.stream().filter(element -> element.getId().equals(threadGroupName)).findAny()
				.orElseGet(() -> userPathElements.stream().findFirst().get());
		final ElementBuilder userPathElementBuilder = ElementBuilder.builder()
				.id(userPathElement.getId())
				.familyId(userPathElement.getFamilyId());
		userPathElement.getName().ifPresent(userPathElementBuilder::name);
		newElements.stream().map(BenchElementMapper::toNlwElement).forEach(userPathElementBuilder::addChild);
		return userPathElementBuilder.build();

	}

	private static StoreRawMappingRequest buildStoreRawMappingRequest(final String benchId, final List<BenchElement> newElements) {
		final Map<Integer, RawMappingElement> elements = newElements.stream().collect(toMap(BenchElement::getObjectId, BenchElementMapper::toRawMappingElement));
		final RawMapping mapping = ImmutableRawMapping.builder()
				.putAllRawMappingElements(elements)
				.build();
		return ImmutableStoreRawMappingRequest.builder()
				.benchId(benchId)
				.rawMapping(mapping)
				.build();
	}

	private Completable logCompletable(Completable completable, String msgOnSuccess) {
		return completable.doOnCompleted(() -> logDebug(msgOnSuccess)).doOnError(e -> logError("Error on method logCompletable", e));
	}

	private RawPoint toRawPoint(final SampleResult sampleResult) {
		final Point p = Point.of((int) (sampleResult.getStartTime() - startDate), ValueNumber.of(sampleResult.getTime()));
		return ImmutableRawPoint.builder()
				.virtualUserID(1)
				.loadGeneratorID(2)
				.elementID(aggregatorManager.getObjectId(sampleResult))
				.success(sampleResult.isSuccessful())
				.point(p)
				.build();
	}


	private void updateStatisticsAsync() {
		updateStatistics().subscribe();
	}

	private void updateStatisticsBlocking() {
		updateStatistics().toCompletable().await();
	}


	private <T> Single<T> logSingle(Single<T> single, String msgOnSuccess) {
		return single.doOnSuccess(resp -> logDebug(msgOnSuccess)).doOnError(e -> logError("Error on method logSingle", e));
	}


	private Single<AddBenchStatisticsResult> updateStatistics() {
		return logSingle(nlWebAPIClient.addBenchStatistics(nlWebContext.getBenchId(), aggregator.getStats()), "updateStatistics done...");
	}

	private static ProductVersion getVersion() {
		final Triple<Integer, Integer, Integer> pluginVersionDigits = getPluginVersionDigits();
		final String jMeterVersion = getJMeterVersion();
		return ProductVersion.of(
				pluginVersionDigits.getLeft(),
				pluginVersionDigits.getMiddle(),
				pluginVersionDigits.getRight(),
				jMeterVersion);
	}

	private static Triple<Integer, Integer, Integer> getPluginVersionDigits() {
		try {
			final MavenXpp3Reader reader = new MavenXpp3Reader();
			final InputStream is = NLWebRuntime.class.getResourceAsStream(POM_PATH);
			if (is == null) {
				logError(String.format("Error while retrieving plugin version: cannot load pom: %s", POM_PATH));
				return VERSION_1_0_0;
			}
			final Model model = reader.read(new InputStreamReader(is));
			final String[] versionDigits = model.getVersion().split("\\-")[0].split("\\.");
			return ImmutableTriple.of(Integer.parseInt(versionDigits[0]), Integer.parseInt(versionDigits[1]), Integer.parseInt(versionDigits[2]));
		} catch (final Exception e) {
			logError("Error while retrieving plugin version", e);
			return VERSION_1_0_0;
		}
	}


	private static String getJMeterVersion() {
		return "jmeter-" + JMeterUtils.getJMeterVersion();
	}

	private BenchDefinition createBenchDefinition() {
		final ProductVersion pluginVersion = getVersion();
		final BenchDefinitionBuilder benchDefinitionBuilder = BenchDefinitionBuilder.builder()
				.vuhOrDaily(false)
				.aggregationSTMAggPointsInterval(STM_SAMPLING_INTERVAL_IN_MILLISECONDS)
				.benchStatisticsSamplingInterval(BENCH_STATISTICS_SAMPLING_INTERVAL_IN_MILLISECONDS)
				.debug(false)
				.description("Test executed by Apache " + pluginVersion.getBuild() + " with NeoLoad plugin version " + pluginVersion.getMajor() + "." + pluginVersion.getMinor() + "." + pluginVersion.getFix() + ".")
				.duration(0)
				.status(BenchStatus.STARTING)
				.estimateMaxVuCount(0)
				.id(nlWebContext.getBenchId())
				.lgCount(1)
				.name(scriptName)
				.statistics(BenchStatistics.of(ImmutableMap.of()))
				.project(Project.of(scriptName, scriptName))
				.scenario(Scenario.of(scriptName, scriptName, empty()))
				.loadPolicies(ImmutableListMultimap.of())
				.dataSources(BenchElementMapper.toDataSources(userPathElements, monitorsRootElement))
				.nlGuiVersion(pluginVersion)
				.percentilesOnRawData(Optional.of(true))
				.controllerAgentUuid(nlWebContext.getControllerAgentUuid())
				.startedByNlw(nlWebContext.startedByNlw());
		if (nlWebContext.getWorkspaceId() != null && !"".equals(nlWebContext.getWorkspaceId())) {
			benchDefinitionBuilder.groupId(nlWebContext.getWorkspaceId());
		}
		if (nlWebContext.getTestId() != null && !"".equals(nlWebContext.getTestId())) {
			benchDefinitionBuilder.testSettingsId(Optional.of(nlWebContext.getTestId()));
		}
		return benchDefinitionBuilder.build();
	}

	private void sendMonitorsAsync() {
		final List<ImCounterPoint> points = Arrays.stream(Monitor.values())
				.map(m -> m.toImCounterPoint(startDate))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());
		final com.neotys.nlweb.bench.result.im.api.definition.request.StorePointsRequest request = com.neotys.nlweb.bench.result.im.api.definition.request.StorePointsRequest.createStorePointsRequest(nlWebContext.getBenchId(), points);
		logSingle(nlWebAPIClient.storeIMPoints(request), "Stored monitors points [" + points.size() + "/" + Monitor.values().length + "]").subscribe();
	}

	private static void logInfo(final String log) {
		if (LOGGER.isInfoEnabled()) {
			LOGGER.info(log);
		}
	}

	private static void logError(final String log) {
		if (LOGGER.isErrorEnabled()) {
			LOGGER.error(log);
		}
	}

	private static void logError(final String log, final Throwable t) {
		if (LOGGER.isErrorEnabled()) {
			LOGGER.error(log, t);
		}
	}

	private static void logDebug(final String log) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug(log);
		}
	}
}
