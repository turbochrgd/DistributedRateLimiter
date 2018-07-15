package com.satadru.ratelimiter.leakybucket.distributed.aws;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.DeleteMessageBatchResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.thirdparty.joda.time.DateTime;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.satadru.ratelimiter.configurations.Configuration;
import com.satadru.ratelimiter.leaderelection.LeaderElectionAlgorithm;
import com.satadru.ratelimiter.leaderelection.SelfElectingLeaderAlgorithm;
import com.satadru.ratelimiter.pojo.SQSPayload;

public class SQSThrottlingMessageConsumer {

    private static final Logger logger = Logger.getLogger( SQSThrottlingMessageConsumer.class.getName() );

    private final DynamoDB dynamoDB;
    private final AmazonSQS sqs;
    private final String queueURL;
    private final LeaderElectionAlgorithm leaderElectionAlgorithm;
    private final Map<String, Item> dynamoDBCache = new HashMap<>();

    public SQSThrottlingMessageConsumer( final DynamoDB dynamoDB, final AmazonSQS sqs, final String queueURL ) {
        this.dynamoDB = dynamoDB;
        this.sqs = sqs;
        this.queueURL = queueURL;
        this.leaderElectionAlgorithm = new SelfElectingLeaderAlgorithm();
    }

    /**
     * Intended to be executed by a ScheduledExecutorService.scheduleWithFixedDelay()
     * If this machine is NOT the leader, return
     * Else, consume payloads from the rate SQS queue and update the corresponding clientId rates in DynamoDB
     */
    public void updateTokens() {
        /*
        Ensures that only one machine in the clique is consuming the queue at a time. Is good for a decent sized service.
        Using the HighestIPAddressInLastMinuteLeaderElectionAlgorithm (not implemented) will ensure that there is always a leader elected every 1 minute.
        For this implementation I am using SelfElectingLeaderAlgorithm (since this is a proof-of-concept only)
         */
        if ( !this.leaderElectionAlgorithm.isLeader() ) {
            return;
        }

        final Table table = this.dynamoDB.getTable( Configuration.RATE_LIMITING_TABLE_NAME );
        final ImmutableList.Builder<DeleteMessageBatchRequestEntry> deleteMessageBatchRequestEntryBuilder = new ImmutableList.Builder<>();
        final ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest( this.queueURL );
        receiveMessageRequest.setMaxNumberOfMessages( Configuration.MAX_NUMBER_OF_MESSAGES );
        final ReceiveMessageResult result = this.sqs.receiveMessage( receiveMessageRequest );
        if ( result != null ) {
            final List<Message> messages = result.getMessages();
            if ( messages != null ) {
                for ( Message message : messages ) {
                    SQSPayload sqsPayload = this.converToSQSPayloadPOJO( message );
                    final String cacheKey = this.getCacheKey( sqsPayload.getHashKey(), sqsPayload.getClientId() );

                    // loading from cache to de-dupe fetching from DDB for the same client and API
                    final Item dynamoDBItem = this.loadFromCache( table, cacheKey, sqsPayload.getHashKey(), sqsPayload.getClientId() );

                    // Not the most elegant code but since I am time boxed, this has to do
                    final Map<String, Object> payload = (Map<String, Object>) dynamoDBItem.get( Configuration.ATTRIBUTE_RATE_LIMITING_PAYLOAD );
                    for ( String periodKey : Configuration.PERIOD_KEYS ) {
                        final Map<String, Double> rateTokens = sqsPayload.getRateTokens();
                        if ( rateTokens.containsKey( periodKey ) ) {
                            long timestamp = sqsPayload.getTimestamp();
                            Map<String, Object> entryData = (Map<String, Object>) payload.get( periodKey );
                            entryData.put( Configuration.ATTRIBUTE_RATE_LIMITING_LAST_UPDATED, BigDecimal.valueOf( timestamp ) );
                            entryData.put( Configuration.ATTRIBUTE_RATE_LIMITING_RATE, BigDecimal.valueOf( rateTokens.get( periodKey ) ) );
                            final Object maxAllowedRate = entryData.get( Configuration.ATTRIBUTE_RATE_LIMITING_MAX_ALLOWED_RATE );
                            entryData.put( Configuration.ATTRIBUTE_RATE_LIMITING_MAX_ALLOWED_RATE, maxAllowedRate );
                            double callsInPeriod = ( (BigDecimal) entryData.get( Configuration.ATTRIBUTE_RATE_LIMITING_CALLS_IN_PERIOD ) ).doubleValue();
                            long lastUpdatedTimestampBurstRate = ( (BigDecimal) entryData.get( Configuration.ATTRIBUTE_RATE_LIMITING_LAST_UPDATED_BURST ) ).longValue();
                            long deltaT = timestamp - lastUpdatedTimestampBurstRate;
                            if ( deltaT >= Configuration.PERIOD_TO_MILLISECOND_MAP.get( periodKey ) ) {
                                logger.finest( String.format( "SQS: Resetting the burst call ticker to 1 for period %s", periodKey ) );
                                // reset the burst rate
                                callsInPeriod = 1;
                                entryData.put( Configuration.ATTRIBUTE_RATE_LIMITING_LAST_UPDATED_BURST, BigDecimal.valueOf( timestamp ) );
                            }
                            else {
                                callsInPeriod++;
                            }
                            entryData.put( Configuration.ATTRIBUTE_RATE_LIMITING_CALLS_IN_PERIOD, BigDecimal.valueOf( callsInPeriod ) );
                        }
                    }

                    // Instead of writing through to DDB, update the item in cache
                    this.dynamoDBCache.put( cacheKey, dynamoDBItem );
                    deleteMessageBatchRequestEntryBuilder.add( new DeleteMessageBatchRequestEntry( message.getMessageId(), message.getReceiptHandle() ) );
                }

                // Flush the updated DynamoDB items in cache to Dynamo
                // Can be improved to use BatchWriteItem API
                for ( Map.Entry<String, Item> item : this.dynamoDBCache.entrySet() ) {
                    table.putItem( item.getValue() );
                }

                // clear the cache
                this.dynamoDBCache.clear();

                // Delete SQS messages
                final List<DeleteMessageBatchRequestEntry> deleteMessageBatchRequestEntries = deleteMessageBatchRequestEntryBuilder.build();
                if ( !deleteMessageBatchRequestEntries.isEmpty() ) {
                    final DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest( this.queueURL, deleteMessageBatchRequestEntries );
                    final DeleteMessageBatchResult deleteMessageBatchResult = this.sqs.deleteMessageBatch( deleteMessageBatchRequest );
                    if ( !deleteMessageBatchResult.getFailed().isEmpty() ) {
                        logger.warning( "Failed to delete message " + deleteMessageBatchResult.getFailed().size() );
                    }
                    // TODO We can move these messages to a DLQ or do a purge at a later point of time
                }
            }
        }

    }

    private SQSPayload converToSQSPayloadPOJO( final Message message ) {
        final String[] data = message.getBody().split( "," );
        final String hashKey = data[0];
        final String clientId = data[1];
        long timestamp = DateTime.parse( data[2] ).getMillis();
        final Map<String, Double> rateTokens = this.getPeriodsToRateMap( data );
        return new SQSPayload( hashKey, clientId, timestamp, rateTokens );
    }

    private Item loadFromCache( final Table table, final String cacheKey, final String hashKey, final String clientId ) {
        if ( this.dynamoDBCache.containsKey( cacheKey ) ) {
            return this.dynamoDBCache.get( cacheKey );
        }
        final Item item = table.getItem( Configuration.RATE_LIMITING_HASH_KEY_NAME, hashKey, Configuration.RATE_LIMITING_RANGE_KEY_NAME, clientId );
        this.dynamoDBCache.put( cacheKey, item );
        return item;
    }

    private String getCacheKey( final String hashKey, final String clientId ) {
        return hashKey + ":" + clientId;
    }

    private Map<String, Double> getPeriodsToRateMap( final String[] data ) {
        ImmutableMap.Builder<String, Double> mapBuilder = new ImmutableMap.Builder<>();
        for ( int i = 3; i < data.length; i++ ) {
            String period = data[i++];
            double rate = Double.valueOf( data[i] );
            if ( this.isPositive( rate ) ) {
                mapBuilder.put( period, rate );
            }
        }
        return mapBuilder.build();
    }

    /*
    Comparing a very small double can be tricky, thus the following
     */
    public boolean isPositive( double d ) {
        return Double.doubleToRawLongBits( d ) > 0;
    }
}
