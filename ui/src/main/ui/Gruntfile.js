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
module.exports = function (grunt) {
  'use strict';
  var distDir = grunt.option('dist-dir') || 'dist';
  var targetPrefix = grunt.option('target-prefix') || '';

  // Load grunt tasks automatically
  require( 'load-grunt-tasks' )( grunt );

  // Define the configuration for all the tasks
  grunt.initConfig( {
    // Project settings
    projectSettings: {
      // configurable paths
      src:      require( './bower.json' ).appPath || 'src',
      dist:     distDir,
      pkg:      grunt.file.readJSON( 'package.json' )
    },

    angularFileLoader: {
      options: {
        scripts: ['src/**/*.js']
      },
      index: {
        src: 'index.html'
      },
    },
    // Automatically inject Bower components into the app
    wiredep: {
      app: {
        src: ['<%= projectSettings.src %>/index.html'],
        ignorePath:  /\.\.\//
      },
      sass: {
        src: ['<%= projectSettings.src %>/styles/{,*/}*.{scss,sass}'],
        ignorePath: /(\.\.\/){1,2}bower_components\//
      }
    },

    compass: {
      dist: {
        options: {
          sassDir: 'src/styles/scss',
          specify: 'src/styles/scss/app.scss',
          cssDir: 'src/styles',
          outputStyle: 'expanded'
        }
      }
    },
    // Watches files for changes and runs tasks based on the changed files
    // Live Reload is to slow need to figure out how to stop reloading of npm modules
    watch:    {
      compass: {
        files: 'src/styles/scss/*.scss',
        tasks: ['compass', 'copy:styles'],
        options: {
          livereload: 37830
        }
      },
      all: {
        files: ['Gruntfile.js', 'template.html', 'src/**/*.js', 'src/**/*.html', 'styles/**/*.css'],
        tasks: ['build'],
        options: {
          livereload: 37830
        }
      },
      livereload: {
        options: {
          livereload: 37830
        },
        files:   [
          '<%= projectSettings.src %>**/*',
          '.tmp/bower_components/{,*/}*.css',
          '.tmp/styles/{,*/}*.css'
        ]
      }
    },

    // The actual grunt server settings
    connect:  {
      options:    {
        base: '<%= projectSettings.src %>',
        port: grunt.option("port") || 8003,
        hostname:   grunt.option("host") || '0.0.0.0', // 0.0.0.0 allows access from outside
        livereload: 37830
      },
      livereload: {
        options: {
          open:       !grunt.option("nobrowser"),
          base:       [
            '.tmp',
            '<%= projectSettings.dist %>'
          ]
        }
      },
      test:       {
        options: {
          port: 9001,
          base: [
            '.tmp',
            '<%= projectSettings.dist %>'
          ]
        }
      },
      deploy:     {
        options: {
          base: '<%= projectSettings.dist %>'
        }
      }
    },

    // Make sure code styles are up to par and there are no obvious mistakes
    jshint:   {
      options: {
        jshintrc: '.jshintrc'
      },
      src:     [
        'Gruntfile.js',
        '<%= projectSettings.src %>/**/*.js'
      ]
    },

    // Template
    ngtemplates:     {
      options:               {
        module: 'hwk.appModule',
        htmlmin: {
          collapseWhitespace:        true,
          collapseBooleanAttributes: true,
          removeCommentsFromCDATA:   true,
          removeOptionalTags:        true
        }
      },
      appModule:             {
        // this is the part we want to strip from the URL, though not the path
        cwd:  '.',
        // this is the part we want actually in the URL (i.e. modules/foo/bar)
        src:  'src/**/*.html',
        // this is where it goes
        dest: '<%= projectSettings.src %>/templates/appModuleTemplates.js'
      }
    },
    // Empties folders to start fresh
    clean: {
      options: { force: true },
      dist:   {
        files: [{
          dot: true,
          src: [
            '.tmp',
            'dist'
          ]
        }]
      },
      source: {
        files: [{
          src: [ '<%= projectSettings.src %>/templates' ]
        }]
      },
      server: '.tmp',
      sass: '.sass-cache',
    },

    // Copies remaining files to places other tasks can use
    copy: {
      indexHtml: {
        cwd: '.',
        src: ['template.html'],
        dest: 'index.html'
      },
      html: {
        expand: true,
        cwd: '.',
        src: ['index.html'],
        dest: '<%= projectSettings.dist %>'
      },
      js: {
        cwd:  '.',
        src:  ['src/**/*.js'],
        dest: '<%= projectSettings.dist %>/'
      },
      img: {
        files: [
          {
            expand: true,
            cwd: 'bower_components/patternfly/dist/',
            src: ['img/**'],
            dest: '<%= projectSettings.dist %>/'
          },
          {
            expand: true,
            cwd: '.',
            src: ['favicon.ico'],
            dest: '<%= projectSettings.dist %>/'
          },
          {
            expand: true,
            cwd: '.',
            src: ['hawkular-alerting.svg'],
            dest: '<%= projectSettings.dist %>/'
          }
        ]
      },
      styles: {
        expand: true,
        cwd: 'src',
        src: ['styles/**'],
        dest: '<%= projectSettings.dist %>'
      },
      // [lponce] Probably there should be a better way to only copy .min.js dependencies on target app
      bower: {
        files: [
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/jquery/dist/jquery.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/bootstrap-select/js/bootstrap-select.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/moment/min/moment.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/angular/angular.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/angular-route/angular-route.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/angular-resource/angular-resource.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/angular-sanitize/angular-sanitize.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/patternfly/dist/css/patternfly.min.css'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/patternfly/dist/fonts/fontawesome-webfont.woff'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/patternfly/dist/fonts/fontawesome-webfont.woff2'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/patternfly/dist/fonts/OpenSans-*-webfont.woff'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/patternfly/dist/fonts/PatternFlyIcons-webfont.ttf'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/patternfly/dist/img/*'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/patternfly/dist/js/patternfly.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/patternfly/dist/less/*'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/patternfly/dist/css/patternfly-additions.min.css'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/angular-bootstrap/ui-bootstrap-tpls.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/bootstrap/dist/js/bootstrap.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/angular-translate/angular-translate.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/angular-translate-loader-static-files/angular-translate-loader-static-files.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/d3/d3.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/c3/c3.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/angular-patternfly/dist/angular-patternfly.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/angular-patternfly/dist/angular-patternfly.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/angular-patternfly/dist/styles/angular-patternfly.min.css'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/lodash/lodash.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/patternfly-timeline/dist/timeline.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/patternfly-timeline/dist/timeline.css'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/moment/min/moment.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/bootstrap-datepicker/dist/js/bootstrap-datepicker.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/bootstrap-datepicker/dist/css/bootstrap-datepicker.min.css'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/eonasdan-bootstrap-datetimepicker/build/js/bootstrap-datetimepicker.min.js'],
            dest: '<%= projectSettings.dist %>'
          },
          {
            expand: true,
            cwd: '.',
            src: ['bower_components/bootstrap-touchspin/dist/jquery.bootstrap-touchspin.min.js'],
            dest: '<%= projectSettings.dist %>'
          }
        ]
      },
      templates: {
        expand: true,
        cwd: 'src',
        src: ['templates/appModuleTemplates.js'],
        dest: '<%= projectSettings.dist %>'
      }
    },
    htmlhint: {
      html: {
        src: ['src/**/*.html'],
        options: {
          htmlhintrc: '.htmlhintrc'
        }
      }
    },
    eslint: {
      options: {
        configFile: 'eslint.yaml'
      },
      target: [
        'Gruntfile.js',
        'src/**/*.js'
      ]
    },
    replace: {
      index: {
        src: ['<%= projectSettings.dist %>/index.html'],
        overwrite: true,
        replacements: [{
          from: 'href="',
          to: 'href="' + targetPrefix + '/'
        }, {
          from: 'src="',
          to: 'src="' + targetPrefix + '/'
        }]
      }
    }
  } );

  grunt.loadNpmTasks('grunt-contrib-compass');
  grunt.loadNpmTasks('grunt-text-replace');

  grunt.registerTask( 'jshintRun', [
    'jshint'
  ] );

  grunt.registerTask( 'server', function (target) {
    grunt.task.run( [
      'clean:server',
      'build',
      'configureProxies:server', // added just before connect
      'connect:livereload',
      'watch'
    ] );
  } );

  grunt.registerTask('lint', ['eslint', 'htmlhint']);

  grunt.registerTask( 'build', function (target) {

    var buildTasks = [
      'clean:dist',
    ];

    var mainBuildTasks = [
      'lint',
      'ngtemplates',
      'copy:indexHtml',
      'angularFileLoader',
      'copy:html',
      'copy:js',
      'copy:img',
      'copy:styles',
      'copy:bower',
      'copy:templates'
    ];

    if (targetPrefix.length === 0) {
      buildTasks.push('compass');
    }

    buildTasks = buildTasks.concat(mainBuildTasks);

    if (targetPrefix.length > 0) {
      buildTasks.push('replace:index');
    }

    grunt.task.run( buildTasks );

  } );

  grunt.registerTask( 'default', [
    'build'
  ] );

  grunt.registerTask( 'purgeinstall', 'Purge bower and npm installation files' , function (target) {
    grunt.log.writeln('Removing bower_components');
    grunt.file.delete('bower_components');
    grunt.log.writeln('Removing node_modules');
    grunt.file.delete('node_modules');
    grunt.log.writeln('Removing package-lock.json');
    grunt.file.delete('package-lock.json');
    grunt.log.writeln('You now must run "npm install && bower install" before attempting to re-build.');
  } );

  grunt.registerTask( 'purge', 'Purge everything' , ['clean','purgeinstall']);

};
