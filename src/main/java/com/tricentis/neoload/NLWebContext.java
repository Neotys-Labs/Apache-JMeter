package com.tricentis.neoload;

import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface NLWebContext {

    String getApiUrl();

    String getApiToken();

    String getWorkspaceId();

    String getTestId();

    String getBenchId();

    boolean startedByNlw();

    Optional<String> getControllerAgentUuid();
}
