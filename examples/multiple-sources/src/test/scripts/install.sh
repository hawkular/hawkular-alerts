#!/bin/sh
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

PROMETHEUS_URL="https://github.com/prometheus/prometheus/releases/download/v1.7.1/prometheus-1.7.1.linux-amd64.tar.gz"
ELASTICSEARCH_URL="https://download.elastic.co/elasticsearch/release/org/elasticsearch/distribution/zip/elasticsearch/2.4.4/elasticsearch-2.4.4.zip"
KIBANA_URL="https://download.elastic.co/kibana/kibana/kibana-4.6.4-linux-x86_64.tar.gz"
KAFKA_URL="http://mirror.cc.columbia.edu/pub/software/apache/kafka/0.11.0.0/kafka_2.11-0.11.0.0.tgz"

mkdir -p ./target || exit 1
cd ./target

echo Copy Demo Script
cp ../src/test/scripts/store.py .

echo Install Prometheus Python Client
sudo pip install prometheus_client

if [ ! -d "./prometheus" ]; then
  echo Download, Install, Configure Prometheus Server into directory "prometheus"
  wget -O prometheus.tgz $PROMETHEUS_URL
  tar zxvf prometheus.tgz
  rm prometheus.tgz
  mv prometheus* prometheus
  cat >> prometheus/prometheus.yml << PROM_CONFIG_EOF
  # HAWKULAR ALERTS STORE DEMO
  - job_name: 'store'
    scrape_interval: 5s
    static_configs:
      - targets: ['localhost:8181']
PROM_CONFIG_EOF
fi

if [ ! -d "./elasticsearch" ]; then
  echo Download, Install ElasticSearch Server into directory "elasticsearch"
  wget -O elasticsearch.zip $ELASTICSEARCH_URL
  unzip elasticsearch.zip
  rm elasticsearch.zip
  mv elasticsearch* elasticsearch
fi

if [ ! -d "./kibana" ]; then
  echo Download, Install Kibana into directory "kibana"
  wget -O kibana.tgz $KIBANA_URL
  tar zxvf kibana.tgz
  rm kibana.tgz
  mv kibana* kibana
fi

if [ ! -d "./kafka" ]; then
  echo Download, Install Kafka into directory "kafka"
  wget -O kafka.tgz $KAFKA_URL
  tar zxvf kafka.tgz
  rm kafka.tgz
  mv kafka* kafka
fi

if [ ! -d "./FakeSMTP" ]; then
  echo Clone and Build FakeSMTP Email Server
  git clone https://github.com/Nilhcem/FakeSMTP
  cd FakeSMTP
  mvn clean install -DskipTests
  mv target/fakeSMTP*.jar target/fakeSMTP.jar
  cd ..
fi

echo Hawkular Alerts Demo Components Have Been Installed
