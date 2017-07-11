angular.module('hwk.appModule').controller( 'hwk.appController', ['$scope', '$rootScope', '$resource',
  function ($scope, $rootScope, $resource ) {
    'use strict';

    // Hawkular Alerting Navigation
    // [lponce] Perhaps in the future is better to have it in the template itself
    $scope.navigationItems = [
      {
        title: "Dashboard",
        iconClass: "fa fa-dashboard",
        href: "#/dashboard"
      },
      {
        title: "Triggers",
        iconClass: "fa fa-flash",
        href: "#/triggers"
      },
      {
        title: "Actions",
        iconClass: "fa fa-bell-o",
        href: "#/actions"
      }
    ];

    $scope.newTenant = {};

    // [lponce] comment if you don't want a pre-filled tenant
    $scope.newTenant.tenant = 'my-organization';

    $scope.updateTenant = function () {
      $rootScope.selectedTenant = $scope.newTenant.tenant;
    };
  }
]);
