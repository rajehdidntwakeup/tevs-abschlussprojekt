package com.tevs.server.service;

import com.tevs.server.model.StatusMessage;
import com.tevs.server.repository.StatusRepository;
import com.tevs.server.replication.ReplicationEvent;
import com.tevs.server.replication.ReplicationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StatusService {

    private static final Logger log = LoggerFactory.getLogger(StatusService.class);

    private final StatusRepository repository;
    private final ReplicationPublisher publisher;
    private final String nodeId;

    public StatusService(StatusRepository repository,
                         ReplicationPublisher publisher,
                         @Value("${node.id:node-a}") String nodeId) {
        this.repository = repository;
        this.publisher = publisher;
        this.nodeId = nodeId;
    }

    public enum SaveOutcome { CREATED, UPDATED }

    public record SaveResult(SaveOutcome outcome, StatusMessage message) {}

    public SaveResult saveOrUpdate(StatusMessage status) {
        if (status.getTime() == null) {
            status.setTime(Instant.now());
        }

        boolean isNew = !repository.existsById(status.getUsername());
        StatusMessage saved = repository.save(status);

        try {
            String eventType = isNew ? "CREATE" : "UPDATE";
            publisher.publish(new ReplicationEvent(eventType, saved, nodeId, Instant.now()));
        } catch (Exception e) {
            log.error("Replication publish failed for {}: {}", status.getUsername(), e.getMessage());
        }

        return new SaveResult(isNew ? SaveOutcome.CREATED : SaveOutcome.UPDATED, saved);
    }

    @Transactional(readOnly = true)
    public Optional<StatusMessage> findByUsername(String username) {
        return repository.findById(username);
    }

    @Transactional(readOnly = true)
    public List<StatusMessage> findAll() {
        return repository.findAll();
    }

    public boolean deleteByUsername(String username) {
        if (!repository.existsById(username)) {
            return false;
        }
        repository.deleteById(username);

        StatusMessage tombstone = new StatusMessage();
        tombstone.setUsername(username);
        try {
            publisher.publish(new ReplicationEvent("DELETE", tombstone, nodeId, Instant.now()));
        } catch (Exception e) {
            log.error("Replication publish failed for DELETE {}: {}", username, e.getMessage());
        }

        return true;
    }

    // LWW: only save if incoming is strictly newer than local, or local is absent
    public boolean applyReplication(ReplicationEvent event) {
        if (event == null || event.getPayload() == null || event.getPayload().getUsername() == null) {
            return false;
        }

        if ("DELETE".equalsIgnoreCase(event.getEventType())) {
            String username = event.getPayload().getUsername();
            if (repository.existsById(username)) {
                repository.deleteById(username);
                log.info("Replication DELETE applied for {}", username);
            }
            return true;
        }

        StatusMessage incoming = event.getPayload();
        Optional<StatusMessage> existing = repository.findById(incoming.getUsername());

        if (existing.isPresent()) {
            Instant localTime = existing.get().getTime();
            Instant incomingTime = incoming.getTime();
            if (incomingTime == null || !incomingTime.isAfter(localTime)) {
                log.debug("Replication rejected (stale): {} local={} incoming={}",
                        incoming.getUsername(), localTime, incomingTime);
                return false;
            }
        }

        if (incoming.getTime() == null) {
            incoming.setTime(Instant.now());
        }

        repository.save(incoming);
        log.info("Replication applied for {} (eventType={})", incoming.getUsername(), event.getEventType());
        return true;
    }
}
