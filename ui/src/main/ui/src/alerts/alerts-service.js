angular.module('hwk.alertsModule').service('hwk.alertsService', ['$resource', '$rootScope',
  function ($resource, $rootScope) {
    'use strict';

    this.Alert = function (tenantId, alertId) {
      return $resource($rootScope.appConfig.server.baseUrl + "/alert/:" + alertId, {
        alertId: alertId
      }, {
        query: {
          method: 'GET',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Query = function (tenantId, alertsCriteria) {
      return $resource($rootScope.appConfig.server.baseUrl, alertsCriteria, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };
  }
]);
