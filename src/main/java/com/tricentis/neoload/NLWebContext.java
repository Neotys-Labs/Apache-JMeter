package com.tricentis.neoload;

import org.immutables.value.Value;

@Value.Immutable
public interface NLWebContext {

    String getApiUrl();

    String getApiToken();

    String getWorkspaceId();

    String getTestId();

    String getBenchId();
}
