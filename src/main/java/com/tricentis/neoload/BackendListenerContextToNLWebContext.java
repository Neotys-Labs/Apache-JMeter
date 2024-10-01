package com.tricentis.neoload;

import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class BackendListenerContextToNLWebContext implements Function<BackendListenerContext, NLWebContext> {
	private static final Logger LOGGER = LoggerFactory.getLogger(BackendListenerContextToNLWebContext.class);
	public static final Function<BackendListenerContext, NLWebContext> INSTANCE = new BackendListenerContextToNLWebContext();

	private BackendListenerContextToNLWebContext() {
	}

	@Override
	public NLWebContext apply(final BackendListenerContext backendListenerContext) {
		LOGGER.info("Create NLWebContext from BackendListenerContext");
		final ImmutableNLWebContext.Builder builder = ImmutableNLWebContext.builder();

		String apiUrl = System.getenv("NEOLOADWEB_API_URL");
		LOGGER.info("System.getenv(\"NEOLOADWEB_API_URL\"): " + apiUrl);
		if (apiUrl == null) {
			apiUrl = backendListenerContext.getParameter(NeoLoadBackendParameters.NEOLOADWEB_API_URL.getName());
			LOGGER.info("Parameter " + NeoLoadBackendParameters.NEOLOADWEB_API_URL.getName() + ": " + apiUrl);
			builder.startedByNlw(false);
		} else {
			builder.startedByNlw(true);
		}
		builder.apiUrl(apiUrl);

		String apiToken = System.getenv("NEOLOADWEB_API_TOKEN");
		LOGGER.info("System.getenv(\"NEOLOADWEB_API_TOKEN\"): " + apiToken);
		if (apiToken == null) {
			apiToken = backendListenerContext.getParameter(NeoLoadBackendParameters.NEOLOADWEB_API_TOKEN.getName());
			LOGGER.info("Parameter " + NeoLoadBackendParameters.NEOLOADWEB_API_TOKEN.getName() + ": " + apiToken);
		}
		builder.apiToken(apiToken);

		String workspaceId = System.getenv("NEOLOADWEB_WORKSPACE_ID");
		LOGGER.info("System.getenv(\"NEOLOADWEB_WORKSPACE_ID\"): " + workspaceId);
		if (workspaceId == null) {
			workspaceId = backendListenerContext.getParameter(NeoLoadBackendParameters.NEOLOADWEB_WORKSPACE_ID.getName());
			LOGGER.info("Parameter " + NeoLoadBackendParameters.NEOLOADWEB_WORKSPACE_ID.getName() + ": " + workspaceId);
		}
		builder.workspaceId(workspaceId);

		String testId = System.getenv("NEOLOADWEB_TEST_ID");
		LOGGER.info("System.getenv(\"NEOLOADWEB_TEST_ID\"): " + testId);
		if (testId == null) {
			testId = backendListenerContext.getParameter(NeoLoadBackendParameters.NEOLOADWEB_TEST_ID.getName());
			LOGGER.info("Parameter " + NeoLoadBackendParameters.NEOLOADWEB_TEST_ID.getName() + ": " + testId);
		}
		builder.testId(testId);

		String benchId = System.getenv("NEOLOADWEB_BENCH_ID");
		LOGGER.info("System.getenv(\"NEOLOADWEB_BENCH_ID\"): " + benchId);
		if (benchId == null) {
			benchId = UUID.randomUUID().toString();
			LOGGER.info("Generating new benchId with randomUUID: " + benchId);
		}
		builder.benchId(benchId);

		final String agentUuid = System.getenv("CONTROLLER_AGENT_UUID");
		LOGGER.info("System.getenv(\"CONTROLLER_AGENT_UUID\"): " + agentUuid);
		builder.controllerAgentUuid(Optional.ofNullable(agentUuid));
		return builder.build();
	}
}
