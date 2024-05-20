mosquitto_sub -v -h localhost -p 1883  \
                                      -t '$SYS/#' \
                                      -t 'request/#' \
                                      -t 'instruction/#' \
                                      -t 'complete' \
                                      > sys_measurements.log
