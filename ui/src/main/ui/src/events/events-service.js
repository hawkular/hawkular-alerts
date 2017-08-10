angular.module('hwk.eventsModule').service('hwk.eventsService', ['$resource', '$rootScope',
  function ($resource, $rootScope) {
    'use strict';

    this.Event = function (tenantId, eventId) {
      return $resource($rootScope.appConfig.server.baseUrl + "/events/event/:" + eventId, {
        eventId: eventId
      }, {
        query: {
          method: 'GET',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Query = function (tenantId, eventsCriteria) {
      return $resource($rootScope.appConfig.server.baseUrl + "/events", eventsCriteria, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Purge = function (tenantId, eventsCriteria) {
      return $resource($rootScope.appConfig.server.baseUrl + "/events/delete", eventsCriteria, {
        update: {
          method: 'PUT',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

  }
]);
