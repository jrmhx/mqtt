# MQTT Analysis Project

*Author: Jeremiah Xing u7439274*

## Requirements

To run the applications you will need:

- A Terminal
- Java JDK 22 or higher
- Gradle 8.5 or higher
- A MQTT broker that can run on your local machine (like Eclipse Mosquitto)

**You don't really need to install everything above manually as some heavy lifting environment config can be done by Gradle Wrapper and Plugin. Please read the NOTE below:**

**NOTE:**

- The Gradle Wrapper are already provided in the project root folder: (`./gradlew` for Linux/macOS and `./gradlew.bat` for Windows). So you don't need to install Gradle in your system if you run the Wrapper according to your system for Gradle tasks.
- The commands in this README assuming you using a Linux/macOS machine when it is about using Gradle Wrapper. Please replace `./gradlew` commands with `gradlew.bat` command accordingly if you are on a Windows machine.
- The Java toolchain auto download are also set in the Gradle config. Run `./gradlew build` should automatically download the required JDK if it is missing. However, if it doesn't work, please install the JDK manually.
- Ideally the only thing that you need to manually install is the MQTT broker, I'll use Mosquitto as an example.

General guides of how to install Gradle, JDK and Mosquitto are also provided below:

- How to install Gradle: [Gradle Installation Guide](https://gradle.org/install/)
- How to install JDK: [OpenJDK JDK 22](https://jdk.java.net/22/)
- How to install Mosquitto [Eclipse Mosquitto](https://mosquitto.org/download/)

After installing the MQTT broker, please run it on your local machine before starting local tests.
You can use `mosquitto` or any other that you prefer, I use `mosquitto` for an example:

```bash
mosquitto
```

The mosquitto broker will use port `1883` by default, if you encounter unwanted process taking port `1883`, you can kill it by:

Linux/macOS:

```bash
sudo kill -9 $(sudo lsof -t -i :1883)
```

Windows:

```bash
netstat -aon | findstr :1883
```

```bash
taskkill /PID <The PID you found above> /F
```

Or feel free to config your MQTT broker for another port. However, the program's default broker url is `tcp://localhost:1883`. So if you do so please pass the broker url with correct port `tcp://localhost:<port>` as a CLI argument for both `Publisher` and `Analyser` accordingly. Please find how to use the customized CLI arguments in the `Usage` section below.

## Build

Under the project root directory, run the following commands to build the application via Gradle:

```bash
./gradlew build
```

If you don't have the required JDK installed in your system, it might take a while for gradle to fetch and install the JDK (several hundreds MB) for you. Please be patient 😸.

## Run

To conduct a successful experiment you need to:

1. Under the project root fold;
2. Open one terminal located in root folder and run:

    ```bash
    ./gradlew runPublisher
    ```

3. Open another terminal located in root folder and run:

    ```bash
    ./gradlew runAnalyser
    ```

**Please note** that the above commands run the experiment using the parameter setting as Assignment 3 required (3x3x4x5=180 tests, 60s per test).
This could take a very long period of time (a little more than 3h).
If you only want to verify the correctness of the program (like to see if there is any dead lock or race condition), feel free to use some custmized parameters. How to use custmized parameters are mentioned in `Usage` section below.

## Usage

The applications accept the following cli arguments:

### Publisher

- `Ptime`: time for each experiment in seconds, the time must be an integer no less than 1.
- `Pbroker`: the broker URL for the program to connect.

### Analyser

- `Ptime`: time for each experiment in seconds, the time must be an integer no less than 1.
- `Pbroker`: the broker URL for the program to connect.
- `Pdelays`: a comma-separated list of delays in milliseconds (e.g., `0,1,2,4`), each delay must be a non-negative integer.
- `Pqoss`: a comma-separated list of QoS levels (e.g., `0,1,2`), each QoS must be an integer in range [0, 2].
- `PinstanceCounts`: a comma-separated list of instance counts (e.g., `1,2,3,4,5`), each instance counts must be an integer in range [1, 5].

**NOTE:**

- If you leave any CLI arguments blank when running either the `Analyser` or `Publisher`, they will use the default arguments:
  - `Ptime` = `60` (seconds)
  - `Pbroker` = `tcp://localhost:1883`
  - `Pdelays` = `0,1,2,4`
  - `Pqoss` = `0,1,2`
  - `PinstanceCounts` = `1,2,3,4,5`
- To ensure the analysis experiment runs successfully, you must use the same set of `Ptime` and `Pbroker` for both `Analyser` and `Publisher`.
- The default `Ptime` is `60` seconds, which is relatively long. If you want to verify the correctness of the programs, feel free to set it small (like `1` second).

### Examples

Publisher:

To run the Publisher with default settings:

```bash
./gradlew runPublisher
```

To run the Publisher with custom settings:

```bash
./gradlew runPublisher -Ptime=60 -Pbroker="tcp://localhost:1883"
```

Analyser:

To run the Analyser with default settings:

```bash
./gradlew runAnalyser
```

```bash
./gradlew runAnalyser -Ptime=60 -Pbroker="tcp://localhost:1883" -Pdelays="0,1,2,4" -Pqoss="0,1,2" -PinstanceCounts="1,2,3,4,5"
```

Or you can leave only some of the arguments default, for example use the default broker URL:

```bash
./gradlew runAnalyser -Ptime=60 -Pdelays="0,1,2,4" -Pqoss="0,1,2" -PinstanceCounts="1,2,3,4,5"
```

## Implementation

I design and implement a Java multithreaded Master-Worker Pool for the `Publisher` and a handshake process to ensure proper synchronization between the `Publisher` and the `Analyser`.

In the Master-Worker Pool in `Publisher.java`:

- There are 5 worker publishers (instance id `1` - `5`), they mainly publisher the message as the instruction that's been published by `Analyser` and announced by the master instance with in the pool;
- There's 1 master (instance id `6`), it receives the instructions published by `Analyser`, updates global states of the pool, monitors the workers status, and sends the batch of tasks `COMPLETE` signals when ready.

The object of this handshake process is to ensure:

1. The `Publishers` sending msg to the `counter/#` and `Anaylser` receiving are asynchronized so that they can clock the `60` sec period separately, but necessary waiting should provide to ensure correctness.
2. Only when `Publishers` know that `Analyser` is ready to receive messages, will `Publishers` send messages. This can be seen as when `Analyser` publish new instructions then it is ready.
3. Only when `Analyser` know that `Publishers` have finished the workload and are ready to start a new batch of msg sending, will `Analyser` start publish new instructions. This can be done by `Publisher` send `Analyser` a `COMPLETE` signal.

The detail of the workflow is like:
1. `Publisher`: The publishers run and await for the instructions from analyzer;
2. `Analyser`: The analyzer send one set of instructions {`instanceCount`, `QoS` …}
3. `Publisher`: On receiving the instructions, the publishers starting publish msg (with a timer of 60 sec), there are `CountDownLatch` to synchronize the worker publishers threads that the early finished threads will wait for the working ones;
4. `Publisher`: When the all publisher are in the state of finish state. They will send a `COMPLETE` signal (publish on a special topic) to analyzer to inform that the new set of instructions can be sent;
5. `Analyser`: After sending a set of instructions the analyzer start reading (with a timer of 60 sec). So that for the msg publishing and receiving on topic `counter/#` we can say that they are asynchronized.  
6. `Analyser`: When finishing reading the messages, the analyser will wait util it received a `COMPLETE` signal from the publishers. Then send new set of instructions to make sure that all publisher are ready to process a new set of instructions.

The code has been tested on my local machine with some edge cases, and it seems to work well and robust.

## Output

- Both `Publisher` and `Analyser` program will print some logs in the terminal to monitor the states and processes.
- The `Analyser` will generate a `result.csv` file for analysis.
- The `result.csv` file header is:
  - `P2B_QoS`: The QoS level used by the publishers when sending messages to the broker.
  - `A2B_QoS`: The QoS level used by the Analyser when subscribing to messages from the broker.
  - `Delay_(ms)`: The delay in milliseconds between messages sent by the publishers.
  - `Instance_Count`: The number of publisher instances running simultaneously.
  - `Total_Messages_Received`: The total number of messages received by the Analyser.
  - `Expected_Messages_Received`: The expected number of messages based on the publisher settings.
  - `Message_Loss_Rate_(%)`: The percentage of messages lost during the test.
  - `Out_of_Order_Message_Rate_(%)`: The percentage of messages received out of order.
  - `Median_Inter_Message_Gap_(ms)`: The median time gap in milliseconds between consecutive messages.

## Documentation

The source code is documented using Javadoc comments. You can generate the Javadoc documentation by running the following command:

```bash
./gradlew javadoc
```

The generated documentation can be found in the `./build/docs/javadoc` directory. You can open the `index.html` file in a web browser to view the documentation.

