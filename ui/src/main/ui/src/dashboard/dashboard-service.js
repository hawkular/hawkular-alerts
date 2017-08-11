angular.module('hwk.dashboardModule').service('hwk.dashboardService', ['$resource', '$rootScope',
  function ($resource, $rootScope) {
    'use strict';

    this.Alert = function (tenantId, alertsCriteria) {
      return $resource($rootScope.appConfig.server.baseUrl, alertsCriteria, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Event = function (tenantId, eventsCriteria) {
      return $resource($rootScope.appConfig.server.baseUrl + '/events', eventsCriteria, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Action = function (tenantId, eventId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/actions/history', {eventIds: eventId}, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };
  }
]);
