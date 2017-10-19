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
angular.module('hwk.actionsModule')
  .controller( 'hwk.actionsController', ['$scope', '$rootScope', '$q', '$modal', 'hwk.actionsService', 'Notifications',
  function ($scope, $rootScope, $q, $modal, actionsService, Notifications) {
    'use strict';

    console.debug("[Actions] Start: " + new Date());
    console.debug("[Actions] $rootScope.selectedTenant " + $rootScope.selectedTenant);

    var toastError = function (reason) {
      console.debug('[Actions] Backend error ' + new Date());
      console.debug(reason);
      var errorMsg = new Date() + " Status [" + reason.status + "] " + reason.statusText;
      if (reason.status === -1) {
        errorMsg = new Date() + " Hawkular Alerting is not responding. Please review browser console for further details";
      }
      Notifications.error(errorMsg);
    };

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
      console.debug("[Action Plugins] Updating plugins for " + selectedTenant + " at " + new Date());

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
          console.debug("[Action Plugins] $scope.plugins[i]=" + $scope.plugins[i]);
          $scope.pluginsFilter.options.push( $scope.plugins[i] );
        }
      }, toastError);
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

      console.debug("[Action Plugins] Updating action defs for " + selectedTenant + " at " + new Date());

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
          console.debug(resultActionDefinitions);
          console.debug($scope.actions);
        }, toastError);
      }, toastError);
    };

    var updateFilteredActionDefinitions = function (pluginFilter) {
      console.debug("[Action Plugins] Updating action defs for " + selectedTenant + " at " + new Date()  + " with filter=" + pluginFilter);

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
          console.debug(resultActionDefinitions);
          console.debug($scope.actions);
        }, toastError);
      }, toastError);
    };

    var watchRef = $rootScope.$watch('selectedTenant', function (newTenant, oldTenant) {
      selectedTenant = newTenant;
      console.debug('[Actions] New Tenant: ' + selectedTenant);
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
              console.debug("[Actions] newActionDefinitionResult=" + result);
              updateActionDefinitions();
            }, toastError);
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
              console.debug("[Actions] updateActionDefinitionResult=" + result);
              updateActionDefinitions();
            }, toastError);
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
          console.debug("[Actions] deleteActionDefinitionResult=" + result);
          updateActionDefinitions();
        }, toastError);
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