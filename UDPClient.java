import java.util.*;
import java.net.*;
import java.io.*;

public class UDPClient {
	public static void main(String[] args) {
		
		DatagramSocket socket = null;
		
		InetAddress address = null;
		int port = 0;
		String command = "";


		for(int i=0; i<args.length;) {
			switch(args[i]) {
				case "-address":
					try {
						address = InetAddress.getByName(args[i+1]);
					} catch (UnknownHostException e) {
						System.err.println("Unknown host: " + args[i+1]);
					}
					i += 2;
					break;
				case "-port":
					port = Integer.parseInt(args[i+1]);
					i +=2;
					break;
				case "-command":
					i++;
					command = args[i++];
					String parameter = "";
					String name = "";
					int value = 0;

					switch(command) {
						case "GET":
							parameter=args[i++];
							command += " " + parameter;
							if(parameter.equals("NAMES")) {
							} else if(parameter.equals("VALUE")) {
								name = args[i++];
								command += " " + name;
							} else {
								System.err.println("Unknown: " + parameter);
							}
							break;
							case "SET":
								name = args[i++];
								value = Integer.parseInt(args[i++]);
								command += " " + name + " " + value;
								break;
						case "QUIT":
							break;
						default:
							System.err.println("Unknown: " + command);
					}
					break;
				default:
					System.err.println("Unknown parameter: " + args[i]);
					i++;
			}
		}
		
		if(address == null || port == 0 || command.equals("")) {
			System.err.println("Incorrect execution syntax");
			System.exit(1);
		}
		try {
			System.out.println("Creating a client socket");
			socket = new DatagramSocket();
			System.out.println("Socket created");
		} catch(SocketException e) {
			System.err.println("Error creating socket: " + e);
			System.exit(1);
		}

		byte[] buffer = (command+" ").getBytes();
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
		try {
			System.out.println("Sending " + command + " as a request");
			socket.send(packet);
		} catch(IOException e) {
			System.err.println("Error while sending: " + e);
			System.exit(1);
		}
	
		if(! command.equals("QUIT")) {	
			buffer = new byte[256];
			packet = new DatagramPacket(buffer, buffer.length);
			try{
				System.out.println("Waiting for a response");
				socket.receive(packet);
			} catch(IOException e) {
				System.err.println("Error while reading: " + e);
				System.exit(1);			
			}
	
			String received = new String(packet.getData(), 0, packet.getLength());
			System.out.println(received);
		}
		socket.close();
	}
}
