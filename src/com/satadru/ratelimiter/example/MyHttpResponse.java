package com.satadru.ratelimiter.example;

public class MyHttpResponse {

    public static final int HTTP_OK = 200;
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int CLIENT_ERROR = 400;
    public static final int SERVER_ERROR = 500;

    private final int responseCode;
    private final Object payload;

    public MyHttpResponse( final int responseCode, final Object payload ) {
        this.responseCode = responseCode;
        this.payload = payload;
    }

    public int getResponseCode() {
        return this.responseCode;
    }

    public Object getPayload() {
        return this.payload;
    }
}
