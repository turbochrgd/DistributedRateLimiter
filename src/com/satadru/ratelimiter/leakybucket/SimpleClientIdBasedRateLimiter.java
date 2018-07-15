package com.satadru.ratelimiter.leakybucket;

/*
Simple interface to consume tokens from token bucket based on clientIds.
Non-blocking. Consumes token and returns true if token is present else returns false.
 */
public interface SimpleClientIdBasedRateLimiter {

    /**
     * Consumes one token per call if token is available for the passed apiName, verb and clientId.
     * Non-blocking. Consumes token and returns true if token is present else returns false.
     *
     * @param apiName  The name of the API being called
     * @param method   The REST verb
     * @param clientId Identifier for the client calling the API
     * @return true iff there was an available token and it was consumed by this call else false
     */
    boolean consume( String apiName, String method, String clientId );

}
