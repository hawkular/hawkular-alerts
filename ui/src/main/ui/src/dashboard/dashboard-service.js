angular.module('hwk.dashboardModule').service('hwk.dashboardService', ['$resource', '$rootScope',
  function ($resource, $rootScope) {
    'use strict';

    this.Alert = function (tenantId) {
      return $resource($rootScope.appConfig.server.baseUrl, {}, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Event = function (tenantId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/events', {eventType: 'EVENT'}, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Action = function (tenantId, eventId) {
      return $resource(baseUrl + '/actions/history', {eventIds: eventId}, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };
  }
]);
