/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
angular.module('hwk.appModule').service('hwk.filterService', ['$rootScope',
  function ($rootScope) {
    'use strict';

    this.rangeFilter = {
      datetime: moment(new Date()).format('YYYY-MM-DD HH:mm:ss'),
      offset: 4,
      unit: 'Hours',
      unitOptions: ['Minutes', 'Hours', 'Days'],
      direction: 'Before',
      directionOptions: ['Before', 'After']
    };

    this.getRange = function () {
      var offset = this.rangeFilter.offset;
      var start;
      var end;
      switch ( this.rangeFilter.unit ) {
      case 'Minutes' :
        offset *= (60 * 1000);
        break;
      case 'Hours' :
        offset *= (60 * 60 * 1000);
        break;
      case 'Days' :
        offset *= (60 * 60 * 24 * 1000);
        break;
      default :
        console.debug("[Dashboard] Unsupported unit: " + this.rangeFilter.unit);
      }
      switch ( this.rangeFilter.direction ) {
      case 'After' :
        start = new Date(this.rangeFilter.datetime).getTime();
        end = start + offset;
        break;
      case 'Before':
        end = new Date(this.rangeFilter.datetime).getTime();
        start = end - offset;
        break;
      default :
        console.debug("[Dashboard] Unsupported direction: " + this.rangeFilter.direction);
      }

      return [start,end];
    };

    this.alertFilter = {
      severity: 'All Severity',
      severityOptions: ['All Severity', 'Low', 'Medium', 'High', 'Critical'],
      status: 'All Status',
      statusOptions: ['All Status', 'Open', 'Acknowledged', 'Resolved'],
      tagQuery: null
    };

    this.eventFilter = {
      tagQuery: null
    };

  }
]);
