#!/bin/python
#
# Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
# and other contributors as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

from prometheus_client import start_http_server, Gauge, Counter
from subprocess import call, Popen, PIPE, STDOUT
import random
import datetime
import time
import sys
import json
import os
import signal

# cd to the scripts location
os.chdir(os.path.dirname(sys.argv[0]))

# globals
ELASTIC_URL = 'http://localhost:9200'
KAFKA_ENDPOINT = 'localhost:9092'

PROMETHEUS_BIN = './prometheus/prometheus'
PROMETHEUS_CONFIG = './prometheus/prometheus.yml'
PROMETHEUS_DATA_DIR = './prometheus/data'
ELASTICSEARCH_BIN = './elasticsearch/bin/elasticsearch'
KIBANA_BIN = './kibana/bin/kibana'
ZOOKEEPER_BIN = './kafka/bin/zookeeper-server-start.sh'
ZOOKEEPER_CONFIG = './kafka/config/zookeeper.properties'
KAFKA_SERVER_BIN = './kafka/bin/kafka-server-start.sh'
KAFKA_SERVER_DATA_DIR = './kafka/logdata'
KAFKA_SERVER_CONFIG = './kafka/config/server.properties'
KAFKA_PRODUCER_BIN = './kafka/bin/kafka-console-producer.sh'
SMTP_JAR = './FakeSMTP/target/fakeSMTP.jar'
SMTP_EMAIL_OUT = './FakeSMTP/target/emails'

SERVER_LOGS_DIR = './server-logs'

PROMETHEUS_SERVER = None
ELASTICSEARCH_SERVER = None
KIBANA_SERVER = None
ZOOKEEPER_SERVER = None
KAFKA_SERVER = None
SMTP_SERVER = None

WIDGETS = 0

# Create some prometheus metrics
TEST_ITEMS_IN_INVENTORY = Gauge('products_in_inventory', 'The number of products currently in inventory', ['product'])
TEST_ITEMS_SOLD_TOTAL = Counter('products_sold_total', 'The total number of products sold')

def send_kafka(msg):
    global KAFKA_ENDPOINT, KAFKA_PRODUCER_BIN
    send_console("TO KAFKA ==> " + str(msg))
    with open(SERVER_LOGS_DIR + '/kafka-producer.log', 'a') as logfile:
        p = Popen([KAFKA_PRODUCER_BIN, "--broker-list", KAFKA_ENDPOINT, "--topic", "store"], stdin=PIPE, stdout=logfile, stderr=STDOUT)
        p.communicate(str(msg))

def send_console(msg):
    print msg
    sys.stdout.flush()

def send_elasticsearch(level, msg):
    global ELASTIC_URL
    jsonmsg = json.dumps({'@timestamp': '{:%Y-%m-%dT%H:%M:%S.%f}'.format(datetime.datetime.utcnow()), 'level': level, 'message': msg, 'category': 'inventory'})
    send_console("TO ELASTIC ==> " + str(jsonmsg))
    call(["curl", "-s", "-o", "/dev/null", "-XPOST", ELASTIC_URL + "/store/org.hawkular", "--data", jsonmsg])

def do_random_sleep():
    t = random.uniform(0.1,3.0)
    time.sleep(t)

def simulate_sell():
    global WIDGETS
    if WIDGETS == 0:
        send_elasticsearch("FATAL", "Lost sale. No items left.")
        simulate_buy()
    else:
        WIDGETS -= 1
        TEST_ITEMS_SOLD_TOTAL.inc()
        TEST_ITEMS_IN_INVENTORY.labels('widget').set(WIDGETS)
        send_console("Sold widget. Number of widgets left =" + str(WIDGETS))

def check_inventory():
    global WIDGETS
    if WIDGETS > 10:
        return # inventory is OK
    elif WIDGETS > 5:
        send_elasticsearch("INFO", "Inventory will be low soon")
    elif WIDGETS > 0:
        send_elasticsearch("WARN", "Inventory is low")
    else:
        send_elasticsearch("ERROR", "Out of stock")
        should_simulate_buy = (random.randint(1,100) > 70)
        if should_simulate_buy:
            simulate_buy()
        else:
            send_elasticsearch("CRITICAL", "Failed to buy more inventory when needed")

def simulate_buy():
    global WIDGETS
    num = random.randint(5,20)
    WIDGETS += num
    send_kafka(num)
    send_console("Bought more widgets for inventory. Number of widgets left =" + str(WIDGETS))

def exit_handler(signum, frame):
    send_console('Signal received (' + str(signum) + ') - will now cleanup and exit')
    if PROMETHEUS_SERVER is not None:
        PROMETHEUS_SERVER.terminate()
    if ELASTICSEARCH_SERVER is not None:
        ELASTICSEARCH_SERVER.terminate()
    if KIBANA_SERVER is not None:
        KIBANA_SERVER.terminate()
    if KAFKA_SERVER is not None:
        KAFKA_SERVER.kill()
    if ZOOKEEPER_SERVER is not None:
        ZOOKEEPER_SERVER.terminate()
    if SMTP_SERVER is not None:
        SMTP_SERVER.terminate()

    call(['rm', '-rf', '/tmp/zookeeper'])

    sys.exit(0)

def start_external_components():
    global PROMETHEUS_SERVER, PROMETHEUS_BIN, PROMETHEUS_DATA_DIR, PROMETHEUS_CONFIG, ELASTICSEARCH_SERVER, ELASTICSEARCH_BIN, KIBANA_SERVER, KIBANA_BIN, ZOOKEEPER_SERVER, ZOOKEEPER_BIN, ZOOKEEPER_CONFIG, KAFKA_SERVER, KAFKA_SERVER_BIN, KAFKA_SERVER_CONFIG, KAFKA_SERVER_DATA_DIR, SMTP_SERVER, SMTP_JAR, SERVER_LOGS_DIR

    # make sure log dir exists
    if not os.path.exists(SERVER_LOGS_DIR):
        os.makedirs(SERVER_LOGS_DIR)

    # start all the servers
    send_console("Starting Prometheus")
    PROMETHEUS_SERVER = Popen([PROMETHEUS_BIN, '-storage.local.path', PROMETHEUS_DATA_DIR, '-config.file', PROMETHEUS_CONFIG], stdout=open(SERVER_LOGS_DIR + '/prometheus.log', 'w'), stderr=STDOUT)

    send_console("Starting ElasticSearch")
    ELASTICSEARCH_SERVER = Popen([ELASTICSEARCH_BIN], stdout=open(SERVER_LOGS_DIR + '/elasticsearch.log', 'w'), stderr=STDOUT)

    send_console("Starting Kibana")
    KIBANA_SERVER = Popen([KIBANA_BIN], stdout=open(SERVER_LOGS_DIR + '/kibana.log', 'w'), stderr=STDOUT)

    send_console("Starting Zookeeper")
    ZOOKEEPER_SERVER = Popen([ZOOKEEPER_BIN, ZOOKEEPER_CONFIG], stdout=open(SERVER_LOGS_DIR + '/zookeeper.log', 'w'), stderr=STDOUT)

    send_console("Starting Kafka")
    KAFKA_SERVER = Popen([KAFKA_SERVER_BIN, KAFKA_SERVER_CONFIG, '--override', 'log.dirs=' + KAFKA_SERVER_DATA_DIR, '--override', 'log.dir=' + KAFKA_SERVER_DATA_DIR], stdout=open(SERVER_LOGS_DIR + '/kafka.log', 'w'), stderr=STDOUT)

    send_console("Starting SMTP")
    SMTP_SERVER = Popen(["java", "-jar", SMTP_JAR, '--start-server', '--port', '2525', '--output-dir', SMTP_EMAIL_OUT], stdout=open(SERVER_LOGS_DIR + '/smtp.log', 'w'), stderr=STDOUT)

    time.sleep(5)

# MAIN
if __name__ == '__main__':
    send_console("Hawkular Alerts Demo: Started...")

    signal.signal(signal.SIGINT, exit_handler)
    signal.signal(signal.SIGQUIT, exit_handler)

    try:
        # start things like Prometheus, ElasticSearch, Kafka
        start_external_components()

        # Start up the server to expose the metrics.
        start_http_server(8181)
        send_console("Listening to port 8181...")

        # buy initial inventory to fill the stock room
        simulate_buy()

        # Simulation loop
        while True:
            do_random_sleep()
            should_simulate_sell = (random.randint(1,100) > 40)
            if should_simulate_sell:
                simulate_sell()
            check_inventory()
    except Exception:
        send_console("Received an unexpected error:" + str(sys.exc_info()[0]) + " - will clean up now")
        exit_handler(0, None)
