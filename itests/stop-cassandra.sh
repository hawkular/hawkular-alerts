#!/usr/bin/env bash

echo "Stopping Cassandra Server"
ps -ef | grep cassandra | grep java | awk '{print "kill  " $2}' | bash
