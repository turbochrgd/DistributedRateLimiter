package com.satadru.ratelimiter.leaderelection;

public interface LeaderElectionAlgorithm {

    boolean isLeader();

    boolean electLeader();

    String leaderIP();

}
