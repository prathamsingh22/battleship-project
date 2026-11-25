import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class battleshipClient {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = battleshipServer.DEFAULT_PORT;

    private static final Color WATER_COLOR = new Color(36, 98, 158);
    private static final Color SHIP_COLOR = new Color(96, 120, 148);
    private static final Color HIT_COLOR = new Color(210, 56, 56);
    private static final Color MISS_COLOR = new Color(180, 194, 208);
    private static final Color PREVIEW_OK = new Color(72, 188, 108);
    private static final Color PREVIEW_BAD = new Color(224, 76, 76);
    private static final Color TARGET_HOVER = new Color(64, 146, 214);

    private final String host;
    private final int port;

    private Socket socket;
    private PrintWriter out;

    private JFrame frame;
    private JLabel statusLabel;
    private JButton[][] ownButtons = new JButton[Board.SIZE][Board.SIZE];
    private JButton[][] oppButtons = new JButton[Board.SIZE][Board.SIZE];
    private JButton rotateButton, autoButton, resetButton, surrenderButton;

    private char[][] ownCells = new char[Board.SIZE][Board.SIZE];
    private char[][] oppCells = new char[Board.SIZE][Board.SIZE];
    private String phase = "WAITING";
    private int myPlayerId = 0;
    private int currentTurn = 0;
    private int nextShipIndex = -1;
    private boolean horizontal = true;
    private int hoverRow = -1, hoverCol = -1;
    private int oppHoverRow = -1, oppHoverCol = -1;

    public battleshipClient(String host, int port) {
        this.host = host;
        this.port = port;
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                ownCells[r][c] = Board.WATER;
                oppCells[r][c] = Board.WATER;
            }
        }
    }

    public void start() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            buildGui();
            Thread t = new Thread(this::connectAndListen, "Server-Listener");
            t.setDaemon(true);
            t.start();
        });
    }

    private void buildGui() {
        frame = new JFrame("Battleship");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));

        statusLabel = new JLabel("Connecting to " + host + ":" + port + "...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        frame.add(statusLabel, BorderLayout.NORTH);

        JPanel boardsPanel = new JPanel(new GridLayout(1, 2, 16, 0));
        boardsPanel.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        boardsPanel.add(createBoardPanel("YOUR FLEET", ownButtons, true));
        boardsPanel.add(createBoardPanel("OPPONENT'S WATERS", oppButtons, false));
        frame.add(boardsPanel, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        rotateButton = new JButton("Rotate: Horizontal");
        autoButton = new JButton("Auto-Place");
        resetButton = new JButton("Reset");
        surrenderButton = new JButton("Surrender");

        rotateButton.addActionListener(e -> toggleOrientation());
        autoButton.addActionListener(e -> send("AUTO"));
        resetButton.addActionListener(e -> send("RESET"));
        surrenderButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(frame, "Are you sure you want to surrender?",
                    "Surrender", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                send("SURRENDER");
            }
        });

        updateControlsForPhase();
        controls.add(rotateButton);
        controls.add(autoButton);
        controls.add(resetButton);
        controls.add(surrenderButton);
        frame.add(controls, BorderLayout.SOUTH);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel createBoardPanel(String title, JButton[][] buttons, boolean isOwn) {
        JPanel wrapper = new JPanel(new BorderLayout(4, 4));
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        wrapper.add(titleLabel, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(Board.SIZE + 1, Board.SIZE + 1, 2, 2));
        grid.add(new JLabel(""));
        for (int c = 1; c <= Board.SIZE; c++) {
            grid.add(new JLabel(String.valueOf(c), SwingConstants.CENTER));
        }

        for (int r = 0; r < Board.SIZE; r++) {
            grid.add(new JLabel(String.valueOf((char) ('A' + r)), SwingConstants.CENTER));
            for (int c = 0; c < Board.SIZE; c++) {
                JButton btn = new JButton();
                btn.setPreferredSize(new Dimension(34, 34));
                btn.setFont(new Font("SansSerif", Font.BOLD, 12));
                btn.setForeground(Color.WHITE);
                btn.setBackground(WATER_COLOR);
                btn.setFocusPainted(false);

                final int row = r, col = c;
                if (isOwn) {
                    btn.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            hoverRow = row;
                            hoverCol = col;
                            refreshOwnBoardColors();
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            hoverRow = -1;
                            hoverCol = -1;
                            refreshOwnBoardColors();
                        }

                        @Override
                        public void mousePressed(MouseEvent e) {
                            if (!"PLACING".equals(phase) || nextShipIndex == -1) return;
                            if (SwingUtilities.isRightMouseButton(e)) {
                                toggleOrientation();
                            } else if (SwingUtilities.isLeftMouseButton(e)) {
                                send("PLACE " + Board.formatCoordinate(row, col) + " " + (horizontal ? "H" : "V"));
                            }
                        }
                    });
                } else {
                    btn.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            oppHoverRow = row;
                            oppHoverCol = col;
                            refreshOppBoardColors();
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            oppHoverRow = -1;
                            oppHoverCol = -1;
                            refreshOppBoardColors();
                        }

                        @Override
                        public void mousePressed(MouseEvent e) {
                            if (!"PLAYING".equals(phase)) return;
                            if (SwingUtilities.isLeftMouseButton(e)) {
                                send("FIRE " + Board.formatCoordinate(row, col));
                            }
                        }
                    });
                }

                buttons[r][c] = btn;
                grid.add(btn);
            }
        }

        wrapper.add(grid, BorderLayout.CENTER);
        return wrapper;
    }

    private void toggleOrientation() {
        horizontal = !horizontal;
        rotateButton.setText("Rotate: " + (horizontal ? "Horizontal" : "Vertical"));
        refreshOwnBoardColors();
    }

    private void updateControlsForPhase() {
        boolean placing = "PLACING".equals(phase);
        boolean playing = "PLAYING".equals(phase);
        rotateButton.setEnabled(placing);
        autoButton.setEnabled(placing);
        resetButton.setEnabled(placing);
        surrenderButton.setEnabled(playing);
    }

    private static void renderGrid(JButton[][] buttons, char[][] cells) {
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                styleCell(buttons[r][c], cells[r][c]);
            }
        }
    }

    private static void loadBoard(char[][] target, String payload) {
        for (int i = 0; i < payload.length(); i++) {
            target[i / Board.SIZE][i % Board.SIZE] = payload.charAt(i);
        }
    }

    private void refreshOppBoardColors() {
        renderGrid(oppButtons, oppCells);

        if ("PLAYING".equals(phase) && currentTurn == myPlayerId
                && oppHoverRow != -1 && oppCells[oppHoverRow][oppHoverCol] == Board.WATER) {
            oppButtons[oppHoverRow][oppHoverCol].setBackground(TARGET_HOVER);
        }
    }

    private void refreshOwnBoardColors() {
        renderGrid(ownButtons, ownCells);

        if ("PLACING".equals(phase) && nextShipIndex != -1 && hoverRow != -1) {
            int len = Board.SHIP_LENGTHS[nextShipIndex];
            boolean valid = true;
            for (int i = 0; i < len; i++) {
                int r = horizontal ? hoverRow : hoverRow + i;
                int c = horizontal ? hoverCol + i : hoverCol;
                if (!Board.isInBounds(r, c) || ownCells[r][c] != Board.WATER) {
                    valid = false;
                    break;
                }
            }
            Color previewColor = valid ? PREVIEW_OK : PREVIEW_BAD;
            for (int i = 0; i < len; i++) {
                int r = horizontal ? hoverRow : hoverRow + i;
                int c = horizontal ? hoverCol + i : hoverCol;
                if (Board.isInBounds(r, c)) {
                    ownButtons[r][c].setBackground(previewColor);
                }
            }
        }
    }

    private static void styleCell(JButton btn, char cell) {
        if (cell == Board.WATER) {
            btn.setBackground(WATER_COLOR);
            btn.setText("");
        } else if (cell == Board.HIT) {
            btn.setBackground(HIT_COLOR);
            btn.setText("X");
        } else if (cell == Board.MISS) {
            btn.setBackground(MISS_COLOR);
            btn.setText("O");
        } else {
            btn.setBackground(SHIP_COLOR);
            btn.setText(String.valueOf(cell));
        }
    }

    private void connectAndListen() {
        try {
            socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String line;
            while ((line = in.readLine()) != null) {
                final String msg = line;
                SwingUtilities.invokeLater(() -> handleServerMessage(msg));
                if (line.startsWith("SERVER_FULL:")) break;
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(frame, "Connection error: " + e.getMessage());
                frame.dispose();
            });
        } finally {
            disconnect();
        }
    }

    private void handleServerMessage(String msg) {
        int sep = msg.indexOf(':');
        if (sep == -1) return;
        String type = msg.substring(0, sep);
        String payload = msg.substring(sep + 1);

        switch (type) {
            case "ASSIGN":
                myPlayerId = Integer.parseInt(payload);
                frame.setTitle("Battleship - Player " + myPlayerId);
                break;
            case "PHASE":
                phase = payload;
                updateControlsForPhase();
                refreshOwnBoardColors();
                refreshOppBoardColors();
                break;
            case "TURN":
                currentTurn = Integer.parseInt(payload);
                refreshOppBoardColors();
                break;
            case "NEXT_SHIP":
                nextShipIndex = Integer.parseInt(payload);
                refreshOwnBoardColors();
                break;
            case "OWN_BOARD":
                loadBoard(ownCells, payload);
                refreshOwnBoardColors();
                break;
            case "OPP_BOARD":
                loadBoard(oppCells, payload);
                refreshOppBoardColors();
                break;
            case "MSG":
                statusLabel.setText(payload);
                break;
            case "SERVER_FULL":
                JOptionPane.showMessageDialog(frame, payload, "Server Full", JOptionPane.WARNING_MESSAGE);
                frame.dispose();
                break;
        }
    }

    private void send(String cmd) {
        if (out != null) out.println(cmd);
    }

    private void disconnect() {
        if (out != null) {
            out.println("QUIT");
            out = null;
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
    }

    public static void main(String[] args) {
        String host = (args.length >= 1) ? args[0] : DEFAULT_HOST;
        int port = (args.length >= 2) ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        new battleshipClient(host, port).start();
    }
}
