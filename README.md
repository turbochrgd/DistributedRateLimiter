# Distributed Rate Limiter

A simple distributed rate limiter. This is a proof-of-concept implementation and has no metrics or operational data points. It is assumed that CLIENT_ID is provided in the request to the framework.

<h2>Framework requirements</h2>

1. Should be able to limit API call rate per-client for each API and REST method (verb) in a distributed system.
2. Should be able to limit API call rate for each endpoint.
3. Should be reliable, durable, highly available and provide near real-time rate limiting.
4. Configurations for per-client based rate limits should be externalized for easier configuration experience.
5. Should be able to be included in any API with ease.


<h2>Design considerations</h2>

1. For a distributed rate limiting framework to work, we need to store the client behavioral data (API call pattern, numbers, rate) and configuration in a highly available datastore. The datastore should support very low latencies in order to not impact the actual working of the APIs. In this implementation I choose to use AWS DynamoDB as the choice of datastore to store customer configuration and behavior.
2. Once the customer behavior data and configuration is available, the framework will make a decision about the current request. A decision can be made to ALLOW or DENY the request.
3. Decisions can be based on the per period rate or total burst rate in that period. 10 queries per second can be consumed in an entire second or in the fist 100 milliseconds, etc.
4. Endpoint based rate limiting is done using a simple leaky bucket algorithm.
5. For a request to reach the actual API, both the rate limiters should ALLOW the request.
6. Actual storing of configuration in de-normalized form is not implemented in this framework, however, the data model has been defined. The configuration updates should be done by a separate microservice which need not server customer traffic.
7. If there is no configuration present for a client identifier in DynamoDB, a default configuration will be present and that will be used.
8. If no configuration is present for a period ( no configuration may be present for the 'month' period), it is assumed to be INFINITE and default behavior is ALLOW.
9. Storing configuration in de-normalized form will reduce complexity. We can store over a million records in DyanmoDB for less than $1 per month.
10. Cost of rate limiting will be proportional to the rate of API calls since we will read customer behavior for each API call. For every API request, we will make 1 Dynamo read + 1 SQS publish + 1 SQS receive + 1 Dynamo write. 
11. We will keep incurring costs even if the clients are continuously being throttled. An automatic blacklisting system can alleviate a situation with a rouge client, but that is out of scope of this design.
12. The framework will publish customer behavior ONLY when the decision is ALLOW. The customer behavior will be published to an SQS queue.
13. Each machine in the fleet will run a scheduled job (every N milliseconds) to consume the customer behavior payload from the SQS queue and update the actual customer behavior in DyanmoDB.
14. To save from concurrent updates, only the machine that has been elected the leader will consume behavior payload and update the customer behavior data in DynamoDB.
15. A simple HighestIPAddressInLastMinuteLeaderElectionAlgorithm (as described in the code but NOT IMPLEMENTED) will ensure that there is ALWAYS only one leader.
16. For the implementation of this proof-of-concept meant to be run from a single machine, a default leader election algorithm is used which will not work in case of multiple machines in the fleet.
17. Two simple APIs have been created as an example of how this framework can be used. For a real system, an annotation and pointcut based decision making is ideal, but that is not implemented here. 
18. The code is not of the best quality since I am time boxed.
19. Running the Main.main() class will give a demo of the framework.
20. Client configuration data has been added to config/ folder which can be imported to DynamoDB.
21. For very high scaling, we can move our datastore to self-manager Redis backed cache since DyanmoDB can be cost prohibitive. SQS can be moved to Amazon Kinesis for higher throughput and scaling for consumers.

<h2>System design diagrams</h2>

<h3>Framework diagram</h3>

![alt text](https://raw.githubusercontent.com/turbochrgd/DistributedRateLimiter/master/system_diagrams/system_diagram.png)

<h3>Client behavior updator diagram</h3>

![alt text](https://raw.githubusercontent.com/turbochrgd/DistributedRateLimiter/master/system_diagrams/sqs_1.png)


<h2>Configuration Data Model</h2>

1. Configuration data is store in the table CLIENT_ID_TOKEN_BUCKET in DynamoDB.
2. A separate service (ConfigurationService) is responsible to update the client configurations. This service has not been implemented here.
3. The ConfigurationService will de-normalize the configurations and store them in DynamoDB. See #6 for details.
4. This framework will update the customer behavior only in the same table and not the configuration.
5. Configuration data + Customer behavior data is stored in the same record in DynamoDB.
6. Data is stored in de-normalized form. Hash key of the table is <API_NAME>:<METHOD> and range key is <CLIENT_ID>.
7. We store a separate record for each such combination.
8. A default configuration record is present and used for any unknown CLIENT_ID. 
9. De-normalizing the configuration and behavior data makes querying DynamoDB easier and reduces latency as well as keeps the framework simple.