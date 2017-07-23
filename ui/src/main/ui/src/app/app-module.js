angular.module ('hwk.appModule', [
  'ngResource',
  'ngRoute',
  'ui.bootstrap',
  'pascalprecht.translate',
  'patternfly',
  'patternfly.toolbars',
  'patternfly.charts',
  'hwk.dashboardModule',
  'hwk.triggersModule',
  'hwk.actionsModule',
  'hwk.alertsModule'
]).config(['$routeProvider', '$translateProvider',
  function ($routeProvider, $translateProvider) {
    'use strict';

    $routeProvider
      .when('/', {
        redirectTo: '/dashboard'
      })
      .when('/dashboard', {
        templateUrl: 'src/dashboard/dashboard.html'
      })
      .when('/triggers', {
        templateUrl: 'src/triggers/triggers.html'
      })
      .when('/actions', {
        templateUrl: 'src/actions/actions.html'
      })
      .when('/alerts', {
        templateUrl: 'src/alerts/alerts.html'
      })

      // Default
      .otherwise({
      });

    $translateProvider.translations('default', 'en');
    $translateProvider.preferredLanguage('default');
  }
]);
