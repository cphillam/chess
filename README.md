# ♕ BYU CS 240 Chess

This project demonstrates mastery of proper software design, client/server architecture, networking using HTTP and WebSocket, database persistence, unit testing, serialization, and security.

## 10k Architecture Overview

The application implements a multiplayer chess server and a command line chess client.

[![Sequence Diagram](10k-architecture.png)](https://sequencediagram.org/index.html#initialData=C4S2BsFMAIGEAtIGckCh0AcCGAnUBjEbAO2DnBElIEZVs8RCSzYKrgAmO3AorU6AGVIOAG4jUAEyzAsAIyxIYAERnzFkdKgrFIuaKlaUa0ALQA+ISPE4AXNABWAexDFoAcywBbTcLEizS1VZBSVbbVc9HGgnADNYiN19QzZSDkCrfztHFzdPH1Q-Gwzg9TDEqJj4iuSjdmoMopF7LywAaxgvJ3FC6wCLaFLQyHCdSriEseSm6NMBurT7AFcMaWAYOSdcSRTjTka+7NaO6C6emZK1YdHI-Qma6N6ss3nU4Gpl1ZkNrZwdhfeByy9hwyBA7mIT2KAyGGhuSWi9wuc0sAI49nyMG6ElQQA)

## Phase 2: Chess Server Design

[Open the Phase 2 sequence diagram](https://sequencediagram.org/index.html#initialData=C4S2BsFMAIAUAsCGBnGAmAXNAwvSzloBlSAJwDczoARfEAcwDsAoZxAY2AHtSdwRIjYMwAOiUqHYgxQ4mUqlR4ydMSyAEmoAmURWIkgpM4HIqHISg0bUnqiYIgCC7dvmTMt9xACMUMOw6+qKyMXMAwXArQmow6ZAA0AU4ublgASpAAjgCu+MCE2tCk+Nng+dDZqNAAUkQA8gByALQAtogiIpBaNYjkiEWQ7DxayAB0NF7OrgTQ8CBa+NDAeNDI3KSI9DAgLSJQLYIOoFyMo6z0pFzZItAAxBeIAJ58kOLQ7XuG9iAn0AAUAHotN4AJR3ADuc3CzGw-EO0CaAD5TAosNQAKIAGXRABV0dAgd5mCQKFQkdFtLosJdsuECcDmDE4rxySTyOYsOwoOI-iDifJzAjkUkpqloFzXqRecwRSkZuSkkFIGisbj8YhwOAKqhSMh4u9afAllwANaCPXvWLQeiIA7uRV+BEKyZy5BYZDZV0yl3TQhNVkC1zuz2+-lmVxO5FMqkvcQZD1lRmUslR5Okd1kEAakAAL0gfwA3gBfPnRskB0np6BoAAMNegxbDUX9yNhAiEWFr9cbgi05xpN1ujF6zwy9BAayogMqZDBt0hYAsbfhFdRcDqRBxBJnpAAOowCzvhwd9WICODhvrIG0QOAi02UxTYjGaXSATuk8-H2WqwsdVn+DzaAxwncJSAyHI8jYMpoDacAADMeAObpakaaAeFgidkBARh6GgeCBHAEZP2ZSMUTILAABY62gXxumKSC1gfFlyWXDtoGo7s7WQTZIHvSBwCqPp+HorJciYn8hRRDkBnHScpQY8TgD5NlBWdBxRQILAtmAABVHU-iPW1ID5WVfSkh1UCwIhVWwLcd1o54jIOb1AkdFsJg010sH0sgknQ3hGFKcBXOScyPNUoNoF80h-IwoLNWgkxnJgDVikQLRnkgAAPUD3EimAPJ-LBHHAdLMpxRAzUYdFstcERjhYSSIvkCjoDIS5eGKZARBOYI2XLVs4XY6iAGYG243j+MEmAUugCd3j6W8fCgJs1OFH0xXYdLwhiv4YqSUzNvlDa3Ks6AAEkGhstItwOrxQqVMizLFD0vQKizjrdcUdsgRxDUMnVjxM0LNL9U6fD8LArpurd-uWJJHvc9SwrFeH4ERl75VXGT0cRj6irTdJIDksD41KYRmtTL8q3-bMgO6imSN0MiBqrLsGxS-VEENHFTUEe82bItjgE7GjDyB4zud5-nGH42JmF7RWFeYC4rkHHg1C2aBMS4cdGH+AFUAIH5GDnBdoRFqS2awWANy3I23FN-cJbIYHTxQZAL1ILRBdallqeZakrjfY3sJOZnvyJ6A-0zemYF1-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)

The editable [SequenceDiagram.org source](phase-2-server-design.uml) documents the Clear, Register, Login, Logout, List Games, Create Game, and Join Game endpoints across the Client, Server, Handler, Service, DataAccess, and Database layers.

## Modules

The application has three modules.

- **Client**: The command line program used to play a game of chess over the network.
- **Server**: The command line program that listens for network requests from the client and manages users and games.
- **Shared**: Code that is used by both the client and the server. This includes the rules of chess and tracking the state of a game.

## Starter Code

As you create your chess application you will move through specific phases of development. This starts with implementing the moves of chess and finishes with sending game moves over the network between your client and server. You will start each phase by copying course provided [starter-code](starter-code/) for that phase into the source code of the project. Do not copy a phases' starter code before you are ready to begin work on that phase.

## IntelliJ Support

Open the project directory in IntelliJ in order to develop, run, and debug your code using an IDE.

## Maven Support

You can use the following commands to build, test, package, and run your code.

| Command                    | Description                                     |
| -------------------------- | ----------------------------------------------- |
| `mvn compile`              | Builds the code                                 |
| `mvn package`              | Run the tests and build an Uber jar file        |
| `mvn package -DskipTests`  | Build an Uber jar file                          |
| `mvn install`              | Installs the packages into the local repository |
| `mvn test`                 | Run all the tests                               |
| `mvn -pl shared test`      | Run all the shared tests                        |
| `mvn -pl client exec:java` | Build and run the client `Main`                 |
| `mvn -pl server exec:java` | Build and run the server `Main`                 |

These commands are configured by the `pom.xml` (Project Object Model) files. There is a POM file in the root of the project, and one in each of the modules. The root POM defines any global dependencies and references the module POM files.

## Running the program using Java

Once you have compiled your project into an uber jar, you can execute it with the following command.

```sh
java -jar client/target/client-jar-with-dependencies.jar

♕ 240 Chess Client: chess.ChessPiece@7852e922
```
