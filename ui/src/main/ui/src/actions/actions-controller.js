angular.module('hwk.actionsModule').controller( 'hwk.actionsController', ['$scope', '$rootScope', '$q', 'hwk.actionsService',
  function ($scope, $rootScope, $q, actionsService) {
    'use strict';

    console.log("[Actions] Start: " + new Date());
    console.log("[Actions] $rootScope.selectedTenant " + $rootScope.selectedTenant);

    var selectedTenant = $rootScope.selectedTenant;

    /*
      [lponce] Note that there is an endpoint /export which can be used to save calls
      but for demo purposes I will left here the pattern about how to chain promises and parse results
      we can change this in future iterations.

      Note: /export return all and doesn't permit to select which elements you want to fetch, so this pattern
      will be needed sooner than later
    */
    var updateActions = function () {
      console.log("[Actions] Updating data for " + selectedTenant + " at " + new Date());

      var promise1 = actionsService.Actions(selectedTenant).get();

      $q.all([promise1.$promise]).then(function (result) {
        var updatedActions = result[0];
        var promises = [];
        for (var actionPlugin in updatedActions) {
          if (updatedActions.hasOwnProperty(actionPlugin)) {
            var actionDefinitions = updatedActions[actionPlugin];
            for (var i = 0; i < actionDefinitions.length; i++) {
              var promiseX = actionsService.ActionDefinition(selectedTenant, actionPlugin, actionDefinitions[i]).get();
              promises.push(promiseX.$promise);
            }
          }
        }
        $q.all(promises).then(function (resultActionDefinitions) {
          $scope.actions = [];
          for (var i = 0; i < resultActionDefinitions.length; i++) {
            $scope.actions.push(resultActionDefinitions[i]);
          }
          console.log(resultActionDefinitions);
          console.log($scope.actions);
        });
      });
    };

    var watchRef = $rootScope.$watch('selectedTenant', function (newTenant, oldTenant) {
      selectedTenant = newTenant;
      console.log('[Actions] New Tenant: ' + selectedTenant);
      if (selectedTenant && selectedTenant.length > 0) {
        updateActions();
      }
    });

    if (selectedTenant && selectedTenant.length > 0) {
      updateActions();
    }

    // When dashboard controler is destroyed, the $interval and $watch are removed.
    $scope.$on('$destroy', function() {
      watchRef();
    });
  }
]);