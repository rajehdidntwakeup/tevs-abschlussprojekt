package com.tevs.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class NodeStateManager {

    private static final Logger log = LoggerFactory.getLogger(NodeStateManager.class);

    private final AtomicReference<NodeState> state = new AtomicReference<>(NodeState.BOOTSTRAPPING);

    public NodeState getState() {
        return state.get();
    }

    public void setState(NodeState newState) {
        NodeState oldState = state.getAndSet(newState);
        if (oldState != newState) {
            log.info("Node state transition: {} -> {}", oldState, newState);
        }
    }

    public boolean isActive() {
        return state.get() == NodeState.ACTIVE;
    }

    public boolean isBootstrapping() {
        return state.get() == NodeState.BOOTSTRAPPING;
    }
}
