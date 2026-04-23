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

# Node A (port 8443)
java -jar target/server-0.0.1-SNAPSHOT.jar

# Node B (port 8444) — in another terminal
SERVER_PORT=8444 NODE_ID=node-b java -jar target/server-0.0.1-SNAPSHOT.jar
```

## 3. Run React Client
```bash
cd client
npm install
npm run dev
```
Client runs at http://localhost:3000 and proxies `/api` to the server.

## 4. Quick API Test
```bash
# Create status
curl -X POST http://localhost:8443/api/status \
  -H "Content-Type: application/json" \
  -d '{"username":"RECON-01","statustext":"On the way","time":"2026-04-21T10:00:00Z","latitude":48.215,"longitude":16.385}'

# List all
curl http://localhost:8443/api/status

# Get one
curl http://localhost:8443/api/status/RECON-01

# Delete
curl -X DELETE http://localhost:8443/api/status/RECON-01
```

## 5. Verify Replication
1. Create a status on Node A (port 8443).
2. Query Node B (port 8444) — the status should appear within seconds via RabbitMQ.

## 6. TLS (Optional for PoC)
To enable HTTPS, generate a keystore:
```bash
keytool -genkeypair -alias tevs -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore server/src/main/resources/keystore.p12 \
  -validity 365 -dname "CN=localhost" -storepass changeit -keypass changeit
```
Then set `server.ssl.enabled: true` in `application.yml` and update client base URLs to `https://`.
