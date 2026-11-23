import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class battleshipServer {
    public static final int DEFAULT_PORT = 12345;
    public static final int MIN_CLIENTS = 2;
    public static final int MAX_CLIENTS = 2;

    private final int port;
    private final ArrayList<ClientHandler> clients = new ArrayList<>();
    private volatile boolean sessionActive = false;

    public battleshipServer(int port) {
        this.port = port;
    }

    public void start() {
        System.out.println("Battleship Server starting on port " + port + "...");
        System.out.println("Waiting for " + MIN_CLIENTS + " players to connect (min: "
                + MIN_CLIENTS + ", max: " + MAX_CLIENTS + ").");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                handleIncomingConnection(clientSocket);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private void handleIncomingConnection(Socket socket) {
        ClientHandler handler = null;

        synchronized (clients) {
            if (clients.size() >= MAX_CLIENTS) {
                rejectClient(socket);
                return;
            }

            int playerId = getNextAvailablePlayerId();
            try {
                handler = new ClientHandler(socket, playerId, this);
                clients.add(handler);
                System.out.println("Player " + playerId + " connected (" + clients.size() + "/" + MAX_CLIENTS + ").");

                if (clients.size() < MIN_CLIENTS) {
                    sessionActive = false;
                    handler.sendMessage("CONNECTED: You are Player " + playerId
                            + ". Waiting for another player to join (" + clients.size() + "/" + MIN_CLIENTS + ")...");
                } else {
                    sessionActive = true;
                    handler.sendMessage("CONNECTED: You are Player " + playerId + ".");
                    broadcast("READY: Both players are connected (" + clients.size() + "/" + MAX_CLIENTS
                            + "). Communication is now active! Type a message or 'quit' to exit.");
                    System.out.println("Both players connected. Session is now active.");
                }
            } catch (IOException e) {
                System.err.println("Failed to initialize client handler: " + e.getMessage());
                closeQuietly(socket);
                return;
            }
        }

        Thread clientThread = new Thread(handler, "Player-" + handler.getPlayerId() + "-Thread");
        clientThread.start();
    }

    private int getNextAvailablePlayerId() {
        boolean player1Taken = false;
        for (ClientHandler client : clients) {
            if (client.getPlayerId() == 1) {
                player1Taken = true;
                break;
            }
        }
        return player1Taken ? 2 : 1;
    }

    private void rejectClient(Socket socket) {
        System.out.println("Rejected incoming connection from " + socket.getRemoteSocketAddress()
                + " (server full: " + MAX_CLIENTS + "/" + MAX_CLIENTS + ").");
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("SERVER_FULL: Server already has the maximum of " + MAX_CLIENTS
                    + " players connected. Connection rejected.");
        } catch (IOException e) {
            System.err.println("Error sending rejection message: " + e.getMessage());
        } finally {
            closeQuietly(socket);
        }
    }

    public boolean isSessionActive() {
        return sessionActive;
    }

    public void broadcast(String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(message);
            }
        }
    }

    public void handleClientMessage(ClientHandler sender, String message) {
        synchronized (clients) {
            if (!sessionActive || clients.size() < MIN_CLIENTS) {
                sender.sendMessage("WAIT: Cannot communicate yet. Waiting for " + MIN_CLIENTS
                        + " players to be connected (" + clients.size() + "/" + MIN_CLIENTS + ").");
                return;
            }

            System.out.println("[Player " + sender.getPlayerId() + "]: " + message);
            for (ClientHandler client : clients) {
                if (client != sender) {
                    client.sendMessage("[Player " + sender.getPlayerId() + "]: " + message);
                }
            }
        }
    }

    public void removeClient(ClientHandler client) {
        synchronized (clients) {
            if (clients.remove(client)) {
                System.out.println("Player " + client.getPlayerId() + " disconnected ("
                        + clients.size() + "/" + MAX_CLIENTS + ").");
                if (clients.size() < MIN_CLIENTS) {
                    sessionActive = false;
                    broadcast("NOTICE: Player " + client.getPlayerId()
                            + " disconnected. Session paused until " + MIN_CLIENTS
                            + " players are connected (" + clients.size() + "/" + MIN_CLIENTS + ").");
                }
            }
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final int playerId;
        private final battleshipServer server;
        private final BufferedReader in;
        private final PrintWriter out;

        public ClientHandler(Socket socket, int playerId, battleshipServer server) throws IOException {
            this.socket = socket;
            this.playerId = playerId;
            this.server = server;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        }

        public int getPlayerId() {
            return playerId;
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("quit")) {
                        sendMessage("DISCONNECTED: You have left the server.");
                        break;
                    }
                    server.handleClientMessage(this, line);
                }
            } catch (IOException e) {
                System.out.println("Connection lost with Player " + playerId + ".");
            } finally {
                server.removeClient(this);
                closeQuietly(socket);
            }
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port argument. Using default port " + DEFAULT_PORT + ".");
            }
        }
        battleshipServer server = new battleshipServer(port);
        server.start();
    }
}
