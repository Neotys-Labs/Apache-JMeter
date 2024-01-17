package com.tricentis.neoload;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.neotys.nlweb.apis.gateway.benchdefinition.api.definition.request.ImmutableDefineNewElementsRequest;
import com.neotys.nlweb.apis.gateway.benchdefinition.rest.client.BenchDefinitionGatewayApiRestClient;
import com.neotys.nlweb.bench.definition.common.*;
import com.neotys.nlweb.bench.definition.common.model.BenchStatistics;
import com.neotys.nlweb.bench.definition.common.model.ProductVersion;
import com.neotys.nlweb.bench.definition.storage.model.*;
import com.neotys.nlweb.bench.definition.storage.model.element.Element;
import com.neotys.nlweb.bench.definition.storage.model.element.ElementBuilder;
import com.neotys.nlweb.bench.event.api.definition.request.StoreBenchEventsRequest;
import com.neotys.nlweb.bench.event.model.BenchEvent;
import com.neotys.nlweb.bench.event.rest.client.BenchResultEventApiRestClient;
import com.neotys.nlweb.bench.result.im.api.definition.request.StoreMappingRequest;
import com.neotys.nlweb.bench.result.im.api.restclient.BenchResultImApiRestClient;
import com.neotys.nlweb.bench.result.im.data.point.ImCounterPoint;
import com.neotys.nlweb.bench.result.raw.api.data.*;
import com.neotys.nlweb.bench.result.raw.api.definition.request.ImmutableStoreRawMappingRequest;
import com.neotys.nlweb.bench.result.raw.api.definition.request.ImmutableStoreRawPointsRequest;
import com.neotys.nlweb.bench.result.raw.api.definition.request.StoreRawMappingRequest;
import com.neotys.nlweb.bench.result.raw.api.definition.request.StoreRawPointsRequest;
import com.neotys.nlweb.bench.result.raw.api.restclient.BenchResultRawApiRestClient;
import com.neotys.nlweb.bench.result.stm.api.definition.request.StorePointsRequest;
import com.neotys.nlweb.bench.result.stm.api.restclient.BenchResultStmApiRestClient;
import com.neotys.nlweb.bench.stm.agg.data.point.STMAggPoint;
import com.neotys.nlweb.bench.stm.agg.data.point.STMAggPointBuilder;
import com.neotys.nlweb.bench.stm.agg.data.point.STMAggPointStat;
import com.neotys.nlweb.bench.stm.agg.data.point.STMAggPointStatBuilder;
import com.neotys.nlweb.benchdefinition.api.definition.request.*;
import com.neotys.nlweb.benchdefinition.api.definition.result.AddBenchStatisticsResult;
import com.neotys.nlweb.benchdefinition.rest.client.BenchDefinitionApiRestClient;
import com.neotys.nlweb.rest.vertx.client.HttpVertxClient;
import com.neotys.nlweb.rest.vertx.client.HttpVertxClientOptions;
import com.neotys.timeseries.data.Point;
import com.neotys.web.data.ValueNumber;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.services.FileServer;
import org.apache.jmeter.threads.JMeterContextService;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
    private static final String DEFAULT_ZONE_OR_POPULATION = "default";
    private static final String POM_PATH = "/META-INF/maven/com.tricentis.neoload/jmeter-listener/pom.xml";
    private static final Triple<Integer, Integer, Integer> VERSION_1_0_0 = ImmutableTriple.of(1, 0, 0);
    private static final AtomicLong EMITTER_ID = new AtomicLong();

    // Rest clients
    private final Vertx vertx;
    private final HttpVertxClient client;
    private final BenchDefinitionApiRestClient benchDefinitionApiRestClient;
    private final BenchResultStmApiRestClient stmApiRestClient;
    private final BenchResultEventApiRestClient eventApiRestClient;
    private final BenchResultRawApiRestClient rawApiRestClient;
    private final BenchDefinitionGatewayApiRestClient benchDefinitionGatewayApiRestClient;
    private final BenchResultImApiRestClient benchResultImApiRestClient;

    // Listener configuration
    public final String token;
    private final String host;
    private final int port;
    private final boolean useSsl;
    private final String path;
    private final String workspaceId;
    private final String testId;

    // Main test info
    private final String benchId;
    private Element rootElement;
    private Element monitorsRootElement;
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
        host = url.getHost();
        port = url.getPort();
        useSsl = urlStringParameter.startsWith("https://");
        token = context.getParameter(NeoLoadBackendParameters.NEOLOADWEB_API_TOKEN.getName());
        workspaceId = context.getParameter(NeoLoadBackendParameters.NEOLOADWEB_WORKSPACE_ID.getName());
        testId = context.getParameter(NeoLoadBackendParameters.NEOLOADWEB_TEST_ID.getName());
        path = url.getPath();
        vertx = Vertx.vertx();
        client = HttpVertxClient.build(vertx, createClientOptions());
        benchDefinitionApiRestClient = BenchDefinitionApiRestClient.of(client);
        stmApiRestClient = BenchResultStmApiRestClient.of(client);
        eventApiRestClient = BenchResultEventApiRestClient.of(client);
        rawApiRestClient = BenchResultRawApiRestClient.of(client);
        benchDefinitionGatewayApiRestClient = BenchDefinitionGatewayApiRestClient.of(client);
        benchResultImApiRestClient = BenchResultImApiRestClient.of(client);
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
    }


    public void start() {
        createBench();
        storeIMMapping();
        storeBenchStartedData();
        scheduleExecutorServices();
    }

    private void createBench() {
        LOGGER.debug("Create bench");
        final BenchDefinition benchDefinition = createBenchDefinition();
        final Throwable benchDefinitionError = benchDefinitionApiRestClient.createBench(token, CreateBenchRequest.createRequest(benchDefinition))
                .retry(1)
                .toCompletable()
                .get();
        if (benchDefinitionError != null) {
            LOGGER.error("Error while sending bench definition", benchDefinitionError);
            throw new RuntimeException(benchDefinitionError);
        }
    }

    private void storeIMMapping() {
        LOGGER.debug("Store IM mapping");
        final Throwable storeMappingError = benchResultImApiRestClient.storeMapping(token, StoreMappingRequest.of(benchId, Monitor.getCountersByGroupId(monitorsRootElement.getId())))
                .retry(1)
                .toCompletable()
                .get();
        if (storeMappingError != null) {
            LOGGER.error("Error while sending IM Mapping", storeMappingError);
            throw new RuntimeException(storeMappingError);
        }
    }

    private void storeBenchStartedData() {
        LOGGER.debug("Store bench started data");
        long preStartDate = JMeterContextService.getTestStartTime();
        final Throwable storeBenchStartedDataError = benchDefinitionApiRestClient.storeBenchStartedData(token, StoreBenchStartedDataRequest.createRequest(benchId, preStartDate, startDate, empty()))
                .doOnSuccess(result -> LOGGER.debug("Start data done...")).toCompletable().get();
        if (storeBenchStartedDataError != null) {
            LOGGER.error("Error while storing Bench Started Data", storeBenchStartedDataError);
            throw new RuntimeException(storeBenchStartedDataError);
        }
    }

    private void scheduleExecutorServices() {
        executorService.scheduleAtFixedRate(this::updateStatisticsAsync, 1, 1, TimeUnit.SECONDS);
        executorService.scheduleAtFixedRate(this::updateStmAndRawAsync, STM_SAMPLING_INTERVAL_IN_MILLISECONDS, STM_SAMPLING_INTERVAL_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
        executorService.scheduleAtFixedRate(this::updateEventsAsync, 5000, 5000, TimeUnit.MILLISECONDS);
        executorService.scheduleAtFixedRate(this::sendMonitorsAsync, MONITORS_SAMPLING_INTERVAL_IN_MILLISECONDS, MONITORS_SAMPLING_INTERVAL_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        final long stopTime = System.currentTimeMillis();
        try {
            executorService.shutdown();
            executorService.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.error("Error while closing NLWebRuntime", e);
        }
        this.updateStatisticsBlocking();
        this.updateEventsBlocking();
        this.updateStmAndRawBlocking();
        LOGGER.debug("Setting Quality status");
        benchDefinitionApiRestClient.storeBenchPostProcessedData(token, StoreBenchPostProcessedDataRequest.createRequest(benchId, QualityStatus.PASSED))
                .toBlocking().value();
        LOGGER.debug("End test");
        benchDefinitionApiRestClient.storeBenchEndedData(token, StoreBenchEndedDataRequest.createRequest(benchId, stopTime, TerminationReason.POLICY)).toBlocking().value();
        LOGGER.debug("Close client");
        client.close();
        LOGGER.debug("Close vertx");
        vertx.close();
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
        final List<BenchEvent> events = eventsCollector.collect();
        return logCompletable(eventApiRestClient.storeBenchEvents(token, StoreBenchEventsRequest.createStoreRequest(benchId, events)), "updateEvents done...");
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
                .map(this::toStmPoint)
                .collect(toList());

        requestsElements.addAll(newElements.stream().filter(e -> e.getKind() == BenchElement.Kind.REQUEST).map(BenchElement::getUuid).collect(Collectors.toSet()));

        totalCount.addAndGet(points.stream().filter(x -> requestsElements.contains(x.getId()) && x.getSuccessAndFailure().isPresent())
                .collect(Collectors.summingInt(p -> p.getSuccessAndFailure().get().getCount())));
        totalOkCount.addAndGet(points.stream().filter(x -> requestsElements.contains(x.getId()) && x.getSuccess().isPresent())
                .collect(Collectors.summingInt(p -> p.getSuccess().get().getCount())));
        totalKoCount.addAndGet(points.stream().filter(x -> requestsElements.contains(x.getId()) && x.getFailure().isPresent())
                .collect(Collectors.summingInt(p -> p.getFailure().get().getCount())));


        final StorePointsRequest storePointsRequest = StorePointsRequest.createRequest(benchId, points);

        final Completable sendStmPoints = points.isEmpty() ? Completable.complete() : stmApiRestClient.storePoints(token, storePointsRequest, 1);

        final List<RawPoint> rawPoints = bulk.getValues().stream().flatMap(ElementResults::stream).map(this::toRawPoint).collect(toList());
        final StoreRawPointsRequest storeRawPointsRequest = ImmutableStoreRawPointsRequest.builder()
                .benchId(benchId)
                .bucketId(UUID.randomUUID().toString())
                .addAllPoints(rawPoints)
                .build();
        final Completable storeRawPointsCompletable = rawPoints.isEmpty() ? Completable.complete() : rawApiRestClient.storeRawPoints(token, storeRawPointsRequest).toCompletable();

        Completable completableStoreMappingAndRawData;
        if (!newElements.isEmpty()) {
            final ImmutableDefineNewElementsRequest defineNewElementsRequest = buildNewElementsRequest(newElements);
            final StoreRawMappingRequest storeRawMappingRequest = buildStoreRawMappingRequest(newElements);
            completableStoreMappingAndRawData = Completable.merge(
                    benchDefinitionGatewayApiRestClient.defineNewElements(token, defineNewElementsRequest).toCompletable(),
                    rawApiRestClient.storeRawMapping(token, storeRawMappingRequest).toCompletable()
            ).andThen(Completable.merge(sendStmPoints, storeRawPointsCompletable));
        } else {
            completableStoreMappingAndRawData = Completable.merge(sendStmPoints, storeRawPointsCompletable);
        }
        return logCompletable(completableStoreMappingAndRawData, "storeMappingAndRawData done...");
    }

    private Completable logCompletable(Completable completable, String msgOnSuccess) {
        return completable.doOnCompleted(() -> LOGGER.debug(msgOnSuccess)).doOnError(e -> LOGGER.error("Error on method logCompletable", e));
    }


    private StoreRawMappingRequest buildStoreRawMappingRequest(final List<BenchElement> newElements) {
        final Map<Integer, RawMappingElement> elements = newElements.stream().collect(toMap(BenchElement::getObjectId, this::toRawMappingElement));
        final RawMapping mapping = ImmutableRawMapping.builder()
                .putAllRawMappingElements(elements)
                .build();
        return ImmutableStoreRawMappingRequest.builder()
                .benchId(benchId)
                .rawMapping(mapping)
                .build();
    }

    private RawMappingElement toRawMappingElement(final BenchElement element) {
        return ImmutableRawMappingElement.builder()
                .populationId(DEFAULT_ZONE_OR_POPULATION)
                .userPathId(scriptName)
                .zoneId(DEFAULT_ZONE_OR_POPULATION)
                .elementId(element.getUuid())
                .parentId(scriptName)
                .build();
    }

    private ImmutableDefineNewElementsRequest buildNewElementsRequest(final List<BenchElement> newElements) {
        ElementBuilder rootBuilder = getRootBuilder();
        newElements.stream().map(this::toNlwElement).forEach(rootBuilder::addChild);
        final Element element = rootBuilder.build();
        return ImmutableDefineNewElementsRequest.builder()
                .benchId(benchId)
                .addCounters(element)
                .build();
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

    private STMAggPoint toStmPoint(final ElementResults values) {
        final Map<Boolean, List<SampleResult>> successErrorResults = values.getResults()
                .stream()
                .collect(Collectors.groupingBy(SampleResult::isSuccessful));

        final Optional<StmElementComputer> ok = StmElementComputer.fromResults(successErrorResults.get(true));
        final Optional<StmElementComputer> ko = StmElementComputer.fromResults(successErrorResults.get(false));
        final int offset = (int) values.getOffset();
        return STMAggPointBuilder.newPointBuilder()
                .id(values.getUuid())
                .emitterId((int) EMITTER_ID.incrementAndGet() % 1024)
                .failure(toSTMAggPointStat(ko))
                .success(toSTMAggPointStat(ok))
                .populationId(DEFAULT_ZONE_OR_POPULATION)
                .userPathId(scriptName)
                .zoneId(DEFAULT_ZONE_OR_POPULATION)
                .timeOffset(offset)
                .build();
    }

    private Optional<STMAggPointStat> toSTMAggPointStat(final Optional<StmElementComputer> optionalResults) {
        return optionalResults.map(results -> STMAggPointStatBuilder.newStatBuilder()
                .count((int) results.count)
                .sumDuration(results.sumDuration)
                .maxDuration((int) results.maxDuration)
                .minDuration((int) results.minDuration)
                .sumTTFB(results.sumTtfb)
                .maxTTFB((int) results.maxTtfb)
                .minTTFB((int) results.minTtfb)
                .sumDownloadedBytes(results.sumDownloadedBytes)
                .build());
    }

    private ElementBuilder getRootBuilder() {
        final ElementBuilder builder = ElementBuilder.builder()
                .id(rootElement.getId())
                .familyId(rootElement.getFamilyId());
        rootElement.getName().ifPresent(builder::name);
        return builder;

    }

    private Element toNlwElement(final BenchElement stmElement) {
        return ElementBuilder.builder()
                .id(stmElement.getUuid())
                .name(stmElement.getName())
                .familyId(getFamilyId(stmElement))
                .build();
    }

    private String getFamilyId(final BenchElement stmElement) {
        switch (stmElement.getKind()) {
            case REQUEST:
                return FamilyName.REQUEST.name();
            case TRANSACTION:
                return FamilyName.TRANSACTION.name();
            default:
                throw new IllegalStateException("Unknown kind " + stmElement.getKind());
        }
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
        final Map<BenchStatistics.Stat, ValueNumber> stats = aggregator.getStats();
        final BenchStatistics benchStatistics = BenchStatistics.of(stats);
        return logSingle(benchDefinitionApiRestClient.addBenchStatistics(token, AddBenchStatisticsRequest.createRequest(benchId, benchStatistics)), "updateStatistics done...");
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
        final DataSource dataSource = buildDataSource(scriptName);
        final String scriptUuid = scriptName;
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
                .project(Project.of(scriptUuid, scriptName))
                .scenario(Scenario.of(scriptUuid, scriptName, empty()))
                .loadPolicies(ImmutableListMultimap.of())
                .dataSources(ImmutableList.of(dataSource, buildMonitorsDataSource()))
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

    private DataSource buildDataSource(final String scriptName) {
        final List<Context> zoneContexts = ImmutableList.of(Context.of(DEFAULT_ZONE_OR_POPULATION));
        final ContextRoot crZones = ContextRoot.of(ContextRoot.ZONES_ID, zoneContexts);
        final List<Context> populationContexts = ImmutableList.of(Context.of(DEFAULT_ZONE_OR_POPULATION));
        final ContextRoot crPopulations = ContextRoot.of(ContextRoot.POPULATIONS_ID, populationContexts);
        final List<Context> vuContexts = ImmutableList.of(Context.of(scriptName));
        final ContextRoot crVus = ContextRoot.of(ContextRoot.USER_PATHS_ID, vuContexts);
        final List<ContextRoot> contextRoots = Arrays.asList(crZones, crPopulations, crVus);
        //Keep it we'll need this later
        rootElement = ElementBuilder.builder()
                .id(scriptName)
                .name(scriptName)
                .familyId(FamilyName.USER_PATH.name())
                .build();

        final List<GroupFilter> filters = ImmutableList.of(GroupFilter.requestsGroupFilter(emptyList()), GroupFilter.transactionsGroupFilter(emptyList()));

        final DataSourceEntry dataSourceEntry = DataSourceEntry.withRootContextId(scriptName, rootElement, filters);
        final List<DataSourceEntry> dataSourceEntries = ImmutableList.of(dataSourceEntry);


        final List<Family> families = ImmutableList.of(Families.DEFAULT_USER_PATH, Families.DEFAULT_TRANSACTION, Families.DEFAULT_REQUEST);
        final List<Statistic> statistics = getStatistics(families);
        return DataSource.of(DataSourceId.USER_PATHS.toString(), dataSourceEntries, filters, statistics, contextRoots, families);
    }

    private DataSource buildMonitorsDataSource() {
        final List<ContextRoot> contextRoots = emptyList();
        final List<GroupFilter> groupFilters = singletonList(GroupFilter.of("ID", singletonList("a"), emptyList(), emptyList()));
        final List<Family> families = ImmutableList.of(Families.DEFAULT_MONITORED_ZONE, Families.DEFAULT_MONITOR);
        final List<Statistic> statistics = emptyList();
        monitorsRootElement = ElementBuilder.builder()
                .name("JMeter")
                .id("f20d1600-8c67-47df-8117-e36bb952c15b")
                .familyId(FamilyName.MONITORED_ZONE.name())
                .addChildren(Arrays.stream(Monitor.values()).map(Monitor::toElement).collect(Collectors.toList()))
                .build();
        return DataSource.of(DataSourceId.MONITORS.toString(), ImmutableList.of(DataSourceEntry.withoutRootContextId(monitorsRootElement, groupFilters)), groupFilters, statistics, contextRoots, families);
    }

    private List<Statistic> getStatistics(final Collection<Family> families) {
        final List<String> statIds = families
                .stream()
                .flatMap(family -> family.getStatisticIds().stream())
                .distinct()
                .collect(toList());
        return Statistics.defaultForNames(statIds);
    }

    private HttpVertxClientOptions createClientOptions() {
        final HttpVertxClientOptions clientOptions = new HttpVertxClientOptions();
        clientOptions.setUseSSL(useSsl);
        if (useSsl) clientOptions.setTrustAll(true);
        clientOptions.setHost(host);
        final int defaultedPort = this.port > 0 ? this.port : getDefaultPort();
        clientOptions.setPort(defaultedPort);
        clientOptions.setIdleTimeout(60000);
        clientOptions.setConnectTimeout(20000);
        final String defaultedPath = StringUtils.isEmpty(this.path) ? "/nlweb/rest" : this.path + "/nlweb/rest";
        clientOptions.setBaseURL(defaultedPath);
        clientOptions.setProductTag("JMETER");
        return clientOptions;
    }

    private int getDefaultPort() {
        return useSsl ? 443 : 80;
    }

    private void sendMonitorsAsync() {
        final List<ImCounterPoint> points = Arrays.stream(Monitor.values())
                .map(m -> m.toImCounterPoint(startDate))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        final com.neotys.nlweb.bench.result.im.api.definition.request.StorePointsRequest request = com.neotys.nlweb.bench.result.im.api.definition.request.StorePointsRequest.createStorePointsRequest(benchId, points);
        logSingle(benchResultImApiRestClient.storePoints(token, request).retry(1), "Stored monitors points [" + points.size() + "/" + Monitor.values().length + "]").subscribe();
    }

}
