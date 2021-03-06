/**
 * Copyright 2016 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.origin.s3;

import com.streamsets.pipeline.api.Config;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.StageUpgrader;
import com.streamsets.pipeline.api.impl.Utils;
import com.streamsets.pipeline.config.Compression;
import com.streamsets.pipeline.stage.lib.aws.AWSUtil;

import java.util.ArrayList;
import java.util.List;

public class AmazonS3SourceUpgrader implements StageUpgrader {
  @Override
  public List<Config> upgrade(String library, String stageName, String stageInstance, int fromVersion, int toVersion, List<Config> configs) throws StageException {
    switch(fromVersion) {
      case 1:
        upgradeV1ToV2(configs);
        // fall through
      case 2:
        upgradeV2ToV3(configs);
        // fall through
      case 3:
        upgradeV3ToV4(configs);
        // fall through
      case 4:
        upgradeV4ToV5(configs);
        // fall through
      case 5:
        upgradeV5ToV6(configs);
        // fall through
      case 6:
        upgradeV6ToV7(configs);
        break;
      default:
        throw new IllegalStateException(Utils.format("Unexpected fromVersion {}", fromVersion));
    }
    return configs;
  }

  private void upgradeV1ToV2(List<Config> configs) {
    configs.add(new Config(S3ConfigBean.S3_CONFIG_BEAN_PREFIX + "advancedConfig.useProxy", false));
    configs.add(new Config(S3ConfigBean.S3_CONFIG_BEAN_PREFIX + "advancedConfig.proxyHost", ""));
    configs.add(new Config(S3ConfigBean.S3_CONFIG_BEAN_PREFIX + "advancedConfig.proxyPort", 0));
    configs.add(new Config(S3ConfigBean.S3_CONFIG_BEAN_PREFIX + "advancedConfig.proxyUser", ""));
    configs.add(new Config(S3ConfigBean.S3_CONFIG_BEAN_PREFIX + "advancedConfig.proxyPassword", ""));
    configs.add(new Config(S3ConfigBean.S3_DATA_FORMAT_CONFIG_PREFIX + "compression", Compression.NONE));
    configs.add(new Config(S3ConfigBean.S3_DATA_FORMAT_CONFIG_PREFIX + "filePatternInArchive", "*"));
    configs.add(new Config(S3ConfigBean.S3_DATA_FORMAT_CONFIG_PREFIX + "csvSkipStartLines", 0));
  }

  private void upgradeV2ToV3(List<Config> configs) {
    AWSUtil.renameAWSCredentialsConfigs(configs);
  }

  private void upgradeV3ToV4(List<Config> configs) {
    List<Config> configsToRemove = new ArrayList<>();
    List<Config> configsToAdd = new ArrayList<>();

    for (Config config : configs) {
      switch (config.getName()) {
        case S3ConfigBean.S3_CONFIG_PREFIX + "folder":
          configsToAdd.add(new Config(S3ConfigBean.S3_CONFIG_PREFIX + "commonPrefix", config.getValue()));
          configsToRemove.add(config);
          break;
        case S3ConfigBean.S3_FILE_CONFIG_PREFIX + "filePattern":
          configsToAdd.add(new Config(S3ConfigBean.S3_FILE_CONFIG_PREFIX + "prefixPattern", config.getValue()));
          configsToRemove.add(config);
          break;
        case S3ConfigBean.ERROR_CONFIG_PREFIX + "errorFolder":
          configsToAdd.add(new Config(S3ConfigBean.ERROR_CONFIG_PREFIX + "errorPrefix", config.getValue()));
          configsToRemove.add(config);
          break;
        case S3ConfigBean.ERROR_CONFIG_PREFIX + "archivingOption":
          if ("MOVE_TO_DIRECTORY".equals(config.getValue())) {
            configsToAdd.add(new Config(S3ConfigBean.ERROR_CONFIG_PREFIX + "archivingOption", "MOVE_TO_PREFIX"));
            configsToRemove.add(config);
          }
          break;
        case S3ConfigBean.POST_PROCESSING_CONFIG_PREFIX + "postProcessFolder":
          configsToAdd.add(new Config(S3ConfigBean.POST_PROCESSING_CONFIG_PREFIX + "postProcessPrefix", config.getValue()));
          configsToRemove.add(config);
          break;
        case S3ConfigBean.POST_PROCESSING_CONFIG_PREFIX + "archivingOption":
          if ("MOVE_TO_DIRECTORY".equals(config.getValue())) {
            configsToAdd.add(new Config(S3ConfigBean.POST_PROCESSING_CONFIG_PREFIX + "archivingOption", "MOVE_TO_PREFIX"));
            configsToRemove.add(config);
          }
          break;
        default:
          // no op
      }
    }

    configs.addAll(configsToAdd);
    configs.removeAll(configsToRemove);
  }

  private void upgradeV4ToV5(List<Config> configs) {
    // rename advancedConfig to proxyConfig
    List<Config> configsToRemove = new ArrayList<>();
    List<Config> configsToAdd = new ArrayList<>();

    for (Config config : configs) {
      switch (config.getName()) {
        case S3ConfigBean.S3_CONFIG_BEAN_PREFIX + "advancedConfig.useProxy":
        case S3ConfigBean.S3_CONFIG_BEAN_PREFIX + "advancedConfig.proxyHost":
        case S3ConfigBean.S3_CONFIG_BEAN_PREFIX + "advancedConfig.proxyPort":
        case S3ConfigBean.S3_CONFIG_BEAN_PREFIX + "advancedConfig.proxyUser":
        case S3ConfigBean.S3_CONFIG_BEAN_PREFIX + "advancedConfig.proxyPassword":
          configsToRemove.add(config);
          configsToAdd.add(new Config(config.getName().replace("advancedConfig", "proxyConfig"), config.getValue()));
          break;
        default:
          // no upgrade needed
          break;
      }
    }

    configs.removeAll(configsToRemove);
    configs.addAll(configsToAdd);
  }

  private void upgradeV5ToV6(List<Config> configs) {
    configs.add(new Config(S3ConfigBean.S3_FILE_CONFIG_PREFIX + "objectOrdering", ObjectOrdering.TIMESTAMP));
  }

  private void upgradeV6ToV7(List<Config> configs) {
    configs.add(new Config(S3ConfigBean.S3_CONFIG_PREFIX + "endpoint", ""));
  }
}
