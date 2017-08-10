
var eventsModule = angular.module( 'hwk.eventsModule', []);

eventsModule.directive('repeatDone', function($timeout) {
  'use strict';

  return {
    restrict: 'A',
    link: function (scope, element, attr) {
      console.log("[Events] Inside directive " + new Date());
      if (scope.$last === true) {
        console.log("[Events] $last");
        $timeout(function () {
          scope.$emit('ngRepeatDoneEvents');
        }, 0);
      }
    }
  };
});