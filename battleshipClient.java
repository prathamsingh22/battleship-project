import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class battleshipClient {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = battleshipServer.DEFAULT_PORT;

    private final String host;
    private final int port;

    public battleshipClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        System.out.println("Connecting to Battleship Server at " + host + ":" + port + "...");

        try (Socket socket = new Socket(host, port);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);
             Scanner userIn = new Scanner(System.in)) {

            String initialResponse = serverIn.readLine();
            if (initialResponse == null) {
                System.out.println("Server closed the connection.");
                return;
            }
            System.out.println(initialResponse);
            if (initialResponse.startsWith("SERVER_FULL:")) {
                return;
            }

            AtomicBoolean connected = new AtomicBoolean(true);

            Thread listenerThread = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = serverIn.readLine()) != null) {
                        System.out.println(serverMessage);
                        if (serverMessage.startsWith("DISCONNECTED:")) {
                            connected.set(false);
                            break;
                        }
                    }
                } catch (IOException e) {
                    if (connected.get()) {
                        System.out.println("Connection to server closed.");
                    }
                } finally {
                    connected.set(false);
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            }, "Server-Listener-Thread");

            listenerThread.setDaemon(true);
            listenerThread.start();

            while (connected.get()) {
                if (!userIn.hasNextLine()) {
                    break;
                }
                String input = userIn.nextLine();
                if (!connected.get()) {
                    break;
                }
                serverOut.println(input);
                if (input.equalsIgnoreCase("quit")) {
                    connected.set(false);
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Could not connect to server at " + host + ":" + port + " (" + e.getMessage() + ")");
        }
    }

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port argument. Using default port " + DEFAULT_PORT + ".");
            }
        }

        battleshipClient client = new battleshipClient(host, port);
        client.start();
    }
}
