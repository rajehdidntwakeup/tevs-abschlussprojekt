package com.tevs.server.replication;

import com.tevs.server.model.StatusMessage;
import java.time.Instant;

public class ReplicationEvent {
    private String eventType;
    private StatusMessage payload;
    private String originNode;
    private Instant timestamp;

    public ReplicationEvent() {}

    public ReplicationEvent(String eventType, StatusMessage payload, String originNode, Instant timestamp) {
        this.eventType = eventType;
        this.payload = payload;
        this.originNode = originNode;
        this.timestamp = timestamp;
    }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public StatusMessage getPayload() { return payload; }
    public void setPayload(StatusMessage payload) { this.payload = payload; }

    public String getOriginNode() { return originNode; }
    public void setOriginNode(String originNode) { this.originNode = originNode; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
