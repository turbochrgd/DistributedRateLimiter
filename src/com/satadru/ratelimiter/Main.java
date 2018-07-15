package com.satadru.ratelimiter;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.satadru.ratelimiter.example.CreateOrder;
import com.satadru.ratelimiter.example.GetOrders;
import com.satadru.ratelimiter.example.MyHttpRequest;
import com.satadru.ratelimiter.example.MyHttpResponse;
import com.satadru.ratelimiter.leakybucket.SimpleClientIdBasedRateLimiter;
import com.satadru.ratelimiter.leakybucket.SimpleEndpointRateLimiter;
import com.satadru.ratelimiter.leakybucket.distributed.aws.DynamoDBBackedSimpleClientIdThrottleClientIdBasedRateLimiter;
import com.satadru.ratelimiter.leakybucket.distributed.aws.SQSThrottlingMessageConsumer;
import com.satadru.ratelimiter.leakybucket.local.LeakyBucketBasedEnpointRateLimiter;

public class Main {

    private static class CallAPI implements Runnable {

        private static final Logger logger = Logger.getLogger( CallAPI.class.getName() );
        private static final int DEFAULT_SLEEP_SECONDS = 2;
        private final String clientId;
        private final CreateOrder createOrder;
        private final GetOrders getOrders;

        private CallAPI( final String clientId, final CreateOrder createOrder, final GetOrders getOrders ) {
            this.clientId = clientId;
            this.createOrder = createOrder;
            this.getOrders = getOrders;
        }

        @Override
        public void run() {
            int success = 0;
            int failures = 0;
            int sleepSeconds = DEFAULT_SLEEP_SECONDS;

            for ( int i = 0; i < 10; i++ ) {
                boolean throttled = false;
                final MyHttpResponse orderResponse = this.createOrder.createOrder( new MyHttpRequest( "createOrder", "POST", this.clientId, null ) );
                if ( orderResponse.getResponseCode() == MyHttpResponse.TOO_MANY_REQUESTS ) {
                    throttled = true;
                    logger.warning( "createOrder API throttled" );
                }
                else {
                    logger.info( "createOrder success" );
                }
                final MyHttpResponse getOrdersResponse = this.getOrders.getOrders( new MyHttpRequest( "getOrders", "GET", this.clientId, null ) );
                if ( getOrdersResponse.getResponseCode() == MyHttpResponse.TOO_MANY_REQUESTS ) {
                    throttled = true;
                    logger.warning( "getOrders API throttled" );
                }
                else {
                    logger.info( "getOrders success" );
                }
                if ( throttled ) {
                    failures++;
                    sleepSeconds = this.exponentialBackOff( sleepSeconds );
                }
                else {
                    success++;
                }
            }
            System.out.println( "ClientId: " + this.clientId + ". Success: " + success + ". Failures: " + failures );
        }

        private int exponentialBackOff( int sleepSeconds ) {
            try {
                logger.fine( "Client " + this.clientId + " sleeping for " + sleepSeconds + " seconds" );
                Thread.sleep( Duration.ofSeconds( sleepSeconds ).toMillis() );
                sleepSeconds *= sleepSeconds;
                if ( sleepSeconds > 16 ) {
                    throw new RuntimeException( "Throttled by server" );
                }
            }
            catch ( InterruptedException e ) {
                e.printStackTrace();
            }
            return sleepSeconds;
        }
    }

    private static class UpdateThrottleData implements Runnable {

        private static final Logger logger = Logger.getLogger( CallAPI.class.getName() );

        private final SQSThrottlingMessageConsumer sqsThrottlingMessageConsumer;

        private UpdateThrottleData( final SQSThrottlingMessageConsumer sqsThrottlingMessageConsumer ) {
            this.sqsThrottlingMessageConsumer = sqsThrottlingMessageConsumer;
        }

        @Override
        public void run() {
            try {
                this.sqsThrottlingMessageConsumer.updateTokens();
            }
            catch ( Exception e ) {
                e.printStackTrace();
                logger.warning( e.getMessage() );
            }
        }
    }

    public static void main( String[] args ) {
        LoggingConfig.configureLogging( Level.FINE, "config/logging.properties" );
        Dependencies dependencies = new Dependencies();
        final ScheduledExecutorService apiCallExecutorService = Executors.newScheduledThreadPool( 1 );

        SimpleClientIdBasedRateLimiter clientIdBasedRateLimiter =
                new DynamoDBBackedSimpleClientIdThrottleClientIdBasedRateLimiter( dependencies.getDynamoDB(), dependencies.getSqs(), dependencies.getQueueURL() );
        // The endpoint limiter will allow average 200 queries per second
        SimpleEndpointRateLimiter endpointRateLimiter = new LeakyBucketBasedEnpointRateLimiter( 2000, Duration.ofSeconds( 10 ).toMillis() );
        CallAPI callAPI1 = new CallAPI( "client1", new CreateOrder( clientIdBasedRateLimiter, endpointRateLimiter ), new GetOrders( clientIdBasedRateLimiter, endpointRateLimiter ) );
        CallAPI callAPI2 = new CallAPI( "client2", new CreateOrder( clientIdBasedRateLimiter, endpointRateLimiter ), new GetOrders( clientIdBasedRateLimiter, endpointRateLimiter ) );
        apiCallExecutorService.scheduleAtFixedRate( callAPI1, Duration.ofSeconds( 5 ).toMillis(), Duration.ofMillis( 100 ).toMillis(), TimeUnit.MILLISECONDS );
        apiCallExecutorService.scheduleAtFixedRate( callAPI2, Duration.ofSeconds( 5 ).toMillis(), Duration.ofMillis( 100 ).toMillis(), TimeUnit.MILLISECONDS );

        final ScheduledExecutorService throttleRateUpdatorExecutorService = Executors.newScheduledThreadPool( 1 );
        SQSThrottlingMessageConsumer sqsThrottlingMessageConsumer =
                new SQSThrottlingMessageConsumer( dependencies.getDynamoDB(), dependencies.getSqs(), dependencies.getQueueURL() );
        UpdateThrottleData updateThrottleData = new UpdateThrottleData( sqsThrottlingMessageConsumer );
        throttleRateUpdatorExecutorService.scheduleWithFixedDelay( updateThrottleData, Duration.ofSeconds( 1 ).toMillis(), Duration.ofMillis( 200 ).toMillis(), TimeUnit.MILLISECONDS );
    }
}
