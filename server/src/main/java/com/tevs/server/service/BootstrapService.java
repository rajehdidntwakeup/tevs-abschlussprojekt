package com.tevs.server.service;

import com.tevs.server.model.StatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class BootstrapService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    private final StatusService statusService;
    private final NodeStateManager nodeStateManager;
    private final RestTemplate restTemplate;
    private final List<String> peerUrls;

    public BootstrapService(StatusService statusService,
                            NodeStateManager nodeStateManager,
                            RestTemplateBuilder restTemplateBuilder,
                            @Value("${node.peers:}") String peers) {
        this.statusService = statusService;
        this.nodeStateManager = nodeStateManager;
        this.restTemplate = restTemplateBuilder
                .rootUri("")
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.peerUrls = peers == null || peers.isBlank()
                ? Collections.emptyList()
                : Arrays.asList(peers.split(","));
    }

    @PostConstruct
    public void bootstrap() {
        log.info("Starting bootstrap. Peer list: {}", peerUrls);

        if (peerUrls.isEmpty()) {
            log.warn("No peers configured. Skipping bootstrap, transitioning to ACTIVE.");
            nodeStateManager.setState(NodeState.ACTIVE);
            return;
        }

        for (String peer : peerUrls) {
            String url = peer.trim() + "/api/sync/all";
            log.info("Attempting bootstrap from peer: {}", url);

            try {
                ResponseEntity<List<StatusMessage>> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<StatusMessage>>() {}
                );

                List<StatusMessage> records = response.getBody();
                if (records == null || records.isEmpty()) {
                    log.info("Peer {} returned no records", peer);
                    nodeStateManager.setState(NodeState.ACTIVE);
                    return;
                }

                int synced = 0;
                for (StatusMessage record : records) {
                    statusService.saveOrUpdate(record);
                    synced++;
                }

                log.info("Bootstrap complete. Synced {} records from peer {}", synced, peer);
                nodeStateManager.setState(NodeState.ACTIVE);
                return;

            } catch (Exception e) {
                log.warn("Failed to bootstrap from peer {}: {}", peer, e.getMessage());
            }
        }

        log.warn("All peers unreachable. Transitioning to ACTIVE with local state only.");
        nodeStateManager.setState(NodeState.ACTIVE);
    }
}
