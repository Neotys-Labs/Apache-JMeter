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
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
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
    private final NLWebAPIClient nlWebAPIClient;

    // Listener configuration

    private final String workspaceId;
    private final String testId;

    // Main test info
    private final String benchId;
    private final Element userPathElement;
    private final Element monitorsRootElement;
    private final String scriptName;
    private final long startDate;

    // Aggregators / Collectors
    private final OverallAggregator aggregator;
    private final ResultsAggregatorManager aggregatorManager;
    private final EventsCollector eventsCollector;

    private final Set<String> requestsElements = new HashSet<>();

    private final ScheduledExecutorService executorService;
    private AtomicInteger totalCount = new AtomicInteger(0);
    private AtomicInteger totalOkCount = new AtomicInteger(0);
    private AtomicInteger totalKoCount = new AtomicInteger(0);


    NLWebRuntime(final BackendListenerContext context) throws MalformedURLException {
        final String urlStringParameter = context.getParameter(NeoLoadBackendParameters.NEOLOADWEB_API_URL.getName());
        final URL url = new URL(urlStringParameter);
        workspaceId = context.getParameter(NeoLoadBackendParameters.NEOLOADWEB_WORKSPACE_ID.getName());
        testId = context.getParameter(NeoLoadBackendParameters.NEOLOADWEB_TEST_ID.getName());
        final String token = context.getParameter(NeoLoadBackendParameters.NEOLOADWEB_API_TOKEN.getName());
        nlWebAPIClient = new NLWebAPIClient(url.getHost(), url.getPort(), urlStringParameter.startsWith("https://"), url.getPath(), token);
        benchId = UUID.randomUUID().toString();
        aggregator = new OverallAggregator();
        aggregatorManager = new ResultsAggregatorManager(STM_SAMPLING_INTERVAL_IN_MILLISECONDS);
        executorService = Executors.newScheduledThreadPool(3, new BasicThreadFactory.Builder().namingPattern("jmeter-nlw").build());
        eventsCollector = new EventsCollector();
        scriptName = FileServer.getFileServer().getScriptName();
        startDate = System.currentTimeMillis();
        aggregator.start(startDate);
        aggregatorManager.start(startDate);
        eventsCollector.start(startDate);
        userPathElement = ElementBuilder.builder()
                .id(scriptName)
                .name(scriptName)
                .familyId(FamilyName.USER_PATH.name())
                .build();
        monitorsRootElement = ElementBuilder.builder()
                .name("JMeter")
                .id("f20d1600-8c67-47df-8117-e36bb952c15b")
                .familyId(FamilyName.MONITORED_ZONE.name())
                .addChildren(Arrays.stream(Monitor.values()).map(Monitor::toElement).collect(Collectors.toList()))
                .build();
    }


    public void start() {
        nlWebAPIClient.createBench(createBenchDefinition());
        nlWebAPIClient.storeMapping(benchId, monitorsRootElement);
        nlWebAPIClient.storeBenchStartedData(benchId, startDate);
        scheduleExecutorServices();
    }

    private void scheduleExecutorServices() {
        executorService.scheduleAtFixedRate(this::updateStatisticsAsync, 1, 1, TimeUnit.SECONDS);
        executorService.scheduleAtFixedRate(this::updateStmAndRawAsync, STM_SAMPLING_INTERVAL_IN_MILLISECONDS, STM_SAMPLING_INTERVAL_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
        executorService.scheduleAtFixedRate(this::updateEventsAsync, 5000, 5000, TimeUnit.MILLISECONDS);
        executorService.scheduleAtFixedRate(this::sendMonitorsAsync, MONITORS_SAMPLING_INTERVAL_IN_MILLISECONDS, MONITORS_SAMPLING_INTERVAL_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        try {
            executorService.shutdown();
            executorService.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.error("Error while closing NLWebRuntime", e);
        }
        this.updateStatisticsBlocking();
        this.updateEventsBlocking();
        this.updateStmAndRawBlocking();
        nlWebAPIClient.stopBench(benchId);
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
        return logCompletable(nlWebAPIClient.storeBenchEvents(benchId, eventsCollector.collect()), "updateEvents done...");
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
                .map(values -> BenchElementMapper.toStmPoint(scriptName, values))
                .collect(toList());

        requestsElements.addAll(newElements.stream().filter(e -> e.getKind() == BenchElement.Kind.REQUEST).map(BenchElement::getUuid).collect(Collectors.toSet()));

        totalCount.addAndGet(points.stream().filter(x -> requestsElements.contains(x.getId()) && x.getSuccessAndFailure().isPresent())
                .collect(Collectors.summingInt(p -> p.getSuccessAndFailure().get().getCount())));
        totalOkCount.addAndGet(points.stream().filter(x -> requestsElements.contains(x.getId()) && x.getSuccess().isPresent())
                .collect(Collectors.summingInt(p -> p.getSuccess().get().getCount())));
        totalKoCount.addAndGet(points.stream().filter(x -> requestsElements.contains(x.getId()) && x.getFailure().isPresent())
                .collect(Collectors.summingInt(p -> p.getFailure().get().getCount())));


        final StorePointsRequest storePointsRequest = StorePointsRequest.createRequest(benchId, points);

        final Completable sendStmPoints = points.isEmpty() ? Completable.complete() : nlWebAPIClient.storeSTMPoints(storePointsRequest);

        final List<RawPoint> rawPoints = bulk.getValues().stream().flatMap(ElementResults::stream).map(this::toRawPoint).collect(toList());
        final StoreRawPointsRequest storeRawPointsRequest = ImmutableStoreRawPointsRequest.builder()
                .benchId(benchId)
                .bucketId(UUID.randomUUID().toString())
                .addAllPoints(rawPoints)
                .build();
        final Completable storeRawPointsCompletable = rawPoints.isEmpty() ? Completable.complete() : nlWebAPIClient.storeRawPoints(storeRawPointsRequest);

        Completable completableStoreMappingAndRawData;
        if (!newElements.isEmpty()) {
            final DefineNewElementsRequest defineNewElementsRequest = buildNewElementsRequest(benchId, newElements, getUserPathElementBuilder());
            final StoreRawMappingRequest storeRawMappingRequest = buildStoreRawMappingRequest(benchId, newElements);
            completableStoreMappingAndRawData = Completable.merge(
                    nlWebAPIClient.defineNewElements(defineNewElementsRequest),
                    nlWebAPIClient.storeRawMapping(storeRawMappingRequest)
            ).andThen(Completable.merge(sendStmPoints, storeRawPointsCompletable));
        } else {
            completableStoreMappingAndRawData = Completable.merge(sendStmPoints, storeRawPointsCompletable);
        }
        return logCompletable(completableStoreMappingAndRawData, "storeMappingAndRawData done...");
    }

    private ImmutableDefineNewElementsRequest buildNewElementsRequest(final String benchId, final List<BenchElement> newElements, final ElementBuilder userPathElementBuilder) {
        newElements.stream().map(BenchElementMapper::toNlwElement).forEach(userPathElementBuilder::addChild);
        return ImmutableDefineNewElementsRequest.builder()
                .benchId(benchId)
                .addCounters(userPathElementBuilder.build())
                .build();
    }

    private static StoreRawMappingRequest buildStoreRawMappingRequest(final String benchId, final List<BenchElement> newElements) {
        final Map<Integer, RawMappingElement> elements = newElements.stream().collect(toMap(BenchElement::getObjectId, e -> BenchElementMapper.toRawMappingElement(benchId, e)));
        final RawMapping mapping = ImmutableRawMapping.builder()
                .putAllRawMappingElements(elements)
                .build();
        return ImmutableStoreRawMappingRequest.builder()
                .benchId(benchId)
                .rawMapping(mapping)
                .build();
    }

    private Completable logCompletable(Completable completable, String msgOnSuccess) {
        return completable.doOnCompleted(() -> LOGGER.debug(msgOnSuccess)).doOnError(e -> LOGGER.error("Error on method logCompletable", e));
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


    private ElementBuilder getUserPathElementBuilder() {
        final ElementBuilder builder = ElementBuilder.builder()
                .id(userPathElement.getId())
                .familyId(userPathElement.getFamilyId());
        userPathElement.getName().ifPresent(builder::name);
        return builder;

    }


    private void updateStatisticsAsync() {
        updateStatistics().subscribe();
    }

    private void updateStatisticsBlocking() {
        updateStatistics().toCompletable().await();
    }


    private <T> Single<T> logSingle(Single<T> single, String msgOnSuccess) {
        return single.doOnSuccess(resp -> LOGGER.debug(msgOnSuccess)).doOnError(e -> LOGGER.error("Error on method logSingle", e));
    }


    private Single<AddBenchStatisticsResult> updateStatistics() {
        return logSingle(nlWebAPIClient.addBenchStatistics(benchId, aggregator.getStats()), "updateStatistics done...");
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
            final Model model = reader.read(new InputStreamReader(NLWebRuntime.class.getResourceAsStream(POM_PATH)));
            final String[] versionDigits = model.getVersion().split("\\-")[0].split("\\.");
            return ImmutableTriple.of(Integer.parseInt(versionDigits[0]), Integer.parseInt(versionDigits[1]), Integer.parseInt(versionDigits[2]));
        } catch (final Exception e) {
            LOGGER.error("Error while retrieving plugin version", e);
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
                .benchStatisticsSamplingInterval(1)
                .debug(false)
                .description("Test executed by Apache " + pluginVersion.getBuild() + " with NeoLoad plugin version " + pluginVersion.getMajor() + "." + pluginVersion.getMinor() + "." + pluginVersion.getFix() + ".")
                .duration(0)
                .status(BenchStatus.STARTING)
                .estimateMaxVuCount(0)
                .id(benchId)
                .lgCount(1)
                .name(scriptName)
                .statistics(BenchStatistics.of(ImmutableMap.of()))
                .project(Project.of(scriptName, scriptName))
                .scenario(Scenario.of(scriptName, scriptName, empty()))
                .loadPolicies(ImmutableListMultimap.of())
                .dataSources(BenchElementMapper.toDataSources(scriptName, userPathElement, monitorsRootElement))
                .nlGuiVersion(pluginVersion)
                .percentilesOnRawData(Optional.of(true));
        if (workspaceId != null && !"".equals(workspaceId)) {
            benchDefinitionBuilder.groupId(workspaceId);
        }
        if (testId != null && !"".equals(testId)) {
            benchDefinitionBuilder.testSettingsId(Optional.of(testId));
        }
        return benchDefinitionBuilder.build();
    }

    private void sendMonitorsAsync() {
        final List<ImCounterPoint> points = Arrays.stream(Monitor.values())
                .map(m -> m.toImCounterPoint(startDate))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        final com.neotys.nlweb.bench.result.im.api.definition.request.StorePointsRequest request = com.neotys.nlweb.bench.result.im.api.definition.request.StorePointsRequest.createStorePointsRequest(benchId, points);
        logSingle(nlWebAPIClient.storeIMPoints(request), "Stored monitors points [" + points.size() + "/" + Monitor.values().length + "]").subscribe();
    }

}
