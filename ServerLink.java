import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;

public class ServerLink {
    public enum Protocol { TCP, UDP, PROXY }
    final InetAddress address;
    final int port;
    private Protocol protocol = null;
    private final ProxyState state;
    public final String neighborId;

    private static final int UDP_SERVER_BUFFER_SIZE = 256;
    private static final String PROXY_DISCOVERY_COMMAND = "NET_ROUTES";

    public ServerLink(String address, int port, ProxyState state) throws UnknownHostException {
        this.address = InetAddress.getByName(address);
        this.port = port;
        this.state = state;
        this.neighborId = address + ":" + port;
    }

    public String getNeighborId() {
        return neighborId;
    }

    public boolean isProxy() {
        return protocol == Protocol.PROXY;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public String sendCommand(String command) {
        if (protocol == null) {
            detectProtocol();
        }

        if (protocol == null) {
            System.err.println("ERROR: Could not detect protocol for " + neighborId);
            return "NA";
        }

        if (protocol == Protocol.TCP || protocol == Protocol.PROXY) {
            return sendTCP(command);
        } else if (protocol == Protocol.UDP) {
            return sendUDP(command);
        }
        return "NA";
    }

    private synchronized void detectProtocol() {
        String testCommand = "GET NAMES";
        final int MAX_RETRIES = 1100;
        final long RETRY_DELAY_MS = 500;

        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            String tcpResponse = sendTCP(testCommand);

            if (tcpResponse != null && tcpResponse.startsWith("OK")) {
                try (Scanner scanner = new Scanner(tcpResponse)) {
                    scanner.next();
                    int keyCount = scanner.nextInt();

                    if (keyCount == 1 && tcpResponse.split(" ").length <= 3) {
                        protocol = Protocol.TCP;
                        System.out.println("Neighbor " + neighborId + " detected as GUARD SERVER (TCP)");
                    } else {
                        protocol = Protocol.PROXY;
                        System.out.println("Neighbor " + neighborId + " detected as PROXY (TCP)");
                    }
                } catch (Exception e) {
                    protocol = Protocol.TCP;
                    System.out.println("Neighbor " + neighborId + " detected as GUARD SERVER (TCP) [Parsing fallback]");
                }
                return;
            }

            String udpResponse = sendUDP(testCommand);
            if (udpResponse != null && udpResponse.startsWith("OK")) {
                protocol = Protocol.UDP;
                System.out.println("Neighbor " + neighborId + " detected as GUARD SERVER (UDP)");
                return;
            }

            try {
                if (retry < MAX_RETRIES - 1) {
                    System.out.println("Protocol detection failed for " + neighborId + ". Retrying in " + RETRY_DELAY_MS + "ms...");
                    Thread.sleep(RETRY_DELAY_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (protocol == null) {
            protocol = Protocol.PROXY;
            System.out.println("Neighbor " + neighborId + " assumed to be a PROXY (TCP fallback) after " + MAX_RETRIES + " retries.");
        }
    }

    private String sendTCP(String command) {
        boolean isQuit = command.endsWith("QUIT") || command.endsWith("NET_QUIT");

        try (Socket socket = new Socket(address, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(command);

            if (isQuit) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "OK";
            }

            String response = in.readLine();
            return (response != null) ? response.trim() : "NA";

        } catch (IOException e) {
            return null;
        }
    }

    private String sendUDP(String command) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(4000);

            String paddedCommand = String.format("%-" + UDP_SERVER_BUFFER_SIZE + "s", command);
            byte[] buf = paddedCommand.getBytes("UTF-8");

            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);

            socket.send(packet);

            byte[] responseBuf = new byte[512];
            DatagramPacket responsePacket = new DatagramPacket(responseBuf, responseBuf.length);
            socket.receive(responsePacket);

            return new String(responsePacket.getData(), 0, responsePacket.getLength(), "UTF-8").trim();
        } catch (IOException e) {
            return null;
        }
    }

    public void discoverAndPropagateKeys() {
        new Thread(this::discoveryLoop, "Discovery-" + neighborId).start();
    }

    private void discoveryLoop() {
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        while (state.isRunning()) {
            try {
                if (!isProxy()) {
                    String response = sendCommand("GET NAMES");
                    if (response != null && response.startsWith("OK")) {
                        Set<String> discoveredKeys = parseKeysFromResponse(response);

                        if (discoveredKeys.size() > 1 && protocol != Protocol.PROXY) {
                            protocol = Protocol.PROXY;
                            System.out.println("RECLASSIFIED " + neighborId + " as PROXY due to multiple keys discovered.");
                        }
                        int hopCountFromSource = 0;

                        if (state.updateRoutes(discoveredKeys, this, hopCountFromSource)) {
                            System.out.println("Guard keys updated via " + neighborId + ".");
                            propagateFullRoutes(null);
                        }
                    }
                } else {
                    propagateFullRoutes(null);
                }

                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("Discovery/Propagation error with " + neighborId + ": " + e.getMessage());
                try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
    }

    public void propagateFullRoutes(ServerLink source) {
        StringBuilder payload = new StringBuilder(PROXY_DISCOVERY_COMMAND).append(" ");
        boolean keysPresent = false;

        for (String key : state.getAllKeys()) {
            int hops = state.getHopCount(key);

            if (hops < Integer.MAX_VALUE) {
                payload.append(key).append(":").append(hops).append(" ");
                keysPresent = true;
            }
        }

        if (!keysPresent) return;

        for (ServerLink neighbor : state.getNeighbors().values()) {
            if (neighbor.isProxy() && neighbor != source) {
                System.out.println("Propagating routes to PROXY " + neighbor.getNeighborId());
                neighbor.sendCommand(payload.toString().trim());
            }
        }
    }

    private Set<String> parseKeysFromResponse(String response) {
        Set<String> keys = new HashSet<>();
        try (Scanner scanner = new Scanner(response)) {
            if (!scanner.next().equals("OK")) return keys;
            scanner.nextInt();
            while (scanner.hasNext()) {
                keys.add(scanner.next());
            }
        } catch (Exception e) {
            System.err.println("Failed to parse keys from response: " + response);
        }
        return keys;
    }
}