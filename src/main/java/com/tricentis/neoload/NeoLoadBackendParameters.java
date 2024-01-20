package com.tricentis.neoload;

public enum NeoLoadBackendParameters {
    NEOLOADWEB_API_URL("NeoLoadWeb-API-URL", "https://neoload-api.saas.neotys.com"),
    NEOLOADWEB_API_TOKEN("NeoLoadWeb-API-token", ""),
    NEOLOADWEB_WORKSPACE_ID("NeoLoadWeb-Workspace-ID", ""),
    NEOLOADWEB_TEST_ID("NeoLoadWeb-Test-ID", "");


    private final String name;
    private final String defaultValue;

    NeoLoadBackendParameters(final String name, final String defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDefaultValue() {
        return defaultValue;
    }
}
