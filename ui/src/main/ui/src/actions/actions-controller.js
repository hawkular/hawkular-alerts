angular.module('hwk.actionsModule')
  .controller( 'hwk.actionsController', ['$scope', '$rootScope', '$q', '$modal', 'hwk.actionsService',
  function ($scope, $rootScope, $q, $modal, actionsService) {
    'use strict';

    console.log("[Actions] Start: " + new Date());
    console.log("[Actions] $rootScope.selectedTenant " + $rootScope.selectedTenant);

    $scope.plugins = [];
    $scope.actions = [];
    $scope.pluginsFilter = {
      filter: null,
      options: null
    };
    $scope.jsonModal = {
      text: null,
      title: null,
      placeholder: null,
      readOnly: false
    };

    var selectedTenant = $rootScope.selectedTenant;

    var updatePlugins = function () {
      console.log("[Action Plugins] Updating plugins for " + selectedTenant + " at " + new Date());

      // fetch current plugins
      var promise1 = actionsService.Plugins(selectedTenant).get();

      $q.all([promise1.$promise]).then(function (result) {
        $scope.plugins = result[0];
        $scope.plugins.sort();
        $scope.pluginsFilter = {
          options: ["All Plugins"],
          filter: "All Plugins" // set as default
        };
        for (var i = 0; i < $scope.plugins.length; i++) {
          console.log("$scope.plugins[i]=" + $scope.plugins[i]);
          $scope.pluginsFilter.options.push( $scope.plugins[i] );
        }
      });
    };

    /*
      [lponce] Note that there is an endpoint /export which can be used to save calls
      but for demo purposes I will left here the pattern about how to chain promises and parse results
      we can change this in future iterations.

      Note: /export return all and doesn't permit to select which elements you want to fetch, so this pattern
      will be needed sooner than later
    */
    var updateActionDefinitions = function () {
      if ( $scope.pluginsFilter.filter && "All Plugins" !== $scope.pluginsFilter.filter ) {
        updateFilteredActionDefinitions($scope.pluginsFilter.filter);
        return;
      }

      console.log("[Action Plugins] Updating action defs for " + selectedTenant + " at " + new Date());

      // fetch map of actionPlugin->actionIds, for the tenant
      var promiseMap = actionsService.ActionPluginMap(selectedTenant).get();

      $q.all([promiseMap.$promise]).then(function (result) {
        var actionDefinitionMap = result[0];
        var promises = [];
        for (var actionPlugin in actionDefinitionMap) {
          if (actionDefinitionMap.hasOwnProperty(actionPlugin)) {
            var actionDefinitionIds = actionDefinitionMap[actionPlugin];
            for (var i = 0; i < actionDefinitionIds.length; i++) {
              var promiseX = actionsService.ActionDefinition(selectedTenant, actionPlugin, actionDefinitionIds[i]).get();
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

    var updateFilteredActionDefinitions = function (pluginFilter) {
      console.log("[Action Plugins] Updating action defs for " + selectedTenant + " at " + new Date()  + " with filter=" + pluginFilter);

      // fetch the action def ids for the selected plugin
      var promiseIds = actionsService.ActionPluginActionDefinitionIds(selectedTenant, pluginFilter).get();

      $q.all([promiseIds.$promise]).then(function (result) {
        var actionDefinitionIds = result[0];
        var promises = [];
        for (var i = 0; i < actionDefinitionIds.length; i++) {
          var promiseX = actionsService.ActionDefinition(selectedTenant, pluginFilter, actionDefinitionIds[i]).get();
          promises.push(promiseX.$promise);
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
        updatePlugins();
        updateActionDefinitions();
      }
    });

    $scope.newActionDefinitionModal = function() {
      $scope.jsonModal.title = 'New Action Definition';
      $scope.jsonModal.placeholder = 'Enter New Action Definition JSON Here...';
      $scope.jsonModal.json = null;
      $scope.jsonModal.readOnly = false;

      var modalInstance = $modal.open({
        templateUrl: 'jsonModal.html',
        backdrop: false, // keep modal up if someone clicks outside of the modal
        controller: function ($scope, $modalInstance, $log, jsonModal) {
          $scope.jsonModal = jsonModal;
          $scope.save = function () {
            $modalInstance.dismiss('save');
            var promise1 = actionsService.NewActionDefinition(selectedTenant).save($scope.jsonModal.json);

            $q.all([promise1.$promise]).then(function (result) {
              console.log("newActionDefinitionResult=" + result);
              updateActionDefinitions();
            });
          };
          $scope.cancel = function () {
            $modalInstance.dismiss('cancel');
          };
          $scope.isValid = function () {
            return ($scope.jsonModal.json && $scope.jsonModal.json.length > 0);
          };
        },
        resolve: {
          jsonModal: function () {
            return $scope.jsonModal;
          }
        }
      });
    };

    $scope.viewActionDefinitionModal = function(actionDefinition) {
      $scope.jsonModal.title = 'View Action Definition';
      $scope.jsonModal.placeholder = 'Action Definition JSON...';
      $scope.jsonModal.json = angular.toJson(actionDefinition,true);
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

    $scope.editActionDefinitionModal = function(actionDefinition) {
      $scope.jsonModal.title = 'Edit Action Definition';
      $scope.jsonModal.placeholder = 'Enter Updated Action Definition JSON Here...';
      $scope.jsonModal.json = angular.toJson(actionDefinition,true);
      $scope.jsonModal.readOnly = false;

      var modalInstance = $modal.open({
        templateUrl: 'jsonModal.html',
        backdrop: false, // keep modal up if someone clicks outside of the modal
        controller: function ($scope, $modalInstance, $log, jsonModal) {
          $scope.jsonModal = jsonModal;
          $scope.save = function () {
            $modalInstance.dismiss('save');
            var promise1 = actionsService.UpdateActionDefinition(selectedTenant).update($scope.jsonModal.json);

            $q.all([promise1.$promise]).then(function (result) {
              console.log("updateActionDefinitionResult=" + result);
              updateActionDefinitions();
            });
          };
          $scope.cancel = function () {
            $modalInstance.dismiss('cancel');
          };
          $scope.isValid = function () {
            return ($scope.jsonModal.json && $scope.jsonModal.json.length > 0);
          };
        },
        resolve: {
          jsonModal: function () {
            return $scope.jsonModal;
          }
        }
      });
    };

    $scope.deleteActionDefinition = function(actionPlugin, actionId) {
      if (actionPlugin && actionId) {
        var promise1 = actionsService.RemoveActionDefinition(selectedTenant, actionPlugin, actionId).remove();

        $q.all([promise1.$promise]).then(function (result) {
          console.log("deleteActionDefinitionResult=" + result);
          updateActionDefinitions();
        });
      }
    };

    $scope.updateFilter = function() {
      updateActionDefinitions();
    };

    // When dashboard controller is destroyed, the $interval and $watch are removed.
    $scope.$on('$destroy', function() {
      watchRef();
    });
  }
]);