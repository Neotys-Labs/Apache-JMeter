package com.tricentis.neoload;

import com.neotys.nlweb.bench.event.model.*;
import com.neotys.web.data.ValueNumber;
import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.samplers.SampleResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.neotys.nlweb.bench.event.model.AssertionResult.createAssertionResult;
import static com.tricentis.neoload.ThreadGroupNameCache.getThreadGroupName;

/**
 * @author lcharlois
 * @since 16/12/2021.
 */
class EventsCollector {

    private final LinkedBlockingQueue<BenchEvent> events = new LinkedBlockingQueue<>();
    private final AtomicInteger eventId = new AtomicInteger();
    private long startTime;

    void start(final long startTime) {
        this.startTime = startTime;
    }

    void add(final List<SampleResult> results) {
        results.stream().filter(result -> !result.isSuccessful())
                .filter(result -> !TransactionController.isFromTransactionController(result))
                .map(this::toNlWebEvents)
                .forEach(events::add);
    }

    List<BenchEvent> collect() {
        List<BenchEvent> copy = new ArrayList<>();
        events.drainTo(copy);
        return copy;
    }

    /**
     * Variable fullName can be replaced by first line of result.getSamplerData(): HTTP Method+' '+URL
     * Variable source can be replaced by result.getThreadName() that is Thread Group name + ' ' + thread group number + " " + vu number
     */
    private BenchEvent toNlWebEvents(final SampleResult result) {
        final String sampleLabel = result.getSampleLabel();
        final int id = eventId.incrementAndGet();

        final int offset = (int) (result.getStartTime() - startTime);
        final String fullName = getFullName(result);
        final String code = result.getResponseCode();
        final String source = result.getThreadName();

        final BenchEventBuilder builder = BenchEventBuilder.builder()
                .offset(offset)
                .error();

        final String elementId = UUID.nameUUIDFromBytes(sampleLabel.getBytes()).toString();
        final Event event = Event.createEvent(String.valueOf(id), Optional.of(elementId), Optional.ofNullable(fullName), Optional.of(code), source);
        builder.event(event).details(getErrorDetails(result));

        return builder.build();
    }

    private String firstLine(String data) {
        if (data == null || "".equals(data)) {
            return null;
        }
        return data.split("\n")[0];
    }

    private String getFullName(SampleResult sampleResult) {
        if (sampleResult.getSamplerData() == null || "".equals(sampleResult.getSamplerData())) {
            return null;
        }
        return firstLine(sampleResult.getSamplerData());
    }

    private String getRequestStatusLine(SampleResult sampleResult) {
        if (sampleResult.getSamplerData() == null || "".equals(sampleResult.getSamplerData())) {
            return null;
        }
        return firstLine(sampleResult.getSamplerData()) + " HTTP/1.1";
    }

    /**
     * - User path can be filled with the Thread group name, replace userId by first part (all but last number-number part) of result.getThreadName() (Thread group name)
     * - Replace instanceId by result.getThreadName()
     * - REQUEST_STATUS_LINE => 'HTTP/1.1 '+ first line of result.getSamplerData() => HTTP Method+' '+URL
     * - RESPONSE_STATUS_LINE => 'HTTP/1.1 '+sampleResult.getResponseCode()+' '+sampleResult.getResponseMessage()
     * - There is no available information of previous request
     * - REQUEST_MESSAGE_REF and RESPONSE_MESSAGE_REF can be computed with result.getSamplerData() and result.getResponseDataAsString()
     * - ITERATION_NUMBER can not be retrieved in a simple way inside a BackendListener. We have to be access to JMeterVariables.getIteration().
     * - TRANSACTION => verify if TransactionController.isFromTransactionController(result) && result.getParent() != null and set result.getParent().getSampleLabel()
     * - ELEMENT => verify if !TransactionController.isFromTransactionController(result) and set result.getSampleLabel()
     * - Assertion results => View proposed implementation below.
     */
    private ErrorDetails getErrorDetails(final SampleResult result) {
        final String userId = getThreadGroupName(result); // Example: Thread Group A 1-25 => Thread group name + ' ' + thread group instance + "-" + thread (vu) number
        final int instanceId = 1; // We have no a better value => It's a number

				return ImmutableErrorDetails.builder()
            .userPathId(userId)
            .userPathInstance(ValueNumber.of(instanceId))
            .requestDuration(ValueNumber.of(result.getTime()))
            .requestStatusLine(Optional.ofNullable(getRequestStatusLine(result)))
            .requestHeaders(Optional.ofNullable(result.getRequestHeaders()))
            .responseHeaders(Optional.ofNullable(result.getResponseHeaders()))
            .transaction(Optional.ofNullable(result.getParent()).map(SampleResult::getSampleLabel))
            .request(result.getSampleLabel())
            .assertionResults(errorEntryToAssertionResults(result)).build();
    }


    private List<AssertionResult> errorEntryToAssertionResults(final SampleResult sampleError) {
        final List<AssertionResult> assertionResults = new ArrayList<>(sampleError.getAssertionResults().length);
        for (final org.apache.jmeter.assertions.AssertionResult assertionResult : sampleError.getAssertionResults()) {
            final AssertionResultType type;
            if (assertionResult.isError()) {
                type = AssertionResultType.ERROR;
            } else if (assertionResult.isFailure()) {
                type = AssertionResultType.FAILURE;
            } else {
                type = AssertionResultType.PASSED;
            }
            if (assertionResult.getFailureMessage() != null) {
                assertionResults.add(createAssertionResult(type, assertionResult.getFailureMessage()));
            }
        }
        return assertionResults;
    }

}