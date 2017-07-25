
var alertsModule = angular.module( 'hwk.alertsModule', []);

alertsModule.directive('repeatDone', function($timeout) {
  'use strict';

  return {
    restrict: 'A',
    link: function (scope, element, attr) {
      if (scope.$last === true) {
        $timeout(function () {
          scope.$emit('ngRepeatDone');
        }, 0);
      }
    }
  };
});