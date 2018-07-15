package com.satadru.ratelimiter.leakybucket.distributed.aws;

import java.math.BigDecimal;
import java.util.Map;
import java.util.logging.Logger;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.thirdparty.joda.time.DateTime;
import com.satadru.ratelimiter.configurations.Configuration;
import com.satadru.ratelimiter.leakybucket.SimpleClientIdBasedRateLimiter;
import com.satadru.ratelimiter.pojo.RateToken;

public class DynamoDBBackedSimpleClientIdThrottleClientIdBasedRateLimiter implements SimpleClientIdBasedRateLimiter {

    private static final Logger logger = Logger.getLogger( DynamoDBBackedSimpleClientIdThrottleClientIdBasedRateLimiter.class.getName() );

    private final DynamoDB dynamoDB;
    private final AmazonSQS sqs;
    private final String queueURL;

    public DynamoDBBackedSimpleClientIdThrottleClientIdBasedRateLimiter( final DynamoDB dynamoDB, final AmazonSQS sqs, final String queueURL ) {
        this.dynamoDB = dynamoDB;
        this.sqs = sqs;
        this.queueURL = queueURL;
    }

    @Override
    public boolean consume( final String apiName, final String method, final String actualClientId ) {
        String clientId = actualClientId;
        final Table table = this.dynamoDB.getTable( Configuration.RATE_LIMITING_TABLE_NAME );
        String hashKey = apiName + ":" + method;
        Item item = table.getItem( Configuration.RATE_LIMITING_HASH_KEY_NAME, hashKey, Configuration.RATE_LIMITING_RANGE_KEY_NAME, clientId );
        if ( item == null ) {
            // Load default configuration
            logger.fine( "ClientId " + actualClientId + " not found. Loading default configuration" );
            clientId = Configuration.DEFAULT_CLIENT_ID;
            item = table.getItem( Configuration.RATE_LIMITING_HASH_KEY_NAME, hashKey, Configuration.RATE_LIMITING_RANGE_KEY_NAME, clientId );
        }
        return this.canConsumeCapacity( hashKey, clientId, item.asMap() );
    }

    private boolean canConsumeCapacity( final String hashKey, final String clientId, final Map<String, Object> dynamoDBItem ) {
        RateToken second = this.getRateTokenForPeriod( dynamoDBItem, Configuration.PERIOD_SECOND );
        RateToken minute = this.getRateTokenForPeriod( dynamoDBItem, Configuration.PERIOD_MINUTE );
        RateToken hour = this.getRateTokenForPeriod( dynamoDBItem, Configuration.PERIOD_HOUR );
        RateToken week = this.getRateTokenForPeriod( dynamoDBItem, Configuration.PERIOD_WEEK );
        RateToken month = this.getRateTokenForPeriod( dynamoDBItem, Configuration.PERIOD_MONTH );
        if ( month.isAllowedToConsume()
             && week.isAllowedToConsume()
             && hour.isAllowedToConsume()
             && minute.isAllowedToConsume()
             && second.isAllowedToConsume() ) {
            logger.finest( String.format( "Month : %s, Week : %s, Hour : %s, Minute : %s, Seconds : %s",
                                          month.isAllowedToConsume(), week.isAllowedToConsume(),
                                          hour.isAllowedToConsume(), minute.isAllowedToConsume(),
                                          second.isAllowedToConsume() ) );
            this.publishConsumeToken( hashKey, clientId, second, minute, hour, week, month );
            logger.finest( "Allowed by clientId based rate limiter" );
            return true;
        }
        logger.fine( "Throttled by clientId based rate limiter" );
        return false;
    }

    private RateToken getRateTokenForPeriod( final Map<String, Object> dynamoDBItem, final String periodKey ) {
        Map<String, Object> payload = (Map<String, Object>) dynamoDBItem.get( Configuration.ATTRIBUTE_RATE_LIMITING_PAYLOAD );
        Map<String, Object> periodConsumption = (Map<String, Object>) payload.get( periodKey );
        if ( periodConsumption == null ) {
            // If not configured for the period for this clientId then ALLOW by default
            // Not to be confused with allowing all. The lowest configuration for default
            // client must be present at per second period
            return new RateToken( periodKey, -1, true );
        }
        long lastTimestamp = ( (BigDecimal) periodConsumption.get( Configuration.ATTRIBUTE_RATE_LIMITING_LAST_UPDATED ) ).longValue();
        double maxRatePerMS = ( (BigDecimal) periodConsumption.get( Configuration.ATTRIBUTE_RATE_LIMITING_MAX_ALLOWED_RATE ) ).doubleValue();
        double observedRate = ( (BigDecimal) periodConsumption.get( Configuration.ATTRIBUTE_RATE_LIMITING_RATE ) ).doubleValue();

        long lastTimestampBurst = ( (BigDecimal) periodConsumption.get( Configuration.ATTRIBUTE_RATE_LIMITING_LAST_UPDATED_BURST ) ).longValue();
        double maxCallsInPeriod = ( (BigDecimal) periodConsumption.get( Configuration.ATTRIBUTE_RATE_LIMITING_MAX_ALLOWED_CALLS_IN_PERIOD ) ).doubleValue();
        double actualCallsInPeriod = ( (BigDecimal) periodConsumption.get( Configuration.ATTRIBUTE_RATE_LIMITING_CALLS_IN_PERIOD ) ).doubleValue();

        return this.isRateUnderMax( periodKey, Configuration.PERIOD_TO_MILLISECOND_MAP.get( periodKey ), lastTimestamp, maxRatePerMS, observedRate,
                                    lastTimestampBurst, maxCallsInPeriod, actualCallsInPeriod );
    }

    private RateToken isRateUnderMax( String periodKey, long periodInMillis, long lastTimestamp, double maxRatePerMS, double observedRate,
                                      final long lastTimestampBurst, final double maxCallsInPeriod, final double actualCallsInPeriod ) {
        long now = System.currentTimeMillis();
        long deltaT = now - lastTimestamp;

        double callsInLastPeriod;
        if ( deltaT >= periodInMillis ) {
            // Last update is too old. Reset everything
            callsInLastPeriod = 1;
        }
        else {
            callsInLastPeriod = observedRate * deltaT + 1;
        }

        double ratePerMS = callsInLastPeriod / deltaT;
        /*
        For each subsequent call made with deltaT < Period,
        rate_new = rate_old + 1/deltaT
        Eventually, rate_new == rate_max <-- throttle
        We will again allow when deltaT > Period i.e reset counter
         */

        boolean pastBurstPeriod = ( now - lastTimestampBurst ) > periodInMillis;
        if ( ratePerMS >= maxRatePerMS ) {
            // Allow burst mode traffic but not for per second calls
            if ( pastBurstPeriod || actualCallsInPeriod < maxCallsInPeriod ) {
                logger.fine( String.format( "Allowing burst mode traffic for period %s, maxCallsInPeriod %s, actualCallsInPeriod %s, pastBurstPeriod %s",
                                            periodKey, maxCallsInPeriod, actualCallsInPeriod, pastBurstPeriod ) );
                return new RateToken( periodKey, maxRatePerMS, true );
            }
            logger.fine( String.format( "Rate limit reached for period %s. Max allowed rate %s, current rate %s. Exceed burst capacity", periodKey, maxRatePerMS, ratePerMS ) );
            return new RateToken( periodKey, maxRatePerMS, false );
        }
        else {
            // Check if traffic is still under burst rates
            if ( !pastBurstPeriod && actualCallsInPeriod >= maxCallsInPeriod ) {
                logger.fine( String.format( "Rate limit reached for period %s. maxCallsInPeriod %s, actualCallsInPeriod %s, pastBurstPeriod %s. Exceeded burst capacity",
                                            periodKey, maxCallsInPeriod, actualCallsInPeriod, pastBurstPeriod ) );
                return new RateToken( periodKey, maxRatePerMS, false );
            }
            return new RateToken( periodKey, ratePerMS, true );
        }
    }

    private void publishConsumeToken( final String hashKey, final String clientId, RateToken... rateTokens ) {
        String rateData = this.getRateDataFromRateTokens( rateTokens );
        SendMessageRequest sendMessageRequest = new SendMessageRequest()
                .withQueueUrl( this.queueURL )
                .withMessageGroupId( Configuration.RATE_LIMITING_EVENT_SQS_QUEUE_NAME )
                .withMessageBody( String.format( Configuration.RATE_LIMITING_EVENT_SQS_MESSAGE_TEMPLATE, hashKey, clientId, DateTime.now(), rateData ) );
        this.sqs.sendMessage( sendMessageRequest );
    }

    private String getRateDataFromRateTokens( final RateToken[] rateTokens ) {
        final int length = rateTokens.length;
        StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < length; i++ ) {
            RateToken token = rateTokens[i];
            sb.append( token.getPeriodKey() ).append( "," ).append( token.getRate() );
            if ( i != length - 1 ) {
                sb.append( "," );
            }
        }
        return sb.toString();
    }

    public boolean init( String configurationFile ) {
        // NOT IMPLEMENTED
        // Create or update table.
        // Read the configurationFile and update the table configurations
        // In production level code, this should be delegated to a separate microservice
        return true;
    }
}
