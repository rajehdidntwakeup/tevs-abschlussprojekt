# Presentation Notes: Distributed Command Center (max. 5 minutes)

## Slide 1: Title & Goal (30 sec)
- **Title:** Distributed Command Center – TEVS Final Project
- **Goal:** Build a fault-tolerant status store with geodata, replicated across multiple nodes, accessible via a web client with map visualization.

## Slide 2: Architecture Overview (1 min)
- Show the block diagram (from `architecture.md`).
- **Key points:**
  - 3+ equivalent Spring Boot nodes, each with local H2.
  - React + Leaflet client.
  - RabbitMQ as the sync bus (communication medium only).
  - No master node; every node accepts writes and reads.

## Slide 3: Communication Methods (1.5 min)
- **Inter-Node:** AMQP via RabbitMQ topic exchange (`status.sync`).
  - Push-based, decoupled, reliable.
  - Every mutation becomes an event consumed by all peers.
- **Client-Node:** REST over HTTPS/TLS.
  - Stateless, interchangeable nodes.
  - Self-signed certificates fulfill the encryption requirement.
- **Bootstrap:** REST snapshot (`/api/sync/all`) when a node joins.
- **Justification:** Simplicity + robustness; explicitly allowed by the specification.

## Slide 4: Proof of Concept Demo (1.5 min)
- Start RabbitMQ + 2 server nodes + React client.
- **Live demo script:**
  1. Create a status via the client → Node A.
  2. Show RabbitMQ Management UI → message published.
  3. Query Node B directly → status is already replicated.
  4. Show the Leaflet map with the new marker.
  5. Kill Node A; client automatically retries Node B (failover).
- **Narrative:** "This proves our two core communication methods work: REST for client interaction and AMQP for backend replication."

## Slide 5: Next Steps (30 sec)
- Full CRUD implementation.
- Last-Writer-Wins conflict resolution.
- Bootstrapping grace period.
- End-to-end fault-tolerance testing.

---

## Speaker Checklist
- [ ] Docker Compose file tested: `docker compose up -d` starts RabbitMQ instantly.
- [ ] Server starts on two ports without port conflicts.
- [ ] React dev proxy configured to reach HTTPS backend.
- [ ] Browser accepts self-signed cert (or use `mkcert`).
- [ ] Leaflet CSS imported in `index.html` or `main.jsx`.
