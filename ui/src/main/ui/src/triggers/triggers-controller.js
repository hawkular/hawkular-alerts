angular.module('hwk.triggersModule')
.filter('startFrom', function () {
  'use strict';

  return function(input, start) {
    if (input) {
      start = +start;
      return input.slice(start);
    }
    return [];
  };
})
.controller( 'hwk.triggersController', ['$scope', '$rootScope', '$q', '$modal', 'hwk.triggersService',
  function ($scope, $rootScope, $q, $modal, triggersService) {
    'use strict';

    console.log("[Triggers] Start: " + new Date());
    console.log("[Triggers] $rootScope.selectedTenant " + $rootScope.selectedTenant);

    $scope.pageSize = 5;
    $scope.triggers = [];
    $scope.filterText = null;
    $scope.jsonModal = {
      text: null,
      title: null,
      placeholder: null,
      readOnly: false
    };

    var selectedTenant = $rootScope.selectedTenant;

    var updateTriggers = function () {
      var promise1;
      if ( $scope.filterText ) {
        console.log("[Triggers] Updating triggers for " + selectedTenant + " at " + new Date() + " with filter=" + $scope.filterText);
        promise1 = triggersService.Query(selectedTenant, $scope.filterText).query();
      } else {
        console.log("[Triggers] Updating triggers for " + selectedTenant + " at " + new Date());
        promise1 = triggersService.Trigger(selectedTenant).query();
      }

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
          $scope.numTotalItems = $scope.triggers.length;
          $scope.maxPages = ( $scope.numTotalItems % $scope.pageSize > 0 ) ? (( $scope.numTotalItems / $scope.pageSize ) | 0) + 1 : ( $scope.numTotalItems / $scope.pageSize ) | 0;
          $scope.pageNumber = 1;
          $scope.fromItem = ($scope.pageNumber - 1) * $scope.pageSize;
          $scope.toItem = ($scope.pageNumber * $scope.pageSize) < $scope.numTotalItems ? ($scope.pageNumber * $scope.pageSize) : $scope.numTotalItems;
        });

      });
    };

    var pageSizeRef = $scope.$watch('pageSize', function (newPageSize, oldPageSize) {
      if (newPageSize) {
        $scope.pageNumber = 1;
        $scope.maxPages = ( $scope.numTotalItems % newPageSize > 0 ) ? (( $scope.numTotalItems / newPageSize ) | 0) + 1 : ( $scope.numTotalItems / newPageSize ) | 0;
      }
    });

    var pageNumberRef = $scope.$watch('pageNumber', function (newPageNumber, oldPageNumber) {
      if (newPageNumber) {
        $scope.fromItem = (newPageNumber - 1) * $scope.pageSize;
        $scope.toItem = (newPageNumber * $scope.pageSize) < $scope.numTotalItems ? (newPageNumber * $scope.pageSize) : $scope.numTotalItems;
      }
    });

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

    // When dashboard controller is destroyed, the $interval and $watch are removed.
    $scope.$on('$destroy', function() {
      watchRef();
      pageSizeRef();
      pageNumberRef();
    });

    $scope.updateFilter = function() {
      if (this.newFilter.text) {
        $scope.filterText = this.newFilter.text;
      } else {
        $scope.filterText = null;
      }
      updateTriggers();
    };

    $scope.newTriggerModal = function() {
      $scope.jsonModal.title = 'New Trigger';
      $scope.jsonModal.placeholder = 'Enter New Full Trigger JSON Here...';
      $scope.jsonModal.json = null;
      $scope.jsonModal.readOnly = false;

      var modalInstance = $modal.open({
        templateUrl: 'jsonModal.html',
        backdrop: false, // keep modal up if someone clicks outside of the modal
        controller: function ($scope, $modalInstance, $log, jsonModal) {
          $scope.jsonModal = jsonModal;
          $scope.save = function () {
            $modalInstance.dismiss('save');
            var promise1 = triggersService.NewTrigger(selectedTenant).save($scope.jsonModal.json);

            $q.all([promise1.$promise]).then(function (result) {
              console.log("newTriggerResult=" + result);
              updateTriggers();
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

    $scope.viewTriggerModal = function(fullTrigger) {
      $scope.jsonModal.title = 'View Trigger';
      $scope.jsonModal.placeholder = 'Full Trigger JSON...';
      $scope.jsonModal.json = angular.toJson(fullTrigger,true);
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

    $scope.editTriggerModal = function(triggerId, fullTrigger) {
      $scope.jsonModal.title = 'Edit Trigger';
      $scope.jsonModal.placeholder = 'Enter Updated Full Trigger JSON Here...';
      $scope.jsonModal.json = angular.toJson(fullTrigger,true);
      $scope.jsonModal.readOnly = false;

      var modalInstance = $modal.open({
        templateUrl: 'jsonModal.html',
        backdrop: false, // keep modal up if someone clicks outside of the modal
        controller: function ($scope, $modalInstance, $log, jsonModal) {
          $scope.jsonModal = jsonModal;
          $scope.save = function () {
            $modalInstance.dismiss('save');
            var promise1 = triggersService.UpdateTrigger(selectedTenant, triggerId).update($scope.jsonModal.json);

            $q.all([promise1.$promise]).then(function (result) {
              console.log("updateTriggerResult=" + result);
              updateTriggers();
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

    $scope.deleteTrigger = function(triggerId) {
      if (triggerId) {
        var promise1 = triggersService.RemoveTrigger(selectedTenant, triggerId).remove();

        $q.all([promise1.$promise]).then(function (result) {
          console.log("deleteTriggerResult=" + result);
          updateTriggers();
        });
      }
    };

    $scope.enableTriggers = function(triggerIds, enabled) {
      if (triggerIds) {
        var promise1 = triggersService.EnableTriggers(selectedTenant, triggerIds, enabled).update();

        $q.all([promise1.$promise]).then(function (result) {
          console.log("enableTriggersResult=" + result);
          updateTriggers();
        });
      }
    };

  }
]);