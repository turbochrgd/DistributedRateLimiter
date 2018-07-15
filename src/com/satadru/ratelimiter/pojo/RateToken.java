package com.satadru.ratelimiter.pojo;

public class RateToken {

    private final String periodKey;
    private final double rate;
    private final boolean isAllowedToConsume;

    public RateToken( final String periodKey, final double rate, final boolean isAllowedToConsume ) {
        this.periodKey = periodKey;
        this.rate = rate;
        this.isAllowedToConsume = isAllowedToConsume;
    }

    public String getPeriodKey() {
        return this.periodKey;
    }

    public double getRate() {
        return this.rate;
    }

    public boolean isAllowedToConsume() {
        return this.isAllowedToConsume;
    }
}
