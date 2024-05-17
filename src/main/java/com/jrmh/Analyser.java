package com.jrmh;

import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Analyser {
    private static final int TIME = 1;
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID = "analyser";
    private static final String REQUEST_QOS = "request/qos";
    private static final String REQUEST_DELAY = "request/delay";
    private static final String REQUEST_INSTANCE_COUNT = "request/instancecount";
    private static final String READY_TOPIC = "instruction/ready";
    private static final String COMPLETE = "complete";

    private final int[] delays = {0, 1, 2, 4};
    private final int[] qoss = {0, 1, 2};
    private final int[] instanceCounts = {1, 2, 3, 4, 5};
    private long maxCounter = 0;
    private final List<Long> medianMsgGaps = new ArrayList<>();
    private long prevMsg = -1;
    private long prevMsgTimestamp = -1;
    private CountDownLatch latch = new CountDownLatch(1);

    public void start() {
        try {
            MqttClient client = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setMaxInflight(1000); // Set the max inflight messages to a higher value
            connOpts.setAutomaticReconnect(true); // Enable automatic reconnection
            connOpts.setCleanSession(true);

            client.connect(connOpts);

            client.subscribe(COMPLETE, 2, (topic, message) ->{
                latch.countDown();
            });

            for (int subQos : qoss) {
                for (int delay : delays) {
                    for (int pubQos : qoss) {
                        for (int instanceCount : instanceCounts) {
                            maxCounter = 0;
                            latch = new CountDownLatch(1);
                            publishInstructions(client, pubQos, delay, instanceCount);
                            sendReadySignal(client);
                            List<Long> messages = listenAndCollectData(client, instanceCount, pubQos, delay, subQos);
                            analyzeData(messages, pubQos, delay, instanceCount, subQos);
                            latch.await();
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

    private void sendReadySignal(MqttClient client) throws MqttException {
        MqttMessage readyMsg = new MqttMessage("ready".getBytes());
        readyMsg.setQos(2);
        client.publish(READY_TOPIC, readyMsg);
    }

    private List<Long> listenAndCollectData(MqttClient client, int instanceCount, int pubQos, int delay, int subQos) throws MqttException, InterruptedException {
        List<Long> messages = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        String topicPath = "counter/#";
        System.out.println("instanceCount: " + instanceCount + " pubQos(P2B): " + pubQos + " delay(ms): " + delay + " subQos(B2A): " + subQos);

        client.subscribe(topicPath, subQos, (topic, message) -> {
            String payload = new String(message.getPayload());
            long currentMsgTimestamp = System.currentTimeMillis();
            long currentMsg = Long.parseLong(payload);
            messages.add(currentMsg);
            if (this.prevMsg != -1 && currentMsg - prevMsg == 1){
                medianMsgGaps.add(currentMsgTimestamp - this.prevMsgTimestamp);
            }
            this.prevMsg = currentMsg;
            this.prevMsgTimestamp = currentMsgTimestamp;
        });

        latch.await(TIME, TimeUnit.SECONDS);
        client.unsubscribe(topicPath);
        return messages;
    }

    private void analyzeData(List<Long> messages, int pubQos, int delay, int instanceCount, int subQos) {
        int totalMessages = messages.size();

        int outOfOrderCount = 0;
        long previous = -1;

        for (long msg : messages) {
            maxCounter = Math.max(maxCounter, msg);
            if (previous != -1 && msg < previous) {
                outOfOrderCount++;
            }
            previous = msg;
        }

        long totalExpectedMessages = maxCounter + 1;
        double messageLossRate = ((double) (totalExpectedMessages - totalMessages) / totalExpectedMessages) * 100;
        double outOfOrderRate = ((double) outOfOrderCount / totalMessages) * 100;
        Collections.sort(this.medianMsgGaps);
        long medianMsgGap = medianMsgGaps.get(medianMsgGaps.size() / 2);

        System.out.println("=== Test Configuration ===");
        System.out.println("Pub QoS: " + pubQos);
        System.out.println("Sub QoS: " + subQos);
        System.out.println("Delay: " + delay + "ms");
        System.out.println("Instance Count: " + instanceCount);
        System.out.println("--- Results ---");
        System.out.println("Total Messages Received: " + totalMessages);
        System.out.println("Expected Messages: " + totalExpectedMessages);
        System.out.println("Message Loss Rate: " + String.format("%.2f", messageLossRate) + "%");
        System.out.println("Out-of-Order Message Rate: " + String.format("%.2f", outOfOrderRate) + "%");
        System.out.println("Median Inter-Message Gap: " + medianMsgGap + "ms");
        System.out.println("--------------------------");
    }

    public static void main(String[] args) {
        new Analyser().start();
    }
}
