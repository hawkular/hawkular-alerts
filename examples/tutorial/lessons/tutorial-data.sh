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

# OS specific support (must be 'true' or 'false').
linux=false;
darwin=false;
other=false;
case "`uname`" in
    Darwin*)
        darwin=true
        ;;
    Linux)
        linux=true
        ;;
    *)
        other=true
        ;;
esac

counter1=0;
dataUrl="http://localhost:8080/hawkular/alerts/data"

while true
do 
  gauge1=$(echo "scale=0; $RANDOM % 101" | bc -l)
  gauge2=$(echo "scale=0; $RANDOM % 101" | bc -l)
  counter1=$(echo "scale=0; $counter1 + ($RANDOM % 6)" | bc -l)
  if $darwin; then
    timestamp=$(gdate +%s%3N)
  else
    timestamp=$(date +%s%3N)
  fi

  gauge1Metric="{\"timestamp\":$timestamp,\"id\":\"gauge-1\",\"value\":$gauge1}"
  gauge2Metric="{\"timestamp\":$timestamp,\"id\":\"gauge-2\",\"value\":$gauge2}"
  counter1Metric="{\"timestamp\":$timestamp,\"id\":\"counter-1\",\"value\":$counter1}"

  data="[$gauge1Metric,$gauge2Metric,$counter1Metric]"

  echo Sending gauge-1 $gauge1Metric
  echo Sending gauge-2 $gauge2Metric
  echo Sending counter-1 $counter1Metric
  response=$(curl --write-out %{http_code}  --output /dev/null -s -i -HContent-Type:application/json -HHawkular-Tenant:tutorial $dataUrl -X POST -d $data)
  echo $response
  if [ 200 -ne $response ]
  then
    echo Exiting on response=$response, requestUrl=$dataUrl
    exit 1
  fi

  sleep 5
done

