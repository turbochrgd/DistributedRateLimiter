package com.satadru.ratelimiter.example;

public class MyHttpRequest {

    private final String apiName;
    private final String verb;
    private final String clientId;
    private final Object payload;

    public MyHttpRequest( final String apiName, final String verb, final String clientId, final Object payload ) {
        this.apiName = apiName;
        this.verb = verb;
        this.clientId = clientId;
        this.payload = payload;
    }

    public String getApiName() {
        return this.apiName;
    }

    public String getVerb() {
        return this.verb;
    }

    public String getClientId() {
        return this.clientId;
    }

    public Object getPayload() {
        return this.payload;
    }
}
