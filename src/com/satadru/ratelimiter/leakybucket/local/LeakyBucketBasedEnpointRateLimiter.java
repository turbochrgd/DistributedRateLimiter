package com.satadru.ratelimiter.leakybucket.local;

import java.util.Date;
import java.util.logging.Logger;

import com.satadru.ratelimiter.leakybucket.SimpleEndpointRateLimiter;

public class LeakyBucketBasedEnpointRateLimiter implements SimpleEndpointRateLimiter {

    private static final Logger logger = Logger.getLogger( LeakyBucketBasedEnpointRateLimiter.class.getName() );

    private int dropsIssued = 0;
    private Date lastTimestamp = null;
    private final int bucketSize;
    private final long refillIntervalInMillis;

    public LeakyBucketBasedEnpointRateLimiter( final int bucketSize, final long refillIntervalInMillis ) {
        this.bucketSize = bucketSize;
        this.refillIntervalInMillis = refillIntervalInMillis;
    }

    /**
     * Simple implementation of leaky bucket algorithm
     *
     * @return true if rate limit has not reached
     */
    @Override
    public synchronized boolean consume() {
        Date now = new Date();
        if ( this.lastTimestamp != null ) {
            long deltaT = now.getTime() - this.lastTimestamp.getTime();
            long numberToLeak = deltaT / this.refillIntervalInMillis;
            if ( numberToLeak > 0 ) {
                if ( this.dropsIssued <= numberToLeak ) {
                    this.dropsIssued = 0;
                }
                else {
                    this.dropsIssued -= (int) numberToLeak;
                }
                this.lastTimestamp = now;
            }
        }

        if ( this.dropsIssued < this.bucketSize ) {
            this.dropsIssued++;
            if ( this.lastTimestamp == null && this.dropsIssued == this.bucketSize ) {
                this.lastTimestamp = new Date();
            }
            logger.finest( "Allowed by endpoint rate limiter" );
            return true;
        }
        logger.fine( "Throttled by endpoint rate limiter" );
        return false;
    }
}
