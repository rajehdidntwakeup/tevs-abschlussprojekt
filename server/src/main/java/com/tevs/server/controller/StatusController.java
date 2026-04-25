package com.tevs.server.controller;

import com.tevs.server.model.StatusMessage;
import com.tevs.server.service.NodeStateManager;
import com.tevs.server.service.StatusService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class StatusController {

    private final StatusService statusService;
    private final NodeStateManager nodeStateManager;
    private final String nodeId;

    public StatusController(StatusService statusService,
                            NodeStateManager nodeStateManager,
                            @Value("${node.id:node-a}") String nodeId) {
        this.statusService = statusService;
        this.nodeStateManager = nodeStateManager;
        this.nodeId = nodeId;
    }

    @PostMapping("/status")
    public ResponseEntity<?> saveOrUpdate(@Valid @RequestBody StatusMessage status) {
        StatusService.SaveResult result = statusService.saveOrUpdate(status);
        HttpStatus httpStatus = result.outcome() == StatusService.SaveOutcome.CREATED
                ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(result.message());
    }

    @GetMapping("/status/{username}")
    public ResponseEntity<StatusMessage> getByUsername(@PathVariable String username) {
        return statusService.findByUsername(username)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @DeleteMapping("/status/{username}")
    public ResponseEntity<Void> delete(@PathVariable String username) {
        boolean deleted = statusService.deleteByUsername(username);
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @GetMapping("/status")
    public List<StatusMessage> getAll() {
        return statusService.findAll();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "nodeId", nodeId,
                "nodeState", nodeStateManager.getState().name()
        ));
    }

    @GetMapping("/sync/all")
    public List<StatusMessage> syncAll() {
        return statusService.findAll();
    }
}
