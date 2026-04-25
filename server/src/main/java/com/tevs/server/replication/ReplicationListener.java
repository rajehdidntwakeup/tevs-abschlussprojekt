package com.tevs.server.replication;

import com.tevs.server.service.NodeStateManager;
import com.tevs.server.service.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReplicationListener {

    private static final Logger log = LoggerFactory.getLogger(ReplicationListener.class);

    private final StatusService statusService;
    private final NodeStateManager nodeStateManager;
    private final String nodeId;

    public ReplicationListener(StatusService statusService,
                               NodeStateManager nodeStateManager,
                               @Value("${node.id:node-a}") String nodeId) {
        this.statusService = statusService;
        this.nodeStateManager = nodeStateManager;
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

        // Drop replication events during bootstrap to avoid race with bulk insert.
        // Bootstrap is authoritative — peer already replicated these events.
        if (nodeStateManager.isBootstrapping()) {
            log.debug("Dropping replication event during bootstrap: type={} from={}",
                    event.getEventType(), event.getOriginNode());
            return;
        }

        statusService.applyReplication(event);
        log.debug("Processed replication event type={} from {}", event.getEventType(), event.getOriginNode());
    }
}
