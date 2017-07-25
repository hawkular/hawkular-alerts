angular.module('hwk.alertsModule').controller( 'hwk.alertsController', ['$scope', '$rootScope', '$resource', '$window', '$interval', '$q', 'hwk.alertsService',
  function ($scope, $rootScope, $resource, $window, $interval, $q, alertsService) {
    'use strict';

    var selectedTenant = $rootScope.selectedTenant;

    console.log("[Alerts] $rootScope.selectedTenant " + selectedTenant);

    var watchRef = $rootScope.$watch('selectedTenant', function (newTenant, oldTenant) {
      selectedTenant = newTenant;
      console.log('[Alerts] New Tenant: ' + selectedTenant);
      if (selectedTenant && selectedTenant.length > 0) {
        updateAlerts();
      }
    });

    $scope.$on('$destroy', function() {
      watchRef();
    });

    var updateAlerts = function () {
      if (selectedTenant && selectedTenant.length > 0) {
        var alertsPromise = alertsService.Alert($rootScope.selectedTenant).query();
        $q.all([alertsPromise.$promise]).then(function(results) {
          $scope.alertsList = results[0];
          console.log("[Alerts] Alerts query returned [" + $scope.alertsList.length + "] alerts");
        }, function(err) {
          console.log("[Alerts] Alerts query failed: " + err);
        });
      }
    };

  }
]);
