# CPD Project 2

CPD Project 2 of group T09G16.

## Chat Application – Setup & Execution Guide

### 1. Requirements

- Java 23 (Ensure your JDK version matches the maven.compiler.source and target)

- Maven for dependency management and build

- Download an LLM, such as one from [Ollama](https://ollama.com/library)

### 2. Starting the Server

- Launch the `Server` class using your IDE (e.g., IntelliJ).
- If you see a configuration error, go to **"Edit Configurations"** in the top-right dropdown.
    - Add `8000` as **Program Arguments**.
    - Set the **Working Directory** to:  
      `.../g16/assign2`

- Run the server. You should see the following message:

`
Secure server listening on port 8000
`

### 3. Starting the Client(s)

- While the server is running in the background, open **one or more terminals**, depending on how many chat sessions you want.

- Navigate to the `assign2` directory and run the following commands:

```bash
javac src/main/java/org/example/Client.java
java -cp src/main/java org.example.Client localhost 8000
```

- On success, you should see:

`
LOGIN <username> <password> or REGISTER <username> <password>
`

## Available Commands

Once connected to the server, you can use the following commands:

| Command | Description
|-----|-----
| `JOIN <room_name>` | Join a chat room with the specified name
| `LIST` | List all available chat rooms
| `SEND <message>` | Send a message to the current room
| `LEAVE` | Leave the current room
| `LLM LIST` | List available LLM (Language Learning Model) models installed locally
| `LLM JOIN <llm_name>` | Join a chat with a specific LLM
| `HELP` | Display a list of all available commands
| `QUIT` | Disconnect from the server

## Authentication Commands

- `LOGIN <username> <password>` - Log in with existing credentials
- `REGISTER <username> <password>` - Create a new account

## Group members:

1. Gonçalo Marques (up202206205@up.pt)
2. Miguel Guerrinha (up202205038@up.pt)
3. Rui Cruz (up202208011@up.pt)
