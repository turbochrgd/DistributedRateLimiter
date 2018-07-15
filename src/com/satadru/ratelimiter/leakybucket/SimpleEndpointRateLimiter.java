package com.satadru.ratelimiter.leakybucket;

/*
Simple interface to consume tokens from token bucket.
Non-blocking. Consumes token and returns true if token is present else returns false.
 */
public interface SimpleEndpointRateLimiter {

    /**
     * @return true iff there was an available token and it was consumed by this call else false
     */
    boolean consume();
}
