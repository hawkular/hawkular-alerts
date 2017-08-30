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


# Hawkular Alerting is the alerting component used in Hawkular Metrics, The
# Hawkular Alerting Tutorial uses a Standalone Hawkular Metrics docker
# container to more easily demonstrate alerting scenarios for incoming
# metric data.  This builds the Metrics container.
#
# NOTE: Building the Hawkular Metrics Docker Container is not part of
# the tutorial.  This is here only to be used when a new version of the
# tutorial container is to be built, typically by a developer.


FROM jboss/wildfly:10.1.0.Final

MAINTAINER Hawkular Alerting <hawkular-dev@lists.jboss.org>

ENV HAWKULAR_METRICS_ENDPOINT_PORT="8080" \
    HAWKULAR_METRICS_VERSION="0.27.0.Final" \
    HAWKULAR_METRICS_DIRECTORY="/opt/hawkular-alerting-tutorial" \
    HAWKULAR_METRICS_SCRIPT_DIRECTORY="/opt/hawkular-alerting-tutorial/scripts/" \
    PATH=$PATH:$HAWKULAR_METRICS_SCRIPT_DIRECTORY \
    JAVA_OPTS_APPEND="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump"


# http, https, management, debug ports
EXPOSE 8080 9990 8787


# Get and copy the hawkular metrics ear to the deployment directory
#RUN cd $JBOSS_HOME/standalone/deployments/ && \
#    curl -Lo hawkular-metrics.ear https://origin-repository.jboss.org/nexus/service/local/artifact/maven/content?r=public\&g=org.hawkular.metrics\&a=hawkular-metrics-#standalone-dist\&e=ear\&v=${HAWKULAR_METRICS_VERSION}
COPY hawkular-metrics.ear \
     $JBOSS_HOME/standalone/deployments/

COPY hawkular-metrics-wrapper.sh \
     $HAWKULAR_METRICS_SCRIPT_DIRECTORY

COPY config.properties \
     $HAWKULAR_METRICS_SCRIPT_DIRECTORY

COPY standalone.conf \
     $JBOSS_HOME/bin/


# Overwrite the welcome-content to display a more appropriate status page
COPY welcome-content $JBOSS_HOME/welcome-content/


# Overwrite the default Standalone.xml file with one that activates the HTTPS endpoint
COPY standalone.xml $JBOSS_HOME/standalone/configuration/standalone.xml


# Overwrite the default logging.properties file
COPY logging.properties $JBOSS_HOME/standalone/configuration/logging.properties

# Change the permissions so that the user running the image can start up Hawkular Metrics
USER root
RUN chmod -R 777 /opt

USER 1000

CMD $HAWKULAR_METRICS_SCRIPT_DIRECTORY/hawkular-metrics-wrapper.sh -b 0.0.0.0 -Dhawkular-metrics.cassandra-nodes=hawkular-cassandra
