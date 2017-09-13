#!/usr/bin/env bash
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


DIRNAME=`dirname "$0"`
$DIRNAME/stop.sh

if [ ! -d "target/dependency" ]; then
    mvn package dependency:copy-dependencies
fi

BACKEND=$1

JAVA_OPTS="$JAVA_OPTS -Dhawkular-alerts.backend=ispn -Dhawkular.data=$(pwd)/target/ispn-itests"

JGROUPS_BIND_ADDR="127.0.0.1"
JAVA_OPTS="$JAVA_OPTS -Xmx64m -Xms64m"
JAVA_OPTS="$JAVA_OPTS -Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=${JGROUPS_BIND_ADDR}"
JAVA_OPTS="$JAVA_OPTS -Dmail.smtp.port=2525"
JAVA_OPTS="$JAVA_OPTS -Dlog4j.configurationFile=src/test/resources/log4j2.xml"
# JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y"

# [lponce] Ispn needs to activate a service from lucene jar and for some reason in standalone mode jar needs to be placed first in classpath
HWK_CLASSPATH=""
SEPARATOR=""
ISPN_LUCENE_JAR=""
for FILE in $(find target/dependency -name "*.jar")
do
    if [ "$FILE" == target/dependency/infinispan-lucene-directory* ]
    then
        ISPN_LUCENE_JAR="$FILE"
    else
        HWK_CLASSPATH="$HWK_CLASSPATH$SEPARATOR$FILE"
        if [ "x$SEPARATOR" = "x" ]
        then
            SEPARATOR=":"
        fi
    fi
done

HWK_CLASSPATH="$ISPN_LUCENE_JAR$SEPARATOR$HWK_CLASSPATH"

CLUSTER=$2

if [ "$CLUSTER" == "cluster" ]
then
    NODE1="-Dhawkular.port=8080 -Dhawkular-alerts.distributed=true"
    java $JAVA_OPTS $NODE1 -cp "$HWK_CLASSPATH" "org.hawkular.HawkularServer" > target/HawkularServer1.out 2>&1 &
    echo "NODE1 Logs under $(pwd)/target/HawkularServer1.out"

    NODE2="-Dhawkular.port=8180 -Dhawkular-alerts.distributed=true"
    java $JAVA_OPTS $NODE2 -cp "$HWK_CLASSPATH" "org.hawkular.HawkularServer" > target/HawkularServer2.out 2>&1 &
    echo "NODE2 Logs under $(pwd)/target/HawkularServer2.out"

    NODE3="-Dhawkular.port=8280 -Dhawkular-alerts.distributed=true"
    java $JAVA_OPTS $NODE3 -cp "$HWK_CLASSPATH" "org.hawkular.HawkularServer" > target/HawkularServer3.out 2>&1 &
    echo "NODE3 Logs under $(pwd)/target/HawkularServer3.out"
else
    java $JAVA_OPTS -cp "$HWK_CLASSPATH" "org.hawkular.HawkularServer" > target/HawkularServer.out 2>&1 &
    echo "Logs under $(pwd)/target/HawkularServer.out"
fi


