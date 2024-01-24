package com.tricentis.neoload;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.jmeter.samplers.SampleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ThreadGroupNameCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadGroupNameCache.class);

    private static final LoadingCache<String, String> THREAD_NAME_TO_THREAD_GROUP_NAME = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(
                    new CacheLoader<String, String>() {
                        public String load(final String threadName) {
                            return threadName.substring(0, Math.max(0, threadName.lastIndexOf(" ")));
                        }
                    });

    private ThreadGroupNameCache() {
    }

    public static String getThreadGroupName(final SampleResult sampleResult) {
        try {
            return THREAD_NAME_TO_THREAD_GROUP_NAME.get(sampleResult.getThreadName());
        } catch (final ExecutionException e) {
            LOGGER.error("Error while getting thread name", e);
        }
        return "";
    }

}
