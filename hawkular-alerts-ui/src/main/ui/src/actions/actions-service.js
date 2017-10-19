/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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