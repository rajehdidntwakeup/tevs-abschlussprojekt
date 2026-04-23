package com.tevs.server.controller;

import com.tevs.server.model.StatusMessage;
import com.tevs.server.repository.StatusRepository;
import com.tevs.server.replication.ReplicationEvent;
import com.tevs.server.replication.ReplicationPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class StatusController {

    private final StatusRepository repository;
    private final ReplicationPublisher publisher;
    private final String nodeId;

    public StatusController(StatusRepository repository,
                            ReplicationPublisher publisher,
                            @Value("${node.id:node-a}") String nodeId) {
        this.repository = repository;
        this.publisher = publisher;
        this.nodeId = nodeId;
    }

    @PostMapping("/status")
    public ResponseEntity<?> saveOrUpdate(@RequestBody StatusMessage status) {
        String validationError = validateStatusMessage(status);
        if (validationError != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", validationError));
        }

        if (status.getTime() == null) {
            status.setTime(Instant.now());
        }

        boolean isNew = !repository.existsById(status.getUsername());
        StatusMessage saved = repository.save(status);

        try {
            publisher.publish(new ReplicationEvent("UPDATE", saved, nodeId, Instant.now()));
        } catch (Exception e) {
            // Log replication failure but don't fail the client request
            System.err.println("Replication publish failed: " + e.getMessage());
        }

        return ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK).body(saved);
    }

    @GetMapping("/status/{username}")
    public ResponseEntity<StatusMessage> getByUsername(@PathVariable String username) {
        Optional<StatusMessage> result = repository.findById(username);
        return result.map(ResponseEntity::ok)
                     .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @DeleteMapping("/status/{username}")
    public ResponseEntity<Void> delete(@PathVariable String username) {
        if (!repository.existsById(username)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        repository.deleteById(username);

        StatusMessage tombstone = new StatusMessage();
        tombstone.setUsername(username);
        try {
            publisher.publish(new ReplicationEvent("DELETE", tombstone, nodeId, Instant.now()));
        } catch (Exception e) {
            System.err.println("Replication publish failed: " + e.getMessage());
        }

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    public List<StatusMessage> getAll() {
        return repository.findAll();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "nodeId", nodeId));
    }

    @GetMapping("/sync/all")
    public List<StatusMessage> syncAll() {
        return repository.findAll();
    }

    private String validateStatusMessage(StatusMessage status) {
        if (status == null) {
            return "Request body is required";
        }
        if (status.getUsername() == null || status.getUsername().isBlank()) {
            return "username is required";
        }
        if (status.getStatustext() == null || status.getStatustext().isBlank()) {
            return "statustext is required";
        }
        if (status.getLatitude() == null) {
            return "latitude is required";
        }
        if (status.getLatitude() < -90.0 || status.getLatitude() > 90.0) {
            return "latitude must be between -90 and 90";
        }
        if (status.getLongitude() == null) {
            return "longitude is required";
        }
        if (status.getLongitude() < -180.0 || status.getLongitude() > 180.0) {
            return "longitude must be between -180 and 180";
        }
        return null;
    }
}
