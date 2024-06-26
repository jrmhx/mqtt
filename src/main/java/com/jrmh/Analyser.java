package com.jrmh;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * The Analyzer class sends instructions, collects and analyzes the data.
 */
public class Analyser {
    private static final String CLIENT_ID = "analyser";
    private static final String REQUEST_QOS = "request/qos";
    private static final String REQUEST_DELAY = "request/delay";
    private static final String REQUEST_INSTANCE_COUNT = "request/instancecount";
    private static final String READY_TOPIC = "instruction/ready";
    private static final String COMPLETE = "complete";
    private static final String RESULT_PATH = "result.csv";

    private final String BROKER_URL;
    private final int[] delays;
    private final int[] pQoss;
    private final int[] sQoss;
    private final int[] instanceCounts;

    private long maxCounter = 0;
    private List<Long> medianMsgGaps = new ArrayList<>();
    private long prevMsg = -1;
    private long prevMsgTimestamp = -1;
    private CountDownLatch latch = new CountDownLatch(1);
    private double listeningTimeSec = 0;
    private int totalMessages = 0;
    private int outOfOrderCount = 0;

    /**
     * Constructs an Analyzer instance.
     * @param brokerURL     the URL of the MQTT broker to connect to
     * @param delays        the array of delays to test in milliseconds
     * @param pQoss          the array of pub QoS levels to test
     * @param sQoss          the array of sub QoS levels to test
     * @param instanceCounts the array of instance counts to test
     */
    public Analyser(String brokerURL, int[] delays, int[] pQoss, int[] sQoss, int[] instanceCounts) {
        this.BROKER_URL = brokerURL;
        this.delays = delays;
        this.pQoss = pQoss;
        this.sQoss = sQoss;
        this.instanceCounts = instanceCounts;
    }

    /**
     * Reset the values for each experiment
     */
    private void reset(){
        this.maxCounter = 0;
        this.latch = new CountDownLatch(1);
        this.medianMsgGaps = new ArrayList<>();
        this.prevMsg = -1;
        this.prevMsgTimestamp = -1;
        this.listeningTimeSec = 0;
        this.totalMessages = 0;
        this.outOfOrderCount = 0;
    }
    /**
     * Starts the Analyzer, sends instructions to publishers, collects and analyzes the data.
     */
    public void start() {
        try {
            MqttClient client = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setMaxInflight(1000); // Set the max inflight messages to a higher value
            connOpts.setAutomaticReconnect(true); // Enable automatic reconnection
            connOpts.setCleanSession(true);

            client.connect(connOpts);

            client.subscribe(COMPLETE, 2, (topic, message) -> {
                // Get the total number of messages expected
                this.maxCounter = Long.parseLong(new String(message.getPayload()));
                // Signal the main thread to continue
                this.latch.countDown();
            });

            try (PrintWriter writer = new PrintWriter(new FileWriter(RESULT_PATH, true))) {
                writer.println("P2B_QoS,A2B_QoS,Delay_(ms),Instance_Count,Total_Messages_Received,Expected_Messages_Received,Message_Loss_Rate_(%),Out_of_Order_Message_Rate_(%),Median_Inter_Message_Gap_(ms),msg_rate_(msg/s)");
                for (int subQos : sQoss) {
                    for (int delay : delays) {
                        for (int pubQos : pQoss) {
                            for (int instanceCount : instanceCounts) {
                                // Reset the values for each experiment
                                reset();
                                // Send instructions to publishers
                                publishInstructions(client, pubQos, delay, instanceCount);
                                // finish instruction publishing, send ready signal to publishers to start publishing
                                sendReadySignal(client);
                                // Listen and collect data
                                listenAndCollectData(client, subQos);
                                // Analyze the data
                                analyzeData(pubQos, delay, instanceCount, subQos, writer);
                            }
                        }
                    }
                }
                client.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Publishes the instructions to the publishers.
     *
     * @param client        the MQTT client
     * @param qos           the QoS level
     * @param delay         the delay between messages
     * @param instanceCount the number of publisher instances
     * @throws MqttException if an error occurs while publishing
     */
    private void publishInstructions(MqttClient client, int qos, int delay, int instanceCount) throws MqttException {
        MqttMessage insCntMsg = new MqttMessage(Integer.toString(instanceCount).getBytes());
        insCntMsg.setQos(2);
        client.publish(REQUEST_INSTANCE_COUNT, insCntMsg);
        MqttMessage qosMsg = new MqttMessage(Integer.toString(qos).getBytes());
        qosMsg.setQos(2);
        client.publish(REQUEST_QOS, qosMsg);
        MqttMessage delayMsg = new MqttMessage(Integer.toString(delay).getBytes());
        delayMsg.setQos(2);
        client.publish(REQUEST_DELAY, delayMsg);
    }

    /**
     * Sends the ready signal to the publishers.
     *
     * @param client the MQTT client
     * @throws MqttException if an error occurs while publishing
     */
    private void sendReadySignal(MqttClient client) throws MqttException {
        MqttMessage readyMsg = new MqttMessage("ready".getBytes());
        readyMsg.setQos(2);
        client.publish(READY_TOPIC, readyMsg);
    }

    /**
     * Listens for messages and collects the data.
     *
     * @param client the MQTT client
     * @param subQos the QoS level to subscribe to
     * @throws MqttException if an error occurs while subscribing
     * @throws InterruptedException if the thread is interrupted
     */
    private void listenAndCollectData(MqttClient client, int subQos) throws MqttException, InterruptedException {
        long startTime = System.currentTimeMillis();

        String topicPath = "counter/#";
        client.subscribe(topicPath, subQos, (topic, message) -> {
            String payload = new String(message.getPayload());
            long currentMsgTimestamp = System.currentTimeMillis();
            long currentMsg = Long.parseLong(payload);
            this.totalMessages++;
            // calculate the out-of-order rate
            if (this.prevMsg != -1 && currentMsg - prevMsg != 1){
                this.outOfOrderCount++;
            }
            // calculate the median inter-message gap
            if (this.prevMsg != -1 && currentMsg - prevMsg == 1){
                long gap = currentMsgTimestamp - this.prevMsgTimestamp;
                medianMsgGaps.add(gap);
            }
            this.prevMsg = currentMsg;
            this.prevMsgTimestamp = currentMsgTimestamp;
        });

        // wait for all publishers to finish publishing, get the max counter as total expected messages number
        this.latch.await();
        long endTime = System.currentTimeMillis();
        this.listeningTimeSec = (endTime - startTime) / 1000.0;
        client.unsubscribe(topicPath);
    }

    /**
     * Analyzes the data and writes the results to a CSV file.
     *
     * @param pubQos        the QoS level of the publisher
     * @param delay         the delay between messages
     * @param instanceCount the number of publisher instances
     * @param subQos        the QoS level of the subscriber
     * @param writer        the PrintWriter to write the results to
     */
    private void analyzeData(int pubQos, int delay, int instanceCount, int subQos, PrintWriter writer) {
        long totalExpectedMessages = maxCounter + 1;
        double messageLossRate = ((double) (totalExpectedMessages - totalMessages) / totalExpectedMessages) * 100;
        double outOfOrderRate = ((double) outOfOrderCount / totalMessages) * 100;
        double medianMsgGap = getMedian(this.medianMsgGaps);
        double msgRate = totalMessages / this.listeningTimeSec;
        System.out.println("instanceCount: " + instanceCount + ", pubQos(P2B): " + pubQos + ", delay(ms): " + delay + ", subQos(B2A): " + subQos);
        // Write results to CSV
        writer.printf("%d,%d,%d,%d,%d,%d,%.2f,%.2f,%.1f,%.3f%n",
                pubQos, subQos, delay, instanceCount, totalMessages, totalExpectedMessages, messageLossRate, outOfOrderRate, medianMsgGap,msgRate);

    }

    /**
     * Calculates the median of a list of long values.
     *
     * @param list the list of long values
     * @return the median of the list
     */
    private double getMedian(List<Long> list) {
        int size = list.size();
        if (size == 0) {
            return 0;
        }
        Collections.sort(list);
        if (size % 2 == 1) { // odd
            return list.get(size / 2);
        } else { // even
            return (list.get((size / 2) - 1) + list.get(size / 2)) / 2.0;
        }
    }

    /**
     * The main method to start the Analyser.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String brokerUrl = "tcp://localhost:1883"; // default value
        int[] delays = {0, 1, 2, 4}; // default values
        int[] pQoss = {0, 1, 2}; // default values
        int[] sQoss = {0, 1, 2}; // default values
        int[] instanceCounts = {1, 2, 3, 4, 5}; // default values
        // read command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-b":
                    if (i + 1 < args.length) {
                        brokerUrl = args[++i];
                    } else {
                        System.err.println("Missing value for -b");
                        return;
                    }
                    break;
                case "-d":
                    if (i + 1 < args.length) {
                        delays = Arrays.stream(args[++i].split(",")).mapToInt(Integer::parseInt).toArray();
                    } else {
                        System.err.println("Missing value for -d");
                        return;
                    }
                    break;
                case "-p":
                    if (i + 1 < args.length) {
                        pQoss = Arrays.stream(args[++i].split(",")).mapToInt(Integer::parseInt).toArray();
                    } else {
                        System.err.println("Missing value for -p");
                        return;
                    }
                    break;
                case "-s":
                    if (i + 1 < args.length) {
                        sQoss = Arrays.stream(args[++i].split(",")).mapToInt(Integer::parseInt).toArray();
                    } else {
                        System.err.println("Missing value for -s");
                        return;
                    }
                    break;
                case "-i":
                    if (i + 1 < args.length) {
                        instanceCounts = Arrays.stream(args[++i].split(",")).mapToInt(Integer::parseInt).toArray();
                    } else {
                        System.err.println("Missing value for -i");
                        return;
                    }
                    break;
            }
        }
        System.out.println("Analyser started, using broker: " + brokerUrl);
        new Analyser(brokerUrl, delays, pQoss, sQoss, instanceCounts).start();
        System.out.println("Analyser finished");
    }
}
