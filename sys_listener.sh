mosquitto_sub -v -h localhost -p 1883  \
                                      -t '$SYS/broker/load/bytes/#' \
                                      -t '$SYS/broker/load/connections/#' \
                                      -t '$SYS/broker/store/messages/count/#' \
                                      -t '$SYS/broker/clients/#' \
                                      -t '$SYS/broker/messages/#' \
                                      -t '$SYS/broker/heap/#' \
                                      -t '$SYS/broker/publish/bytes' \
                                      -t '$SYS/broker/bytes/received' \
                                      -t '$SYS/broker/bytes/sent' \
                                      -t 'request/#' \
                                      -t 'instruction/#' \
                                      -t 'complete' \
                                      > sys_measurements.log
