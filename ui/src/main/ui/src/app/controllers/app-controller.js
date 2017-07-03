angular.module('hwk.appModule').controller( 'hwk.appController', ['$scope', '$rootScope', '$resource',
  function ($scope, $rootScope, $resource ) {
    'use strict';

    $scope.username = 'Administrator';

    //Navigation should be loaded from a service
    $scope.navigationItems = [];
    $scope.navigationItems.push({"title":"Dashboard", "iconClass": "fa fa-dashboard","href":"#/dashboard"});
    $scope.navigationItems.push({"title":"Triggers", "iconClass": "fa fa-flash","href":"#/triggers"});
    $scope.navigationItems.push({"title":"Actions", "iconClass": "fa fa-bell-o","href":"#/actions"});
  }
]);
