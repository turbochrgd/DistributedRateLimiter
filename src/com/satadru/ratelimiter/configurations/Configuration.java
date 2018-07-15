package com.satadru.ratelimiter.configurations;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class Configuration {

    private static final Duration TOLERANCE = Duration.ofSeconds( 5 );
    public static final String DEFAULT_CLIENT_ID = "default";
    public static final String ATTRIBUTE_RATE_LIMITING_PAYLOAD = "payload";
    public static final String ATTRIBUTE_RATE_LIMITING_RATE = "rate";
    public static final String ATTRIBUTE_RATE_LIMITING_MAX_ALLOWED_RATE = "maxAllowedRateInPeriod";
    public static final String ATTRIBUTE_RATE_LIMITING_LAST_UPDATED = "lastUpdated";
    // Used for burst calls
    public static final String ATTRIBUTE_RATE_LIMITING_LAST_UPDATED_BURST = "lastUpdatedBurst";
    public static final String ATTRIBUTE_RATE_LIMITING_MAX_ALLOWED_CALLS_IN_PERIOD = "maxAllowedCallsInPeriod";
    public static final String ATTRIBUTE_RATE_LIMITING_CALLS_IN_PERIOD = "callsInPeriod";

    public static final String RATE_LIMITING_TABLE_NAME = "CLIENT_ID_TOKEN_BUCKET";
    public static final String RATE_LIMITING_EVENT_SQS_QUEUE_NAME = "CLIENT_THROTTLING_EVENTS.fifo";
    public static final String RATE_LIMITING_EVENT_SQS_MESSAGE_TEMPLATE = "%s,%s,%s,%s";
    public static final String RATE_LIMITING_HASH_KEY_NAME = "hashKey";
    public static final String RATE_LIMITING_RANGE_KEY_NAME = "clientId";
    public static final int MAX_NUMBER_OF_MESSAGES = 10;

    public static final String PERIOD_SECOND = "second";
    public static final String PERIOD_MINUTE = "minute";
    public static final String PERIOD_HOUR = "hour";
    public static final String PERIOD_WEEK = "week";
    public static final String PERIOD_MONTH = "month";
    public static final Set<String> PERIOD_KEYS = ImmutableSet.of( "second", "minute", "hour", "week", "month" );
    public static final Map<String, Long> PERIOD_TO_MILLISECOND_MAP = new ImmutableMap.Builder<String, Long>()
            .put( PERIOD_SECOND, Duration.ofSeconds( 1 ).toMillis() )
            .put( PERIOD_MINUTE, Duration.ofMinutes( 1 ).toMillis() )
            .put( PERIOD_HOUR, Duration.ofHours( 1 ).toMillis() )
            .put( PERIOD_WEEK, Duration.ofDays( 7 ).toMillis() )
            .put( PERIOD_MONTH, Duration.ofDays( 30 ).toMillis() ).build();

    public static final BasicAWSCredentials AWS_CREDENTIALS = new BasicAWSCredentials( "", "" );

    /**
     * Infers the best AWS region based upon the machines locations.
     *
     * @return AWS region
     */
    public static Regions inferAWSRegion() {
        // This implementations returns US-WEST-2
        return Regions.US_WEST_2;
    }
}
