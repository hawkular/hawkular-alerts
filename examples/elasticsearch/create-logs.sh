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

function json() {
    local timestamp=$(date +%Y-%m-%d'T'%H:%M:%S.%6N%z)
    local index_app=$RANDOM
    local index_level=$RANDOM
    local index_component=$RANDOM
    let "index_app %= 3"
    let "index_level %= 3"
    let "index_component %= 3"

    local levels=(
        'ERROR'
        'WARN'
        'INFO'
    )
    local apps=(
        'AppA'
        'AppB'
        'AppC'
    )
    local components=(
        'Frontend'
        'Backend'
        'Security'
    )
    local level=${levels[$index_level]}
    local app=${apps[$index_app]}
    local component=${components[$index_component]}
    MSG="{\"@timestamp\":\"$timestamp\","
    MSG="$MSG \"level\":\"$level\","
    MSG="$MSG \"app\":\"$app\","
    MSG="$MSG \"message\":\"Message $RANDOM from $component\"}"
    echo $MSG
}

function send_message() {
    local url=$1
    local message=$2

    local response=$(curl -s -o /dev/null -w "%{http_code}" -XPOST $url --data "$message")
    echo $response
}

main() {
    local url=$1
    local index=$2
    local type=$3
    if [ "x$url" == "x" ]; then
        url="http://localhost:9200"
    fi
    if [ "x$index" == "x" ]; then
        index="log"
    fi
    if [ "x$type" == "x" ]; then
        type="org.hawkular.logging"
    fi
    echo "$url $index $type"
    while true
    do
        local message=$(json)
        local response=$(send_message "$url/$index/$type" "$message")
        echo "[$response] $message"
        if [ "$response" -gt "300" ] || [ "$response" == "000" ] ; then
            echo "Error writing document into Elasticsearch"
            exit 1
        fi
        sleep 1
    done
}

main "$@"