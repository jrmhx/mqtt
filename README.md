# COMP3310 A3 MQTT Project

*Jeremiah Xing (u7439274)*

## Requirements

- Java JDK 22 or higher
- Gradle 8.5 or higher
- a MQTT broker that can run on your local machine (like Eclipse Mosquitto)

**NOTE:**

- The Gradle Wrapper are already provided in the project root folder: (`./gradlew` for Linux and macOS and `./gradlew.bat` for Windows). So that you don't need to install Gradle in your system if you run the Wrapper according to your system for Gradle tasks.
- The Java toolchain auto download are also set in the Gradle config. Run `./gradlew build` should automatically download the required JDK if it is missing. However, if it doesn't work, please install the JDK manually.
- Ideally the only thing that you need to manually install is the MQTT broker, I'll use Mosquitto as example.

General guides of how to install Gradle, JDK and Mosquitto are also provided below:

- How to install Gradle: [Gradle Installation Guide](https://gradle.org/install/)
- How to install JDK: [OpenJDK JDK 22 General-Availability Release](https://jdk.java.net/22/)
- How to install Mosquitto [Eclipse Mosquitto](https://mosquitto.org/download/)


## Build

Under the project root directory, run the following commands to build the application via Gradle:

```bash
./gradlew build
```

## Run

After building the application, you can simply run the application using the following command:

```bash

```


```bash

```


```bash

```


## Usage

The application accepts the following cli arguments:

```bash

```


Example of running the application with arguments:

```bash

```



## Implementation


## Edge Cases Handling


## Output


## Documentation

The source code is documented using Javadoc comments. You can generate the Javadoc documentation by running the following command:

```bash
gradle javadoc
```

The generated documentation can be found in the `build/docs/javadoc` directory. You can open the `index.html` file in a web browser to view the documentation.


```bash

```

```bash

```

