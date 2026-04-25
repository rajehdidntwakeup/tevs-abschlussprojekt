# Completion Plan — TEVS Distributed Command Center

> **Generated:** 2026-04-25
> **Source:** `docs/deep-analysis.md`, `project-tasks.md`, full codebase review
> **Goal:** Step-by-step tasks to finish all unfinished project work, ordered by dependency and grading impact.

---

## Overview

**Total remaining work items:** 25, organized into 7 execution phases.

### Priority Legend

| Icon | Meaning |
|------|---------|
| 🔴 **Critical** | Blocks grading criteria (TLS, bootstrapping, tests) |
| 🟡 **Important** | Improves correctness, code quality, or grade confidence |
| 🔵 **Polish** | Nice-to-have for submission polish |

### Execution Order Rationale

1. **Phase 1** (TLS) first — quick win, high grading impact, no code dependencies
2. **Phase 2** (Service layer) — foundational refactor that both controller and listener need
3. **Phase 3** (Node lifecycle + Bootstrap) — the core missing feature, depends on Phase 2
4. **Phase 4** (Quality) — logging, validation, race condition fixes
5. **Phase 5** (Tests) — only possible once code is stable
6. **Phase 6** (Client) — remaining client features
7. **Phase 7** (Demo infrastructure + submission) — Docker, docs, final checks

---

## Phase 1: TLS/HTTPS 🔴

> **Grading impact:** 50% Functionality — TLS is explicitly required
> **Effort:** ~1h

### Task 1.1 — Generate self-signed PKCS12 keystore

```bash
keytool -genkeypair -alias tevs -keyalg RSA -keysize 4096 \
  -storetype PKCS12 -keystore server/src/main/resources/keystore.p12 \
  -validity 365 -storepass tevs-demo \
  -dname "CN=localhost, OU=TEVS, O=TEVS, L=Unknown, ST=Unknown, C=AT"
```

### Task 1.2 — Configure TLS in application.yml

Replace `server.ssl.enabled: false` with:

```yaml
server:
  port: ${SERVER_PORT:8443}
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: tevs-demo
    key-store-type: PKCS12
    key-alias: tevs
```

### Task 1.3 — Add HTTP→HTTPS redirect (optional for dev)

Add a `@Configuration` class that starts a secondary Tomcat connector on port 8080 and redirects to HTTPS.

### Task 1.4 — Update apiClient.js URLs

Change `http://localhost:...` to `https://localhost:...` in `client/src/apiClient.js`.

### Task 1.5 — Update Vite dev proxy config (if used)

Ensure `vite.config.js` proxy target uses `https:` and ignores cert warnings (`secure: false`).

### Verification

```bash
curl -k https://localhost:8443/api/health
# → {"status":"UP","nodeId":"node-a"}
```

---

## Phase 2: Service Layer 🟡

> **Prerequisite for:** Phase 3 (BootstrapService), Phase 5 (Tests)
> **Effort:** ~2h
> **Why:** Controller + Listener both need shared business logic. Must exist before BootstrapService can use it.

### Task 2.1 — Create `server/src/main/java/com/tevs/server/service/StatusService.java`

Extract business logic from `StatusController` into a service class:

- `saveOrUpdate(StatusMessage)` — validate, auto-fill time, save via repository, publish replication event
- `findByUsername(String)` — delegate to repository, return `Optional`
- `findAll()` — delegate to repository
- `deleteByUsername(String)` — delete, publish DELETE replication event
- Return wrappers: `StatusService.SaveResult` (status: CREATED/UPDATED, message: StatusMessage)

### Task 2.2 — Update StatusController to use StatusService

- Inject `StatusService` instead of `StatusRepository` + `ReplicationPublisher`
- Endpoints become thin delegates to the service

### Task 2.3 — Update ReplicationListener to use StatusService

- Inject `StatusService` for LWW save and delete operations
- Removes direct `StatusRepository` dependency from both controller and listener

### Files Affected

- `server/src/main/java/com/tevs/server/service/StatusService.java` (new)
- `server/src/main/java/com/tevs/server/controller/StatusController.java` (refactor)
- `server/src/main/java/com/tevs/server/replication/ReplicationListener.java` (refactor)

---

## Phase 3: Node Lifecycle + BootstrapService 🔴

> **Grading impact:** 15% Fault Tolerance — bootstrapping is explicitly tested in Phase 4
> **Depends on:** Phase 2 (StatusService)
> **Effort:** ~4h

### Task 3.1 — Create `NodeState` enum

```java
package com.tevs.server.service;

public enum NodeState {
    BOOTSTRAPPING,
    ACTIVE
}
```

### Task 3.2 — Create `NodeStateManager` bean

```java
@Component
public class NodeStateManager {
    private final AtomicReference<NodeState> state = new AtomicReference<>(NodeState.BOOTSTRAPPING);
    // getState(), setState(), isActive(), isBootstrapping()
    // Log state transitions
}
```

### Task 3.3 — Add peer list configuration

Add to `application.yml`:

```yaml
node:
  id: ${NODE_ID:node-a}
  peers: ${NODE_PEERS:}
```

### Task 3.4 — Create `BootstrapService`

```java
@Component
public class BootstrapService {
    @PostConstruct
    public void bootstrap() {
        // 1. Read node.peers list
        // 2. For each peer URL, try GET /api/sync/all
        // 3. On first successful response, bulk-insert using LWW via StatusService
        // 4. Set NodeState to ACTIVE
        // 5. Log all state transitions
    }
}
```

Key behaviors:
- Must be resilient: if no peer is reachable, log warning but transition to ACTIVE anyway (standalone mode)
- Must handle empty peer list gracefully
- Log the number of records synced

### Task 3.5 — Add 503 filter during BOOTSTRAPPING

Create `BootstrapFilter` or use `HandlerInterceptor`:

```java
@Component
public class BootstrapFilter implements Filter {
    public void doFilter(request, response, chain) {
        if (stateManager.isBootstrapping()
            && requestURI starts with "/api/status"
            && requestURI != "/api/health"
            && requestURI != "/api/sync/all") {
            response.setStatus(503);
            response.getWriter().write("{\"error\":\"Node bootstrapping, try again later\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
```

Exceptions: `/api/health` (must remain reachable), `/api/sync/all` (needed by peers).

### Task 3.6 — Update health endpoint

Add `nodeState` field to `/api/health` response:

```json
{"status":"UP","nodeId":"node-a","nodeState":"ACTIVE"}
```

### Task 3.7 — Handle bootstrap + replication race

Option A (simpler): Defer RabbitMQ listener processing until ACTIVE using a queue + polling.

Option B (simplest for PoC): In `ReplicationListener.onEvent()`, check `nodeStateManager.isActive()` and silently drop events if still bootstrapping (the bootstrap bulk-insert is authoritative, and the peer already replicated these events).

### Files Created/Modified

- `server/src/main/java/com/tevs/server/service/NodeState.java` (new)
- `server/src/main/java/com/tevs/server/service/NodeStateManager.java` (new)
- `server/src/main/java/com/tevs/server/service/BootstrapService.java` (new)
- `server/src/main/java/com/tevs/server/config/BootstrapFilter.java` (new)
- `server/src/main/java/com/tevs/server/controller/StatusController.java` (modify health endpoint)
- `server/src/main/resources/application.yml` (add `node.peers`)

---

## Phase 4: Code Quality & Robustness 🟡

> **Effort:** ~2h

### Task 4.1 — Replace `System.err` with SLF4J

All files: `StatusController`, `ReplicationPublisher`, `ReplicationListener`, `BootstrapService`.

```java
private static final Logger log = LoggerFactory.getLogger(getClass());
log.error("Replication publish failed: {}", e.getMessage());
```

### Task 4.2 — Add Bean Validation annotations to StatusMessage

```java
@NotBlank(message = "username is required")
private String username;

@NotBlank(message = "statustext is required")
private String statustext;

@NotNull(message = "latitude is required")
@Min(-90) @Max(90)
private Double latitude;

@NotNull(message = "longitude is required")
@Min(-180) @Max(180)
private Double longitude;
```

Add `@Valid` to `@RequestBody` in controller's POST endpoint. Keep `GlobalExceptionHandler` to handle `MethodArgumentNotValidException` → 400.

### Task 4.3 — Remove manual validation

Delete `StatusController.validateStatusMessage()` — replaced by Bean Validation annotations.

### Task 4.4 — Fix DELETE tombstone null fields

Instead of creating a `StatusMessage` with only `username`, add a dedicated `DeleteEvent` or ensure the listener handles null fields defensively (already does, but make it explicit).

### Task 4.5 — Add `CREATE` vs `UPDATE` distinction in event type

Currently everything is `"UPDATE"`. Change to:
- `"CREATE"` when `!existsById(username)`
- `"UPDATE"` when `existsById(username)`

---

## Phase 5: Tests 🟡

> **Effort:** ~4h
> **Depends on:** Phase 2 (StatusService), Phase 3 (BootstrapService), Phase 4 (Bean Validation)

### Task 5.1 — Set up test infrastructure

Create test directories:
- `server/src/test/java/com/tevs/server/repository/`
- `server/src/test/java/com/tevs/server/controller/`
- `server/src/test/java/com/tevs/server/service/`
- `server/src/test/java/com/tevs/server/replication/`

Use `@SpringBootTest` with `@AutoConfigureMockMvc` for integration tests.

### Task 5.2 — Repository tests

```java
@DataJpaTest
class StatusRepositoryTest {
    // CRUD operations
    // Find by ID
    // Empty DB returns empty list
}
```

### Task 5.3 — StatusService unit tests

Mock `StatusRepository` and `ReplicationPublisher`. Test:
- `saveOrUpdate` with valid data
- `findByUsername` found/not found
- `deleteByUsername` existing/missing
- Replication event published on save and delete

### Task 5.4 — Controller integration tests

Use `@WebMvcTest(StatusController.class)` with mocked service:
- `POST /api/status` → 201 (new) / 200 (update)
- `POST /api/status` with invalid body → 400
- `GET /api/status/{username}` → 200 / 404
- `DELETE /api/status/{username}` → 204 / 404
- `GET /api/status` → 200 with list
- `GET /api/health` → 200 with nodeState

### Task 5.5 — ReplicationListener unit tests

Test LWW logic:
- Newer event overwrites older
- Older event is rejected
- Same-timestamp event is rejected (not strictly after)
- DELETE removes existing record
- DELETE on missing record is no-op
- Self-published event (same originNode) is ignored

### Task 5.6 — BootstrapService test

Integration test:
- Start a "seed" node with data
- Start a new node that bootstraps from the seed
- Verify all records are synced
- Verify node transitions to ACTIVE

---

## Phase 6: Client Remaining Features 🔵

> **Effort:** ~3h

### Task 6.1 — Implement StatusDetail component

- Route or tab-based navigation to `/status/:username`
- Calls `GET /api/status/{username}`
- Displays full user data + map zooms to location

### Task 6.2 — Add navigation/routing

- Install `react-router-dom` (or use simple state-based tab switching in App.jsx)
- Wire: Form tab, Feed+Map tab, Detail view
- Active tab highlighting

### Task 6.3 — Error toast for "all nodes unreachable"

- `apiClient.js` already throws when all nodes fail
- Surface this as a dismissible toast/alert in the UI

### Task 6.4 — Auto-refresh feed

- Add a `useEffect` interval (e.g., every 15s) that polls `GET /api/status`
- Manual refresh button remains as fallback

---

## Phase 7: Demo Infrastructure + Submission 🔵

> **Effort:** ~3h

### Task 7.1 — Create Dockerfile for server

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/server-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8443
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Update `pom.xml` to package as JAR (already Spring Boot default).

### Task 7.2 — Create docker-compose.demo.yml

3-node setup:

```yaml
services:
  rabbitmq:
    image: rabbitmq:3-management
  node-a:
    build: ./server
    environment:
      - NODE_ID=node-a
      - SERVER_PORT=8443
      - NODE_PEERS=https://node-b:8444,https://node-c:8445
  node-b:
    build: ./server
    environment:
      - NODE_ID=node-b
      - SERVER_PORT=8444
      - NODE_PEERS=https://node-a:8443,https://node-c:8445
  node-c:
    build: ./server
    environment:
      - NODE_ID=node-c
      - SERVER_PORT=8445
      - NODE_PEERS=https://node-a:8443,https://node-b:8444
```

### Task 7.3 — Update README-DEV.md

- Build & run instructions (backend + frontend + RabbitMQ)
- TLS/keystore generation steps
- Multi-node demo instructions
- Architecture diagram (ASCII or Mermaid)

### Task 7.4 — Disable H2 console in production profile

Profile-gate `spring.h2.console.enabled` to only be active in a `dev` profile.

### Task 7.5 — Final code cleanup

- Remove unused imports
- Remove dead code
- Verify all `System.err` replaced with SLF4J

---

## Phase 8: Verification & Grading Prep 🔴

> **Effort:** ~2h
> **Depends on:** All prior phases

### Task 8.1 — Run full test suite

```bash
cd server && mvn clean test
```

### Task 8.2 — Manual end-to-end test

| Test Case | Expected Result |
|-----------|----------------|
| Start RabbitMQ | `docker compose up -d` |
| Start Node A | `mvn spring-boot:run` on port 8443 |
| Start Node B | `mvn spring-boot:run` on port 8444 |
| POST status to Node A | 201 Created, appears on Node B within 15s |
| Kill Node A | Client failover to Node B works |
| Restart Node A | Node A bootstraps from Node B, becomes ACTIVE |
| Delete on Node B | Removed from both nodes |
| Kill all nodes | Client shows error toast |

### Task 8.3 — Phase 6 checklist verification

| Task | Verification |
|------|-------------|
| 6.1 | Functionality (50%) — all CRUD, map, TLS |
| 6.2 | Replication (15%) — LWW, <15s eventual consistency |
| 6.3 | Fault tolerance (15%) — n+1, client retry, bootstrapping |
| 6.4 | Knowledge prep (20%) — Q&A notes ready |
| 6.5 | Demo video or script prepared |
| 6.6 | Final commit + tag |

### Task 8.4 — Final commit and tag

```bash
git add -A
git commit -m "feat: complete project implementation"
git tag v1.0-final
```

---

## Quick-Reference: File Creation/Modification Plan

### New Files to Create (13)

| File | Phase |
|------|-------|
| `server/src/main/resources/keystore.p12` | 1 |
| `server/src/main/java/com/tevs/server/service/StatusService.java` | 2 |
| `server/src/main/java/com/tevs/server/service/NodeState.java` | 3 |
| `server/src/main/java/com/tevs/server/service/NodeStateManager.java` | 3 |
| `server/src/main/java/com/tevs/server/service/BootstrapService.java` | 3 |
| `server/src/main/java/com/tevs/server/config/BootstrapFilter.java` | 3 |
| `server/Dockerfile` | 7 |
| `docker-compose.demo.yml` | 7 |
| `server/src/test/java/com/tevs/server/repository/StatusRepositoryTest.java` | 5 |
| `server/src/test/java/com/tevs/server/service/StatusServiceTest.java` | 5 |
| `server/src/test/java/com/tevs/server/controller/StatusControllerTest.java` | 5 |
| `server/src/test/java/com/tevs/server/replication/ReplicationListenerTest.java` | 5 |
| `server/src/test/java/com/tevs/server/service/BootstrapServiceTest.java` | 5 |

### Existing Files to Modify (8)

| File | Phase |
|------|-------|
| `server/src/main/resources/application.yml` | 1, 3, 7 |
| `client/src/apiClient.js` | 1 |
| `server/src/main/java/com/tevs/server/controller/StatusController.java` | 2, 3, 4 |
| `server/src/main/java/com/tevs/server/replication/ReplicationListener.java` | 2, 4 |
| `server/src/main/java/com/tevs/server/model/StatusMessage.java` | 4 |
| `server/src/main/java/com/tevs/server/replication/ReplicationPublisher.java` | 4 |
| `client/src/main.jsx`/`App.jsx` | 6 |
| `README-DEV.md` | 7 |

---

## Dependency Graph

```
Phase 1 (TLS)           → independent, can start immediately
Phase 2 (Service)        → independent, can start in parallel with Phase 1
Phase 3 (Bootstrap)      → depends on Phase 2
Phase 4 (Quality)        → mostly independent (Bean Validation independent, SLF4J independent)
Phase 5 (Tests)          → depends on Phase 2 + Phase 3 + Phase 4
Phase 6 (Client)         → independent of backend changes
Phase 7 (Infra)          → depends on Phase 1 (TLS for demo)
Phase 8 (Verification)   → depends on everything
```

**Recommended parallel execution:**
- Stream A: Phase 1 → Phase 7 → Phase 8
- Stream B: Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 8
- Stream C: Phase 6 → Phase 8
