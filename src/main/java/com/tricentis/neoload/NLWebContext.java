package com.tricentis.neoload;

import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
public abstract class NLWebContext {

    public abstract String getApiUrl();

    public abstract String getApiToken();

    public abstract String getWorkspaceId();

    public abstract String getTestId();

    public abstract String getBenchId();

    public abstract boolean startedByNlw();

    public abstract Optional<String> getControllerAgentUuid();

    @Nullable
    public abstract NLWebProxyInfo getProxyInfo();

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("API URL=").append(getApiUrl()).append("\n")
						.append("API Token=").append(getApiToken()).append("\n")
						.append("Workspace=").append(getWorkspaceId()).append("\n")
						.append("TestId=").append(getTestId()).append("\n")
            .append("BenchId=").append(getBenchId()).append("\n");
        if(getControllerAgentUuid().isPresent()) {
            sb.append("ControllerAgentUuid=").append(getControllerAgentUuid().get()).append("\n");
        }
        if(getProxyInfo() != null) {
            sb.append("Proxy Host=").append(getProxyInfo().getHost()).append("\n")
                .append("Proxy Port=").append(getProxyInfo().getPort()).append("\n");
            if(getProxyInfo().getLogin() != null) {
                sb.append("Proxy Info=").append(getProxyInfo().getLogin()).append("\n");
            }
            if(getProxyInfo().getPassword() != null) {
                sb.append("Proxy Info=").append(getProxyInfo().getPassword()).append("\n");
            }
        }
        return sb.toString();
    }
}
