package com.tevs.server.service;

import com.tevs.server.model.StatusMessage;
import com.tevs.server.repository.StatusRepository;
import com.tevs.server.replication.ReplicationEvent;
import com.tevs.server.replication.ReplicationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusServiceTest {

    @Mock
    private StatusRepository repository;

    @Mock
    private ReplicationPublisher publisher;

    @Captor
    private ArgumentCaptor<ReplicationEvent> eventCaptor;

    private StatusService service;
    private StatusMessage sample;

    @BeforeEach
    void setUp() {
        service = new StatusService(repository, publisher, "node-a");
        sample = new StatusMessage("alice", "Hello", Instant.now(), 48.2, 16.4);
    }

    @Test
    void saveOrUpdateCreatesNewRecord() {
        when(repository.existsById("alice")).thenReturn(false);
        when(repository.save(any())).thenReturn(sample);

        StatusService.SaveResult result = service.saveOrUpdate(sample);

        assertThat(result.outcome()).isEqualTo(StatusService.SaveOutcome.CREATED);
        verify(publisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("CREATE");
    }

    @Test
    void saveOrUpdateUpdatesExisting() {
        when(repository.existsById("alice")).thenReturn(true);
        when(repository.save(any())).thenReturn(sample);

        StatusService.SaveResult result = service.saveOrUpdate(sample);

        assertThat(result.outcome()).isEqualTo(StatusService.SaveOutcome.UPDATED);
        verify(publisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("UPDATE");
    }

    @Test
    void saveOrUpdateAutoFillsTime() {
        sample.setTime(null);
        when(repository.existsById("alice")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StatusService.SaveResult result = service.saveOrUpdate(sample);

        assertThat(result.message().getTime()).isNotNull();
    }

    @Test
    void saveOrUpdatePublishesReplicationEvent() {
        when(repository.existsById("alice")).thenReturn(false);
        when(repository.save(any())).thenReturn(sample);

        service.saveOrUpdate(sample);

        verify(publisher).publish(eventCaptor.capture());
        ReplicationEvent event = eventCaptor.getValue();
        assertThat(event.getOriginNode()).isEqualTo("node-a");
        assertThat(event.getPayload()).isEqualTo(sample);
    }

    @Test
    void saveOrUpdateHandlesPublishFailureGracefully() {
        when(repository.existsById("alice")).thenReturn(false);
        when(repository.save(any())).thenReturn(sample);
        doThrow(new RuntimeException("RabbitMQ down")).when(publisher).publish(any());

        StatusService.SaveResult result = service.saveOrUpdate(sample);

        // Replication failure must not break the client request
        assertThat(result.outcome()).isEqualTo(StatusService.SaveOutcome.CREATED);
    }

    @Test
    void findByUsernameReturnsRecord() {
        when(repository.findById("alice")).thenReturn(Optional.of(sample));
        assertThat(service.findByUsername("alice")).isPresent();
    }

    @Test
    void findByUsernameReturnsEmptyWhenMissing() {
        when(repository.findById("nobody")).thenReturn(Optional.empty());
        assertThat(service.findByUsername("nobody")).isEmpty();
    }

    @Test
    void findAllReturnsAll() {
        when(repository.findAll()).thenReturn(List.of(sample));
        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    void deleteByUsernameDeletesAndPublishes() {
        when(repository.existsById("alice")).thenReturn(true);

        boolean result = service.deleteByUsername("alice");

        assertThat(result).isTrue();
        verify(repository).deleteById("alice");
        verify(publisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("DELETE");
    }

    @Test
    void deleteByUsernameReturnsFalseWhenMissing() {
        when(repository.existsById("nobody")).thenReturn(false);

        boolean result = service.deleteByUsername("nobody");

        assertThat(result).isFalse();
        verify(repository, never()).deleteById(any());
    }

    @Test
    void applyReplicationRejectsStaleEvent() {
        Instant oldTime = Instant.now().minusSeconds(60);
        Instant newerLocal = Instant.now();
        StatusMessage incoming = new StatusMessage("alice", "Old", oldTime, 48.2, 16.4);
        StatusMessage local = new StatusMessage("alice", "Newer", newerLocal, 48.2, 16.4);
        when(repository.findById("alice")).thenReturn(Optional.of(local));

        ReplicationEvent event = new ReplicationEvent("UPDATE", incoming, "node-b", Instant.now());
        boolean applied = service.applyReplication(event);

        assertThat(applied).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void applyReplicationAcceptsNewerEvent() {
        Instant newerTime = Instant.now();
        Instant olderLocal = Instant.now().minusSeconds(60);
        StatusMessage incoming = new StatusMessage("alice", "Newer", newerTime, 48.2, 16.4);
        StatusMessage local = new StatusMessage("alice", "Older", olderLocal, 48.2, 16.4);
        when(repository.findById("alice")).thenReturn(Optional.of(local));

        ReplicationEvent event = new ReplicationEvent("UPDATE", incoming, "node-b", Instant.now());
        boolean applied = service.applyReplication(event);

        assertThat(applied).isTrue();
        verify(repository).save(incoming);
    }

    @Test
    void applyReplicationAcceptsNewRecord() {
        when(repository.findById("alice")).thenReturn(Optional.empty());

        ReplicationEvent event = new ReplicationEvent("UPDATE", sample, "node-b", Instant.now());
        boolean applied = service.applyReplication(event);

        assertThat(applied).isTrue();
        verify(repository).save(sample);
    }

    @Test
    void applyReplicationHandlesDelete() {
        when(repository.existsById("alice")).thenReturn(true);
        StatusMessage tombstone = new StatusMessage();
        tombstone.setUsername("alice");

        ReplicationEvent event = new ReplicationEvent("DELETE", tombstone, "node-b", Instant.now());
        boolean applied = service.applyReplication(event);

        assertThat(applied).isTrue();
        verify(repository).deleteById("alice");
    }

    @Test
    void applyReplicationHandlesDeleteOfMissing() {
        when(repository.existsById("alice")).thenReturn(false);
        StatusMessage tombstone = new StatusMessage();
        tombstone.setUsername("alice");

        ReplicationEvent event = new ReplicationEvent("DELETE", tombstone, "node-b", Instant.now());
        boolean applied = service.applyReplication(event);

        assertThat(applied).isTrue();
        verify(repository, never()).deleteById(any());
    }
}
