package com.satadru.ratelimiter.example;

import com.satadru.ratelimiter.leakybucket.SimpleClientIdBasedRateLimiter;
import com.satadru.ratelimiter.leakybucket.SimpleEndpointRateLimiter;

public class CreateOrder {

    private static MyHttpResponse TOO_MANY_REQUESTS_RESPONSE = new MyHttpResponse( MyHttpResponse.TOO_MANY_REQUESTS, null );
    private static MyHttpResponse OK_EMPTY_RESPONSE = new MyHttpResponse( MyHttpResponse.HTTP_OK, null );
    private final SimpleClientIdBasedRateLimiter clientIdBasedRateLimiter;
    private final SimpleEndpointRateLimiter endpointRateLimiter;

    public CreateOrder( final SimpleClientIdBasedRateLimiter clientIdBasedRateLimiter, final SimpleEndpointRateLimiter endpointRateLimiter ) {
        this.clientIdBasedRateLimiter = clientIdBasedRateLimiter;
        this.endpointRateLimiter = endpointRateLimiter;
    }

    @SuppressWarnings( "Duplicates" )
    public MyHttpResponse createOrder( MyHttpRequest request ) {
        final boolean consumeFromEndpoint = this.endpointRateLimiter.consume();
        if ( !consumeFromEndpoint ) {
            return TOO_MANY_REQUESTS_RESPONSE;
        }

        final boolean consumeForClientId = this.clientIdBasedRateLimiter.consume( request.getApiName(), request.getVerb(), request.getClientId() );
        if ( !consumeForClientId ) {
            return TOO_MANY_REQUESTS_RESPONSE;
        }
        return OK_EMPTY_RESPONSE;
    }
}
