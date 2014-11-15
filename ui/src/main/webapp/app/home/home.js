/**
 * Home module for displaying home page content
 */

angular
  .module('pipelineAgentApp.home', [
    'ngRoute',
    'ngTagsInput',
    'jsonFormatter',
    'splitterDirectives',
    'tabDirectives',
    'pipelineGraphDirectives',
    'showErrorsDirectives',
    'ngEnterDirectives',
    'underscore'
  ])
  .config(['$routeProvider', function ($routeProvider) {
    $routeProvider.when('/',
      {
        templateUrl: 'app/home/home.tpl.html',
        controller: 'HomeController'
      }
    );
  }])
  .controller('HomeController', function ($scope, $rootScope, $timeout, api, _, $q) {
    var stageCounter = 0,
      timeout,
      dirty = false,
      ignoreUpdate = false,
      edges = [],
      SOURCE_STAGE_TYPE = 'SOURCE',
      PROCESSOR_STAGE_TYPE = 'PROCESSOR',
      TARGET_STAGE_TYPE = 'TARGET';

    angular.extend($scope, {
      isPipelineRunning: false,
      sourceExists: false,
      stageLibraries: [],
      pipelineConfigInfo: {},
      pipelineGraphData: {},
      previewMode: false,
      previewData: {},
      stagePreviewData: {
        input: [],
        output: []
      },
      previewSourceOffset: 0,
      previewBatchSize: 10,

      /**
       * Add Stage Instance to the Pipeline Graph.
       * @param stage
       */
      addStageInstance: function (stage) {
        var xPos = ($scope.pipelineConfig.stages && $scope.pipelineConfig.stages.length) ?
            $scope.pipelineConfig.stages[$scope.pipelineConfig.stages.length - 1].uiInfo.xPos + 300 : 200,
          yPos = 70,
          inputConnectors = (stage.type !== SOURCE_STAGE_TYPE) ? ['i1'] : [],
          outputConnectors = (stage.type !== TARGET_STAGE_TYPE) ? ['01'] : [],
          stageInstance = {
            instanceName: stage.name + (new Date()).getTime(),
            library: stage.library,
            stageName: stage.name,
            stageVersion: stage.version,
            configuration: [],
            uiInfo: {
              label: stage.label + (++stageCounter),
              description: stage.description,
              xPos: xPos,
              yPos: yPos,
              inputConnectors: inputConnectors,
              outputConnectors: outputConnectors,
              stageType: stage.type
            },
            inputLanes: [],
            outputLanes: []
          };

        if (stage.type !== TARGET_STAGE_TYPE) {
          stageInstance.outputLanes = [stageInstance.instanceName + 'OutputLane'];
        }

        angular.forEach(stage.configDefinitions, function (configDefinition) {
          var config = {
            name: configDefinition.name
          };

          if(configDefinition.type === 'MODEL' && configDefinition.model.modelType === 'FIELD_SELECTOR') {
            config.value = [];
          }

          stageInstance.configuration.push(config);
        });

        switch(stage.type) {
          case SOURCE_STAGE_TYPE:
            stageInstance.uiInfo.icon = 'assets/stage/ic_insert_drive_file_48px.svg';
            break;
          case PROCESSOR_STAGE_TYPE:
            stageInstance.uiInfo.icon = 'assets/stage/ic_settings_48px.svg';
            break;
          case TARGET_STAGE_TYPE:
            stageInstance.uiInfo.icon = 'assets/stage/ic_storage_48px.svg';
            break;
        }

        $scope.$broadcast('addNode', stageInstance);

        $scope.detailPaneConfig = stageInstance;
        $scope.detailPaneConfigDefn = stage;
      },

      /**
       * Returns label of the Stage Instance.
       *
       * @param stageInstanceName
       * @returns {*|string}
       */
      getStageInstanceLabel: function (stageInstanceName) {
        var instance;
        angular.forEach($scope.pipelineConfig.stages, function (stageInstance) {
          if (stageInstance.instanceName === stageInstanceName) {
            instance = stageInstance;
          }
        });
        return (instance && instance.uiInfo) ? instance.uiInfo.label : undefined;
      },

      /**
       * Returns message string of the issue.
       *
       * @param stageInstanceName
       * @param issue
       * @returns {*}
       */
      getIssuesMessage: function (stageInstanceName, issue) {
        var msg = issue.message;

        if (issue.level === 'STAGE_CONFIG') {
          var stageInstance = _.find($scope.pipelineConfig.stages, function (stage) {
            return stage.instanceName === stageInstanceName;
          });

          if (stageInstance) {
            msg += ' : ' + getConfigurationLabel(stageInstance, issue.configName);
          }
        }

        return msg;
      },

      /**
       * Fetches preview data for the pipeline and sets previewMode flag to true.
       *
       * @param nextBatch - By default it starts fetching from sourceOffset=0, if nextBatch is true sourceOffset is
       * updated to fetch next batch.
       */
      previewPipeline: function (nextBatch) {
        $scope.previewMode = true;

        if (nextBatch) {
          $scope.previewSourceOffset += $scope.previewBatchSize;
        } else {
          $scope.previewSourceOffset = 0;
        }

        api.pipelineAgent.previewPipeline('xyz', $scope.previewSourceOffset, $scope.previewBatchSize).success(function (previewData) {
          $scope.previewData = previewData;

          var firstStageInstance = $scope.pipelineConfig.stages[0];
          $scope.$broadcast('selectNode', firstStageInstance);
          updateDetailPane(firstStageInstance);

          /*if (!$scope.detailPaneConfig.stages) {
            $scope.stagePreviewData = getPreviewDataForStage(previewData, $scope.detailPaneConfig);
          }*/
        });
      },

      /**
       * Sets previewMode flag to false.
       */
      closePreview: function () {
        $scope.previewMode = false;
      },

      /**
       * Returns length of the preview collection.
       *
       * @returns {Array}
       */
      getPreviewRange: function () {
        var range = 0;

        if ($scope.stagePreviewData && $scope.stagePreviewData.input &&
          $scope.stagePreviewData.input.length) {
          range = $scope.stagePreviewData.input.length;
        } else if ($scope.stagePreviewData && $scope.stagePreviewData.output &&
          $scope.stagePreviewData.output.length) {
          range = $scope.stagePreviewData.output.length;
        }

        return new Array(range);
      },

      /**
       * Checks if configuration has any issue.
       *
       * @param {Object} configObject - The Pipeline Configuration/Stage Configuration Object.
       * @returns {Boolean} - Returns true if configuration has any issue otherwise false.
       */
      hasConfigurationIssues: function(configObject) {
        var config = $scope.pipelineConfig,
          issues;

        if(config && config.issues) {
          if(configObject.instanceName && config.issues.stageIssues &&
            config.issues.stageIssues && config.issues.stageIssues[configObject.instanceName]) {
            issues = config.issues.stageIssues[configObject.instanceName];
          } else if(config.issues.pipelineIssues){
            issues = config.issues.pipelineIssues;
          }
        }

        return _.find(issues, function(issue) {
          return issue.level === 'STAGE_CONFIG';
        });
      },

      /**
       * Returns message for the give Configuration Object and Definition.
       *
       * @param configObject
       * @param configDefinition
       */
      getConfigurationIssueMessage: function(configObject, configDefinition) {
        var config = $scope.pipelineConfig,
          issues,
          issue;

        if(config && config.issues) {
          if(configObject.instanceName && config.issues.stageIssues &&
            config.issues.stageIssues && config.issues.stageIssues[configObject.instanceName]) {
            issues = config.issues.stageIssues[configObject.instanceName];
          } else if(config.issues.pipelineIssues){
            issues = config.issues.pipelineIssues;
          }
        }

        issue = _.find(issues, function(issue) {
           return (issue.level === 'STAGE_CONFIG' && issue.configName === configDefinition.name);
        });

        return issue ? issue.message : '';
      },

      /**
       * On clicking issue in Issues dropdown selects the stage and if issue level is STAGE_CONFIG
       * Configuration is
       * @param issue
       * @param instanceName
       */
      onIssueClick: function(issue, instanceName) {
        var pipelineConfig = $scope.pipelineConfig,
          stageInstance;

        if(instanceName) {
          //Select stage instance
          stageInstance = _.find(pipelineConfig.stages, function(stage) {
            return stage.instanceName === instanceName;
          });
          $scope.$broadcast('selectNode', stageInstance);
          updateDetailPane(stageInstance);
          //$('.configuration-tabs a:last').tab('show');
        } else {
          //Select Pipeline Config
          $scope.$broadcast('selectNode');
          updateDetailPane();
        }
      },

      /**
       * Toggles selection of value in given Array.
       *
       * @param arr
       * @param value
       */
      toggleSelector: function(arr, value) {
        var index = _.indexOf(arr, value);
        if(index !== -1) {
          arr.splice(index, 1);
        } else {
          arr.push(value);
        }
      },

      /**
       * Remove the field from uiInfo.inputFields and passed array.
       * @param fieldArr
       * @param index
       * @param configValueArr
       */
      removeFieldSelector: function(fieldArr, index, configValueArr) {
        var field = fieldArr[index];
        fieldArr.splice(index, 1);

        index = _.indexOf(configValueArr, field.name);
        if(index !== -1) {
          configValueArr.splice(index, 1);
        }
      },

      /**
       * Adds new field to the array uiInfo.inputFields
       * @param fieldArr
       */
      addNewField: function(fieldArr) {
        if(this.newFieldName) {
          fieldArr.push({
            name: this.newFieldName
          });
          this.newFieldName = '';
        }
      },


      /**
       * Returns output records produced by input record.
       *
       * @param outputRecords
       * @param inputRecord
       * @returns {*}
       */
      getOutputRecords: function(outputRecords, inputRecord) {
        var matchedRecords = _.filter(outputRecords, function(outputRecord) {
          return outputRecord.header.previousStageTrackingId === inputRecord.header.trackingId;
        });

        return matchedRecords;
      },


      /**
       * Update Preview Stage Instance.
       *
       * @param stageInstance
       */
      updatePreviewStage: function(stageInstance) {
        if(stageInstance) {
          $scope.$broadcast('selectNode', stageInstance);
          updateDetailPane(stageInstance);
        }
      }
    });


    /**
     * Fetch definitions for Pipeline and Stages, Pipeline Configuration and Pipeline Information.
     */
    $q.all([api.pipelineAgent.getDefinitions(),
      api.pipelineAgent.getPipelineConfig('xyz'),
      api.pipelineAgent.getPipelineConfigInfo('xyz')])
      .then(function (results) {

        //Definitions
        var definitions = results[0].data;
        $scope.pipelineConfigDefinition = definitions.pipeline[0];
        $scope.stageLibraries = definitions.stages;

        $scope.sources = _.filter($scope.stageLibraries, function (stageLibrary) {
          return stageLibrary.type === SOURCE_STAGE_TYPE;
        });

        $scope.processors = _.filter($scope.stageLibraries, function (stageLibrary) {
          return (stageLibrary.type === PROCESSOR_STAGE_TYPE);
        });

        $scope.targets = _.filter($scope.stageLibraries, function (stageLibrary) {
          return (stageLibrary.type === TARGET_STAGE_TYPE);
        });

        //Pipeline Configuration Info
        $scope.pipelineConfigInfo = results[2].data;

        //Pipeline Configuration
        updateGraph(results[1].data);

        stageCounter = ($scope.pipelineConfig && $scope.pipelineConfig.stages) ?
          $scope.pipelineConfig.stages.length : 0;
      });

    /**
     * Save Updates
     * @param config
     */
    var saveUpdates = function (config) {
      if ($rootScope.common.saveOperationInProgress) {
        return;
      }

      if (!config) {
        config = _.clone($scope.pipelineConfig);
      }

      delete config.info;

      dirty = false;
      $rootScope.common.saveOperationInProgress = true;
      api.pipelineAgent.savePipelineConfig('xyz', config).success(function (res) {

        $rootScope.common.saveOperationInProgress = false;

        if (dirty) {
          config = _.clone($scope.pipelineConfig);
          config.uuid = res.uuid;

          //Updated new changes in return config
          res.configuration = config.configuration;
          res.uiInfo = config.uiInfo;
          res.stages = config.stages;

          saveUpdates(config);
        }
        updateGraph(res);
      });
    };

    /**
     * Update Pipeline Graph
     *
     * @param pipelineConfig
     */
    var updateGraph = function (pipelineConfig) {
      var selectedStageInstance;

      ignoreUpdate = true;

      //Force Validity Check - showErrors directive
      $scope.$broadcast('show-errors-check-validity');

      if (!pipelineConfig.uiInfo) {
        pipelineConfig.uiInfo = {
          label: 'Pipeline',
          description: 'Default Pipeline'
        };
      }

      $scope.pipelineConfig = pipelineConfig || {};


      //Determine edges from input lanes and output lanes
      //And also set flag sourceExists if pipeline Config contains source
      edges = [];
      $scope.sourceExists = false;
      angular.forEach($scope.pipelineConfig.stages, function (sourceStageInstance) {
        if(sourceStageInstance.uiInfo.stageType === SOURCE_STAGE_TYPE) {
          $scope.sourceExists = true;
        }

        if (sourceStageInstance.outputLanes && sourceStageInstance.outputLanes.length) {
          angular.forEach(sourceStageInstance.outputLanes, function (outputLane) {
            angular.forEach($scope.pipelineConfig.stages, function (targetStageInstance) {
              if (targetStageInstance.inputLanes && targetStageInstance.inputLanes.length &&
                _.contains(targetStageInstance.inputLanes, outputLane)) {
                edges.push({
                  source: sourceStageInstance,
                  target: targetStageInstance
                });
              }
            });
          });
        }
      });

      $scope.$broadcast('updateGraph', $scope.pipelineConfig.stages, edges,
        $scope.pipelineConfig.issues,
        ($scope.detailPaneConfig && !$scope.detailPaneConfig.stages) ? $scope.detailPaneConfig : undefined);

      if ($scope.detailPaneConfig === undefined) {
        //First time
        $scope.detailPaneConfigDefn = $scope.pipelineConfigDefinition;
        $scope.detailPaneConfig = $scope.pipelineConfig;
      } else {
        //Check
        if ($scope.detailPaneConfig.stages) {
          //In case of detail pane is Pipeline Configuration
          $scope.detailPaneConfig = $scope.pipelineConfig;
        } else {
          //In case of detail pane is stage instance
          angular.forEach($scope.pipelineConfig.stages, function (stageInstance) {
            if (stageInstance.instanceName === $scope.detailPaneConfig.instanceName) {
              selectedStageInstance = stageInstance;
            }
          });

          if (selectedStageInstance) {
            $scope.detailPaneConfig = selectedStageInstance;
          } else {
            $scope.detailPaneConfig = $scope.pipelineConfig;
            $scope.detailPaneConfigDefn = $scope.pipelineConfigDefinition;
          }

        }
      }
    };

    /**
     * Update Detail Pane when selection changes in Pipeline Graph.
     *
     * @param stageInstance
     */
    var updateDetailPane = function(stageInstance) {
      var stageInstances = $scope.pipelineConfig.stages,
        inputLane,
        outputLane;

      if(stageInstance) {
        //Stage Instance Configuration
        //Stage Instance Configuration
        $scope.detailPaneConfig = stageInstance;
        $scope.detailPaneConfigDefn = _.find($scope.stageLibraries, function (stageLibrary) {
          return stageLibrary.name === stageInstance.stageName &&
            stageLibrary.version === stageInstance.stageVersion;
        });

        if ($scope.previewMode) {
          $scope.stagePreviewData = getPreviewDataForStage($scope.previewData, $scope.detailPaneConfig);


          if(stageInstance.inputLanes && stageInstance.inputLanes.length) {
            $scope.previousStageInstances = _.filter(stageInstances, function(instance) {
              return (_.intersection(instance.outputLanes, stageInstance.inputLanes)).length > 0;
            });
          } else {
            $scope.previousStageInstances = [];
          }

          if(stageInstance.outputLanes && stageInstance.outputLanes.length) {
            $scope.nextStageInstances = _.filter(stageInstances, function(instance) {
              return (_.intersection(instance.inputLanes, stageInstance.outputLanes)).length > 0;
            });
          } else {
            $scope.nextStageInstances = [];
          }

        } else {

          //In case of processors and targets run the preview to get input fields
          // if current state of config is previewable.
          if(stageInstance.uiInfo.stageType !== SOURCE_STAGE_TYPE) {
            if(!stageInstance.uiInfo.inputFields || stageInstance.uiInfo.inputFields.length === 0) {
              if($scope.pipelineConfig.previewable) {
                api.pipelineAgent.previewPipeline('xyz', $scope.previewSourceOffset, $scope.previewBatchSize).success(function (previewData) {
                  var stagePreviewData = getPreviewDataForStage(previewData, stageInstance);
                  stageInstance.uiInfo.inputFields = getFields(stagePreviewData.input);
                });
              }
            }
          }
        }
      } else {
        //Pipeline Configuration
        $scope.detailPaneConfigDefn = $scope.pipelineConfigDefinition;
        $scope.detailPaneConfig = $scope.pipelineConfig;

        if ($scope.previewMode) {
          $scope.stagePreviewData = {
            input: {},
            output: {}
          };
        }
      }
    };

    /**
     * Returns label of Configuration for given Stage Instance object and Configuration Name.
     *
     * @param stageInstance
     * @param configName
     * @returns {*}
     */
    var getConfigurationLabel = function (stageInstance, configName) {
      var stageDefinition = _.find($scope.stageLibraries, function (stage) {
          return stageInstance.library === stage.library &&
            stageInstance.stageName === stage.name &&
            stageInstance.stageVersion === stage.version;
        }),
        configDefinition = _.find(stageDefinition.configDefinitions, function (configDefinition) {
          return configDefinition.name === configName;
        });

      return configDefinition ? configDefinition.label : configName;
    };


    /**
     * Returns Preview input lane & output lane data for the given Stage Instance.
     *
     * @param previewData
     * @param stageInstance
     * @returns {{input: Array, output: Array}}
     */
    var getPreviewDataForStage = function (previewData, stageInstance) {
      var inputLane = (stageInstance.inputLanes && stageInstance.inputLanes.length) ?
          stageInstance.inputLanes[0] : undefined,
        outputLane = (stageInstance.outputLanes && stageInstance.outputLanes.length) ?
          stageInstance.outputLanes[0] : undefined,
        stagePreviewData = {
          input: [],
          output: []
        },
        batchData = previewData.batchesOutput[0];

      angular.forEach(batchData, function (stageOutput) {
        if (inputLane && stageOutput.output[inputLane] && stageOutput.output) {
          stagePreviewData.input = stageOutput.output[inputLane];
        } else if (outputLane && stageOutput.output[outputLane] && stageOutput.output) {
          stagePreviewData.output = stageOutput.output[outputLane];
        }
      });

      return stagePreviewData;
    };

    /**
     * Fetch fields information from Preview Data.
     *
     * @param lanePreviewData
     * @returns {Array}
     */
    var getFields = function(lanePreviewData) {
      var recordValues = _.isArray(lanePreviewData) && lanePreviewData.length ? lanePreviewData[0].values : [],
        fields = [];

      angular.forEach(recordValues, function(typeObject, fieldName) {
        fields.push({
          name : fieldName,
          type: typeObject.type,
          sampleValue: typeObject.value
        });
      });

      return fields;
    };

    //Event Handling

    $scope.$watch('pipelineConfig', function (newValue, oldValue) {
      if (ignoreUpdate) {
        $timeout(function () {
          ignoreUpdate = false;
        });
        return;
      }
      if (!angular.equals(newValue, oldValue)) {
        dirty = true;
        if (timeout) {
          $timeout.cancel(timeout);
        }
        timeout = $timeout(saveUpdates, 1000);
      }
    }, true);

    $scope.$on('onNodeSelection', function (event, stageInstance) {
      updateDetailPane(stageInstance);
    });

    $scope.$on('onRemoveNodeSelection', function () {
      updateDetailPane();
    });

  });