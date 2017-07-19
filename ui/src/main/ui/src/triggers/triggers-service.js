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
      return $resource($rootScope.appConfig.server.baseUrl + '/triggers/trigger/:triggerId', {triggerId: triggerId}, {
        get: {
          method: 'GET',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Query = function (tenantId, tagQuery) {
      // TODO: This should change to tagQuery when it is supported on TriggersCriteria
      return $resource($rootScope.appConfig.server.baseUrl + '/triggers', {tags: tagQuery}, {
        query: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.NewTrigger = function (tenantId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/triggers/trigger', {}, {
        save: {
          method: 'POST',
          headers: {'Hawkular-Tenant': tenantId, 'Content-Type': 'application/json'}
        },
      });
    };

    this.UpdateTrigger = function (tenantId, triggerId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/triggers/:triggerId', {triggerId: triggerId}, {
        update: {
          method: 'PUT',
          headers: {'Hawkular-Tenant': tenantId, 'Content-Type': 'application/json'}
        },
      });
    };

    this.RemoveTrigger = function (tenantId, triggerId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/triggers/:triggerId', {triggerId: triggerId}, {
        remove: {
          method: 'DELETE',
          headers: {'Hawkular-Tenant': tenantId}
        },
      });
    };

    this.EnableTriggers = function (tenantId, triggerIds, enabled) {
      return $resource($rootScope.appConfig.server.baseUrl + '/triggers/enabled', {triggerIds: triggerIds, enabled: enabled}, {
        update: {
          method: 'PUT',
          headers: {'Hawkular-Tenant': tenantId}
        },
      });
    };

  }
]);