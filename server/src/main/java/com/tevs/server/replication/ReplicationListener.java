package com.tevs.server.replication;

import com.tevs.server.model.StatusMessage;
import com.tevs.server.repository.StatusRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class ReplicationListener {

    private final StatusRepository repository;
    private final String nodeId;

    public ReplicationListener(StatusRepository repository,
                               @Value("${node.id:node-a}") String nodeId) {
        this.repository = repository;
        this.nodeId = nodeId;
    }

    @RabbitListener(queues = "#{statusQueue.name}")
    public void onEvent(ReplicationEvent event) {
        if (event == null || event.getPayload() == null) {
            return;
        }

        // Ignore self-published events to prevent loops
        if (nodeId.equals(event.getOriginNode())) {
            return;
        }

        StatusMessage payload = event.getPayload();
        String username = payload.getUsername();
        if (username == null) {
            return;
        }

        if ("DELETE".equalsIgnoreCase(event.getEventType())) {
            if (repository.existsById(username)) {
                repository.deleteById(username);
            }
            return;
        }

        // Last-Writer-Wins: only apply if incoming is strictly newer
        Optional<StatusMessage> existing = repository.findById(username);
        if (existing.isPresent()) {
            Instant localTime = existing.get().getTime();
            Instant incomingTime = payload.getTime();
            if (incomingTime == null || !incomingTime.isAfter(localTime)) {
                return;
            }
        }

        // Ensure all required fields are present before saving
        if (payload.getTime() == null) {
            payload.setTime(Instant.now());
        }

        repository.save(payload);
    }
}
