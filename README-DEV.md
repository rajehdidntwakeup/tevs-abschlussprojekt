# TEVS Final Project – Developer Setup

## Prerequisites
- Java 21+
- Maven 3.9+
- Node.js 20+
- Docker & Docker Compose

## 1. Start RabbitMQ
```bash
docker compose up -d
```
RabbitMQ Management UI: http://localhost:15672 (guest / guest)

## 2. Build & Run Server Node(s)
```bash
cd server
mvn clean package

# Node A (port 8443, standalone mode)
java -jar target/server-0.0.1-SNAPSHOT.jar

# Node B (port 8444, bootstraps from Node A)
SERVER_PORT=8444 NODE_ID=node-b \
  NODE_PEERS=https://localhost:8443 \
  java -jar target/server-0.0.1-SNAPSHOT.jar

# Node C (port 8445, bootstraps from Node A)
SERVER_PORT=8445 NODE_ID=node-c \
  NODE_PEERS=https://localhost:8443 \
  java -jar target/server-0.0.1-SNAPSHOT.jar

# Dev mode with H2 console enabled (http://localhost:PORT/h2-console):
java -Dspring.profiles.active=dev -jar target/server-0.0.1-SNAPSHOT.jar
```

## 3. Run React Client
```bash
cd client
npm install
npm run dev
```
Client runs at http://localhost:3000 and proxies `/api` to the server.

## 4. Quick API Test
All API calls use HTTPS with self-signed certificate (`-k` flag bypasses cert validation).

```bash
# Create status
curl -k -X POST https://localhost:8443/api/status \
  -H "Content-Type: application/json" \
  -d '{"username":"RECON-01","statustext":"On the way","time":"2026-04-21T10:00:00Z","latitude":48.215,"longitude":16.385}'

# List all
curl -k https://localhost:8443/api/status

# Get one
curl -k https://localhost:8443/api/status/RECON-01

# Delete
curl -k -X DELETE https://localhost:8443/api/status/RECON-01

# Health (includes node state)
curl -k https://localhost:8443/api/health
# → {"status":"UP","nodeId":"node-a","nodeState":"ACTIVE"}
```

## 5. Verify Replication
1. Start RabbitMQ, Node A, and Node B (see section 2).
2. Create a status on Node A (port 8443).
3. Query Node B (port 8444) — the status should appear within seconds via RabbitMQ.
4. Check Node B bootstrapped: `curl -k https://localhost:8444/api/health` shows `"nodeState":"ACTIVE"`.

## 6. Multi-Node Demo with Docker
```bash
# Build all server JARs
cd server && mvn clean package && cd ..

# Start full stack (RabbitMQ + 3 nodes)
docker compose -f docker-compose.demo.yml up --build
```

## 7. Architecture

```
┌──────────────┐     HTTPS      ┌──────────────┐
│   Client     │ ──────────────▶│   Node A     │────┐
│ (React+Vite) │                │ (8443)       │    │
└──────────────┘                └──────────────┘    │
                                      │             │
                                      │ AMQP        │ HTTPS /api/sync/all
                                      ▼             ▼
                               ┌──────────────┐ ┌──────────────┐
                               │  RabbitMQ    │ │   Node B     │
                               │ status.sync  │ │ (8444)       │
                               └──────────────┘ └──────────────┘
                                      │
                                      │ AMQP
                                      ▼
                               ┌──────────────┐
                               │   Node C     │
                               │ (8445)       │
                               └──────────────┘

Node lifecycle: BOOTSTRAPPING → (sync from peer via /api/sync/all) → ACTIVE
Conflict resolution: Last-Writer-Wins (LWW) by timestamp
