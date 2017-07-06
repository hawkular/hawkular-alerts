angular.module('hwk.triggersModule').controller( 'hwk.triggersController', ['$scope', '$rootScope', '$q', 'hwk.triggersService',
  function ($scope, $rootScope, $q, triggersService) {
    'use strict';

    console.log("[Triggers] Start: " + new Date());
    console.log("[Triggers] $rootScope.selectedTenant " + $rootScope.selectedTenant);

    var selectedTenant = $rootScope.selectedTenant;

    var updateTriggers = function () {
      console.log("[Triggers] Updating data for " + selectedTenant + " at " + new Date());

      var promise1 = triggersService.Trigger(selectedTenant).query();

      $q.all([promise1.$promise]).then(function (result) {
        var updatedTriggers = result[0];
        var promises = [];

        for (var i = 0; i < updatedTriggers.length; i++) {
          var promiseX = triggersService.FullTrigger(selectedTenant, updatedTriggers[i].id).get();
          promises.push(promiseX.$promise);
        }

        $q.all(promises).then(function (resultFullTriggers) {
          $scope.triggers = [];
          for (var i = 0; i < resultFullTriggers.length; i++) {
            $scope.triggers.push(resultFullTriggers[i]);
          }
          console.log(resultFullTriggers);
        });

      });
    };

    var watchRef = $rootScope.$watch('selectedTenant', function (newTenant, oldTenant) {
      selectedTenant = newTenant;
      console.log('[Triggers] New Tenant: ' + selectedTenant);
      if (selectedTenant && selectedTenant.length > 0) {
        updateTriggers();
      }
    });

    if (selectedTenant && selectedTenant.length > 0) {
      updateTriggers();
    }

    // When dashboard controler is destroyed, the $interval and $watch are removed.
    $scope.$on('$destroy', function() {
      watchRef();
    });
  }
]);