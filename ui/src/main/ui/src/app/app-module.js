angular.module ('hwk.appModule', [
  'ngResource',
  'ngRoute',
  'ui.bootstrap',
  'pascalprecht.translate',
  'patternfly',
  'patternfly.toolbars',
  'patternfly.charts',
  'patternfly.notification',
  'hwk.dashboardModule',
  'hwk.triggersModule',
  'hwk.actionsModule',
  'hwk.alertsModule',
  'hwk.eventsModule'
]).config(['$routeProvider', '$translateProvider', 'NotificationsProvider',
  function ($routeProvider, $translateProvider, NotificationsProvider) {
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
      .when('/events', {
        templateUrl: 'src/events/events.html'
      })

      // Default
      .otherwise({
      });

    $translateProvider.translations('default', 'en');
    $translateProvider.preferredLanguage('default');

    NotificationsProvider.setDelay(10000).setVerbose(false).setPersist({'error': true, 'httpError': true, 'warn': true});
  }
]);
