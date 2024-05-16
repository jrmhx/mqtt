package com.jrmh;

import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Analyser {
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID = "analyser";
    private static final String REQUEST_QOS = "request/qos";
    private static final String REQUEST_DELAY = "request/delay";
    private static final String REQUEST_INSTANCE_COUNT = "request/instancecount";

    private int[] delays = {0, 1, 2, 4};
    private int[] qoss = {0, 1, 2};
    private int[] instanceCounts = {1, 2, 3, 4, 5};
    private static final int MESSAGES_PER_SECOND_AT_ZERO_DELAY = 10000; // Estimated number of messages per second at zero delay

    public void start() {
        try {
            MqttClient client = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());
            client.connect();

            for (int subQos : qoss) {
                for (int delay : delays) {
                    for (int pubQos : qoss) {
                        for (int instanceCount : instanceCounts) {
                            publishInstructions(client, pubQos, delay, instanceCount);
                            List<String> messages = listenAndCollectData(client, instanceCount, pubQos, delay, subQos);
                            analyzeData(messages, pubQos, delay, instanceCount, subQos);
                        }
                    }
                }
            }

            client.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void publishInstructions(MqttClient client, int qos, int delay, int instanceCount) throws MqttException {
        client.publish(REQUEST_QOS, new MqttMessage(Integer.toString(qos).getBytes()));
        client.publish(REQUEST_DELAY, new MqttMessage(Integer.toString(delay).getBytes()));
        client.publish(REQUEST_INSTANCE_COUNT, new MqttMessage(Integer.toString(instanceCount).getBytes()));
    }

    private List<String> listenAndCollectData(MqttClient client, int instanceCount, int pubQos, int delay, int subQos) throws MqttException, InterruptedException {
        List<String> messages = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        String topicPath = "counter/#";
        System.out.println("Listening topic: " + topicPath);

        client.subscribe(topicPath, subQos, (topic, message) -> {
            String payload = new String(message.getPayload());
            messages.add(payload);
        });

        latch.await(60, TimeUnit.SECONDS);
        client.unsubscribe(topicPath);
        return messages;
    }

    private void analyzeData(List<String> messages, int pubQos, int delay, int instanceCount, int subQos) {
        int totalMessages = messages.size();
        int expectedMessages;
        if (delay > 0) {
            expectedMessages = (60 * 1000 / delay) * instanceCount;
        } else {
            expectedMessages = MESSAGES_PER_SECOND_AT_ZERO_DELAY * 60 * instanceCount;
        }

        int outOfOrderCount = 0;
        long totalGap = 0;
        int previous = -1;

        for (String msg : messages) {
            int current = Integer.parseInt(msg);
            if (previous != -1 && current < previous) {
                outOfOrderCount++;
            }
            if (previous != -1) {
                totalGap += (current - previous);
            }
            previous = current;
        }

        double messageLossRate = ((double) (expectedMessages - totalMessages) / expectedMessages) * 100;
        double outOfOrderRate = ((double) outOfOrderCount / totalMessages) * 100;
        double averageGap = totalMessages > 1 ? (double) totalGap / (totalMessages - 1) : 0;

        System.out.println("=== Test Configuration ===");
        System.out.println("Pub QoS: " + pubQos);
        System.out.println("Sub QoS: " + subQos);
        System.out.println("Delay: " + delay + "ms");
        System.out.println("Instance Count: " + instanceCount);
        System.out.println("--- Results ---");
        System.out.println("Total Messages Received: " + totalMessages);
        System.out.println("Expected Messages: " + expectedMessages);
        System.out.println("Message Loss Rate: " + String.format("%.2f", messageLossRate) + "%");
        System.out.println("Out-of-Order Message Rate: " + String.format("%.2f", outOfOrderRate) + "%");
        System.out.println("Average Inter-Message Gap: " + String.format("%.2f", averageGap) + "ms");
        System.out.println("--------------------------");
    }

    public static void main(String[] args) {
        new Analyser().start();
    }
}