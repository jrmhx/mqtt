package com.jrmh;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Publisher class represents an MQTT publisher client that subscribes to request topics,
 * listens for instructions, and publishes messages to specified topics. The class also includes
 * a master publisher that coordinates the start and completion of publishing tasks among multiple publishers.
 */
public class Publisher {
    private static final String CLIENT_ID_PREFIX = "pub-";
    private static final String REQUEST_QOS = "request/qos";
    private static final String REQUEST_DELAY = "request/delay";
    private static final String REQUEST_INSTANCE_COUNT = "request/instancecount";
    private static final String READY_TOPIC = "instruction/ready";
    private static final String COMPLETE = "complete";
    private static final AtomicLong counter = new AtomicLong(0);

    private static CountDownLatch startLatch = new CountDownLatch(1);
    private static CountDownLatch doneLatch = new CountDownLatch(6);
    private static final int MASTER = 6;

    private final int TIME;
    private final String BROKER_URL;
    private final int instance;
    private static int qos = 0;
    private static int delay = 0;
    private static int activeInstances = 0;

    /**
     * Constructs a Publisher instance.
     *
     * @param time      the duration for which the publisher should publish messages
     * @param brokerURL the URL of the MQTT broker
     * @param instance  the instance number of this publisher
     */
    public Publisher(int time, String brokerURL, int instance) {
        this.TIME = time;
        this.BROKER_URL = brokerURL;
        this.instance = instance;
    }

    /**
     * Starts the publisher client and publishes messages to the broker.
     */
    public void start() {
        try {
            MqttClient client = new MqttClient(BROKER_URL, CLIENT_ID_PREFIX + instance, new MemoryPersistence());
            // Set up connection options with increased max inflight messages
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setMaxInflight(1000); // Set the max inflight messages to a higher value
            connOpts.setAutomaticReconnect(true); // Enable automatic reconnection
            connOpts.setCleanSession(true);

            client.connect(connOpts);

            // Subscribe to request topics if this is the master publisher
            if (instance == MASTER){
                client.subscribe(REQUEST_INSTANCE_COUNT, 2, this::handleRequest);
                client.subscribe(REQUEST_QOS, 2, this::handleRequest);
                client.subscribe(REQUEST_DELAY, 2, this::handleRequest);
                client.subscribe(READY_TOPIC, 2, this::handleReady);
            }


            while (true) {
                // Wait for start signal from all publisher threads
                startLatch.await();

                // reset startLatch for next experiment
                if (instance == MASTER){
                    startLatch = new CountDownLatch(1);
                }

                // Publish messages
                if (instance <= activeInstances) {
                    System.out.println("actIns: " + activeInstances +", instance: "+ instance + ", qos: " + qos + ", delay: " + delay);
                    long endTime = System.currentTimeMillis() + TIME * 1000; // convert s to ms

                    while (System.currentTimeMillis() < endTime) {
                        String topic = String.format("counter/%d/%d/%d", instance, qos, delay);
                        String message = Long.toString(counter.get());
                        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                        mqttMessage.setQos(qos);

                        try {
                            client.publish(topic, mqttMessage);
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                        counter.incrementAndGet();
                        Thread.sleep(delay);
                    }
                }

                // Signal that this thread is done
                doneLatch.countDown();

                // Wait for all threads to finish
                doneLatch.await();

                // Reset counter and latch for next experiment
                if (instance == MASTER) {
                    // Send the max counter value to the master publisher
                    long maxCounter = counter.get();
                    // Reset counter and latch for next experiment
                    counter.set(0);
                    // Reset doneLatch for next experiment
                    doneLatch = new CountDownLatch(6);
                    String message = Long.toString(maxCounter);
                    MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                    mqttMessage.setQos(2);
                    try {
                        client.publish(COMPLETE, mqttMessage);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles the ready signal to start all publisher threads.
     *
     * @param topic   the topic of the ready signal
     * @param message the message containing the ready signal
     */
    private void handleRequest(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        switch (topic) {
            case REQUEST_QOS:
                qos = Integer.parseInt(payload);
                break;
            case REQUEST_DELAY:
                delay = Integer.parseInt(payload);
                break;
            case REQUEST_INSTANCE_COUNT:
                activeInstances = Integer.parseInt(payload);
                break;
        }
    }

    /**
     * Handles the ready signal to start all publisher threads.
     *
     * @param topic   the topic of the ready signal
     * @param message the message containing the ready signal
     */
    private void handleReady(String topic, MqttMessage message) {
        // Signal all threads to start when receiving the ready message
        startLatch.countDown();
    }

    /**
     * The main method that starts the publisher pool.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        int time = 60; // default value 60 seconds
        String brokerUrl = "tcp://localhost:1883"; // default value
        // read command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-t":
                    if (i + 1 < args.length) {
                        time = Integer.parseInt(args[++i]);
                    } else {
                        System.err.println("Missing value for -t");
                        return;
                    }
                    break;
                case "-b":
                    if (i + 1 < args.length) {
                        brokerUrl = args[++i];
                    } else {
                        System.err.println("Missing value for -b");
                        return;
                    }
                    break;
            }
        }
        System.out.println("Publisher Pool started with " + time + " second(s) for each experiment, using broker: " + brokerUrl);
        for (int i = 1; i <= 6; i++) {
            int instance = i;
            int finalTime = time;
            String finalBrokerUrl = brokerUrl;
            new Thread(() -> new Publisher(finalTime, finalBrokerUrl, instance).start()).start();
        }
    }
}
