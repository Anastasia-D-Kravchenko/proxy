import java.io.*;
import java.net.Socket;
import java.net.InetAddress;
import java.util.*;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final ProxyState state;

    public ClientHandler(Socket socket, ProxyState state) {
        this.clientSocket = socket;
        this.state = state;
    }

    @Override
    public void run() {
        System.out.println("TCP connection from " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String inputLine = in.readLine();
            if (inputLine == null) return;

            String response = processRequest(inputLine, clientSocket);

            if (!inputLine.startsWith("QUIT")) {
                out.println(response);
            }

        } catch (IOException e) {
            System.err.println("Error handling TCP client/proxy: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) { /* ignore */ }
        }
    }

    private String processRequest(String request, Socket sourceSocket) throws IOException {
        Scanner parser = new Scanner(request);
        if (!parser.hasNext()) return "NA";

        String command = parser.next();

        if (command.startsWith("NET_")) {
            return handleProxyCommand(command, parser, sourceSocket);
        } else {
            return handleClientCommand(command, request, parser);
        }
    }

    private String handleClientCommand(String command, String request, Scanner parser) throws IOException {
        try {
            switch (command) {
                case "GET":
                    String param = parser.next();
                    if (param.equals("NAMES")) {
                        return buildGetNamesResponse(state.getAllKeys());
                    } else if (param.equals("VALUE")) {
                        String keyName = parser.next();
                        return forwardRequest(keyName, request);
                    }
                    return "NA";
                case "SET":
                    String keyName = parser.next();
                    return forwardRequest(keyName, request);
                case "QUIT":
                    propagateQuit();
                    return "";
                default:
                    return "NA";
            }
        } catch (Exception e) {
            return "NA";
        }
    }

    private String handleProxyCommand(String command, Scanner parser, Socket sourceSocket) {
        try {
            InetAddress senderAddress = sourceSocket.getInetAddress();
            int listeningPort = sourceSocket.getLocalPort();

            ServerLink senderLink = state.getNeighbors().values().stream()
                    .filter(link -> link.address.equals(senderAddress))
                    .findFirst().orElse(null);

            if (senderLink == null) {
                System.err.println("ERROR: Received PROXY command from unknown neighbor IP: " + senderAddress.getHostAddress());
                return "NA";
            }

            switch (command) {
                case "NET_ROUTES":
                    Set<String> updatedKeys = new HashSet<>();

                    while (parser.hasNext()) {
                        String routeEntry = parser.next();
                        String[] parts = routeEntry.split(":");
                        if (parts.length == 2) {
                            String key = parts[0];
                            int remoteHops = Integer.parseInt(parts[1]);

                            if (state.updateRoutes(Collections.singleton(key), senderLink, remoteHops)) {
                                updatedKeys.add(key);
                            }
                        }
                    }

                    if (!updatedKeys.isEmpty()) {
                        System.out.println("Received NET_ROUTES from " + senderLink.getNeighborId() + ". Updated " + updatedKeys.size() + " routes.");
                        senderLink.propagateFullRoutes(senderLink);
                    }
                    return "OK";

                case "NET_REQ":
                    String originalRequest = parser.nextLine().trim();
                    String[] parts = originalRequest.split("\\s+");
                    String keyName = parts.length > 2 ? parts[2] : "NA";
                    if (parts[0].equals("SET")) keyName = parts[1];

                    String serverResponse = forwardRequest(keyName, originalRequest);

                    return "NET_ACK " + serverResponse;

                case "NET_ACK":
                    return "NA";

                case "NET_QUIT":
                    System.out.println("Received NET_QUIT command. Terminating.");
                    propagateQuit();
                    System.exit(0);
                    return "";
                default:
                    return "NA";
            }
        } catch (Exception e) {
            System.err.println("Error processing proxy command: " + e.getMessage());
            return "NA";
        }
    }

    private String forwardRequest(String keyName, String clientRequest) {
        ServerLink nextHop = state.getRouteForKey(keyName);

        if (nextHop == null) {
            System.out.println("Key " + keyName + " not found in network.");
            return "NA";
        }

        System.out.println("Forwarding request '" + clientRequest + "' for key " + keyName + " to " + nextHop.getNeighborId());

        if (!nextHop.isProxy()) {
            return nextHop.sendCommand(clientRequest);
        } else {
            String proxyCommand = "NET_REQ " + clientRequest;
            String proxyResponse = nextHop.sendCommand(proxyCommand);

            if (proxyResponse.startsWith("NET_ACK")) {
                return proxyResponse.substring("NET_ACK ".length());
            }
            return "NA";
        }
    }

    private String buildGetNamesResponse(Set<String> keys) {
        if (keys.isEmpty()) {
            return "NA";
        }
        StringBuilder response = new StringBuilder("OK ").append(keys.size()).append(" ");
        for (String key : keys) {
            response.append(key).append(" ");
        }
        return response.toString().trim();
    }

    private void propagateQuit() {
        state.stop();
        System.out.println("Propagating QUIT command...");
        for (ServerLink neighbor : state.getNeighbors().values()) {
            String command = neighbor.isProxy() ? "NET_QUIT" : "QUIT";
            neighbor.sendCommand(command);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.exit(0);
    }
}