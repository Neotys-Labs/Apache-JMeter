package com.tricentis.neoload;

import com.neotys.nlweb.apis.gateway.benchdefinition.api.definition.request.DefineNewElementsRequest;
import com.neotys.nlweb.apis.gateway.benchdefinition.rest.client.BenchDefinitionGatewayApiRestClient;
import com.neotys.nlweb.bench.definition.common.QualityStatus;
import com.neotys.nlweb.bench.definition.common.TerminationReason;
import com.neotys.nlweb.bench.definition.common.model.BenchStatistics;
import com.neotys.nlweb.bench.definition.storage.model.BenchDefinition;
import com.neotys.nlweb.bench.definition.storage.model.element.Element;
import com.neotys.nlweb.bench.event.api.definition.request.StoreBenchEventsRequest;
import com.neotys.nlweb.bench.event.model.BenchEvent;
import com.neotys.nlweb.bench.event.rest.client.BenchResultEventApiRestClient;
import com.neotys.nlweb.bench.result.im.api.definition.request.StoreMappingRequest;
import com.neotys.nlweb.bench.result.im.api.definition.result.StorePointsResult;
import com.neotys.nlweb.bench.result.im.api.restclient.BenchResultImApiRestClient;
import com.neotys.nlweb.bench.result.raw.api.definition.request.StoreRawMappingRequest;
import com.neotys.nlweb.bench.result.raw.api.definition.request.StoreRawPointsRequest;
import com.neotys.nlweb.bench.result.raw.api.restclient.BenchResultRawApiRestClient;
import com.neotys.nlweb.bench.result.stm.api.definition.request.StorePointsRequest;
import com.neotys.nlweb.bench.result.stm.api.restclient.BenchResultStmApiRestClient;
import com.neotys.nlweb.benchdefinition.api.definition.request.*;
import com.neotys.nlweb.benchdefinition.api.definition.result.AddBenchStatisticsResult;
import com.neotys.nlweb.benchdefinition.rest.client.BenchDefinitionApiRestClient;
import com.neotys.nlweb.rest.vertx.client.HttpVertxClient;
import com.neotys.nlweb.rest.vertx.client.HttpVertxClientOptions;
import com.neotys.web.data.ValueNumber;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.threads.JMeterContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static java.util.Optional.empty;

public class NLWebAPIClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(NLWebAPIClient.class);

    private final Vertx vertx;
    private final HttpVertxClient client;
    private final BenchDefinitionApiRestClient benchDefinitionApiRestClient;
    private final BenchResultStmApiRestClient stmApiRestClient;
    private final BenchResultEventApiRestClient eventApiRestClient;
    private final BenchResultRawApiRestClient rawApiRestClient;
    private final BenchDefinitionGatewayApiRestClient benchDefinitionGatewayApiRestClient;
    private final BenchResultImApiRestClient benchResultImApiRestClient;

    public final String token;

    NLWebAPIClient(final NLWebContext nlWebContext) throws MalformedURLException {
        final URL url = new URL(nlWebContext.getApiUrl());
        final boolean useSsl = nlWebContext.getApiUrl().startsWith("https://");
        vertx = Vertx.vertx();
        final HttpVertxClientOptions clientOptions = new HttpVertxClientOptions();
        clientOptions.setUseSSL(useSsl);
        if (useSsl) clientOptions.setTrustAll(true);
        clientOptions.setHost(url.getHost());
        final int defaultedPort = url.getPort() > 0 ? url.getPort() : getDefaultPort(useSsl);
        clientOptions.setPort(defaultedPort);
        clientOptions.setIdleTimeout(60000);
        clientOptions.setConnectTimeout(20000);
        if(nlWebContext.getProxyInfo() != null) {
            clientOptions.setProxyHost(nlWebContext.getProxyInfo().getHost());
            clientOptions.setProxyPort(nlWebContext.getProxyInfo().getPort());
            clientOptions.setProxyUsername(nlWebContext.getProxyInfo().getLogin());
            clientOptions.setProxyPassword(nlWebContext.getProxyInfo().getPassword());
        }
        final String defaultedPath = StringUtils.isEmpty(url.getPath()) ? "/nlweb/rest" : url.getPath() + "/nlweb/rest";
        clientOptions.setBaseURL(defaultedPath);
        clientOptions.setProductTag("JMETER");
        client = HttpVertxClient.build(vertx, clientOptions);
        benchDefinitionApiRestClient = BenchDefinitionApiRestClient.of(client);
        stmApiRestClient = BenchResultStmApiRestClient.of(client);
        eventApiRestClient = BenchResultEventApiRestClient.of(client);
        rawApiRestClient = BenchResultRawApiRestClient.of(client);
        benchDefinitionGatewayApiRestClient = BenchDefinitionGatewayApiRestClient.of(client);
        benchResultImApiRestClient = BenchResultImApiRestClient.of(client);
        this.token = nlWebContext.getApiToken();
    }

    private static int getDefaultPort(final boolean useSsl) {
        return useSsl ? 443 : 80;
    }

    public void createBench(final BenchDefinition benchDefinition) {
        LOGGER.debug("Create bench");
        try {
            benchDefinitionApiRestClient.createBench(token, CreateBenchRequest.createRequest(benchDefinition))
                .retry(1)
                .ignoreElement()
                .blockingAwait();
        } catch (Throwable benchDefinitionError) {
            LOGGER.error("Error while sending bench definition", benchDefinitionError);
            throw new RuntimeException(benchDefinitionError);
        }
    }

    public void storeMapping(final String benchId, final Element monitorsRootElement) {
        LOGGER.debug("Store IM mapping");
        try {
            benchResultImApiRestClient.storeMapping(token, StoreMappingRequest.of(benchId, Monitor.getCountersByGroupId(monitorsRootElement.getId())))
                .retry(1)
                .ignoreElement()
                .blockingAwait();
        } catch (Throwable storeMappingError) {
            LOGGER.error("Error while sending IM Mapping", storeMappingError);
            throw new RuntimeException(storeMappingError);
        }
    }

    public void storeBenchStartedData(final String benchId, final long startDate) {
        LOGGER.debug("Store bench started data");
        long preStartDate = JMeterContextService.getTestStartTime();
        try {
            benchDefinitionApiRestClient
                .storeBenchStartedData(token, StoreBenchStartedDataRequest.createRequest(benchId, preStartDate, startDate, empty()))
                .doOnSuccess(result -> LOGGER.debug("Start data done..."))
                .ignoreElement()
                .blockingAwait();
        } catch (Throwable storeBenchStartedDataError) {
            LOGGER.error("Error while storing Bench Started Data", storeBenchStartedDataError);
            throw new RuntimeException(storeBenchStartedDataError);
        }
    }

    public void stopBench(final String benchId) {
        final long stopTime = System.currentTimeMillis();
        LOGGER.debug("Setting Quality status");
        benchDefinitionApiRestClient.storeBenchPostProcessedData(token, StoreBenchPostProcessedDataRequest.createRequest(benchId, QualityStatus.PASSED))
                .blockingGet();
        LOGGER.debug("End test");
        benchDefinitionApiRestClient.storeBenchEndedData(token, StoreBenchEndedDataRequest.createRequest(benchId, stopTime, TerminationReason.POLICY)).blockingGet();
        LOGGER.debug("Close client");
        client.close();
        LOGGER.debug("Close vertx");
        vertx.close();
    }

    public Completable storeBenchEvents(final String benchId, final List<BenchEvent> events) {
        return eventApiRestClient.storeBenchEvents(token, StoreBenchEventsRequest.createStoreRequest(benchId, events));
    }

    public Completable storeSTMPoints(final StorePointsRequest storePointsRequest) {
        return stmApiRestClient.storePoints(token, storePointsRequest, 1);
    }

    public Completable storeRawPoints(final StoreRawPointsRequest storeRawPointsRequest) {
        return rawApiRestClient.storeRawPoints(token, storeRawPointsRequest).ignoreElement();
    }

    public Single<StorePointsResult> storeIMPoints(com.neotys.nlweb.bench.result.im.api.definition.request.StorePointsRequest request) {
        return benchResultImApiRestClient.storePoints(token, request).retry(1);
    }

    public Single<AddBenchStatisticsResult> addBenchStatistics(final String benchId, final Map<BenchStatistics.Stat, ValueNumber> stats) {
        return benchDefinitionApiRestClient.addBenchStatistics(token, AddBenchStatisticsRequest.createRequest(benchId, BenchStatistics.of(stats)));
    }

    public Completable defineNewElements(final DefineNewElementsRequest defineNewElementsRequest) {
        return benchDefinitionGatewayApiRestClient.defineNewElements(token, defineNewElementsRequest).ignoreElement();
    }

    public Completable storeRawMapping(final StoreRawMappingRequest storeRawMappingRequest) {
        return rawApiRestClient.storeRawMapping(token, storeRawMappingRequest).ignoreElement();
    }
}
