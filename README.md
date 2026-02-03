# Proxy Network for Data Aggregation (Task 2)

**Student Identification:** s32802

**Scenario:** Intermediary Proxy Network Implementation

**Standard:** Java 8 (JDK 1.8)

**Date:** 15 December 2025

---

## 🚀 Quick Start & Installation

### Prerequisites

* **JRE:** Java Runtime Environment compatible with JDK 1.8 or later.
* **Environment:** Multiple terminal instances are required for concurrent execution.

### Compilation

Compile all source files from the working directory:

```bash
javac *.java

```

### Execution Protocol

> [!IMPORTANT]
> All nodes must be launched simultaneously. If proxies aren't started within **15 minutes**, the network may misidentify neighbors as "TCP fallback" (standard servers). If this occurs, stop all terminals (Ctrl+C) and restart.

#### 1. Guard Server

```bash
java [TCPServer | UDPServer] -port <num> -key <name> -value <val>

```

#### 2. Proxy Node

```bash
java Proxy -port <num> -server <addr> <port> [...]

```

#### 3. Client Command

```bash
java [TCPClient | UDPClient] -address <addr> -port <proxy_port> -command <cmd>

```

---

## 🛠 Functionality Overview

The system implements a robust proxy infrastructure capable of handling arbitrary connection topologies (mesh/cycles) using the **Distance Vector (DV) Routing Algorithm**.

### Key Features

* **DV Routing:** `ProxyState` manages hop counts. Routes only update if a strictly shorter path is found: .
* **Protocol Interop:** Seamless bridging between TCP/UDP clients and servers.
* **UDP Padding Fix:** Custom logic to handle the specific payload requirements of the unmodifiable `UDPServer`.
* **Coordinated Shutdown:** Uses `propagateQuit()` with a 200ms synchronization delay to ensure `NET_QUIT` signals traverse the entire network before termination.

---

## 📡 Custom Inter-Proxy Protocol (`NET_PROTOCOL`)

Communication between proxies occurs over **TCP** for maximum reliability.

| Command | Purpose |
| --- | --- |
| `NET_ROUTES` | Carries the DV routing table (`key:hop_count`) for path calculation. |
| `NET_REQ` | Wraps a client's GET/SET command for forwarding to the next hop. |
| `NET_ACK` | Wraps the final server response (OK/NA) back to the originator. |
| `NET_QUIT` | Instructs a proxy to terminate and propagate the signal to neighbors. |

---

## 🧩 Technical Challenges & Solutions

### A. The "KeyB Anomaly" (Startup Race Condition)

* **Difficulty:** P1 would misclassify P2 as a 1-hop Guard Server rather than a multi-hop Proxy during rapid startup.
* **Solution:** Augmented `ProxyState.updateRoutes` with enforced DV metrics. If a link is identified as a Proxy but advertises a 1-hop path for a key it doesn't hold, the logic automatically corrects the metric to **2** to maintain routing integrity.

### B. Shutdown Reliability

* **Difficulty:** Race conditions during termination led to packet loss, preventing some nodes from closing.
* **Solution:** Increased `Thread.sleep` in `propagateQuit()` to 50ms (or higher). This ensures TCP kernel buffers are flushed and signals are delivered before the process exits.

---

## 📂 Source File Descriptions

| File | Type | Description |
| --- | --- | --- |
| `Proxy.java` | Custom | Entry point. Handles argument parsing and thread initialization. |
| `ProxyState.java` | Custom | Thread-safe shared state. Implements the core **DV Logic**. |
| `ServerLink.java` | Custom | Neighbor interface. Handles protocol detection and UDP padding. |
| `ClientHandler.java` | Custom | Processes inbound TCP. Aggregates data for `GET NAMES`. |
| `UDPListener.java` | Custom | Dedicated thread for handling client UDP requests. |
| `TCPServer.java` | Unmodifiable | Standard TCP Guard Server provided for the task. |
| `UDPServer.java` | Unmodifiable | Standard UDP Guard Server provided for the task. |

---

## 🧪 Network Test Scenarios

### Test Case 1: Standard Cyclic Topology

**Setup:**  (3 Keys)

```bash
# Servers
java TCPServer -port 8001 -key KeyA -value 10
java UDPServer -port 8002 -key KeyB -value 20
java TCPServer -port 8003 -key KeyC -value 30

# Proxies
java Proxy -port 9000 -server localhost 8001 -server localhost 9001
java Proxy -port 9001 -server localhost 8002 -server localhost 9002
java Proxy -port 9002 -server localhost 8003 -server localhost 9000

```

### Test Case 2: 6-Key Multi-Protocol Mesh

**Expected Output for `GET NAMES`:** `OK 6 KeyA KeyB KeyC KeyD KeyE KeyF`

### Test Case 3: Star Topology (Longest Path)

**Setup:** Linear Chain 

* **Route Verification:** A request for `KeyB` from `P1` correctly traverses 3 hops ().