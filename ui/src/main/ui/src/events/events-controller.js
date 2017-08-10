angular.module('hwk.eventsModule').controller( 'hwk.eventsController', ['$scope', '$rootScope', '$resource', '$location', '$window', '$interval', '$q', '$modal', 'hwk.eventsService', 'hwk.filterService',
  function ($scope, $rootScope, $resource, $location, $window, $interval, $q, $modal, eventsService, filterService) {
    'use strict';

    $scope.filter = {
      'date': filterService.dateFilter,
      'tag': filterService.tagFilter
    };

    $scope.lifecycleModal = {
      user: null,
      notes: null,
      title: null,
      placeholder: null,
      state: null,
      readOnly: false
    };

    var selectedTenant = $rootScope.selectedTenant;

    console.log("[Events] $rootScope.selectedTenant " + selectedTenant);

    var watchRef = $rootScope.$watch('selectedTenant', function (newTenant, oldTenant) {
      selectedTenant = newTenant;
      console.log('[Events] New Tenant: ' + selectedTenant);
      if (selectedTenant && selectedTenant.length > 0) {
        updateEvents();
      }
    });

    $scope.deleteEvent = function (eventId) {
      var eventsCriteria = {
        eventIds: eventId
      };
      var eventsPromise = eventsService.Purge($rootScope.selectedTenant, eventsCriteria).update();
      $q.all([eventsPromise.$promise]).then(function(results) {
        updateEvents();
      }, function(err) {
        console.log("[Events] deleteEvent(" + eventId + ") failed: " + err);
      });
    };

    var updateEvents = function () {
      if (selectedTenant && selectedTenant.length > 0) {
        var eventsCriteria = {
          'thin': false,   // TODO: we need to add this, we should not initially fetch fat alerts
          'eventType': 'EVENT'
        };
        if ( $scope.filter.date.start ) {
          var startTime = $scope.filter.date.start;
          startTime.setHours(0,0,0,0);
          eventsCriteria.startTime = startTime.getTime();
        }
        if ( $scope.filter.date.end ) {
          var endTime = $scope.filter.date.end;
          endTime.setHours(23,59,59,999);
          eventsCriteria.endTime = endTime.getTime();
        }
        if ( $scope.filter.tag.tagQuery && $scope.filter.tag.tagQuery.length > 0 ) {
          eventsCriteria.tags = $scope.filter.tag.tagQuery;
        }

        var eventsPromise = eventsService.Query($rootScope.selectedTenant, eventsCriteria).query();
        $q.all([eventsPromise.$promise]).then(function(results) {
          $scope.eventsList = results[0];
          console.log("[Events] Events query returned [" + $scope.eventsList.length + "] events");
        }, function(err) {
          console.log("[Events] Events query failed: " + err);
        });
      }
    };

    $scope.$on('ngRepeatDoneEvents', function(ngRepeatDoneEvent) {
      console.log("[Events] Inside ngRepeatDone " + new Date());

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

        console.log("[Events] Debug text: " + $this.text());

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
  }
]);
