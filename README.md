# battleship-project
A local two-player Battleship game built in Java.

## The Problem
Playing Battleship with a friend on a computer requires keeping each player's board hidden while still validating every move. The goal of this project is to build a local networked Battleship game in Java where a central server tracks both boards and enforces the rules, and each player plays through a simple Swing window.

## How It Works
The project has three Java files:
- `battleshipServer.java` waits for two players to join, tracks both 10x10 boards, validates ship placements and shots, enforces alternating turns, broadcasts board updates to both players, and detects win/surrender conditions.
- `Board.java` stores each player's grid, ships, hits, and misses.
- `battleshipClient.java` connects to the server and opens a Swing window showing your fleet on the left and your opponent's waters on the right. Players click cells to place ships (right-click or use **Rotate** to flip, or use **Auto-Place** and **Reset**), then click cells on the opponent's grid to fire during battle (or click **Surrender** to forfeit).


![System Flowchart](FlowChart%20CS193%20Battleship%20Project.drawio.png)

## Tech Stack
- **Language:** Java (JDK 17 or newer)
- **Libraries:** Java Sockets (`ServerSocket`, `Socket`), `ArrayList`, Java Swing (`JFrame`, `JPanel`, `JButton`)
- **External Tools:** Git, GitHub, Draw.io
- **Data Storage:** In-memory arrays and `ArrayList`s on the server (future scoreboard will save to a text file)

## How to Run
Make sure you have **Git** and **Java (JDK 17+)** installed.

1. Clone the repo and compile:
   ```bash
   git clone https://github.com/prathamsingh22/battleship-project.git
   cd battleship-project
   javac *.java
   ```

2. Start the server in one terminal:
   ```bash
   java battleshipServer
   ```

3. Start two clients in two separate terminals:
   ```bash
   java battleshipClient
   ```

Once both windows open, hover over **YOUR FLEET** and left-click to place each ship (green = valid, red = invalid). Right-click or click **Rotate** to switch between horizontal and vertical, or click **Auto-Place** to place your remaining ships randomly. Once both players finish placing their ships, battle starts with Player 1 going first—left-click cells on **OPPONENT'S WATERS** on your turn to fire (or click **Surrender** to forfeit). When the game ends, both boards are revealed.

### Example Run
When two players connect, place ships, and play a turn, the server terminal logs:
```text
Battleship Server starting on port 12345...
Player 1 connected (1/2).
Player 2 connected (2/2).
Both players connected. Starting ship placement.
Player 1 placed Carrier at A1 (H).
Player 2 auto-placed ships.
Player 1 auto-placed ships.
Both players finished placing ships. Battle started! Player 1 goes first.
Player 1 fired at B4: HIT.
Player 2 fired at E7: MISS.
```

For example:
- **Placing a ship (Player 1 clicks A1):** Client sends `PLACE A1 H` to the server. The server validates the spot, updates Player 1's board with the Carrier at `A1`–`A5`, and prompts for the next ship.
- **Firing a shot (Player 1 clicks B4):** Client sends `FIRE B4` to the server. The server checks that it is Player 1's turn and that `B4` has not been fired at yet, updates both players' boards (`X` for hit, `O` for miss), switches to Player 2's turn, or ends the game if all enemy ships are sunk.

## Roadmap

### MVP Scope
- [x] Setup git repo and environment
- [x] Create client and server communication with max and min of two clients
- [x] Setup board tracking for both players in the server
- [x] Setup initial boat placements for both players before game starts (can happen at the same time)
- [x] Setup showing the correct board for both players with updates for both players after each turn
- [x] Setup different inputs they can each perform
- [x] Create win conditions to end game
- [x] Create turn enforcement and input checks to disallow bad inputs

### Future Plans
- [ ] Handle client disconnect mid-game
- [ ] AI as a third client for solo playing
- [ ] Visual icons for ships and color coded hits and misses
- [ ] Reset game after win instead of killing the server
- [ ] Scoreboard
