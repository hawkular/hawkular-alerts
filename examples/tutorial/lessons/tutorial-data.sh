#!/bin/bash
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

# Generate the following metrics at 5 second intervals
#   Gauge   gauge-1   random values between 0 and 100
#   Gauge   gauge-2   random values between 0 and 100
#   Counter counter-1 increasing counter, incremented 0..5 each time

counter=0;
gauge1Url="http://localhost:8080/hawkular/metrics/gauges/gauge-1/raw"
gauge2Url="http://localhost:8080/hawkular/metrics/gauges/gauge-2/raw"
counterUrl="http://localhost:8080/hawkular/metrics/counters/counter-1/raw"

while true
do 
  gauge1=$(echo "scale=0; $RANDOM % 101" | bc -l)
  gauge2=$(echo "scale=0; $RANDOM % 101" | bc -l)
  counter=$(echo "scale=0; $counter + ($RANDOM % 6)" | bc -l)
  timestamp=$(date +%s%3N)

  gauge1Metric="[{'timestamp':$timestamp,'value':$gauge1}]"
  gauge2Metric="[{'timestamp':$timestamp,'value':$gauge2}]"
  counterMetric="[{'timestamp':$timestamp,'value':$counter}]"

  echo Sending gauge-1 $gauge1Metric
  response=$(curl --write-out %{http_code}  --output /dev/null -s -i -HContent-Type:application/json -HHawkular-Tenant:tutorial $gauge1Url -X POST -d $gauge1Metric)
  echo $response
  if [ 200 -ne $response ]
  then
    echo Exiting on response=$response, requestUrl=$gauge1Url
    exit 1
  fi

  echo Sending gauge-2 $gauge2Metric
  response=$(curl --write-out %{http_code}  --output /dev/null -s -i -HContent-Type:application/json -HHawkular-Tenant:tutorial $gauge2Url -X POST -d $gauge2Metric)
  echo $response
  if [ 200 -ne $response ]
  then
    echo Exiting on response=$response, requestUrl=$gauge2Url
    exit 1
  fi

  echo Sending counter-1 $counterMetric
  response=$(curl --write-out %{http_code}  --output /dev/null -s -i -HContent-Type:application/json -HHawkular-Tenant:tutorial $counterUrl -X POST -d $counterMetric)
  echo $response
  if [ 200 -ne $response ]
  then
    echo Exiting on response=$response, requestUrl=$counterUrl
    exit 1
  fi

  sleep 5
done

