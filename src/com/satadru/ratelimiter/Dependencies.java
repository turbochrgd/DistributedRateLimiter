package com.satadru.ratelimiter;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.AmazonSQSException;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.satadru.ratelimiter.configurations.Configuration;

public class Dependencies {
    private final DynamoDB dynamoDB;
    private final AmazonSQS sqs;
    private final String queueURL;

    public Dependencies() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withCredentials( new AWSStaticCredentialsProvider( Configuration.AWS_CREDENTIALS ) ).withRegion( Configuration.inferAWSRegion() ).build();
        this.dynamoDB = new DynamoDB( client );
        this.sqs = AmazonSQSClientBuilder.standard().withCredentials( new AWSStaticCredentialsProvider( Configuration.AWS_CREDENTIALS ) ).withRegion( Configuration.inferAWSRegion() ).build();
        this.queueURL = this.createAndGetQueueURL();
    }

    private String createAndGetQueueURL() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put( "FifoQueue", "true" );
        attributes.put( "ContentBasedDeduplication", "true" );
        attributes.put( "ReceiveMessageWaitTimeSeconds", "20" );
        CreateQueueRequest createRequest = new CreateQueueRequest( Configuration.RATE_LIMITING_EVENT_SQS_QUEUE_NAME )
                .addAttributesEntry( "MessageRetentionPeriod", "100" ).withAttributes( attributes );

        try {
            this.sqs.createQueue( createRequest );
        }
        catch ( AmazonSQSException e ) {
            if ( !e.getErrorCode().equals( "QueueAlreadyExists" ) ) {
                throw e;
            }
        }

        // Get the URL for a queue
        return this.sqs.getQueueUrl( Configuration.RATE_LIMITING_EVENT_SQS_QUEUE_NAME ).getQueueUrl();
    }

    public DynamoDB getDynamoDB() {
        return this.dynamoDB;
    }

    public AmazonSQS getSqs() {
        return this.sqs;
    }

    public String getQueueURL() {
        return this.queueURL;
    }
}
