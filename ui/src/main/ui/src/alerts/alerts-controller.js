angular.module('hwk.alertsModule').controller( 'hwk.alertsController', ['$scope', '$rootScope', '$resource', '$window', '$interval', '$q', 'hwk.alertsService',
  function ($scope, $rootScope, $resource, $window, $interval, $q, alertsService) {
    'use strict';

    var initStart = new Date();
    initStart.setHours(0,0,0,0);
    $scope.filter = {
      start: new Date(),
      end: new Date(),
      severity: 'All Severity',
      severityOptions: ['All Severity', 'Low', 'Medium', 'High', 'Critical'],
      status: 'All Status',
      statusOptions: ['All Status', 'Open', 'Acknowledged', 'Resolved'],
      tagQuery: null
    };

    var selectedTenant = $rootScope.selectedTenant;

    console.log("[Alerts] $rootScope.selectedTenant " + selectedTenant);

    var watchRef = $rootScope.$watch('selectedTenant', function (newTenant, oldTenant) {
      selectedTenant = newTenant;
      console.log('[Alerts] New Tenant: ' + selectedTenant);
      if (selectedTenant && selectedTenant.length > 0) {
        updateAlerts();
      }
    });

    var updateAlerts = function () {
      if (selectedTenant && selectedTenant.length > 0) {
        var alertsCriteria = {
          'thin': false   // TODO: we need to add this, we should not initially fetch fat alerts
        };
        if ( $scope.filter.start ) {
          var startTime = $scope.filter.start;
          startTime.setHours(0,0,0,0);
          alertsCriteria.startTime = startTime.getTime();
        }
        if ( $scope.filter.end ) {
          var endTime = $scope.filter.end;
          endTime.setHours(23,59,59,999);
          alertsCriteria.endTime = endTime.getTime();
        }
        if ( $scope.filter.tagQuery && $scope.filter.tagQuery.length > 0 ) {
          alertsCriteria.tags = $scope.filter.tagQuery;
        }
        if ( $scope.filter.severity && $scope.filter.severity !== 'All Severity' ) {
          alertsCriteria.severities = $scope.filter.severity.toUpperCase();
        }
        if ( $scope.filter.status && $scope.filter.status !== 'All Status' ) {
          alertsCriteria.statuses = $scope.filter.status.toUpperCase();
        }

        var alertsPromise = alertsService.Query($rootScope.selectedTenant, alertsCriteria).query();
        $q.all([alertsPromise.$promise]).then(function(results) {
          $scope.alertsList = results[0];
          console.log("[Alerts] Alerts query returned [" + $scope.alertsList.length + "] alerts");
        }, function(err) {
          console.log("[Alerts] Alerts query failed: " + err);
        });
      }
    };

    $scope.$on('ngRepeatDone', function(ngRepeatDoneEvent) {
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
      updateAlerts();
    };

  }
]);
