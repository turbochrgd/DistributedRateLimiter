package com.satadru.ratelimiter.pojo;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

public class SQSPayload {
    private final String hashKey;
    private final String clientId;
    private long timestamp;
    private final Map<String, Double> rateTokens;

    public SQSPayload( final String hashKey, final String clientId, final long timestamp, final Map<String, Double> rateTokens ) {
        this.hashKey = hashKey;
        this.clientId = clientId;
        this.timestamp = timestamp;
        this.rateTokens = ImmutableMap.copyOf( rateTokens );
    }

    public String getHashKey() {
        return this.hashKey;
    }

    public String getClientId() {
        return this.clientId;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public Map<String, Double> getRateTokens() {
        return this.rateTokens;
    }
}
