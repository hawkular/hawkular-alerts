#!/usr/bin/env bash

DIRNAME=`dirname "$0"`

HWK_CLASSPATH=""
HWK_CONFIG="${DIRNAME}/config"
HWK_DATA="${DIRNAME}/data"
HWK_LOGS="${DIRNAME}/logs"

init_hawkular_folders() {
    mkdir -p ${HWK_DATA} ${HWK_LOGS}
}

hwk_prop() {
    grep "${1}" ${HWK_CONFIG}/hawkular.properties | cut -d'=' -f2
}

set_java_opts() {
    JAVA_OPTS="$JAVA_OPTS -Xmx64m -Xms64m"
    JAVA_OPTS="$JAVA_OPTS -Dhawkular.data=${HWK_DATA}"
    JAVA_OPTS="$JAVA_OPTS -Dhawkular.configuration=${HWK_CONFIG}"
    JAVA_OPTS="$JAVA_OPTS -Dhawkular.bind-address=$(hwk_prop 'hawkular-alerts.bind-address')"
    JAVA_OPTS="$JAVA_OPTS -Dhawkular.port=$(hwk_prop 'hawkular-alerts.port')"
    JAVA_OPTS="$JAVA_OPTS -Dhawkular.cors-url=http://127.0.0.1:8003"
    JAVA_OPTS="$JAVA_OPTS -Dhawkular.cors-headers=Hawkular-Tenant"
    JAVA_OPTS="$JAVA_OPTS -Dlog4j.configurationFile=${HWK_CONFIG}/log4j2.xml"
    JAVA_OPTS="$JAVA_OPTS -Djava.net.preferIPv4Stack=true"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.authenticate=false"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.port=$(hwk_prop 'hawkular-alerts.jmx-port')"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.ssl=false"
    JAVA_OPTS="$JAVA_OPTS -Djava.rmi.server.hostname=$(hwk_prop 'hawkular-alerts.bind-address')"
    # JGROUPS_BIND_ADDR="127.0.0.1"
    # JAVA_OPTS="$JAVA_OPTS -Djgroups.bind_addr=${JGROUPS_BIND_ADDR}"
    # JAVA_OPTS="$JAVA_OPTS -Dmail.smtp.port=2525"
    # JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y"
}

set_hawkular_classpath() {
    SEPARATOR=""
    ISPN_LUCENE_JAR=""
    for FILE in $(find lib -name "*.jar")
    do
        if [ "$FILE" == lib/infinispan-lucene-directory* ]
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

    # [lponce] Ispn needs to activate a service from lucene jar
    # and for some reason in standalone mode jar needs to be placed first in classpath
    HWK_CLASSPATH="$ISPN_LUCENE_JAR$SEPARATOR$HWK_CLASSPATH"
}

console_alerting() {
    java $JAVA_OPTS -cp "$HWK_CLASSPATH" "org.hawkular.HawkularServer"
}

start_alerting() {
    java $JAVA_OPTS -cp "$HWK_CLASSPATH" "org.hawkular.HawkularServer" > ${HWK_LOGS}/HawkularServer.out 2>&1 &
    echo "$!" > ${HWK_DATA}/HawkularServer.pid
    HWK_PID=$(cat ${HWK_DATA}/HawkularServer.pid)
    echo "Started HawkularServer PID ${HWK_PID}"
    echo "Logs under ${HWK_LOGS}/HawkularServer.out"
}

stop_alerting() {
    if [ -f ${HWK_DATA}/HawkularServer.pid ]
    then
        HWK_PID=$(cat ${HWK_DATA}/HawkularServer.pid)
        rm ${HWK_DATA}/HawkularServer.pid
        java -cp "$HWK_CLASSPATH" "org.hawkular.HawkularManager" "stop" "$(hwk_prop 'hawkular-alerts.bind-address')" "$(hwk_prop 'hawkular-alerts.jmx-port')"
        echo "Stopping HawkularServer PID ${HWK_PID}"
    fi
}

main() {
    init_hawkular_folders
    set_java_opts
    set_hawkular_classpath
    case "$1" in
        "")
        ;&
        "start")
            start_alerting
            ;;
        "stop")
            stop_alerting
            ;;
        "console")
            console_alerting
            ;;
        *)
            echo $0 '[start|stop|console]'
            ;;
    esac
}

main "$@"
