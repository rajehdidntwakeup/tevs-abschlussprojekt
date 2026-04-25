package com.tevs.server.replication;

import com.tevs.server.model.StatusMessage;
import com.tevs.server.service.NodeStateManager;
import com.tevs.server.service.NodeState;
import com.tevs.server.service.StatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplicationListenerTest {

    @Mock
    private StatusService statusService;

    @Mock
    private NodeStateManager nodeStateManager;

    private ReplicationListener listener;
    private ReplicationEvent event;

    @BeforeEach
    void setUp() {
        listener = new ReplicationListener(statusService, nodeStateManager, "node-a");
        StatusMessage payload = new StatusMessage("alice", "Hello", Instant.now(), 48.2, 16.4);
        event = new ReplicationEvent("UPDATE", payload, "node-b", Instant.now());
    }

    @Test
    void ignoresSelfPublishedEvents() {
        ReplicationEvent selfEvent = new ReplicationEvent("UPDATE", new StatusMessage(), "node-a", Instant.now());

        listener.onEvent(selfEvent);

        verify(statusService, never()).applyReplication(any());
    }

    @Test
    void ignoresNullEvent() {
        listener.onEvent(null);
        verify(statusService, never()).applyReplication(any());
    }

    @Test
    void ignoresEventWithNullPayload() {
        listener.onEvent(new ReplicationEvent("UPDATE", null, "node-b", Instant.now()));
        verify(statusService, never()).applyReplication(any());
    }

    @Test
    void forwardsEventsToService() {
        when(nodeStateManager.isBootstrapping()).thenReturn(false);

        listener.onEvent(event);

        verify(statusService).applyReplication(event);
    }

    @Test
    void dropsEventsDuringBootstrap() {
        when(nodeStateManager.isBootstrapping()).thenReturn(true);

        listener.onEvent(event);

        verify(statusService, never()).applyReplication(any());
    }

    @Test
    void forwardsDeleteEvents() {
        when(nodeStateManager.isBootstrapping()).thenReturn(false);
        StatusMessage tombstone = new StatusMessage();
        tombstone.setUsername("alice");
        ReplicationEvent deleteEvent = new ReplicationEvent("DELETE", tombstone, "node-b", Instant.now());

        listener.onEvent(deleteEvent);

        verify(statusService).applyReplication(deleteEvent);
    }
}
