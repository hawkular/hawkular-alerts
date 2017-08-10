angular.module('hwk.alertsModule').controller( 'hwk.alertsController', ['$scope', '$rootScope', '$resource', '$location', '$window', '$interval', '$q', '$modal', 'hwk.alertsService', 'hwk.filterService',
  function ($scope, $rootScope, $resource, $location, $window, $interval, $q, $modal, alertsService, filterService) {
    'use strict';

    $scope.filter = {
      'range': filterService.rangeFilter,
      'alert': filterService.alertFilter,
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

    console.log("[Alerts] $rootScope.selectedTenant " + selectedTenant);

    var watchRef = $rootScope.$watch('selectedTenant', function (newTenant, oldTenant) {
      selectedTenant = newTenant;
      console.log('[Alerts] New Tenant: ' + selectedTenant);
      if (selectedTenant && selectedTenant.length > 0) {
        updateAlerts();
      }
    });

    $scope.ackAlert = function (alertId) {
      $scope.lifecycleModal.title = 'Acknowledge Alert';
      $scope.lifecycleModal.user = null;
      $scope.lifecycleModal.notes = null;
      $scope.lifecycleModal.readOnly = false;
      $scope.lifecycleModal.state = "Acknowledge";
      changeState(alertId);
    };

    $scope.resolveAlert = function (alertId) {
      $scope.lifecycleModal.title = 'Resolve Alert';
      $scope.lifecycleModal.user = null;
      $scope.lifecycleModal.notes = null;
      $scope.lifecycleModal.readOnly = false;
      $scope.lifecycleModal.state = "Resolve";
      changeState(alertId);
    };

    var changeState = function(alertId) {
      var modalInstance = $modal.open({
        templateUrl: 'lifecycleModal.html',
        backdrop: false, // keep modal up if someone clicks outside of the modal
        controller: function ($scope, $modalInstance, $log, lifecycleModal) {
          $scope.lifecycleModal = lifecycleModal;
          $scope.save = function () {

            $modalInstance.dismiss(lifecycleModal.state);
            var promise1;
            if ( lifecycleModal.state === 'Acknowledge') {
              promise1 = alertsService.Ack(selectedTenant, alertId, lifecycleModal.user, lifecycleModal.notes).update();
            } else {
              promise1 = alertsService.Resolve(selectedTenant, alertId, lifecycleModal.user, lifecycleModal.notes).update();
            }

            $q.all([promise1.$promise]).then(function (result) {
              console.log("Result[" + lifecycleModal.state + "]=" + result);
              updateAlerts();
            });
          };
          $scope.cancel = function () {
            $modalInstance.dismiss('cancel');
          };
          $scope.isValid = function () {
            return (
              $scope.lifecycleModal.notes
              && $scope.lifecycleModal.notes.length > 0
              && $scope.lifecycleModal.user
              && $scope.lifecycleModal.user.length > 0
            );
          };
        },
        resolve: {
          lifecycleModal: function () {
            return $scope.lifecycleModal;
          }
        }
      });
    };

    $scope.reopenAlert = function (id) {
      alert("TODO REOPEN: " + id);
    };

    $scope.deleteAlert = function (alertId) {
      var alertsCriteria = {
        alertIds: alertId
      };
      var alertsPromise = alertsService.Purge($rootScope.selectedTenant, alertsCriteria).update();
      $q.all([alertsPromise.$promise]).then(function(results) {
        updateAlerts();
      }, function(err) {
        console.log("[Alerts] deleteAlert(" + alertId + ") failed: " + err);
      });
    };

    var updateAlerts = function () {
      if (selectedTenant && selectedTenant.length > 0) {
        var alertsCriteria = {
          'thin': false   // TODO: we need to add this, we should not initially fetch fat alerts
        };
        if ( $scope.filter.range.datetime ) {
          var offset = $scope.filter.range.offset;
          var start;
          var end;
          switch ( $scope.filter.range.unit ) {
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
            console.log("Unsupported unit: " + $scope.filter.range.unit);
          }
          switch ( $scope.filter.range.direction ) {
          case 'After' :
            start = new Date($scope.filter.range.datetime).getTime();
            end = start + offset;
            break;
          case 'Before':
            end = new Date($scope.filter.range.datetime).getTime();
            start = end - offset;
            break;
          default :
            console.log("Unsupported direction: " + $scope.filter.range.direction);
          }
          alertsCriteria.startTime = start;
          alertsCriteria.endTime = end;
        }
        if ( $scope.filter.tag.tagQuery && $scope.filter.tag.tagQuery.length > 0 ) {
          alertsCriteria.tags = $scope.filter.tag.tagQuery;
        }
        if ( $scope.filter.alert.severity && $scope.filter.alert.severity !== 'All Severity' ) {
          alertsCriteria.severities = $scope.filter.alert.severity.toUpperCase();
        }
        if ( $scope.filter.alert.status && $scope.filter.alert.status !== 'All Status' ) {
          alertsCriteria.statuses = $scope.filter.alert.status.toUpperCase();
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

    $scope.$on('ngRepeatDoneAlerts', function(ngRepeatDoneEvent) {
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
        console.log("Unsupported Range: " + range);
      }
      $scope.filter.range.datetime = moment(new Date()).format('YYYY-MM-DD HH:mm:ss');
      $scope.filter.range.direction = 'Before';

      updateAlerts();
    };

    // this is here because the datetimepicker seems unable to handle standard ng-model binding.  See
    // https://stackoverflow.com/questions/19316937/how-to-bind-bootstrap-datepicker-element-with-angularjs-ng-model
    $("#datetime").on("dp.change", function (e) {
      $scope.filter.range.datetime = $("#currentdatetime").val(); // pure magic
    });
  }
]);
