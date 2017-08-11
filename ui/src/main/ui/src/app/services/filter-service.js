angular.module('hwk.appModule').service('hwk.filterService', ['$rootScope',
  function ($rootScope) {
    'use strict';

    this.rangeFilter = {
      datetime: moment(new Date()).format('YYYY-MM-DD HH:mm:ss'),
      offset: 4,
      unit: 'Hours',
      unitOptions: ['Minutes', 'Hours', 'Days'],
      direction: 'Before',
      directionOptions: ['Before', 'After']
    };

    this.alertFilter = {
      severity: 'All Severity',
      severityOptions: ['All Severity', 'Low', 'Medium', 'High', 'Critical'],
      status: 'All Status',
      statusOptions: ['All Status', 'Open', 'Acknowledged', 'Resolved'],
      tagQuery: null
    };

    this.eventFilter = {
      tagQuery: null
    };

  }
]);
