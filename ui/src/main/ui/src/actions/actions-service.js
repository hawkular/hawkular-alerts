angular.module('hwk.actionsModule').service('hwk.actionsService', ['$resource',
  function ($resource) {
    'use strict';

    var host = '';

    // [lponce] TODO Enable this for testing
    // host = 'http://localhost:8080';

    var baseUrl = host + '/hawkular/alerts';

    this.Actions = function (tenantId) {
      return $resource(baseUrl + '/actions', {}, {
        get: {
          method: 'GET',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.ActionDefinition = function (tenantId, actionPlugin, actionId) {
      return $resource(baseUrl + '/actions/:actionPlugin/:actionId', {
        actionPlugin: actionPlugin,
        actionId: actionId
      }, {
        get: {
          method: 'GET',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

  }
]);