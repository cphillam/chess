# ♕ BYU CS 240 Chess

This project demonstrates mastery of proper software design, client/server architecture, networking using HTTP and WebSocket, database persistence, unit testing, serialization, and security.

## 10k Architecture Overview

The application implements a multiplayer chess server and a command line chess client.

[![Sequence Diagram](10k-architecture.png)](https://sequencediagram.org/index.html#initialData=C4S2BsFMAIGEAtIGckCh0AcCGAnUBjEbAO2DnBElIEZVs8RCSzYKrgAmO3AorU6AGVIOAG4jUAEyzAsAIyxIYAERnzFkdKgrFIuaKlaUa0ALQA+ISPE4AXNABWAexDFoAcywBbTcLEizS1VZBSVbbVc9HGgnADNYiN19QzZSDkCrfztHFzdPH1Q-Gwzg9TDEqJj4iuSjdmoMopF7LywAaxgvJ3FC6wCLaFLQyHCdSriEseSm6NMBurT7AFcMaWAYOSdcSRTjTka+7NaO6C6emZK1YdHI-Qma6N6ss3nU4Gpl1ZkNrZwdhfeByy9hwyBA7mIT2KAyGGhuSWi9wuc0sAI49nyMG6ElQQA)

## Phase 2: Chess Server Design

[Open the Phase 2 sequence diagram](https://sequencediagram.org/index.html#initialData=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-Z-RvDPEWYa5ZL0TrMIJQcu+SUKWWqvus+ZhykrHJcR06FFzGBwpuSHGM+kHnwCeVoWBrz4EVwTUgjqpAuoDF6v1CwjcuUOOBUQggJChKQo9TC2hXdnUkoxja4Adq7TSmkcvSZfr0QzI1FqEdkBhHuVES68R1rxVcEQtAedi7F5+kpXIodmFN3bsURPEmYCxW2sleo6mRjjXtQZnoutLMK2UU5pLDVyAOVDT4C2nl3ZP18q8Rra4dwRDZFIHsGA2B-oSoOMbDV4SlwKocdEqtDstzqtPFqx5Or0mMH1Y6xgbsNUNFPN+vu+TTVYdcqUsB5TKkgRg68cI866ksTTQhbOud0IFywk-YuBEiJCXLnepVLd6wMTrunJtw0-3V1BcQ9uEKYBXOk-Uu9lb2TRR2ixyA86R4STyJYjN6KlErpngM91RGxk7J9bwqdMzo1BpDfLPexLD4SIUTGzZ99439zkc50+Ry4JooCVmnNZ7lFgILe04tpa3klLE1p9q3y61-MbVgwF8mCFtr4spztaVu2LUiz0hFzDdOsIMyehwtnMXLNhgIrdp4ArgjAEaRgkBwRwcgJdag7mI1MN+jVxAXrcZabkhqvrtK83tWY1V08N6LVaJjI+qpBjb0ss+Wyj9p4+sUfsYcCWAG9vUCA0rYDPjbiPCEoPbr1QuB4R6wh0ydxvDgA4CaFDq4UtVlgPpJ2GqcNFrw3q9NZy3Z7CeGQbAXBB6kH1FNs7RrZtVgKTRiwdG7nQAY0nGoj3GBsaMxxuCXGUJdDzhhfjz8S7CZIu4V9ds8GSdrqPFisnf1HeZ3ljtZC1Ns82+RfdWAABWBODMC5M2c7p2nJ4DtXW6oGYXPXjqxZO9eSaT7BuNEsolg3d1ebmT5rZ-mrVBZTaFNNyue2XL7bmlb7VYvaugSWl5iXmrJbfcgmtqDfkNqNc2rntEQVgo7kVojPbSuy96a6gZBmkd1e9RO31GuRtbyRwNg+kbhsteTbG8+2fdnRTTxbjOGqcff0IL-DqeV2dRfvVWAAQpldjwAEvls90ztLaD-cc+btzxT7aCtkIFxToY8Oe3pVeFlTCLhrgCGufX1HpVyrT8eFVGq7f3mM5idW2tPeMGZYBYH0aNYJqD-y+Cwr4o4dYUWr0RAK13tH+j+V361xPCsdPH8NPbXljew+xu1IETyXjV1Xn0lgGoEcHiRG0zzf2DC9FN0TWpQIBmwdyrAezwiJwTEFzZm23fW7AO2yy53ZX5TIM8TVl-giCiA5kSCJVKkYGeEPi31+EWhEEuDEBtHCG6G4CWBWB1CiEKGAA2AwQ+24IWiEC4GiBxBxFgGgAAFZW4lMBIzggA)

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
