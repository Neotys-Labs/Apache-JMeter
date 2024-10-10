package com.tricentis.neoload;


import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
public interface NLWebProxyInfo {

	String getHost();

	Integer getPort();

	@Nullable
	String getLogin();

	@Nullable
	String getPassword();
}
