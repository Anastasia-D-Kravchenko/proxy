import java.io.IOException;
import java.net.*;
import java.util.*;

public class Proxy {

    private final ProxyState state = new ProxyState();
    private final int clientPort;
    private final List<String[]> serverAddresses = new ArrayList<>();
    private static final int EXPECTED_KEY_COUNT = 2;

    public Proxy(int port, List<String[]> servers) {
        this.clientPort = port;
        this.serverAddresses.addAll(servers);
    }

    public static void main(String[] args) {
        int portNumber = 0;
        List<String[]> servers = new ArrayList<>();

        for (int i = 0; i < args.length; ) {
            switch (args[i]) {
                case "-port":
                    if (i + 1 < args.length) {
                        portNumber = Integer.parseInt(args[i + 1]);
                        i += 2;
                    } else {
                        System.err.println("Error: Missing port number for -port.");
                        System.exit(1);
                    }
                    break;
                case "-server":
                    if (i + 2 < args.length) {
                        servers.add(new String[]{args[i + 1], args[i + 2]});
                        i += 3;
                    } else {
                        System.err.println("Error: Missing address or port for -server.");
                        System.exit(1);
                    }
                    break;
                default:
                    System.err.println("Unknown parameter: " + args[i]);
                    i++;
            }
        }

        if (portNumber == 0 || servers.isEmpty()) {
            System.err.println("Incorrect execution syntax: java Proxy -port <port> -server <address> <port> [...]");
            System.exit(1);
        }

        try {
            Proxy proxy = new Proxy(portNumber, servers);
            proxy.start();
        } catch (IOException e) {
            System.err.println("FATAL: Could not start proxy: " + e.getMessage());
            System.exit(1);
        }
    }

    public void start() throws IOException {
        System.out.println("Starting Proxy on port " + clientPort + "...");

        System.out.println("Initializing " + serverAddresses.size() + " neighbors...");
        for (String[] pair : serverAddresses) {
            String address = pair[0];
            int port = Integer.parseInt(pair[1]);
            try {
                ServerLink link = new ServerLink(address, port, state);
                state.addNeighbor(link.getNeighborId(), link);
            } catch (UnknownHostException e) {
                System.err.println("Could not resolve host: " + address);
            }
        }

        ServerSocket tcpListener = new ServerSocket(clientPort);
        new Thread(() -> {
            System.out.println("TCP Listener started on port " + clientPort);
            while (state.isRunning()) {
                try {
                    Socket clientSocket = tcpListener.accept();
                    new Thread(new ClientHandler(clientSocket, state)).start();
                } catch (IOException e) {
                    if (state.isRunning()) {
                        System.err.println("TCP Listener Accept Error: " + e.getMessage());
                    }
                }
            }
            try { tcpListener.close(); } catch (IOException e) { /* ignore */ }
        }, "TCP-Listener").start();

        DatagramSocket udpSocket = new DatagramSocket(clientPort);
        new Thread(() -> {
            System.out.println("UDP Listener started on port " + clientPort);
            new UDPListener(udpSocket, state).run();
        }, "UDP-Listener").start();

        System.out.println("Initiating network key discovery...");
        for (ServerLink link : state.getNeighbors().values()) {
            link.discoverAndPropagateKeys();
        }

        System.out.println("Initial discovery setup complete.");

        waitForConvergence();
    }

    private void waitForConvergence() {
        System.out.println("Waiting for network convergence (Expected keys: " + EXPECTED_KEY_COUNT + ")...");
        long startTime = System.currentTimeMillis();
        long timeout = 600000;

        while (state.getAllKeys().size() < EXPECTED_KEY_COUNT && (System.currentTimeMillis() - startTime) < timeout) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (state.getAllKeys().size() >= EXPECTED_KEY_COUNT) {
            System.out.println("Network CONVERGED. Total keys: " + state.getAllKeys().size());
        } else {
            System.err.println("WARNING: Network failed to converge within timeout. Total keys: " + state.getAllKeys().size());
        }
    }
}