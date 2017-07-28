angular.module('hwk.appModule').service('hwk.filterService', ['$rootScope',
  function ($rootScope) {
    'use strict';

    this.dateFilter = {
      start: new Date(),
      end: new Date(),
    };

    this.alertFilter = {
      severity: 'All Severity',
      severityOptions: ['All Severity', 'Low', 'Medium', 'High', 'Critical'],
      status: 'All Status',
      statusOptions: ['All Status', 'Open', 'Acknowledged', 'Resolved'],
    };

    this.tagFilter = {
      tagQuery: null
    };

  }
]);
