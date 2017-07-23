angular.module('hwk.alertsModule').controller( 'hwk.alertsController', ['$scope', '$rootScope', '$resource', '$window', '$interval', '$q', 'hwk.dashboardService',
  function ($scope, $rootScope, $resource, $window, $interval, $q, dashboardService) {
    'use strict';

    console.log("[Alerts] $rootScope.selectedTenant " + $rootScope.selectedTenant);

  }
]);
