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

# @author Guilherme Baufaker Rêgo (@gbaufake)
# Docker Container for Tutorial on Hawkular Alerts with Elasticsearch

FROM jboss/base-jdk:8

MAINTAINER Hawkular Alerting <hawkular-dev@lists.jboss.org>

USER root
RUN chmod -R 777 /opt

RUN yum -y install wget git maven unzip

EXPOSE 5601 8080 9200

# Install Elastic Search
RUN wget https://download.elastic.co/elasticsearch/release/org/elasticsearch/distribution/zip/elasticsearch/2.4.4/elasticsearch-2.4.4.zip
RUN unzip elasticsearch-2.4.4.zip -d /opt/
RUN mv /opt/elasticsearch-2.4.4/ /opt/elasticsearch/

# Install Kibana
RUN wget https://download.elastic.co/kibana/kibana/kibana-4.6.4-linux-x86_64.tar.gz
RUN mkdir -p tar /opt/kibana/ && tar xvfz kibana-4.6.4-linux-x86_64.tar.gz -C /opt/kibana/ --strip-components=1

# Clone the repository
RUN git clone -b next https://github.com/hawkular/hawkular-alerts.git /opt/hawkular-alerts

# Copy the resources

COPY create-definitions.sh \ /opt/

COPY create-logs.sh \ /opt/

COPY elasticsearch-triggers.json \ /opt/

RUN  cd /opt/hawkular-alerts/ && mvn clean install -DskipTests

# CMD /opt/hawkular-alerts/hawkular-alerts-rest-tests/target/wildfly-10.0.0.Final/bin/standalone.sh 2>&1
# CMD /opt/elasticsearch/bin/elasticsearch -Des.insecure.allow.root=true
# CMD /opt/kibana/bin/kibana
# CMD /opt/create-definitions.sh
# CMD /opt/create-logs.sh
