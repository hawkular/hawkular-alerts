angular.module('hwk.triggersModule').service('hwk.triggersService', ['$resource', '$rootScope',
  function ($resource, $rootScope) {
    'use strict';

    this.Trigger = function (tenantId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/triggers', {}, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.FullTrigger = function (tenantId, triggerId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/triggers/trigger/:triggerId', {
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