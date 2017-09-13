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
angular.module('hwk.appModule').controller( 'hwk.appController', ['$scope', '$rootScope', '$resource', '$location',
  function ($scope, $rootScope, $resource, $location ) {
    'use strict';

    //
    // Global Application Configuration - in root scope so services and controllers can use it
    //
    // [mazz] developers can change host and port if the server is running somewhere that can't be guessed
    $rootScope.appConfig = {};
    $rootScope.appConfig.server = {};
    $rootScope.appConfig.server.protocol = $location.protocol();
    $rootScope.appConfig.server.host = $location.host();
    $rootScope.appConfig.server.port = $location.port();

    // if the location is 0.0.0.0 set the host to the loopback address - this assumes the server is bound there
    if ($rootScope.appConfig.server.host === '0.0.0.0') {
      $rootScope.appConfig.server.host = '127.0.0.1';
    }
    // if port is 8003, assume this is running in a grunt development environment in which the server is at 8080
    if ($rootScope.appConfig.server.port === 8003) {
      $rootScope.appConfig.server.port = 8080;
    }

    $rootScope.appConfig.server.baseUrl = $rootScope.appConfig.server.protocol
      + "://"
      + $rootScope.appConfig.server.host
      + ":"
      + $rootScope.appConfig.server.port
      + "/hawkular/alerts";
    console.debug('[App Config] ' + angular.toJson($rootScope.appConfig));

    //
    // Hawkular Alerting Navigation
    // [lponce] Perhaps in the future is better to have it in the template itself
    //
    $scope.navigationItems = [
      {
        title: "Dashboard",
        iconClass: "fa fa-dashboard",
        href: "#/dashboard"
      },
      {
        title: "Alerts",
        iconClass: "pficon pficon-messages",
        href: "#/alerts"
      },
      {
        title: "Events",
        iconClass: "pficon pficon-topology",
        href: "#/events"
      },
      {
        title: "Triggers",
        iconClass: "fa fa-flash",
        href: "#/triggers"
      },
      {
        title: "Actions",
        iconClass: "fa fa-bell-o",
        href: "#/actions"
      }
    ];

    //
    // Global tenant setting
    //
    $scope.newTenant = {};

    // [lponce] comment if you don't want a pre-filled tenant
    $scope.newTenant.tenant = 'my-organization';

    $scope.updateTenant = function () {
      $rootScope.selectedTenant = $scope.newTenant.tenant;
    };
  }
]);
