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

    this.getRange = function () {
      var offset = this.rangeFilter.offset;
      var start;
      var end;
      switch ( this.rangeFilter.unit ) {
      case 'Minutes' :
        offset *= (60 * 1000);
        break;
      case 'Hours' :
        offset *= (60 * 60 * 1000);
        break;
      case 'Days' :
        offset *= (60 * 60 * 24 * 1000);
        break;
      default :
        console.debug("[Dashboard] Unsupported unit: " + this.rangeFilter.unit);
      }
      switch ( this.rangeFilter.direction ) {
      case 'After' :
        start = new Date(this.rangeFilter.datetime).getTime();
        end = start + offset;
        break;
      case 'Before':
        end = new Date(this.rangeFilter.datetime).getTime();
        start = end - offset;
        break;
      default :
        console.debug("[Dashboard] Unsupported direction: " + this.rangeFilter.direction);
      }

      return [start,end];
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
