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
angular.module('hwk.eventsModule').controller( 'hwk.eventsController', ['$scope', '$rootScope', '$resource', '$location', '$window', '$interval', '$q', '$modal', 'hwk.eventsService', 'hwk.filterService', 'Notifications',
  function ($scope, $rootScope, $resource, $location, $window, $interval, $q, $modal, eventsService, filterService, Notifications) {
    'use strict';

    $scope.filter = {
      'range': filterService.rangeFilter,
      'event': filterService.eventFilter
    };

    $scope.jsonModal = {
      text: null,
      title: null,
      placeholder: null,
      readOnly: false
    };

    var selectedTenant = $rootScope.selectedTenant;

    console.debug("[Events] $rootScope.selectedTenant " + selectedTenant);

    var toastError = function (reason) {
      console.debug('[Events] Backend error ' + new Date());
      console.debug(reason);
      var errorMsg = new Date() + " Status [" + reason.status + "] " + reason.statusText;
      if (reason.status === -1) {
        errorMsg = new Date() + " Hawkular Alerting is not responding. Please review browser console for further details";
      }
      Notifications.error(errorMsg);
    };

    var watchRef = $rootScope.$watch('selectedTenant', function (newTenant, oldTenant) {
      selectedTenant = newTenant;
      console.debug('[Events] New Tenant: ' + selectedTenant);
      if (selectedTenant && selectedTenant.length > 0) {
        updateEvents();
      }
    });

    $scope.viewEvent = function(event) {
      $scope.jsonModal.title = 'View Event';
      $scope.jsonModal.placeholder = 'Event JSON...';
      $scope.jsonModal.json = angular.toJson(event,true);
      $scope.jsonModal.readOnly = true;

      var modalInstance = $modal.open({
        templateUrl: 'jsonModal.html',
        backdrop: false, // keep modal up if someone clicks outside of the modal
        controller: function ($scope, $modalInstance, $log, jsonModal) {
          $scope.jsonModal = jsonModal;
          $scope.cancel = function () {
            $modalInstance.dismiss('cancel');
          };
        },
        resolve: {
          jsonModal: function () {
            return $scope.jsonModal;
          }
        }
      });
    };

    $scope.deleteEvent = function (eventId) {
      var eventsCriteria = {
        eventIds: eventId
      };
      var eventsPromise = eventsService.Purge($rootScope.selectedTenant, eventsCriteria).update();
      $q.all([eventsPromise.$promise]).then(function(results) {
        updateEvents();
      }, toastError);
    };

    var updateEvents = function () {
      if (selectedTenant && selectedTenant.length > 0) {
        var eventsCriteria = {
          'thin': false,   // TODO: we need to add this, we should not initially fetch fat events
          'eventType': 'EVENT'
        };
        if ( $scope.filter.range.datetime ) {
          var range = filterService.getRange();
          eventsCriteria.startTime = range[0];
          eventsCriteria.endTime = range[1];
        }
        if ( $scope.filter.event.tagQuery && $scope.filter.event.tagQuery.length > 0 ) {
          eventsCriteria.tags = $scope.filter.event.tagQuery;
        }

        var eventsPromise = eventsService.Query($rootScope.selectedTenant, eventsCriteria).query();
        $q.all([eventsPromise.$promise]).then(function(results) {
          $scope.eventsList = results[0];
          console.debug("[Events] Events query returned [" + $scope.eventsList.length + "] events");
        }, toastError);
      }
    };

    $scope.$on('ngRepeatDoneEvents', function(ngRepeatDoneEvent) {
      console.debug("[Events] Inside ngRepeatDone " + new Date());

      // row checkbox selection
      $("input[type='checkbox']").change(function (e) {
        if ($(this).is(":checked")) {
          $(this).closest('.list-group-item').addClass("active");
        } else {
          $(this).closest('.list-group-item').removeClass("active");
        }
      });

      // toggle dropdown menu
      $(".list-view-pf-actions").on("show.bs.dropdown", function () {
        var $this = $(this);
        var $dropdown = $this.find(".dropdown");
        var space = $(window).height() - $dropdown[0].getBoundingClientRect().top - $this.find(".dropdown-menu").outerHeight(true);
        $dropdown.toggleClass("dropup", space < 10);
      });

      // compound expansion
      $(".list-view-pf-expand").on("click", function () {
        var $this = $(this);
        var $heading = $(this).parents(".list-group-item");
        var $subPanels = $heading.find(".list-group-item-container");
        var index = $heading.find(".list-view-pf-expand").index(this);

        console.debug("[Events] Debug text: " + $this.text());

        // remove all active status
        $heading.find(".list-view-pf-expand.active").find(".fa-angle-right").removeClass("fa-angle-down")
          .end().removeClass("active")
            .end().removeClass("list-view-pf-expand-active");
        // add active to the clicked item
        $(this).addClass("active")
          .parents(".list-group-item").addClass("list-view-pf-expand-active")
            .end().find(".fa-angle-right").addClass("fa-angle-down");
        // check if it needs to hide
        if ($subPanels.eq(index).hasClass("hidden")) {
          $heading.find(".list-group-item-container:visible").addClass("hidden");
          $subPanels.eq(index).removeClass("hidden");
        } else {
          $subPanels.eq(index).addClass("hidden");
          $heading.find(".list-view-pf-expand.active").find(".fa-angle-right").removeClass("fa-angle-down")
           .end().removeClass("active")
            .end().removeClass("list-view-pf-expand-active");
        }
      });

      // click close button to close the panel
      $(".list-group-item-container .close").on("click", function () {
        var $this = $(this);
        var $panel = $this.parent();

        // close the container and remove the active status
        $panel.addClass("hidden")
          .parent().removeClass("list-view-pf-expand-active")
            .find(".list-view-pf-expand.active").removeClass("active")
              .find(".fa-angle-right").removeClass("fa-angle-down");
      });
    });

    $scope.updateFilter = function() {
      updateEvents();
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
        console.debug("[Events] Unsupported Range: " + range);
      }
      $scope.filter.range.datetime = moment(new Date()).format('YYYY-MM-DD HH:mm:ss');
      $scope.filter.range.direction = 'Before';

      updateEvents();
    };

    // this is here because the datetimepicker seems unable to handle standard ng-model binding.  See
    // https://stackoverflow.com/questions/19316937/how-to-bind-bootstrap-datepicker-element-with-angularjs-ng-model
    $("#datetime").on("dp.change", function (e) {
      $scope.filter.range.datetime = $("#currentdatetime").val(); // pure magic
    });
  }
]);
