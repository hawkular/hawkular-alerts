#!/usr/bin/env bash

echo "Stopping HawkularServer"
ps -ef | grep HawkularServer | grep java | while read -r line
do
    echo $line | awk '{print "kill " $2}' | bash
done
