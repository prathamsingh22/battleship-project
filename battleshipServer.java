import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class battleshipServer {
    public static final int DEFAULT_PORT = 12345;
    public static final int MAX_CLIENTS = 2;

    private final int port;
    private final ArrayList<ClientHandler> clients = new ArrayList<>();
    private final Board[] boards = {new Board(), new Board()};
    private String phase = "WAITING";
    private int currentTurn = 1;

    public battleshipServer(int port) {
        this.port = port;
    }

    public void start() {
        System.out.println("Battleship Server starting on port " + port + "...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                handleConnection(serverSocket.accept());
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private void handleConnection(Socket socket) {
        ClientHandler handler;
        synchronized (clients) {
            if (clients.size() >= MAX_CLIENTS) {
                rejectClient(socket);
                return;
            }

            int playerId = isPlayerIdTaken(1) ? 2 : 1;
            try {
                handler = new ClientHandler(socket, playerId, this);
            } catch (IOException e) {
                closeQuietly(socket);
                return;
            }

            clients.add(handler);
            System.out.println("Player " + playerId + " connected (" + clients.size() + "/" + MAX_CLIENTS + ").");
            handler.send("ASSIGN:" + playerId);

            if (clients.size() < MAX_CLIENTS) {
                phase = "WAITING";
                handler.send("PHASE:WAITING");
                handler.send("MSG:Connected as Player " + playerId + ". Waiting for another player...");
                sendBoards(handler);
            } else {
                startPlacementPhase();
            }
        }
        new Thread(handler, "Player-" + handler.playerId).start();
    }

    private void startPlacementPhase() {
        boards[0].reset();
        boards[1].reset();
        phase = "PLACING";
        currentTurn = 1;
        System.out.println("Both players connected. Starting ship placement.");

        broadcast("PHASE:PLACING");
        broadcast("TURN:0");
        broadcastBoards(false);
        for (ClientHandler c : clients) {
            sendNextShipPrompt(c);
        }
    }

    private static int otherPlayer(int playerId) {
        return (playerId == 1) ? 2 : 1;
    }

    private void broadcast(String msg) {
        for (ClientHandler c : clients) {
            c.send(msg);
        }
    }

    private void sendBoards(ClientHandler c) {
        sendBoards(c, false);
    }

    private void sendBoards(ClientHandler c, boolean revealOpponent) {
        Board own = boards[c.playerId - 1];
        Board opp = boards[otherPlayer(c.playerId) - 1];
        c.send("OWN_BOARD:" + own.serialize(true));
        c.send("OPP_BOARD:" + opp.serialize(revealOpponent));
    }

    private void broadcastBoards(boolean revealAll) {
        for (ClientHandler c : clients) {
            sendBoards(c, revealAll);
        }
    }

    private void sendNextShipPrompt(ClientHandler c) {
        int next = boards[c.playerId - 1].getNextShipIndex();
        c.send("NEXT_SHIP:" + next);
        if (next == -1) {
            c.send("MSG:All ships placed! Waiting for Player " + otherPlayer(c.playerId) + "...");
        } else {
            c.send("MSG:Place " + Board.SHIP_NAMES[next] + " (length " + Board.SHIP_LENGTHS[next]
                    + ") - click a cell, right-click or Rotate to flip.");
        }
    }

    public void handleCommand(ClientHandler player, String raw) {
        synchronized (clients) {
            String input = raw.trim();
            if (input.isEmpty()) return;

            switch (phase) {
                case "PLACING":
                    handlePlacementCommand(player, input);
                    break;
                case "PLAYING":
                    handleBattleCommand(player, input);
                    break;
                case "GAME_OVER":
                    player.send("MSG:Game is over. No more moves allowed.");
                    break;
                default:
                    player.send("MSG:Waiting for another player to join...");
                    break;
            }
        }
    }

    private void handlePlacementCommand(ClientHandler player, String input) {
        String upper = input.toUpperCase();
        Board board = boards[player.playerId - 1];

        if (upper.equals("RESET")) {
            board.reset();
            System.out.println("Player " + player.playerId + " reset their board.");
            sendBoards(player);
            sendNextShipPrompt(player);
            return;
        }

        if (upper.equals("AUTO")) {
            if (!board.isPlacementComplete()) {
                board.placeRemainingRandomly();
                System.out.println("Player " + player.playerId + " auto-placed ships.");
                sendBoards(player);
                checkAllPlaced(player);
            }
            return;
        }

        if (board.isPlacementComplete()) {
            player.send("MSG:Already placed all ships. Waiting for opponent (or click Reset).");
            return;
        }

        // Expected: PLACE <coord> <H|V>
        String[] parts = input.split("\\s+");
        if (parts.length != 3 || !parts[0].equalsIgnoreCase("PLACE")) {
            player.send("MSG:Invalid command during placement.");
            return;
        }

        int[] coord = Board.parseCoordinate(parts[1]);
        boolean horizontal = parts[2].equalsIgnoreCase("H");
        if (coord == null || (!parts[2].equalsIgnoreCase("H") && !parts[2].equalsIgnoreCase("V"))) {
            player.send("MSG:Invalid coordinate or orientation.");
            return;
        }

        int next = board.getNextShipIndex();
        String error = board.placeShip(next, coord[0], coord[1], horizontal);
        if (error != null) {
            player.send("MSG:Cannot place: " + error);
            return;
        }

        System.out.println("Player " + player.playerId + " placed " + Board.SHIP_NAMES[next]
                + " at " + Board.formatCoordinate(coord[0], coord[1]) + " (" + (horizontal ? "H" : "V") + ").");
        sendBoards(player);
        checkAllPlaced(player);
    }

    private void handleBattleCommand(ClientHandler player, String input) {
        int opponentId = otherPlayer(player.playerId);

        if (input.equalsIgnoreCase("SURRENDER")) {
            endGame(opponentId, "Player " + player.playerId + " surrendered! Player " + opponentId + " wins!");
            return;
        }

        // Expected: FIRE <coord>
        String[] parts = input.split("\\s+");
        if (parts.length != 2 || !parts[0].equalsIgnoreCase("FIRE")) {
            player.send("MSG:Invalid command. Click Opponent's Waters to fire (or Surrender).");
            return;
        }

        if (player.playerId != currentTurn) {
            player.send("MSG:Not your turn! Waiting for Player " + currentTurn + "...");
            return;
        }

        int[] coord = Board.parseCoordinate(parts[1]);
        if (coord == null) {
            player.send("MSG:Invalid coordinate: " + parts[1]);
            return;
        }

        Board opponentBoard = boards[opponentId - 1];
        Board.ShotResult result = opponentBoard.recordShot(coord[0], coord[1]);
        String coordStr = Board.formatCoordinate(coord[0], coord[1]);

        if (result == Board.ShotResult.ALREADY_FIRED) {
            player.send("MSG:You already fired at " + coordStr + ". Pick another cell.");
            return;
        }

        String sunkName = (result == Board.ShotResult.SUNK)
                ? opponentBoard.getShipNameAt(coord[0], coord[1])
                : null;
        System.out.println("Player " + player.playerId + " fired at " + coordStr + ": " + result
                + (sunkName != null ? " (" + sunkName + ")" : "") + ".");

        if (opponentBoard.allShipsSunk()) {
            endGame(player.playerId, "Player " + player.playerId + " sunk the " + sunkName
                    + " and wins the game!");
            return;
        }

        broadcastBoards(false);
        currentTurn = opponentId;
        broadcast("TURN:" + currentTurn);

        String shooterMsg;
        String defenderMsg;
        if (result == Board.ShotResult.MISS) {
            shooterMsg = "Miss at " + coordStr + ". Waiting for Player " + opponentId + "...";
            defenderMsg = "Player " + player.playerId + " missed at " + coordStr + ". Your turn!";
        } else if (result == Board.ShotResult.HIT) {
            shooterMsg = "Hit at " + coordStr + "! Waiting for Player " + opponentId + "...";
            defenderMsg = "Player " + player.playerId + " hit your ship at " + coordStr + "! Your turn!";
        } else {
            int remaining = opponentBoard.getRemainingShipCount();
            shooterMsg = "Sunk Player " + opponentId + "'s " + sunkName + " at " + coordStr
                    + "! (" + remaining + " enemy ships left) Waiting for Player " + opponentId + "...";
            defenderMsg = "Player " + player.playerId + " sunk your " + sunkName + " at " + coordStr
                    + "! Your turn!";
        }

        for (ClientHandler c : clients) {
            c.send("MSG:" + (c.playerId == player.playerId ? shooterMsg : defenderMsg));
        }
    }

    private void checkAllPlaced(ClientHandler player) {
        if (boards[0].isPlacementComplete() && boards[1].isPlacementComplete()) {
            phase = "PLAYING";
            currentTurn = 1;
            System.out.println("Both players finished placing ships. Battle started! Player 1 goes first.");
            broadcast("PHASE:PLAYING");
            broadcast("NEXT_SHIP:-1");
            broadcast("TURN:" + currentTurn);
            broadcastBoards(false);
            for (ClientHandler c : clients) {
                c.send(c.playerId == currentTurn
                        ? "MSG:Battle started! Your turn - click Opponent's Waters to fire."
                        : "MSG:Battle started! Waiting for Player " + currentTurn + " to fire...");
            }
        } else {
            sendNextShipPrompt(player);
        }
    }

    private void endGame(int winnerId, String message) {
        phase = "GAME_OVER";
        currentTurn = 0;
        System.out.println("Game over (Winner: Player " + winnerId + "): " + message);
        broadcastBoards(true);
        broadcast("PHASE:GAME_OVER");
        broadcast("TURN:0");
        broadcast("MSG:" + message);
    }

    public void removeClient(ClientHandler client) {
        synchronized (clients) {
            if (clients.remove(client)) {
                System.out.println("Player " + client.playerId + " disconnected ("
                        + clients.size() + "/" + MAX_CLIENTS + ").");
                phase = "WAITING";
                currentTurn = 1;
                boards[0].reset();
                boards[1].reset();
                for (ClientHandler remaining : clients) {
                    remaining.send("PHASE:WAITING");
                    remaining.send("NEXT_SHIP:-1");
                    remaining.send("TURN:0");
                    sendBoards(remaining);
                    remaining.send("MSG:Player " + client.playerId + " disconnected. Waiting for another player...");
                }
            }
        }
    }

    private boolean isPlayerIdTaken(int id) {
        for (ClientHandler c : clients) {
            if (c.playerId == id) return true;
        }
        return false;
    }

    private void rejectClient(Socket socket) {
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("SERVER_FULL:Server already has " + MAX_CLIENTS + " players.");
        } catch (IOException ignored) {
        } finally {
            closeQuietly(socket);
        }
    }

    private static void closeQuietly(Socket s) {
        try {
            if (s != null && !s.isClosed()) s.close();
        } catch (IOException ignored) {
        }
    }

    private static class ClientHandler implements Runnable {
        final Socket socket;
        final int playerId;
        final battleshipServer server;
        final BufferedReader in;
        final PrintWriter out;

        ClientHandler(Socket socket, int playerId, battleshipServer server) throws IOException {
            this.socket = socket;
            this.playerId = playerId;
            this.server = server;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        }

        synchronized void send(String msg) {
            out.println(msg);
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("QUIT")) break;
                    server.handleCommand(this, line);
                }
            } catch (IOException ignored) {
            } finally {
                server.removeClient(this);
                closeQuietly(socket);
            }
        }
    }

    public static void main(String[] args) {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new battleshipServer(port).start();
    }
}
