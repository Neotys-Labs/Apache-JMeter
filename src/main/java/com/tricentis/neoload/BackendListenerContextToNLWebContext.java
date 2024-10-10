package com.tricentis.neoload;

import static com.tricentis.neoload.BackendListenerContextToNLWebContext.ContextParamters.*;

import java.util.*;
import java.util.stream.Collectors;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public final class BackendListenerContextToNLWebContext implements Function<BackendListenerContext, NLWebContext> {

	enum ContextParamters {
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
		ContextParamters(NeoLoadBackendParameters jmeterParam) {
			this.jmeterParam = jmeterParam;
		}
		ContextParamters() {
		}
		public NeoLoadBackendParameters getJmeterParam() {
			return jmeterParam;
		}
	}

	public static final Function<BackendListenerContext, NLWebContext> INSTANCE = new BackendListenerContextToNLWebContext();

	private BackendListenerContextToNLWebContext() {
	}


	@Override
	public NLWebContext apply(final BackendListenerContext backendListenerContext) {

		Map<ContextParamters, String> paramMap = new EnumMap<>(ContextParamters.class);
		for(ContextParamters contextParamter : ContextParamters.values()) {
			String value = System.getenv(contextParamter.name());
			if (value == null && contextParamter.getJmeterParam() != null) {
				value = backendListenerContext.getParameter(contextParamter.getJmeterParam().getName());
			}
			if (value != null) {
				paramMap.put(contextParamter, value);
			}
		}

		boolean startedByNlWeb = System.getenv(NEOLOADWEB_API_URL.name()) != null;
		final String benchId = paramMap.get(NEOLOADWEB_BENCH_ID);

		final ImmutableNLWebContext.Builder builder =
				ImmutableNLWebContext.builder()
				.apiUrl(paramMap.get(NEOLOADWEB_API_URL))
				.startedByNlw(startedByNlWeb)
				.apiToken(paramMap.get(NEOLOADWEB_API_TOKEN))
				.workspaceId(paramMap.get(NEOLOADWEB_WORKSPACE_ID))
				.testId(paramMap.get(NEOLOADWEB_TEST_ID))
				.benchId(benchId != null ? benchId : UUID.randomUUID().toString())
				.controllerAgentUuid(paramMap.get(CONTROLLER_AGENT_UUID));

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
}
