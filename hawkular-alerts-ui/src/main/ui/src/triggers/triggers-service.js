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

    this.Query = function (tenantId, criteria) {
      // TODO: This should change to tagQuery when it is supported on TriggersCriteria
      return $resource($rootScope.appConfig.server.baseUrl + '/triggers', criteria, {
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
      return $resource($rootScope.appConfig.server.baseUrl + '/triggers/trigger/:triggerId', {triggerId: triggerId}, {
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