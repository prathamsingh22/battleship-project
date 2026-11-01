# battleship-project
local virtual battleship between two clients

Project Goal: 
- Battleship in the terminal using java so that you can play with your friend or potentially with an AI agent.

MVP Scope:
- Setup git repo and environment
- Create client and server communication with max and min of two clients
- Setup board tracking for both players in the server
- Setup initial boat placements for both players before game starts (can happen at the same time)
- Setup showing the correct board for both players with updates for both players after each turn
- Setup different inputs they can each perform
- Create win conditions to end game
- Create turn enforcement and input checks to disallow bad inputs

Future Plans:
- Handle client disconnect mid-game 
- AI as a third client for solo playing
- Visual icons for ships and color coded hits and misses
- Reset game after win instead of killing the server
- Scoreboard


Tech Stack: 
Language: Java
Libraries/commands: ArrayList, Scanners, Server Socket and Socket
Stored data: likely sits in ArrayList (if I build a scoreboard, it will be saved in a basic text document)
