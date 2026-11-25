import java.util.ArrayList;
import java.util.Random;

public class Board {
    public static final int SIZE = 10;
    public static final char WATER = '~';
    public static final char HIT = 'X';
    public static final char MISS = 'O';

    public static final String[] SHIP_NAMES = {"Carrier", "Battleship", "Cruiser", "Submarine", "Destroyer"};
    public static final char[] SHIP_SYMBOLS = {'C', 'B', 'R', 'S', 'D'};
    public static final int[] SHIP_LENGTHS = {5, 4, 3, 3, 2};

    public enum ShotResult {
        INVALID, ALREADY_FIRED, MISS, HIT, SUNK
    }

    public static class Ship {
        final int typeIndex;
        final int startRow;
        final int startCol;
        final boolean horizontal;
        int hits = 0;

        Ship(int typeIndex, int startRow, int startCol, boolean horizontal) {
            this.typeIndex = typeIndex;
            this.startRow = startRow;
            this.startCol = startCol;
            this.horizontal = horizontal;
        }

        boolean occupies(int row, int col) {
            int len = SHIP_LENGTHS[typeIndex];
            if (horizontal) {
                return row == startRow && col >= startCol && col < startCol + len;
            }
            return col == startCol && row >= startRow && row < startRow + len;
        }

        boolean isSunk() {
            return hits >= SHIP_LENGTHS[typeIndex];
        }
    }

    private final char[][] grid = new char[SIZE][SIZE];
    private final ArrayList<Ship> ships = new ArrayList<>();

    public Board() {
        reset();
    }

    public void reset() {
        ships.clear();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                grid[r][c] = WATER;
            }
        }
    }

    public static boolean isInBounds(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    public static int[] parseCoordinate(String token) {
        if (token == null) return null;
        String s = token.trim().toUpperCase();
        if (s.length() < 2 || s.length() > 3) return null;
        int row = s.charAt(0) - 'A';
        int col;
        try {
            col = Integer.parseInt(s.substring(1)) - 1;
        } catch (NumberFormatException e) {
            return null;
        }
        return isInBounds(row, col) ? new int[]{row, col} : null;
    }

    public static String formatCoordinate(int row, int col) {
        return "" + (char) ('A' + row) + (col + 1);
    }

    public int getNextShipIndex() {
        return ships.size() < SHIP_LENGTHS.length ? ships.size() : -1;
    }

    public boolean isPlacementComplete() {
        return ships.size() == SHIP_LENGTHS.length;
    }

    public boolean allShipsSunk() {
        return !ships.isEmpty() && getRemainingShipCount() == 0;
    }

    public String getShipNameAt(int row, int col) {
        for (Ship s : ships) {
            if (s.occupies(row, col)) {
                return SHIP_NAMES[s.typeIndex];
            }
        }
        return null;
    }

    public int getRemainingShipCount() {
        int count = 0;
        for (Ship s : ships) {
            if (!s.isSunk()) count++;
        }
        return count;
    }

    /** Returns null on success, or an error message if placement is invalid. */
    public String placeShip(int typeIndex, int startRow, int startCol, boolean horizontal) {
        int len = SHIP_LENGTHS[typeIndex];
        for (int i = 0; i < len; i++) {
            int r = horizontal ? startRow : startRow + i;
            int c = horizontal ? startCol + i : startCol;
            if (!isInBounds(r, c)) {
                return SHIP_NAMES[typeIndex] + " goes out of bounds.";
            }
            if (grid[r][c] != WATER) {
                return "Overlaps an existing ship at " + formatCoordinate(r, c) + ".";
            }
        }

        ships.add(new Ship(typeIndex, startRow, startCol, horizontal));
        for (int i = 0; i < len; i++) {
            int r = horizontal ? startRow : startRow + i;
            int c = horizontal ? startCol + i : startCol;
            grid[r][c] = SHIP_SYMBOLS[typeIndex];
        }
        return null;
    }

    public void placeRemainingRandomly() {
        Random rand = new Random();
        while (!isPlacementComplete()) {
            int next = getNextShipIndex();
            boolean placed = false;
            for (int tries = 0; tries < 500 && !placed; tries++) {
                if (placeShip(next, rand.nextInt(SIZE), rand.nextInt(SIZE), rand.nextBoolean()) == null) {
                    placed = true;
                }
            }
            if (!placed) reset();
        }
    }

    public ShotResult recordShot(int row, int col) {
        if (!isInBounds(row, col)) return ShotResult.INVALID;
        if (grid[row][col] == HIT || grid[row][col] == MISS) return ShotResult.ALREADY_FIRED;

        for (Ship ship : ships) {
            if (ship.occupies(row, col)) {
                ship.hits++;
                grid[row][col] = HIT;
                return ship.isSunk() ? ShotResult.SUNK : ShotResult.HIT;
            }
        }
        grid[row][col] = MISS;
        return ShotResult.MISS;
    }

    public String serialize(boolean revealShips) {
        StringBuilder sb = new StringBuilder(SIZE * SIZE);
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                char cell = grid[r][c];
                if (!revealShips && cell != WATER && cell != HIT && cell != MISS) {
                    cell = WATER;
                }
                sb.append(cell);
            }
        }
        return sb.toString();
    }
}
