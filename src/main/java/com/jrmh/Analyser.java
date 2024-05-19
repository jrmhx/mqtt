package com.jrmh;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Analyser {
    private static final int TIME = 60;
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID = "analyser";
    private static final String REQUEST_QOS = "request/qos";
    private static final String REQUEST_DELAY = "request/delay";
    private static final String REQUEST_INSTANCE_COUNT = "request/instancecount";
    private static final String READY_TOPIC = "instruction/ready";
    private static final String COMPLETE = "complete";
    private static final String RESULT_PATH = "result.csv";

    private final int[] delays = {0, 1, 2, 4};
    private final int[] qoss = {0, 1, 2};
    private final int[] instanceCounts = {1, 2, 3, 4, 5};
    private long maxCounter = 0;
    private List<Long> medianMsgGaps = new ArrayList<>();
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

            client.subscribe(COMPLETE, 2, (topic, message) -> {
                this.maxCounter = Long.parseLong(new String(message.getPayload()));
                this.latch.countDown();
            });

            try (PrintWriter writer = new PrintWriter(new FileWriter(RESULT_PATH, true))) {
                writer.println("P2B_QoS,A2B_QoS,Delay_(ms),Instance_Count,Total_Messages_Received,Expected_Messages_Received,Message_Loss_Rate_(%),Out_of_Order_Message_Rate_(%),Median_Inter_Message_Gap_(ms)");
                for (int subQos : qoss) {
                    for (int delay : delays) {
                        for (int pubQos : qoss) {
                            for (int instanceCount : instanceCounts) {
                                this.maxCounter = 0;
                                this.latch = new CountDownLatch(1);
                                this.medianMsgGaps = new ArrayList<>();
                                publishInstructions(client, pubQos, delay, instanceCount);
                                sendReadySignal(client);
                                List<Long> messages = listenAndCollectData(client, subQos);
                                this.latch.await();
                                analyzeData(messages, pubQos, delay, instanceCount, subQos, writer);
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

    private List<Long> listenAndCollectData(MqttClient client, int subQos) throws MqttException, InterruptedException {
        List<Long> messages = new ArrayList<>();
        CountDownLatch timeLatch = new CountDownLatch(1);
        String topicPath = "counter/#";
        client.subscribe(topicPath, subQos, (topic, message) -> {
            String payload = new String(message.getPayload());
            long currentMsgTimestamp = System.currentTimeMillis();
            long currentMsg = Long.parseLong(payload);
            messages.add(currentMsg);
            if (this.prevMsg != -1 && currentMsg - prevMsg == 1){
                long gap = currentMsgTimestamp - this.prevMsgTimestamp;
                medianMsgGaps.add(gap);
            }
            this.prevMsg = currentMsg;
            this.prevMsgTimestamp = currentMsgTimestamp;
        });

        timeLatch.await(TIME, TimeUnit.SECONDS);
        client.unsubscribe(topicPath);
        return messages;
    }

    private void analyzeData(List<Long> messages, int pubQos, int delay, int instanceCount, int subQos, PrintWriter writer) {
        int totalMessages = messages.size();

        int outOfOrderCount = 0;
        long previous = -1;

        for (long msg : messages) {
            // maxCounter = Math.max(maxCounter, msg);
            if (previous != -1 && msg < previous) {
                outOfOrderCount++;
            }
            previous = msg;
        }

        long totalExpectedMessages = maxCounter + 1;
        double messageLossRate = ((double) (totalExpectedMessages - totalMessages) / totalExpectedMessages) * 100;
        double outOfOrderRate = ((double) outOfOrderCount / totalMessages) * 100;
        double medianMsgGap = getMedian(this.medianMsgGaps);
        System.out.println("instanceCount: " + instanceCount + ", pubQos(P2B): " + pubQos + ", delay(ms): " + delay + ", subQos(B2A): " + subQos);
        // Write results to CSV
        writer.printf("%d,%d,%d,%d,%d,%d,%.2f,%.2f,%.1f%n",
                pubQos, subQos, delay, instanceCount, totalMessages, totalExpectedMessages, messageLossRate, outOfOrderRate, medianMsgGap);

    }

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

    public static void main(String[] args) {
        new Analyser().start();
    }
}
