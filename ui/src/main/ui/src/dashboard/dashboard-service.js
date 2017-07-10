angular.module('hwk.dashboardModule').service('hwk.dashboardService', ['$resource',
  function ($resource) {
    'use strict';

    var host = '';

    // [lponce] TODO Enable this for testing
    host = 'http://localhost:8080';

    var baseUrl = host + '/hawkular/alerts';

    this.Alert = function (tenantId) {
      return $resource(baseUrl, {}, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Event = function (tenantId) {
      return $resource(baseUrl + '/events', {eventType: 'EVENT'}, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };
  }
]);
