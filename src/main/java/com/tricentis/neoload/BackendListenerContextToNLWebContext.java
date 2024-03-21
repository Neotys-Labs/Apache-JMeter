package com.tricentis.neoload;

import org.apache.jmeter.visualizers.backend.BackendListenerContext;

import java.util.UUID;
import java.util.function.Function;

public final class BackendListenerContextToNLWebContext implements Function<BackendListenerContext, NLWebContext> {
    public static final Function<BackendListenerContext, NLWebContext> INSTANCE = new BackendListenerContextToNLWebContext();

    private BackendListenerContextToNLWebContext() {
    }

    @Override
    public NLWebContext apply(final BackendListenerContext backendListenerContext) {
        final ImmutableNLWebContext.Builder builder = ImmutableNLWebContext.builder();

        final String urlStringParameter = System.getenv("NEOLOADWEB_API_URL");
        if (urlStringParameter == null) {
            builder.apiUrl(backendListenerContext.getParameter(NeoLoadBackendParameters.NEOLOADWEB_API_URL.getName()));
        } else {
            builder.apiUrl(urlStringParameter);
        }

        final String token = System.getenv("NEOLOADWEB_API_TOKEN");
        if (token == null) {
            builder.apiToken(backendListenerContext.getParameter(NeoLoadBackendParameters.NEOLOADWEB_API_TOKEN.getName()));
        } else {
            builder.apiToken(token);
        }

        final String workspaceId = System.getenv("NEOLOADWEB_WORKSPACE_ID");
        if (workspaceId == null) {
            builder.workspaceId(backendListenerContext.getParameter(NeoLoadBackendParameters.NEOLOADWEB_WORKSPACE_ID.getName()));
        } else {
            builder.workspaceId(workspaceId);
        }

        final String testId = System.getenv("NEOLOADWEB_TEST_ID");
        if (testId == null) {
            builder.testId(backendListenerContext.getParameter(NeoLoadBackendParameters.NEOLOADWEB_TEST_ID.getName()));
        } else {
            builder.testId(testId);
        }

        final String benchId = System.getenv("NEOLOADWEB_BENCH_ID");
        if (benchId == null) {
            builder.benchId(UUID.randomUUID().toString());
        } else {
            builder.benchId(benchId);
        }

        return builder.build();
    }
}
