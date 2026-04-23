# Communication Protocols & Methods

## 1. Inter-Node Synchronization (Backend → Backend)

### Chosen Technology: **AMQP via RabbitMQ (Topic Exchange)**

#### Why AMQP / RabbitMQ?
| Criterion | AMQP (RabbitMQ) | REST Polling | WebSocket | gRPC |
|-----------|-----------------|------------|-----------|------|
| **Performance** | Push-based, near-instant delivery. | High latency; wasteful empty polls. | Excellent, but requires persistent connections. | Excellent, but complex binary protocol. |
| **Simplicity** | Declarative queues/exchanges; Spring AMQP handles wiring. | Simple HTTP, but requires scheduling. | Requires connection management and reconnect logic. | Requires proto definitions; steep learning curve. |
| **Robustness** | Messages are queued if a node is down; automatic reconnect. | Missed events if polling interval is too long. | Fragile over unreliable networks; harder to debug. | Similar to WebSocket fragility. |
| **Decoupling** | Publisher does not know subscribers. | Tight coupling (must know peer URLs). | Tight coupling (point-to-point sockets). | Tight coupling. |
| **Scalability** | Add nodes by binding a new queue; no code changes. | Every new node adds polling load. | Every new node adds socket load. | Requires service mesh or LB. |

**Conclusion:** RabbitMQ offers the best balance of **simplicity**, **robustness**, and **decoupling** for a student project while still being explicitly allowed by the specification.

#### Protocol Details
- **Exchange Type:** `topic`
- **Exchange Name:** `status.sync`
- **Routing Key:** `status.update` (for all mutations)
- **Queue Naming:** `queue.{nodeId}` (one durable/auto-delete queue per node)
- **Message Format:** JSON payload wrapped in a `StatusEvent` object.
- **Delivery Mode:** Persistent (`deliveryMode = 2`) so RabbitMQ survives a restart.

---

## 2. Client ↔ Server Communication (Frontend → Backend)

### Chosen Technology: **REST over HTTPS/TLS (JSON)**

#### Why REST + HTTPS?
| Criterion | REST/HTTPS | WebSocket | gRPC-Web |
|-----------|----------|-----------|----------|
| **Simplicity** | Universally understood; easy to test with `curl` or browser DevTools. | Requires stateful connection handling on both sides. | Needs proxy/transcoding layer. |
| **Statelessness** | Each request is self-contained; nodes are interchangeable. | Stateful; failover requires reconnect logic. | Stateless, but complex tooling. |
| **Caching / CDNs** | HTTP semantics support standard caching. | Not cacheable. | Limited. |
| **Browser Support** | Native `fetch` / `axios`; zero polyfills. | Native but with boilerplate. | Requires protobuf client. |
| **TLS Integration** | Single command (`keytool`) to enable HTTPS in Spring Boot. | WebSocket Secure (WSS) is similar, but adds complexity. | Same as gRPC. |

**Conclusion:** REST over HTTPS is the **simplest and most predictable** choice for a browser-based client. It satisfies the transport encryption requirement with minimal configuration.

#### API Contract Summary
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/status` | Create or replace a status (body = JSON status). |
| `GET` | `/api/status/{username}` | Retrieve one status. |
| `DELETE` | `/api/status/{username}` | Delete a status. |
| `GET` | `/api/status` | Retrieve all statuses (feed). |
| `GET` | `/api/health` | Health check for failover detection. |
| `GET` | `/api/sync/all` | Internal bootstrap endpoint; returns full dataset. |

#### Security Details
- **Protocol:** HTTPS only (TLS 1.2+)
- **Certificate:** Self-signed PKCS12 keystore (`keystore.p12`)
- **CORS:** Configured in Spring Boot to allow the React dev server origin.

---

## 3. Bootstrapping Communication (Node → Node)

### Chosen Technology: **REST over HTTPS**

During the grace period, a bootstrapping node acts as a **client** to an existing peer:
- `GET https://peer:port/api/sync/all`
- Response: JSON array of all `StatusMessage` objects.
- The bootstrap node applies LWW rules during bulk insertion to avoid overwriting newer data that may arrive concurrently via RabbitMQ.

**Why not AMQP for bootstrap?** AMQP is optimized for events, not for snapshot transfer. A single REST call is more efficient for fetching the entire dataset.

---

## 4. Summary Table

| Communication Path | Protocol | Format | Justification |
|--------------------|----------|--------|---------------|
| Node → Node (sync) | AMQP | JSON | Decoupled, reliable, push-based replication. |
| Client → Node | HTTPS / REST | JSON | Stateless, browser-native, trivial TLS setup. |
| Node → Node (bootstrap) | HTTPS / REST | JSON | Efficient snapshot retrieval; simple request/response. |
