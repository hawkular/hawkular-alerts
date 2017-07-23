angular.module('hwk.alertsModule').service('hwk.alertsService', ['$resource', '$rootScope',
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
  }
]);
