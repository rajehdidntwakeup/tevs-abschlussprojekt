# Project Tasks: Distributed Command Center (TEVS)

> **Source:** `project-plan.md`
> **Date:** 2026-04-21
> **Legend:** [x] = Open | [x] = Done | [-] = Skipped

---

## Phase 0: Environment & Project Setup

| ID | Task | Deliverable / Success Criteria | Status |
|----|------|-------------------------------|--------|
| **0.1** | Initialize Maven project for Spring Boot 3.x with Java 21. | `server/pom.xml` compiles and runs via `mvn spring-boot:run`. | [x] |
| **0.2** | Add required Maven dependencies: `spring-boot-starter-web`, `spring-boot-starter-amqp`, `h2`, `spring-boot-starter-data-jpa`, `jackson-datatype-jsr310`. | `pom.xml` contains all deps; `mvn dependency:resolve` succeeds. | [x] |
| **0.3** | Create base package structure: `com.tevs.server.{config,controller,model,repository,service,replication}`. | All packages exist under `src/main/java`. | [x] |
| **0.4** | Create `application.yml` with profiles for at least 2 nodes (`node-a`, `node-b`) using different ports and distinct `node.id`. | Start node A on port 8443 and node B on port 8444 via `--spring.profiles.active`. | [-] |
| **0.5** | Configure RabbitMQ connection settings in `application.yml` (host, port, user, pass). | Values loaded correctly; app starts without AMQP connection errors (RabbitMQ can be offline for now). | [x] |
| **0.6** | Generate a self-signed PKCS12 keystore (`keystore.p12`) and configure Spring Boot HTTPS (`server.ssl.*`). | `curl -k https://localhost:8443/api/health` returns HTTP 200 (or 404 if endpoint not yet present). | [x] |
| **0.7** | Initialize React + Vite project in `client/` directory. | `npm install` succeeds; `npm run dev` serves the app on its default port. | [x] |
| **0.8** | Install client dependencies: `leaflet`, `react-leaflet`, `axios` (or `fetch` wrapper). | `package.json` updated; app builds without errors. | [x] |
| **0.9** | Set up Vite dev proxy to forward `/api` to the local Spring Boot HTTPS server (ignore cert warnings in dev). | A request from the React dev server reaches the backend without CORS or SSL errors. | [x] |
| **0.10** | Create `docker-compose.yml` with a RabbitMQ service (`rabbitmq:3-management`) exposing ports `5672` and `15672`. | `docker compose up -d` starts RabbitMQ; management UI reachable at `http://localhost:15672`. | [x] |
| **0.11** | Create `README-DEV.md` with build & run instructions for backend, frontend, and RabbitMQ. | File committed; another team member can set up from scratch using only this file. | [x] |

---

## Phase 1: Core Server & Persistence

| ID | Task | Deliverable / Success Criteria | Status |
|----|------|-------------------------------|--------|
| **1.1** | Define `StatusMessage` JPA entity with fields: `username` (PK, String), `statustext` (String), `time` (Instant/OffsetDateTime), `latitude` (Double), `longitude` (Double). | Table `status_messages` created in H2; DDL matches spec. | [x] |
| **1.2** | Create `StatusRepository` extending `JpaRepository<StatusMessage, String>`. | Repository bean wired correctly; basic CRUD unit tests pass. | [x] |
| **1.3** | Implement `StatusService` with methods: `saveOrUpdate(StatusMessage)`, `findByUsername(String)`, `findAll()`, `deleteByUsername(String)`. | Service covered by unit tests using an in-memory H2 instance. | [x] |
| **1.4** | Implement `POST /api/status` in `StatusController`. Accept JSON body, validate required fields, return `201 Created` (new) or `200 OK` (update). | Controller unit test (`WebTestClient`) passes for both create and update. | [x] |
| **1.5** | Implement `GET /api/status/{username}` in `StatusController`. Return `200 OK` with JSON if found, else `404 Not Found`. | Unit test passes for existing and missing usernames. | [x] |
| **1.6** | Implement `DELETE /api/status/{username}` in `StatusController`. Return `204 No Content` if deleted, `404` if not found. | Unit test passes for existing and missing usernames. | [x] |
| **1.7** | Implement `GET /api/status` (list all) in `StatusController`. Return `200 OK` with JSON array. | Unit test returns an empty array initially and a populated array after inserts. | [x] |
| **1.8** | Implement `GET /api/health` returning JSON `{"status":"UP","nodeId":"..."}`. | Always returns HTTP 200 when server is running. | [x] |
| **1.9** | Add input validation annotations (`@NotBlank`, range checks for lat/lon `-90/90`, `-180/180`) and a global exception handler (`@ControllerAdvice`). | Invalid payload returns HTTP `400 Bad Request` with a clear error message. | [x] |
| **1.10** | Ensure `time` field is auto-populated with current ISO timestamp if missing in the request body. | Postman/curl request without `time` gets a server-generated timestamp. | [x] |

---

## Phase 2: Custom Replication & Sync

| ID | Task | Deliverable / Success Criteria | Status |
|----|------|-------------------------------|--------|
| **2.1** | Define `StatusEvent` record/class with fields: `eventType` (CREATE/UPDATE/DELETE), `payload` (StatusMessage), `originNode` (String), `timestamp` (Instant). | Class compiles; JSON serialization verified. | [x] |
| **2.2** | Configure Spring AMQP: declare TopicExchange `status.sync` and a uniquely named queue per node (e.g., `queue.node-a`) bound with routing key `status.#`. | RabbitMQ Management UI shows the exchange, queue, and binding after app startup. | [x] |
| **2.3** | Implement `ReplicationPublisher` that sends a `StatusEvent` to `status.sync` after every successful local mutation in `StatusService`. | Publish action logged; message visible in RabbitMQ queue. | [x] |
| **2.4** | Implement `ReplicationListener` ( `@RabbitListener` ) that consumes `StatusEvent` and calls `StatusService.applyReplication(StatusEvent)`. | Listener receives messages from RabbitMQ; logs confirm reception. | [x] |
| **2.5** | Implement **Last-Writer-Wins (LWW)** logic in `applyReplication`: compare incoming `payload.time` against local record time. Only apply if incoming is newer or local is absent. Reject stale events silently (log warning). | Unit test: old event does not overwrite newer local record; new event overwrites older record. | [x] |
| **2.6** | Implement `DELETE` replication: if event type is DELETE, remove the record locally if it exists; do nothing if already absent. | Unit test passes. | [x] |
| **2.7** | Implement **Bootstrapping / Grace Period** logic: on startup, node sets internal state `BOOTSTRAPPING` and blocks `/api/status*` endpoints (return `503 Service Unavailable`) until sync is complete. | Health check still returns `200` during bootstrap so peers know the node is alive. | [x] |
| **2.8** | Implement `BootstrapService` that calls `GET /api/sync/all` on a configured peer URL, iterates over the returned list, and bulk-inserts into H2 using LWW. | Integration test: a fresh node starts, queries a live peer, populates DB, then transitions to `ACTIVE`. | [x] |
| **2.9** | Implement `GET /api/sync/all` endpoint (no auth needed for now) returning the full current dataset. | Returns JSON array of all `StatusMessage` records. | [x] |
| **2.10** | Prevent replication loop: ignore `StatusEvent` where `originNode` equals local `node.id`. | Unit test: local mutation does not trigger infinite self-replication. | [x] |
| **2.11** | Handle out-of-order / duplicate replication events idempotently via LWW checks. | Unit test with shuffled event order results in correct final state. | [x] |
| **2.12** | Add an `AtomicReference` or enum for node lifecycle state (`BOOTSTRAPPING` -> `ACTIVE`). Log state transitions. | Logs clearly show `BOOTSTRAPPING` then `ACTIVE`. | [x] |

---

## Phase 3: Client Application (React + Leaflet)

| ID | Task | Deliverable / Success Criteria | Status |
|----|------|-------------------------------|--------|
| **3.1** | Create `apiClient.js` with a configurable list of backend node URLs. Use `fetch` (or `axios`) with `{ rejectUnauthorized: false }` equivalent or dev proxy for self-signed certs. | Can successfully call `GET /api/status` from the browser console. | [x] |
| **3.2** | Implement **client-side failover**: wrap every API call in a loop that tries Node A, then Node B, then Node C, etc., until one responds within a 3-second timeout. Show a toast/error if all fail. | Kill Node A; client automatically gets data from Node B without a page reload. | [x] |
| **3.3** | Build `StatusForm` component: input fields for `username`, `statustext`, and a Leaflet map where clicking sets `latitude`/`longitude`. Include submit button. | Submitting the form sends `POST /api/status`; map click updates lat/lon inputs. | [x] |
| **3.4** | Build `StatusFeed` component: a table or card list showing all statuses fetched from `GET /api/status`. Include a "Refresh" button. | Displays real data from the backend; refresh pulls latest state. | [x] |
| **3.5** | Build `StatusMap` component: a Leaflet map displaying a marker for every status in the feed. Clicking a marker shows a popup with `username` and `statustext`. | Map renders at least 2 markers correctly positioned. | [x] |
| **3.6** | Build `StatusDetail` component: given a `username`, call `GET /api/status/{username}` and display the full object, zooming the map to that location. | URL or search-based navigation to a user shows correct data and map focus. | [x] |
| **3.7** | Add **Delete** button next to each status entry. On click, call `DELETE /api/status/{username}` and refresh the feed/map. Show a confirmation dialog. | Delete removes the marker and table row after replication sync (may need a short delay or manual refresh). | [x] |
| **3.8** | Wire all components together in `App.jsx` with basic routing (or simple tab switching): Form, Feed+Map, Detail. | Navigation between views works without full page reload. | [x] |
| **3.9** | Add basic CSS / UI framework (e.g., plain CSS, Tailwind, or Bootstrap) to make the client presentable. | Layout is responsive enough for a demo; map has defined height. | [x] |
| **3.10** | Test HTTPS calls against the self-signed backend. Document any browser certificate import steps in `README-DEV.md`. | A user can follow the steps and use the client without SSL errors. | [x] |

---

## Phase 4: Fault Tolerance & Resilience Testing

| ID | Task | Deliverable / Success Criteria | Status |
|----|------|-------------------------------|--------|
| **4.1** | Run 2 nodes (A, B) + RabbitMQ + Client. Create a status via the client targeting Node A. Verify it appears on Node B within 15 seconds. | `curl -k https://node-b:8444/api/status/{username}` returns the same JSON. | [-] |
| **4.2** | Kill Node A (SIGKILL or `docker stop`). While Node A is down, create a new status via the client (failover to Node B). Verify Node B persists it and client shows confirmation. | Client does not crash; request succeeds via Node B. | [-] |
| **4.3** | Restart Node A. Verify Node A bootstraps the missing status from Node B during its grace period, then becomes `ACTIVE`. | Node A eventually returns the new status on `GET /api/status`. | [-] |
| **4.4** | Kill RabbitMQ. Create a status on Node A. Bring RabbitMQ back up. Verify the status eventually replicates to Node B (or document why it does not). | Document observed behavior and any manual recovery steps. | [-] |
| **4.5** | Simulate concurrent edit: send two `POST /api/status` updates for the same `username` to Node A and Node B **simultaneously** (within <1s). Verify all nodes converge to the same final state (LWW). | All nodes return identical JSON after 15 seconds. | [-] |
| **4.6** | Test DELETE replication: delete a status from Node A; verify it disappears from Node B and the client map/feed. | `GET /api/status/{username}` returns `404` on both nodes. | [-] |
| **4.7** | Verify client correctly shows a global error when **all** backend nodes are unreachable. | Error banner/toast displayed; UI remains interactive. | [x] |
| **4.8** | Verify `GET /api/health` returns node state (`BOOTSTRAPPING` vs `ACTIVE`) in the JSON response. | Useful for debugging demo; confirmed working. | [x] |

---

## Phase 5: Security, Polish & Documentation

| ID | Task | Deliverable / Success Criteria | Status |
|----|------|-------------------------------|--------|
| **5.1** | Confirm all REST endpoints (`/api/**`) and static web assets are served exclusively over HTTPS. No plain HTTP port is open (or it redirects to HTTPS). | `curl http://localhost:8080/api/status` either fails or redirects. | [x] |
| **5.2** | Add CORS configuration in Spring Boot allowing the client origin (dev proxy or deployed URL). | Client on a different port can still call the API without CORS errors. | [x] |
| **5.3** | Write inline code comments on the **custom replication logic** (LWW, bootstrapping, event loop prevention). | A reviewer can understand the protocol without asking the team. | [x] |
| **5.4** | Update `README-DEV.md` with final architecture diagram (Mermaid or ASCII), build instructions, and demo steps. | File is complete and accurate. | [x] |
| **5.5** | Prepare `docker-compose.demo.yml` that spins up RabbitMQ + 3 server nodes + Nginx (optional) for a one-command demo. | `docker compose -f docker-compose.demo.yml up --build` runs the full stack. | [x] |
| **5.6** | Final code cleanup: remove `console.log` spam, unused imports, dead code. | Static analysis (e.g., `eslint`, IDE warnings) shows zero critical issues. | [x] |

---

## Phase 6: Final Review & Submission

| ID | Task | Deliverable / Success Criteria | Status |
|----|------|-------------------------------|--------|
| **6.1** | **Functionality check (50%):** Confirm all CRUD, list, map visualization, and TLS are working end-to-end. | Run through every client feature without bugs. | [x] |
| **6.2** | **Replication check (15%):** Confirm custom replication, LWW, and <15s eventual consistency are demonstrable. | Show logs or API diffs proving sync time < 15s. | [-] |
| **6.3** | **Fault tolerance check (15%):** Confirm `n+1` availability, client retry, and bootstrapping are demonstrable. | Live demo: kill one node, continue operation, restart and sync. | [-] |
| **6.4** | **Knowledge prep (20%):** Draft Q&A notes on architecture, protocol, conflict resolution, and fault-tolerance design decisions. | Each team member can explain the replication flow and LWW choice. | [x] |
| **6.5** | Record a 3-5 minute demo video or prepare a live presentation script. | Covers create, replicate, kill node, failover, restart, and map view. | [ ] |
| **6.6** | Final commit, tag (`v1.0-final`), or archive the submission package. | Repository state is frozen and ready for grading. | [x] |

---

## Quick Reference: Task Count Summary

| Phase | Total Tasks |
|-------|-------------|
| Phase 0: Setup | 11 |
| Phase 1: Core Server | 10 |
| Phase 2: Replication | 12 |
| Phase 3: Client | 10 |
| Phase 4: Testing | 8 |
| Phase 5: Polish | 6 |
| Phase 6: Review | 6 |
| **Grand Total** | **63** |
