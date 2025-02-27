package com.tricentis.neoload;

import static com.tricentis.neoload.BackendListenerContextToNLWebContext.ContextParameters.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;

public final class BackendListenerContextToNLWebContext implements Function<BackendListenerContext, NLWebContext> {

	private final SystemEnvironment environment;

	private BackendListenerContextToNLWebContext(final SystemEnvironment environment) {
		this.environment = environment;
	}

	public static BackendListenerContextToNLWebContext of(final SystemEnvironment environment) {
		return new BackendListenerContextToNLWebContext(environment);
	}

	@Override
	public NLWebContext apply(final BackendListenerContext backendListenerContext) {

		Map<ContextParameters, String> paramMap = new EnumMap<>(ContextParameters.class);
		for (ContextParameters contextParameter : ContextParameters.values()) {
			String value = environment.get(contextParameter.name());
			if (value == null && contextParameter.getJmeterParam() != null) {
				value = backendListenerContext.getParameter(contextParameter.getJmeterParam().getName());
			}
			if (value != null) {
				paramMap.put(contextParameter, value);
			}
		}

		boolean startedByNlWeb = environment.get(NEOLOADWEB_API_URL.name()) != null;
		final String benchId = paramMap.get(NEOLOADWEB_BENCH_ID);

		final ImmutableNLWebContext.Builder builder =
				ImmutableNLWebContext.builder()
						.apiUrl(paramMap.get(NEOLOADWEB_API_URL))
						.startedByNlw(startedByNlWeb)
						.apiToken(paramMap.get(NEOLOADWEB_API_TOKEN))
						.workspaceId(paramMap.get(NEOLOADWEB_WORKSPACE_ID))
						.testId(paramMap.get(NEOLOADWEB_TEST_ID))
						.benchId(benchId != null ? benchId : UUID.randomUUID().toString());

		if (paramMap.containsKey(CONTROLLER_AGENT_UUID)) {
			builder.controllerAgentUuid(paramMap.get(CONTROLLER_AGENT_UUID));
		}

		if (paramMap.containsKey(NEOLOADWEB_PROXY_HOST)) {
			ImmutableNLWebProxyInfo.Builder proxyInfoBuilder = ImmutableNLWebProxyInfo.builder()
					.host(paramMap.get(NEOLOADWEB_PROXY_HOST))
					.port(Integer.parseInt(paramMap.get(NEOLOADWEB_PROXY_PORT)));
			if (paramMap.containsKey(NEOLOADWEB_PROXY_LOGIN)) {
				proxyInfoBuilder
						.login(paramMap.get(NEOLOADWEB_PROXY_LOGIN));
			}
			if (paramMap.containsKey(NEOLOADWEB_PROXY_PASSWORD)) {
				proxyInfoBuilder
						.password(paramMap.get(NEOLOADWEB_PROXY_PASSWORD));
			}
			builder.proxyInfo(proxyInfoBuilder.build());
		}
		return builder.build();
	}


	enum ContextParameters {
		NEOLOADWEB_API_URL(NeoLoadBackendParameters.NEOLOADWEB_API_URL),
		NEOLOADWEB_API_TOKEN(NeoLoadBackendParameters.NEOLOADWEB_API_TOKEN),
		NEOLOADWEB_WORKSPACE_ID(NeoLoadBackendParameters.NEOLOADWEB_WORKSPACE_ID),
		NEOLOADWEB_TEST_ID(NeoLoadBackendParameters.NEOLOADWEB_TEST_ID),
		NEOLOADWEB_BENCH_ID,
		CONTROLLER_AGENT_UUID,
		NEOLOADWEB_PROXY_HOST(NeoLoadBackendParameters.NEOLOADWEB_PROXY_HOST),
		NEOLOADWEB_PROXY_PORT(NeoLoadBackendParameters.NEOLOADWEB_PROXY_PORT),
		NEOLOADWEB_PROXY_LOGIN(NeoLoadBackendParameters.NEOLOADWEB_PROXY_LOGIN),
		NEOLOADWEB_PROXY_PASSWORD(NeoLoadBackendParameters.NEOLOADWEB_PROXY_PASSWORD);

		private NeoLoadBackendParameters jmeterParam;

		ContextParameters(NeoLoadBackendParameters jmeterParam) {
			this.jmeterParam = jmeterParam;
		}

		ContextParameters() {
		}

		public NeoLoadBackendParameters getJmeterParam() {
			return jmeterParam;
		}
	}
}
