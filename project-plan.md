# Project Plan: Distributed Command Center (TEVS Final Project)

> **Project:** Technologies of Distributed Systems (TEVS) - Final Project
> **Date:** 2026-04-21
> **Team Size:** max. 3 persons
> **Goal:** Build a fault-tolerant, replicated status-message store with a web-based map client.

---

## 1. Executive Summary

Develop a distributed application consisting of multiple redundant **Status Server Nodes** and a **Web Client**. Users can create, update, delete, and retrieve text-based status messages containing geodata (latitude/longitude). The backend must replicate data across nodes with custom replication logic, survive single-node failures without data loss or client outage, and guarantee eventual consistency within 15 seconds.

---

## 2. Technology Stack

| Layer | Technology | Justification |
|-------|------------|---------------|
| **Backend** | Java 21 + Spring Boot 3.x | Mature ecosystem, built-in embedded server, easy REST/WebSocket support, explicit threading control. |
| **Per-Node Store** | H2 Database (file or in-memory mode) | Explicitly allowed by specification; zero external installation; SQL-like querying. |
| **Inter-Node Messaging** | RabbitMQ (single instance) | Allowed as communication medium only; lightweight; easy pub/sub for replication broadcasts. |
| **Client<->Server Protocol** | REST (HTTPS/TLS) | Simple, stateless, universally supported. JSON payload. |
| **Frontend** | React + Vite + Leaflet.js | Fast setup; Leaflet is a standard, lightweight map library; no build restrictions. |
| **Security** | Self-signed TLS certificates (Java keystore) | Fulfills transport encryption requirement without external CA. |
| **Build Tool** | Maven | Standard for Spring Boot; dependency management for H2, Spring Web, Spring AMQP. |
| **Containerization (optional)** | Docker + Docker Compose | Simplifies demo deployment of multiple nodes + RabbitMQ on one or many hosts. |

---

## 3. System Architecture

### 3.1 High-Level Diagram

```text
+----------------+         +----------------+         +----------------+
|   Client 1     |<------->|  Node A (SB)   |<------->|  Node B (SB)   |
|  (React/HTTPS) |         |   H2 + TLS     |         |   H2 + TLS     |
+----------------+         +--------+-------+         +--------+-------+
                                  |   RabbitMQ (Sync Bus)   |
                                  +-------------------------+
                                  |                         |
                           +------v-------+          +------v-------+
                           |  Node C (SB) |          |  Node D (SB) |
                           |   H2 + TLS   |          |   H2 + TLS   |
                           +--------------+          +--------------+
```

### 3.2 Design Decisions

* **Decentralized writes:** Any node can accept client write/read requests (no master/slave).
* **Total-order broadcast via topic:** RabbitMQ topic exchange `status.sync` broadcasts every mutation (CREATE, UPDATE, DELETE) to all online nodes.
* **Last-Writer-Wins (LWW):** Conflict resolution uses the `time` attribute (ISO 8601). If a replicated message has an older timestamp than the local record, it is discarded.
* **Bootstrapping / Grace Period:** On startup, a node enters `BOOTSTRAPPING` state. It queries a random peer via REST for the full state, populates H2, then switches to `ACTIVE`.
* **Node Liveness:** Simple heartbeat or peer-list in configuration. Nodes treat missing peers as failed and continue operating.
* **Client Fault Tolerance:** Client holds a list of all node endpoints. If a request fails (connection refused, timeout), it retries the next node in the list. Local cache is optional for read resilience.

---

## 4. Data Model

```json
{
  "username": "RECON-01",
  "statustext": "On the way to the mission",
  "time": "2026-03-03T13:30:00+01:00",
  "latitude": 48.2150,
  "longitude": 16.3850
}
```

**Persistence:** H2 table `status_messages`
- `username` VARCHAR(255) PRIMARY KEY
- `statustext` VARCHAR(1000)
- `time` TIMESTAMP
- `latitude` DOUBLE
- `longitude` DOUBLE

---

## 5. API Specification (REST + TLS)

All endpoints return JSON and require HTTPS.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/status` | Create or update a status (body: JSON status). |
| `GET` | `/api/status/{username}` | Retrieve a single status by username. |
| `DELETE` | `/api/status/{username}` | Delete a status by username. |
| `GET` | `/api/status` | Retrieve **all** status messages (feed). |
| `GET` | `/api/health` | Health check for load balancing / failover detection. |
| `GET` | `/api/sync/all` | **Internal** endpoint used only during bootstrapping to fetch full dataset from a peer. |

---

## 6. Replication Protocol

### 6.1 Mutation Flow

1. Client sends `POST /api/status` to **Node A**.
2. Node A validates payload, updates local H2 with LWW check, and publishes a `StatusEvent` to RabbitMQ exchange `status.sync` with routing key `status.update`.
3. All other active nodes (B, C, D) consume the event, perform LWW validation against local H2, and update/delete accordingly.
4. Node A returns `200 OK` to client after local commit (fire-and-forget broadcast acceptable for eventual consistency).

### 6.2 Event Schema (RabbitMQ)

```json
{
  "eventType": "CREATE|UPDATE|DELETE",
  "payload": { <Status Object> },
  "originNode": "node-a",
  "timestamp": "2026-04-21T10:00:00Z"
}
```

### 6.3 Bootstrapping Flow

1. New node starts, state = `BOOTSTRAPPING`.
2. It requests `GET /api/sync/all` from the first reachable peer.
3. It bulk-inserts the data into H2.
4. It connects to RabbitMQ and starts listening.
5. State switches to `ACTIVE`; client endpoints are now accessible.

---

## 7. Implementation Roadmap

### Phase 0: Project Setup (Day 1)
- [ ] Initialize Maven multi-module or single-module project (`server`, `client`).
- [ ] Add dependencies: Spring Web, Spring AMQP, H2, Lombok (optional), Jackson JSR-310.
- [ ] Configure `application.yml` with node identity (`node.id`, `server.port`, `ssl.enabled`), RabbitMQ connection, and peer list.
- [ ] Generate self-signed TLS certificate and configure Spring Boot embedded Tomcat for HTTPS.
- [ ] Set up React project with Leaflet and a simple dev proxy.

### Phase 1: Core Server & Persistence (Days 2-3)
- [ ] Implement JPA / JDBC repository for `StatusMessage` on H2.
- [ ] Implement REST controllers (`POST`, `GET`, `DELETE`, `/api/status`).
- [ ] Implement input validation (username uniqueness, lat/lon bounds, mandatory fields).
- [ ] Unit tests for repository and controller (WebTestClient).

### Phase 2: Custom Replication & Sync (Days 4-6)
- [ ] Configure RabbitMQ connection, exchange, and queues (one per node or shared topic).
- [ ] Implement `ReplicationService` that publishes `StatusEvent` after every local mutation.
- [ ] Implement event listener that consumes `StatusEvent`, performs LWW check, and applies to H2.
- [ ] Implement bootstrapping endpoint `/api/sync/all` and `BootstrapService`.
- [ ] Handle edge cases: duplicate bootstrap, node reconnect, out-of-order messages.
- [ ] Integration tests with Testcontainers or embedded RabbitMQ.

### Phase 3: Client Application (Days 7-9)
- [ ] Build React UI:
  - Form to set/update status (username, text, lat, lon) with map click-to-set-location.
  - Feed page listing all statuses.
  - Map view showing all status markers.
  - Detail view for single user with map focus.
  - Delete button with confirmation.
- [ ] Implement client-side failover: maintain list of node URLs, retry on failure with timeout.
- [ ] Enforce HTTPS requests (self-signed cert acceptance in dev mode / proper CA in prod).
- [ ] End-to-end tests (manual or Cypress).

### Phase 4: Fault Tolerance & Resilience (Day 10)
- [ ] Verify client behavior when a node is killed (retry, no crash).
- [ ] Verify data consistency after node restart (bootstrapping reloads state).
- [ ] Verify replication under concurrent edits (LWW determinism).
- [ ] Test split-brain scenarios (e.g., RabbitMQ down briefly) and document behavior.
- [ ] Add health check endpoint and client usage of it.

### Phase 5: Security & Polish (Day 11)
- [ ] Confirm all REST and web interfaces use TLS/HTTPS.
- [ ] Add CORS configuration if needed (client on different host/port).
- [ ] Final code cleanup, documentation (`README-DEV.md`), and build instructions.
- [ ] Prepare demo script (start 2+ nodes, RabbitMQ, client; show create, kill node, read from other node).

### Phase 6: Final Review & Submission (Day 12)
- [ ] Run full grading rubric self-check:
  - Functionality (50%): all CRUD operations, list, map visualization, TLS.
  - Replication mechanism (15%): custom logic, LWW, <15s consistency.
  - Fault tolerance (15%): n+1 availability, client retry, bootstrapping.
  - Individual knowledge prep (20%): architecture diagrams, protocol explanation.
- [ ] Record demo video or prepare live presentation.

---

## 8. Folder Structure

```
tevs_abschlussprojekt/
├── server/
│   ├── src/main/java/com/tevs/server/
│   │   ├── ServerApplication.java
│   │   ├── config/
│   │   ├── controller/
│   │   ├── model/
│   │   ├── repository/
│   │   ├── service/
│   │   └── replication/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── keystore.p12
│   └── pom.xml
├── client/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── api/
│   │   └── App.jsx
│   ├── public/
│   └── package.json
├── docker-compose.yml          # RabbitMQ + 2-4 server nodes
└── project-plan.md
```

---

## 9. Risk Mitigation & Tips

| Risk | Mitigation |
|------|------------|
| **Clock skew breaks LWW** | Use NTP on all hosts or include logical clocks (Lamport / vector clocks) alongside wall-clock time. |
| **RabbitMQ becomes a single point of failure** | Allowed per spec (middleware need not be HA), but document this decision. If time permits, run RabbitMQ cluster. |
| **Self-signed TLS issues in browser** | Document how to import the certificate or use `mkcert` for local dev trust. |
| **Concurrent bootstrapping + replication race** | Lock local DB during bootstrap or ignore replication events until bootstrap finishes. |
| **Map library bundle size** | Use Leaflet (lightweight); avoid heavy GIS libraries. |

---

## 10. Grading Alignment Checklist

| Grading Criterion | How the Plan Addresses It |
|-------------------|---------------------------|
| **Functionality & required functions (50%)** | Full CRUD via REST, list endpoint, bootstrapping, map visualization, TLS everywhere. |
| **Replication mechanism (15%)** | Custom RabbitMQ pub/sub with LWW validation; no third-party replication library. |
| **Fault tolerance (15%)** | n+1 node redundancy, client retry logic, graceful bootstrapping, no SPoF in backend. |
| **Individual knowledge (20%)** | Clear architecture docs, protocol sequence diagrams, code comments on replication logic. |

---

## 11. Next Immediate Actions

1. **Agree on team roles** (e.g., Backend, Frontend, Integration/Test).
2. **Initialize the repository structure** (Maven + React) and commit.
3. **Set up local RabbitMQ** via Docker: `docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management`.
4. **Generate TLS keystore** and verify Spring Boot HTTPS startup.
5. **Begin Phase 1** (H2 + REST endpoints).
