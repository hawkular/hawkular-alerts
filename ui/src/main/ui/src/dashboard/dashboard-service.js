angular.module('hwk.dashboardModule').service('hwk.dashboardService', ['$resource',
  function ($resource) {
    'use strict';

    // [lponce] TODO Enable this for testing
    // var testHost = 'http://localhost:8080';
    // var baseUrl = testHost + '/hawkular/alerts';

    var baseUrl = '/hawkular/alerts';

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
      return $resource(baseUrl + '/events', {}, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };
  }
]);
