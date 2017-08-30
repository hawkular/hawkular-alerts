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


set -o errexit
set -o nounset
set -o pipefail

STARTTIME=$(date +%s)
source_root=$(dirname "${0}")/..

prefix="jshaughn/"
# set to the alerting version in the target metrics ear
version="1.7.0.Final"
verbose=false
options=""
help=false

for args in "$@"
do
  case $args in
      --prefix=*)
        prefix="${args#*=}"
        ;;
      --version=*)
        version="${args#*=}"
        ;;
      --no-cache)
        options="${options} --no-cache"
        ;;
      --verbose)
        verbose=true
        ;;
     --help)
        help=true
        ;;
  esac
done

# allow ENV to take precedent over switches
prefix="${PREFIX:-$prefix}"
version="${OS_TAG:-$version}" 

if [ "$help" = true ]; then
  echo "Builds the docker images for Hawkular Alerting Tutorial"
  echo
  echo "Options: "
  echo "  --prefix=PREFIX"
  echo "  The prefix to use for the image names."
  echo "  default: jshaughn/"
  echo
  echo "  --version=VERSION"
  echo "  The version used to tag the image"
  echo "  default: 1.7.0.Final"
  echo 
  echo "  --no-cache"
  echo "  If set will perform the build without a cache."
  echo
  echo "  --verbose"
  echo "  Enables printing of the commands as they run."
  echo
  echo "  --help"
  echo "  Prints this help message"
  echo
  exit 0
fi

if [ "$verbose" = true ]; then
  set -x
fi

for component in hawkular-alerting-tutorial; do
  BUILD_STARTTIME=$(date +%s)
  comp_path=.
  docker_tag=${prefix}${component}:${version}
  echo
  echo
  echo "--- Building component '$comp_path' with docker tag '$docker_tag' ---"
  docker build ${options} -t $docker_tag       $comp_path
  BUILD_ENDTIME=$(date +%s); echo "--- $docker_tag took $(($BUILD_ENDTIME - $BUILD_STARTTIME)) seconds ---"
  echo
  echo
done

echo
echo
echo "++ Active images"
docker images | grep ${prefix} | grep ${version} | sort
echo


ret=$?; ENDTIME=$(date +%s); echo "$0 took $(($ENDTIME - $STARTTIME)) seconds"; exit "$ret"
