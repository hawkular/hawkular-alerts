angular.module('hwk.actionsModule').service('hwk.actionsService', ['$resource',
  function ($resource) {
    'use strict';

    // [lponce] TODO Enable this for testing
    // var testHost = 'http://localhost:8080';
    // var baseUrl = testHost + '/hawkular/alerts';

    var baseUrl = '/hawkular/alerts';

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