package com.satadru.ratelimiter.leaderelection;

/**
 * Simple IP address sorting and last heartbeat timestamp based leader election algorithm
 * NOT IMPLEMENTED
 */
public class HighestIPAddressInLastMinuteLeaderElectionAlgorithm implements LeaderElectionAlgorithm {

    /**
     * All candidates read the HOST_TABLE for the hosts that have heartbeated in the last one minute.
     * Sort by IP address in reverse natural and the first (highest) IP wins.
     * This method will returns true if this IP is the leader, else will return false.
     * A host that was elected leader and failed to heartbeat into the clique will get
     * dethroned by another host in the next minute (2 * heartbeats)
     *
     * @return
     */
    @Override
    public boolean isLeader() {
        throw new UnsupportedOperationException( "Implement me!" );
    }

    /**
     * No implementation needed
     */
    @Override
    public boolean electLeader() {
        return true;
    }

    @Override
    public String leaderIP() {
        throw new UnsupportedOperationException( "Implement me!" );
    }

    /**
     * Method to be called by a scheduled executor every 30 seconds.
     * Will update its record in DynamoDB table (HOST_TABLE)
     * and write the IP address (hash key) and timestamp (range key)
     */
    public void heartBeat() {
        throw new UnsupportedOperationException( "Implement me!" );
    }
}
