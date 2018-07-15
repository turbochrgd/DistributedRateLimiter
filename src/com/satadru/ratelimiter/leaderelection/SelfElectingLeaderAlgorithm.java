package com.satadru.ratelimiter.leaderelection;

public class SelfElectingLeaderAlgorithm implements LeaderElectionAlgorithm {
    @Override
    public boolean isLeader() {
        return true;
    }

    @Override
    public boolean electLeader() {
        return true;
    }

    @Override
    public String leaderIP() {
        return "127.0.0.1";
    }
}
