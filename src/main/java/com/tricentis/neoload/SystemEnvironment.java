package com.tricentis.neoload;

public class SystemEnvironment {

	public SystemEnvironment() {
	}

	public String get(final String key) {
		return System.getenv(key);
	}
}
