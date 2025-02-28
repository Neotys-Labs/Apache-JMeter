package com.tricentis.neoload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Optional;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.junit.jupiter.api.Test;

class BackendListenerContextToNLWebContextTest {

	private static final String API_URL = "https://api-url.com";
	private static final String API_TOKEN = "some-token";
	private static final String WORKSPACE_ID = "workspaceId";
	private static final String TEST_ID = "testId";
	private static final String BENCH_ID = "benchId";
	private static final String CONTROLLER_AGENT_UUID = "controllerAgentUuid";
	private static final String NEOLOAD_PROXY_HOST = "proxyHost";
	private static final int NEOLOAD_PROXY_PORT = 0;
	private static final String NEOLOAD_PROXY_LOGIN = "proxyLogin";
	private static final String NEOLOAD_PROXY_PASSWORD = "proxyPassword";

	@Test
	void apply_whenStandalonePluginConfiguration_shouldApply() {
		// Given
		final HashMap<String, String> params = new HashMap<>();
		params.put("NeoLoadWeb-API-URL", API_URL);
		params.put("NeoLoadWeb-API-token", API_TOKEN);
		params.put("NeoLoadWeb-Workspace-ID", WORKSPACE_ID);
		params.put("NeoLoadWeb-Test-ID", TEST_ID);
		final BackendListenerContext backendListenerContext = new BackendListenerContext(params);
		final SystemEnvironment environment = new EmptySystemEnvironment();

		// When
		final NLWebContext nlWebContext = BackendListenerContextToNLWebContext.of(environment).apply(backendListenerContext);

		// Then
		assertEquals(API_URL, nlWebContext.getApiUrl());
		assertEquals(API_TOKEN, nlWebContext.getApiToken());
		assertEquals(WORKSPACE_ID, nlWebContext.getWorkspaceId());
		assertEquals(TEST_ID, nlWebContext.getTestId());
	}

	@Test
	void apply_whenExecutionFromNlwConfiguration_shouldApply() {
		// Given
		final HashMap<String, String> params = new HashMap<>();
		params.put("NeoLoadWeb-API-URL", API_URL);
		params.put("NeoLoadWeb-API-token", API_TOKEN);
		params.put("NeoLoadWeb-Workspace-ID", WORKSPACE_ID);
		params.put("NeoLoadWeb-Test-ID", TEST_ID);
		params.put("NEOLOADWEB_BENCH_ID", BENCH_ID);
		params.put("CONTROLLER_AGENT_UUID", CONTROLLER_AGENT_UUID);
		final BackendListenerContext backendListenerContext = new BackendListenerContext(params);
		final SystemEnvironment systemEnvironment = new MockedSystemEnvironment();

		// When
		final NLWebContext nlWebContext = BackendListenerContextToNLWebContext.of(systemEnvironment).apply(backendListenerContext);

		// Then
		assertEquals(API_URL, nlWebContext.getApiUrl());
		assertEquals(API_TOKEN, nlWebContext.getApiToken());
		assertEquals(WORKSPACE_ID, nlWebContext.getWorkspaceId());
		assertEquals(TEST_ID, nlWebContext.getTestId());
		assertEquals(BENCH_ID, nlWebContext.getBenchId());
		assertEquals(Optional.of(CONTROLLER_AGENT_UUID), nlWebContext.getControllerAgentUuid());
	}

	@Test
	void apply_whenExecutionFromNlwConfigurationWithProxy_shouldApply() {
		// Given
		final HashMap<String, String> params = new HashMap<>();
		params.put("NeoLoadWeb-API-URL", API_URL);
		params.put("NeoLoadWeb-API-token", API_TOKEN);
		params.put("NeoLoadWeb-Workspace-ID", WORKSPACE_ID);
		params.put("NeoLoadWeb-Test-ID", TEST_ID);
		final BackendListenerContext backendListenerContext = new BackendListenerContext(params);
		final SystemEnvironment systemEnvironment = new MockedSystemEnvironment();

		// When
		final NLWebContext nlWebContext = BackendListenerContextToNLWebContext.of(systemEnvironment).apply(backendListenerContext);

		// Then
		assertEquals(API_URL, nlWebContext.getApiUrl());
		assertEquals(API_TOKEN, nlWebContext.getApiToken());
		assertEquals(WORKSPACE_ID, nlWebContext.getWorkspaceId());
		assertEquals(TEST_ID, nlWebContext.getTestId());
		assertEquals(BENCH_ID, nlWebContext.getBenchId());
		assertEquals(Optional.of(CONTROLLER_AGENT_UUID), nlWebContext.getControllerAgentUuid());
		assertNotNull(nlWebContext.getProxyInfo());
		assertEquals(NEOLOAD_PROXY_HOST, nlWebContext.getProxyInfo().getHost());
		assertEquals(NEOLOAD_PROXY_PORT, nlWebContext.getProxyInfo().getPort());
		assertEquals(NEOLOAD_PROXY_LOGIN, nlWebContext.getProxyInfo().getLogin());
		assertEquals(NEOLOAD_PROXY_PASSWORD, nlWebContext.getProxyInfo().getPassword());
	}

	static final class EmptySystemEnvironment extends SystemEnvironment {

		@Override
		public String get(final String key) {
			return null;
		}
	}

	static final class MockedSystemEnvironment extends SystemEnvironment {

		@Override
		public String get(final String key) {
			switch (key) {
				case "NEOLOADWEB_BENCH_ID":
					return BENCH_ID;
				case "CONTROLLER_AGENT_UUID":
					return CONTROLLER_AGENT_UUID;
				case "NEOLOADWEB_PROXY_HOST":
					return NEOLOAD_PROXY_HOST;
				case "NEOLOADWEB_PROXY_PORT":
					return String.valueOf(NEOLOAD_PROXY_PORT);
				case "NEOLOADWEB_PROXY_LOGIN":
					return NEOLOAD_PROXY_LOGIN;
				case "NEOLOADWEB_PROXY_PASSWORD":
					return NEOLOAD_PROXY_PASSWORD;
				default:
					return null;
			}
		}
	}

}