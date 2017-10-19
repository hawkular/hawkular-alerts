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

    this.Purge = function (tenantId, alertsCriteria) {
      return $resource($rootScope.appConfig.server.baseUrl + "/delete", alertsCriteria, {
        update: {
          method: 'PUT',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Ack = function (tenantId, alertIds, ackBy, ackNotes) {
      return $resource($rootScope.appConfig.server.baseUrl + "/ack", {
        alertIds: alertIds,
        ackBy: ackBy,
        ackNotes: ackNotes
      }, {
        update: {
          method: 'PUT',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Resolve = function (tenantId, alertIds, resolvedBy, resolvedNotes) {
      return $resource($rootScope.appConfig.server.baseUrl + "/resolve", {
        alertIds: alertIds,
        resolvedBy: resolvedBy,
        resolvedNotes: resolvedNotes
      }, {
        update: {
          method: 'PUT',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };

    this.Note = function (tenantId, alertId, user, text) {
      return $resource($rootScope.appConfig.server.baseUrl + "/note/" + alertId, {
        alertId: alertId,
        user: user,
        text: text
      }, {
        update: {
          method: 'PUT',
          headers: {'Hawkular-Tenant': tenantId}
        }
      });
    };
  }
]);
