package com.jrmh;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;


public class Publisher {
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String CLIENT_ID_PREFIX = "pub-";
    private static final String REQUEST_QOS = "request/qos";
    private static final String REQUEST_DELAY = "request/delay";
    private static final String REQUEST_INSTANCE_COUNT = "request/instancecount";

    private int instance;
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

            client.subscribe(REQUEST_QOS, this::handleRequest);
            client.subscribe(REQUEST_DELAY, this::handleRequest);
            client.subscribe(REQUEST_INSTANCE_COUNT, this::handleRequest);

            while (true) {
                if (instance <= activeInstances) {
                    System.out.println("activeInstances: " + activeInstances + " qos: " + qos + " delay: " + delay);
                    long endTime = System.currentTimeMillis() + 60000; // 60 seconds
                    long counter = 0;

                    while (System.currentTimeMillis() < endTime) {
                        String topic = String.format("counter/%d/%d/%d", instance, qos, delay);
                        String message = Long.toString(counter);
                        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                        mqttMessage.setQos(qos);

                        try {
                            client.publish(topic, mqttMessage);
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                        counter++;
                        Thread.sleep(delay);
                    }
                }

                // Sleep briefly before checking for new instructions
                Thread.sleep(1000);
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

    public static void main(String[] args) {
        for (int i = 1; i <= 5; i++) {
            int instance = i;
            new Thread(() -> new Publisher(instance).start()).start();
        }
    }
}

