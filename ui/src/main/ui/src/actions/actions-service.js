angular.module('hwk.actionsModule').service('hwk.actionsService', ['$resource', '$rootScope',
  function ($resource, $rootScope) {
    'use strict';

    this.Actions = function (tenantId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/actions', {}, {
        get: {
          method: 'GET',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.ActionDefinition = function (tenantId, actionPlugin, actionId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/actions/:actionPlugin/:actionId', {
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