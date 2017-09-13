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
angular.module('hwk.dashboardModule').controller( 'hwk.dashboardController', ['$scope', '$rootScope', '$resource', '$window', '$location', '$interval', '$q', 'hwk.dashboardService', 'hwk.filterService', 'Notifications',
  function ($scope, $rootScope, $resource, $window, $location, $interval, $q, dashboardService, filterService, Notifications) {
    'use strict';

    console.debug("[Dashboard] Start: " + new Date());
    console.debug("[Dashboard] $rootScope.selectedTenant " + $rootScope.selectedTenant);

    $scope.showTimeline = false;
    $scope.refresh = true;

    $scope.filter = {
      'range': filterService.rangeFilter,
      'alert': filterService.alertFilter,
      'event': filterService.eventFilter,
    };

    var ONE_SECOND = 1000,
      ONE_HOUR = 60 * 60 * ONE_SECOND,
      ONE_DAY = 24 * ONE_HOUR,
      ONE_WEEK = 7 * ONE_DAY,
      ONE_MONTH = 30 * ONE_DAY;

    var PING_INTERVAL = 5000;

    var selectedTenant = $rootScope.selectedTenant;

    var OPEN = 0, ACKNOWLEDGED = 1, RESOLVED = 2, EVENTS = 3;

    var dataTimeline = [
      {
        name: 'Open Alerts',
        data: []
      },
      {
        name: 'Acknowledged Alerts',
        data: []
      },
      {
        name: 'Resolved Alerts',
        data: []
      },
      {
        name: 'Events',
        data: []
      }
    ];

    var today = new Date();

    var onTimelineColor = function (eventData) {
      switch (eventData.name) {
      case 'Open Alerts':
        return '#c00';
      case 'Acknowledged Alerts':
        return '#ec7a08';
      case 'Resolved Alerts':
        return '#3f9c35';
      case 'Events':
        return '';
      }
      return '';
    };

    var severityColorPatterns = {
      pattern: [
        $.pfPaletteColors.red, // Critical
        $.pfPaletteColors.orange, // High
        $.pfPaletteColors.cyan, // Medium
        $.pfPaletteColors.blue // Low
      ]
    };

    var toastError = function (reason) {
      console.debug('[Dashboard] Backend error ' + new Date());
      console.debug(reason);
      var errorMsg = new Date() + " Status [" + reason.status + "] " + reason.statusText;
      if (reason.status === -1) {
        errorMsg = new Date() + " Hawkular Alerting is not responding. Please review browser console for further details";
      }
      Notifications.error(errorMsg);
    };

    function countEventSections(event) {
      var count = 0;

      if (event.evalSets) {
        count++;
      }
      if (event.resolvedEvalSets) {
        count++;
      }
      if (event.lifecycle) {
        count++;
      }
      if (event.actions) {
        count++;
      }
      return count;
    }

    function transformExternalEventsOnEvalSets(event) {
      var i, j, evalSet;
      if (event.evalSets) {
        for (i = 0; i < event.evalSets.length; i++) {
          for (j = 0; j < event.evalSets[i].length; j++) {
            evalSet = event.evalSets[i][j];
            if (evalSet.context && evalSet.context.events) {
              // This is a trick to convert a string into a json, as context can only hold string values not objects
              evalSet.context.parsed = JSON.parse(evalSet.context.events);
            }
          }
        }
      }
      if (event.resolvedEvalSets) {
        for (i = 0; i < event.resolvedEvalSets.length; i++) {
          for (j = 0; j < event.resolvedEvalSets[i].length; j++) {
            evalSet = event.resolvedEvalSets[i][j];
            if (evalSet.context && evalSet.context.events) {
              // This is a trick to convert a string into a json, as context can only hold string values not objects
              evalSet.context.parsed = JSON.parse(evalSet.context.events);
            }
          }
        }
      }
    }

    function getActions(event) {
      var promise = dashboardService.Action(selectedTenant, event.id).query();
      return promise;
    }

    var onTimelineClick = function (eventTimeline) {
      $scope.timelineEvents = [];
      var actionPromises = [];
      if (eventTimeline.events) {
        for (var i = 0; i < eventTimeline.events.length; i++) {
          transformExternalEventsOnEvalSets(eventTimeline.events[i].details);
          actionPromises.push(getActions(eventTimeline.events[i].details));
          $scope.timelineEvents.push(eventTimeline.events[i].details);
        }
      }
      if (eventTimeline.details) {
        transformExternalEventsOnEvalSets(eventTimeline.details);
        actionPromises.push(getActions(eventTimeline.details));
        $scope.timelineEvents.push(eventTimeline.details);
      }
      $q.all(actionPromises).then(function (result) {
        if ( $scope.refresh === true ) {
          $scope.updateRefresh();
        }
        for (var i = 0; i < result.length; i++) {
          if ( result.length > 0 ) {
            $scope.timelineEvents[i].actions = result[i];
          }
          $scope.timelineEvents[i].eventSections = countEventSections($scope.timelineEvents[i]);
        }
      }, toastError);
    };

    var timeline;
    var element;
    var intervalRef;

    var severityAlertChart;
    var activeAlertChart;

    var createCharts = function () {
      // active alerts chart
      var activeAlertChartConfig = {
        'chartId': 'activeAlertChart',
        'legend': {'show': true},
        'color': severityColorPatterns,
        'bindto': '#active-alert-chart',
        'axis': {
          'rotated': false,
          'x': {
            categories: ['Open', 'Acknowledged'],
            type: 'category'
          }
        },
        'size': {
          'width': 200,
          'height': 200
        },
        'data': {
          'type': 'bar',
          'groups': [['Critical', 'High', 'Medium', 'Low']],
          'columns': [
            ['Critical', 0, 0],
            ['High', 0, 0],
            ['Medium', 0, 0],
            ['Low', 0, 0]
          ]
        }
      };
      activeAlertChart = c3.generate(activeAlertChartConfig);

      // severity alerts chart - alert severities are fixed to either CRITICAL, HIGH, MEDIUM, or LOW
      var severityAlertChartConfig = {
        'chartId': 'severityAlertChart',
        'legend': {"show": true},
        'color': severityColorPatterns,
        'bindto': '#severity-alert-chart',
        'size': {
          'width': 200,
          'height': 200
        },
        'data': {
          'type': 'pie',
          'rows': [
            ['Critical', 'High', 'Medium', 'Low'],
            [0, 0, 0, 0]
          ],
          'onclick': function (d, i) {
            $scope.linkAlerts('All Status', d.name);
          }
        },
        'tooltip': {
          'format': {
            'value': function (value, ratio, id) {
              return d3.format('%')(ratio) + " (" + d3.format('0')(value) + ")";
            }
          }
        },
        'pie': {
          'label': {
            'show': false // hide to avoid it 'flashing' during our constant reloads during update
          }
        }
      };
      severityAlertChart = c3.generate(severityAlertChartConfig);
    };

    var updateDashboard = function () {
      console.debug("[Dashboard] Updating data for " + selectedTenant + " at " + new Date());

      var severity = new Map();
      var alertsByOpenAck = [
        ['Critical', 0, 0],
        ['High', 0, 0],
        ['Medium', 0, 0],
        ['Low', 0, 0]
      ];

      var alertsCriteria = {
        'thin': false   // TODO: we need to add this, we should not initially fetch fat alerts
      };
      var eventsCriteria = {
        'thin': false,   // TODO: we need to add this, we should not initially fetch fat alerts
        'eventType': 'EVENT' // In UI, we should not duplicate Alerts and Events
      };

      if ( $scope.refresh ) {
        // update to now on each refresh to keep a sliding window
        $scope.filter.range.datetime = moment(new Date()).format('YYYY-MM-DD HH:mm:ss');
      }

      if ( $scope.filter.range.datetime ) {
        var r = filterService.getRange();
        alertsCriteria.startTime = r[0];
        alertsCriteria.endTime = r[1];
        eventsCriteria.startTime = r[0];
        eventsCriteria.endTime = r[1];
      }

      var promise1 = dashboardService.Alert(selectedTenant, alertsCriteria).query();
      var promise2 = dashboardService.Event(selectedTenant, eventsCriteria).query();

      $q.all([promise1.$promise, promise2.$promise]).then(function (result) {
        var updatedAlerts = result[0];
        var updatedEvents = result[1];

        dataTimeline[OPEN].data = [];
        dataTimeline[ACKNOWLEDGED].data = [];
        dataTimeline[RESOLVED].data = [];
        dataTimeline[EVENTS].data = [];

        $scope.showTimeline = ( updatedAlerts.length !== 0 || updatedEvents.length !== 0 );

        var i;
        for (i = 0; i < updatedAlerts.length; i++) {

          // build counts of severities
          var sevCount = severity.get(updatedAlerts[i].severity);
          if (sevCount === undefined) {
            sevCount = 0;
          }
          sevCount = sevCount + ((updatedAlerts[i].status !== 'RESOLVED') ? 1 : 0);
          severity.set(updatedAlerts[i].severity, sevCount);

          // the outer array index for the severity into alertsByOpenAck object
          var alertsByOpenAckIndex;
          switch (updatedAlerts[i].severity) {
          case 'CRITICAL':
            alertsByOpenAckIndex = 0;
            break;
          case 'HIGH':
            alertsByOpenAckIndex = 1;
            break;
          case 'MEDIUM':
            alertsByOpenAckIndex = 2;
            break;
          case 'LOW':
            alertsByOpenAckIndex = 3;
            break;
          }

          // build timeline data that is also used for alert counts
          var status = updatedAlerts[i].status;
          var stime = updatedAlerts[i].lifecycle[updatedAlerts[i].lifecycle.length - 1].stime;
          switch (status) {
          case 'OPEN':
            dataTimeline[OPEN].data.push({
              date: new Date(stime),
              details: updatedAlerts[i]
            });
            alertsByOpenAck[alertsByOpenAckIndex][1]++;
            break;
          case 'ACKNOWLEDGED':
            dataTimeline[ACKNOWLEDGED].data.push({
              date: new Date(stime),
              details: updatedAlerts[i]
            });
            alertsByOpenAck[alertsByOpenAckIndex][2]++;
            break;
          case 'RESOLVED':
            dataTimeline[RESOLVED].data.push({
              date: new Date(stime),
              details: updatedAlerts[i]
            });
            break;
          }
        }

        for (i = 0; i < updatedEvents.length; i++) {
          dataTimeline[EVENTS].data.push({
            date: new Date(updatedEvents[i].ctime),
            details: updatedEvents[i]
          });
        }

        $scope.openAlerts = dataTimeline[OPEN].data;
        $scope.acknowledgedAlerts = dataTimeline[ACKNOWLEDGED].data;
        $scope.resolvedAlerts = dataTimeline[RESOLVED].data;
        $scope.events = dataTimeline[EVENTS].data;

        // load the charts with the new data

        if (severityAlertChart === undefined) {
          createCharts();
        }

        var sevCritical = severity.has('CRITICAL') ? severity.get('CRITICAL') : 0;
        var sevHigh = severity.has('HIGH') ? severity.get('HIGH') : 0;
        var sevMedium = severity.has('MEDIUM') ? severity.get('MEDIUM') : 0;
        var sevLow = severity.has('LOW') ? severity.get('LOW') : 0;

        severityAlertChart.load({
          'rows': [
            ['Critical', 'High', 'Medium', 'Low'],
            [sevCritical, sevHigh, sevMedium, sevLow]
          ],
        });

        activeAlertChart.load({
          'columns': alertsByOpenAck
        });

        // prepare timeline
        console.debug('[Dashboard] Update timeline data ' + new Date());
        // console.debug(JSON.stringify(dataTimeline));

        // [lponce] remove objects to re-draw
        d3.select('#pf-timeline').selectAll('div').remove();

        if ( $scope.showTimeline ) {
          var r = filterService.getRange();
          var startTimeline = r[0] - ((r[1] - r[0]) * 0.10);
          var endTimeline = r[1] + ((r[1] - r[0]) * 0.10);

          timeline = d3.chart.timeline()
            .end(new Date(endTimeline))
            .start(new Date(startTimeline))
            .minScale(ONE_WEEK / ONE_MONTH)
            .maxScale(ONE_WEEK / ONE_HOUR)
            .eventLineColor(onTimelineColor)
            .eventShape('\u2b24')
            .eventClick(onTimelineClick)
            .eventGrouping(ONE_SECOND);
          element = d3.select('#pf-timeline').append('div').datum(dataTimeline);
          timeline(element);
        }

      }, toastError);
    };

    // Init scope to show at least an empty chart
    $scope.openAlerts = dataTimeline[OPEN].data;
    $scope.acknowledgedAlerts = dataTimeline[ACKNOWLEDGED].data;
    $scope.resolvedAlerts = dataTimeline[RESOLVED].data;
    $scope.events = dataTimeline[EVENTS].data;

    // Initial dashboard if tenant is valid
    if (selectedTenant && selectedTenant.length > 0) {
      updateDashboard();
      if ($scope.refresh) {
        intervalRef = $interval(updateDashboard, PING_INTERVAL);
      }
    }

    // Watch for tenant changes
    var watchRef = $rootScope.$watch('selectedTenant', function (newTenant, oldTenant) {
      selectedTenant = newTenant;
      console.debug('[Dashboard] New Tenant: ' + selectedTenant);
      if (intervalRef) {
        $interval.cancel(intervalRef);
      }
      // Create an $interval only if tenant is valid
      if (selectedTenant && selectedTenant.length > 0) {
        if ($scope.refresh) {
          intervalRef = $interval(updateDashboard, PING_INTERVAL);
        } else {
          updateDashboard();
        }
      }
    });

    // When dashboard controller is destroyed, the $interval and $watch are removed.
    $scope.$on('$destroy', function() {
      $interval.cancel(intervalRef);
      watchRef();
    });

    angular.element($window).bind('resize', function () {
      if (selectedTenant && selectedTenant.length > 0 && timeline !== undefined) {
        timeline(element);
      }
    });

    $scope.updateRefresh = function () {
      if ($scope.refresh) {
        console.debug('[Dashboard] Stopping refresh');
        $interval.cancel(intervalRef);
        $scope.refresh = false;
      } else {
        console.debug('[Dashboard] Starting refresh');
        if (selectedTenant && selectedTenant.length > 0) {
          intervalRef = $interval(updateDashboard, PING_INTERVAL);
        }
        $scope.timelineEvents = [];
        $scope.refresh = true;
      }
    };

    $scope.linkAlerts = function (statusFilter, severityFilter) {
      // note, range filter is global
      if (! (statusFilter && statusFilter.length > 0 )) {
        statusFilter = 'All Status';
      }
      if (! (severityFilter && severityFilter.length > 0 )) {
        severityFilter = 'All Severity';
      }
      $scope.filter.alert.severity = severityFilter;
      $scope.filter.alert.status = statusFilter;
      $scope.filter.alert.tagQuery = null;
      $location.url("/alerts");
    };

    $scope.linkEvents = function() {
      // note, range filter is global
      $scope.filter.event.tagQuery = null;
      $location.url("/events");
    };

    $scope.setRange = function() {
      updateDashboard();
    };

    $scope.updateRange = function(range) {
      switch ( range ) {
      case '30m' :
        $scope.filter.range.offset = 30;
        $scope.filter.range.unit = 'Minutes';
        break;
      case '1h' :
        $scope.filter.range.offset = 1;
        $scope.filter.range.unit = 'Hours';
        break;
      case '4h' :
        $scope.filter.range.offset = 4;
        $scope.filter.range.unit = 'Hours';
        break;
      case '8h' :
        $scope.filter.range.offset = 8;
        $scope.filter.range.unit = 'Hours';
        break;
      case '12h' :
        $scope.filter.range.offset = 12;
        $scope.filter.range.unit = 'Hours';
        break;
      case '1d' :
        $scope.filter.range.offset = 1;
        $scope.filter.range.unit = 'Days';
        break;
      case '7d' :
        $scope.filter.range.offset = 7;
        $scope.filter.range.unit = 'Days';
        break;
      case '30d' :
        $scope.filter.range.offset = 30;
        $scope.filter.range.unit = 'Days';
        break;
      default :
        console.debug("[Dashboard] Unsupported Range: " + range);
      }
      $scope.filter.range.datetime = moment(new Date()).format('YYYY-MM-DD HH:mm:ss');
      $scope.filter.range.direction = 'Before';

      updateDashboard();
    };

    // this is here because the datetimepicker seems unable to handle standard ng-model binding.  See
    // https://stackoverflow.com/questions/19316937/how-to-bind-bootstrap-datepicker-element-with-angularjs-ng-model
    $("#datetime").on("dp.change", function (e) {
      $scope.filter.range.datetime = $("#currentdatetime").val(); // pure magic
    });
  }
]);
