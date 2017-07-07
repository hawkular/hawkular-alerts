angular.module('hwk.triggersModule').service('hwk.triggersService', ['$resource',
  function ($resource) {
    'use strict';

    var host = '';

    // [lponce] TODO Enable this for testing
    // host = 'http://localhost:8080';

    var baseUrl = host + '/hawkular/alerts';

    this.Trigger = function (tenantId) {
      return $resource(baseUrl + '/triggers', {}, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.FullTrigger = function (tenantId, triggerId) {
      return $resource(baseUrl + '/triggers/trigger/:triggerId', {
        triggerId: triggerId
      }, {
        get: {
          method: 'GET',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };
  }
]);