angular.module('hwk.actionsModule').service('hwk.actionsService', ['$resource', '$rootScope',
  function ($resource, $rootScope) {
    'use strict';

    this.Plugins = function (tenantId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/plugins', {}, {
        get: {
          method: 'GET',
          isArray: true,
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Plugin = function (tenantId, actionPlugin) {
      return $resource($rootScope.appConfig.server.baseUrl + '/plugins/:actionPlugin', {
        actionPlugin: actionPlugin
      }, {
        get: {
          method: 'GET',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.ActionPluginMap = function (tenantId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/actions', {}, {
        get: {
          method: 'GET',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.ActionPluginActionDefinitionIds = function (tenantId, actionPlugin) {
      return $resource($rootScope.appConfig.server.baseUrl + '/actions/plugin/:actionPlugin', {
        actionPlugin: actionPlugin
      }, {
        get: {
          method: 'GET',
          isArray: true,
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

    this.NewActionDefinition = function (tenantId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/actions', {}, {
        save: {
          method: 'POST',
          headers: {'Hawkular-Tenant': tenantId, 'Content-Type': 'application/json'}
        },
      });
    };

    this.UpdateActionDefinition = function (tenantId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/actions', {}, {
        update: {
          method: 'PUT',
          headers: {'Hawkular-Tenant': tenantId, 'Content-Type': 'application/json'}
        },
      });
    };

    this.RemoveActionDefinition = function (tenantId, actionPlugin, actionId) {
      return $resource($rootScope.appConfig.server.baseUrl + '/actions/:actionPlugin/:actionId', {
        actionPlugin: actionPlugin,
        actionId: actionId
      }, {
        remove: {
          method: 'DELETE',
          headers: {'Hawkular-Tenant': tenantId}
        },
      });
    };

  }
]);