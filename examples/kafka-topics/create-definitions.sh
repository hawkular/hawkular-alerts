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

main() {
    local url=$1
    local tenant=$2
    if [ "x$url" == "x" ]; then
        url="http://localhost:8080"
    fi
    if [ "x$tenant" == "x" ]; then
        tenant="my-organization"
    fi
    local response=$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -H "Hawkular-Tenant: $tenant" -XPOST "$url/hawkular/alerts/import/all" --data "@kafka-triggers.json")
    if [ "$response" -gt "300" ]; then
        echo "Error importing definitions into hawkular"
        exit 1
    fi
}

main "$@"