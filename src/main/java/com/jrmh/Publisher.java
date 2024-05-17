package com.jrmh;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class Publisher {
    private static final int TIME = 60;
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID_PREFIX = "pub-";
    private static final String REQUEST_QOS = "request/qos";
    private static final String REQUEST_DELAY = "request/delay";
    private static final String REQUEST_INSTANCE_COUNT = "request/instancecount";
    private static final String READY_TOPIC = "instruction/ready";
    private static final AtomicLong counter = new AtomicLong(0);

    private static CountDownLatch startLatch = new CountDownLatch(1);
    private static CountDownLatch doneLatch = new CountDownLatch(5);

    private final int instance;
    private int qos = 0;
    private int delay = 0;
    private int activeInstances = 0;

    public Publisher(int instance) {
        this.instance = instance;
    }

    public void start() {
        try {
            MqttClient client = new MqttClient(BROKER_URL, CLIENT_ID_PREFIX + instance, new MemoryPersistence());

            // Set up connection options with increased max inflight messages
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setMaxInflight(1000); // Set the max inflight messages to a higher value

            client.connect(connOpts);

            client.subscribe(REQUEST_INSTANCE_COUNT, 2, this::handleRequest);
            client.subscribe(REQUEST_QOS, 2, this::handleRequest);
            client.subscribe(REQUEST_DELAY, 2, this::handleRequest);
            client.subscribe(READY_TOPIC, 2, this::handleReady);

            while (true) {
                // Wait for start signal from all publisher threads
                startLatch.await();

                counter.set(0);

                if (instance <= activeInstances) {
                    System.out.println("activeInstances: " + activeInstances + " qos: " + qos + " delay: " + delay);
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

                // Reset the latches for the next round
                if (doneLatch.getCount() == 0) {
                    startLatch = new CountDownLatch(1);
                    doneLatch = new CountDownLatch(5);
                }

                // Sleep briefly before checking for new instructions
                Thread.sleep(TIME);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

    private void handleReady(String topic, MqttMessage message) {
        // Signal all threads to start when receiving the ready message
        startLatch.countDown();
    }

    public static void main(String[] args) {
        for (int i = 1; i <= 5; i++) {
            int instance = i;
            new Thread(() -> new Publisher(instance).start()).start();
        }
    }
}
