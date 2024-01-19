package com.tricentis.neoload;

import com.google.common.collect.ImmutableList;
import com.neotys.nlweb.bench.definition.common.*;
import com.neotys.nlweb.bench.definition.storage.model.*;
import com.neotys.nlweb.bench.definition.storage.model.element.Element;
import com.neotys.nlweb.bench.definition.storage.model.element.ElementBuilder;
import com.neotys.nlweb.bench.result.raw.api.data.ImmutableRawMappingElement;
import com.neotys.nlweb.bench.result.raw.api.data.RawMappingElement;
import com.neotys.nlweb.bench.stm.agg.data.point.STMAggPoint;
import com.neotys.nlweb.bench.stm.agg.data.point.STMAggPointBuilder;
import com.neotys.nlweb.bench.stm.agg.data.point.STMAggPointStat;
import com.neotys.nlweb.bench.stm.agg.data.point.STMAggPointStatBuilder;
import org.apache.jmeter.samplers.SampleResult;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class Mapper {

    private static final String DEFAULT_ZONE_OR_POPULATION = "default";
    private static final AtomicLong EMITTER_ID = new AtomicLong();


    public static RawMappingElement toRawMappingElement(final String scriptName, final BenchElement element) {
        return ImmutableRawMappingElement.builder()
                .populationId(DEFAULT_ZONE_OR_POPULATION)
                .userPathId(scriptName)
                .zoneId(DEFAULT_ZONE_OR_POPULATION)
                .elementId(element.getUuid())
                .parentId(scriptName)
                .build();
    }

    public static Element toNlwElement(final BenchElement stmElement) {
        return ElementBuilder.builder()
                .id(stmElement.getUuid())
                .name(stmElement.getName())
                .familyId(getFamilyId(stmElement))
                .build();
    }

    public static STMAggPoint toStmPoint(final String scriptName, final ElementResults values) {
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

    private static Optional<STMAggPointStat> toSTMAggPointStat(final Optional<StmElementComputer> optionalResults) {
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

    private static String getFamilyId(final BenchElement stmElement) {
        switch (stmElement.getKind()) {
            case REQUEST:
                return FamilyName.REQUEST.name();
            case TRANSACTION:
                return FamilyName.TRANSACTION.name();
            default:
                throw new IllegalStateException("Unknown kind " + stmElement.getKind());
        }
    }

    public static List<DataSource> toDataSources(final String scriptName, final Element rootElement, final Element monitorsRootElement) {
        return ImmutableList.of(Mapper.toUserPathsDataSource(rootElement, scriptName), toMonitorsDataSource(monitorsRootElement));
    }

    private static DataSource toUserPathsDataSource(final Element rootElement, final String scriptName) {
        final List<Context> zoneContexts = ImmutableList.of(Context.of(DEFAULT_ZONE_OR_POPULATION));
        final ContextRoot crZones = ContextRoot.of(ContextRoot.ZONES_ID, zoneContexts);
        final List<Context> populationContexts = ImmutableList.of(Context.of(DEFAULT_ZONE_OR_POPULATION));
        final ContextRoot crPopulations = ContextRoot.of(ContextRoot.POPULATIONS_ID, populationContexts);
        final List<Context> vuContexts = ImmutableList.of(Context.of(scriptName));
        final ContextRoot crVus = ContextRoot.of(ContextRoot.USER_PATHS_ID, vuContexts);
        final List<ContextRoot> contextRoots = Arrays.asList(crZones, crPopulations, crVus);


        final List<GroupFilter> filters = ImmutableList.of(GroupFilter.requestsGroupFilter(emptyList()), GroupFilter.transactionsGroupFilter(emptyList()));

        final DataSourceEntry dataSourceEntry = DataSourceEntry.withRootContextId(scriptName, rootElement, filters);
        final List<DataSourceEntry> dataSourceEntries = ImmutableList.of(dataSourceEntry);


        final List<Family> families = ImmutableList.of(Families.DEFAULT_USER_PATH, Families.DEFAULT_TRANSACTION, Families.DEFAULT_REQUEST);
        final List<Statistic> statistics = getStatistics(families);
        return DataSource.of(DataSourceId.USER_PATHS.toString(), dataSourceEntries, filters, statistics, contextRoots, families);
    }

    private static DataSource toMonitorsDataSource(final Element monitorsRootElement) {
        final List<ContextRoot> contextRoots = emptyList();
        final List<GroupFilter> groupFilters = singletonList(GroupFilter.of("ID", singletonList("a"), emptyList(), emptyList()));
        final List<Family> families = ImmutableList.of(Families.DEFAULT_MONITORED_ZONE, Families.DEFAULT_MONITOR);
        final List<Statistic> statistics = emptyList();

        return DataSource.of(DataSourceId.MONITORS.toString(), ImmutableList.of(DataSourceEntry.withoutRootContextId(monitorsRootElement, groupFilters)), groupFilters, statistics, contextRoots, families);
    }

    private static List<Statistic> getStatistics(final Collection<Family> families) {
        final List<String> statIds = families
                .stream()
                .flatMap(family -> family.getStatisticIds().stream())
                .distinct()
                .collect(toList());
        return Statistics.defaultForNames(statIds);
    }

}
