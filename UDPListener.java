import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.Set;

public class UDPListener implements Runnable {
    private final DatagramSocket socket;
    private final ProxyState state;

    public UDPListener(DatagramSocket socket, ProxyState state) {
        this.socket = socket;
        this.state = state;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[512];
        System.out.println("UDP Listener started on port " + socket.getLocalPort());
        while (state.isRunning()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String request = new String(packet.getData(), 0, packet.getLength(), "UTF-8").trim();
                System.out.println("UDP request from " + packet.getAddress().getHostAddress() + ":" + packet.getPort() + ": " + request);

                String response = processUDPRequest(request);

                if (!request.startsWith("QUIT") && !response.isEmpty()) {
                    byte[] responseData = response.getBytes("UTF-8");
                    DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, packet.getAddress(), packet.getPort());
                    socket.send(responsePacket);
                }

            } catch (IOException e) {
                if (state.isRunning()) {
                    System.err.println("UDP Listener error: " + e.getMessage());
                }
            }
        }
    }

    private String processUDPRequest(String request) throws IOException {
        Scanner parser = new Scanner(request);
        if (!parser.hasNext()) return "NA";

        String command = parser.next();

        if (command.startsWith("NET_")) {
            return "NA";
        } else {
            return handleUDPClientCommand(command, request, parser);
        }
    }

    private String handleUDPClientCommand(String command, String request, Scanner parser) {
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

    private String forwardRequest(String keyName, String clientRequest) {
        ServerLink nextHop = state.getRouteForKey(keyName);
        if (nextHop == null) return "NA";

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
        if (keys.isEmpty()) return "NA";
        StringBuilder response = new StringBuilder("OK ").append(keys.size()).append(" ");
        for (String key : keys) { response.append(key).append(" "); }
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