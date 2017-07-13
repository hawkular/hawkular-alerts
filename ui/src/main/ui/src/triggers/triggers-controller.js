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
.controller( 'hwk.triggersController', ['$scope', '$rootScope', '$q', 'hwk.triggersService',
  function ($scope, $rootScope, $q, triggersService) {
    'use strict';

    console.log("[Triggers] Start: " + new Date());
    console.log("[Triggers] $rootScope.selectedTenant " + $rootScope.selectedTenant);

    $scope.pageSize = 5;
    $scope.triggers = [];
    $scope.filterText = null;

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
        console.log("FOUND=" + updatedTriggers.length);
        for (var i = 0; i < updatedTriggers.length; i++) {
          var promiseX = triggersService.FullTrigger(selectedTenant, updatedTriggers[i].id).get();
          promises.push(promiseX.$promise);
        }

        $q.all(promises).then(function (resultFullTriggers) {
          $scope.triggers = [];
          console.log("FOUND FULL=" + resultFullTriggers.length);
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
  }
]);