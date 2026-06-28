package com.aspireserver.smp.trade;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TradeManager {

    private final Map<UUID, UUID> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, TradeSession> activeTrades = new ConcurrentHashMap<>();

    public void sendRequest(UUID sender, UUID target) {
        pendingRequests.put(sender, target);
    }

    public boolean hasPendingRequest(UUID sender, UUID target) {
        return target.equals(pendingRequests.get(sender));
    }

    public void removeRequest(UUID sender) {
        pendingRequests.remove(sender);
    }

    public UUID getPendingTarget(UUID sender) {
        return pendingRequests.get(sender);
    }

    public TradeSession createSession(UUID player1, UUID player2) {
        TradeSession session = new TradeSession(player1, player2);
        activeTrades.put(player1, session);
        activeTrades.put(player2, session);
        return session;
    }

    public TradeSession getSession(UUID player) {
        return activeTrades.get(player);
    }

    public void endSession(TradeSession session) {
        activeTrades.remove(session.getPlayer1());
        activeTrades.remove(session.getPlayer2());
    }

    public boolean isInTrade(UUID player) {
        return activeTrades.containsKey(player);
    }
}
