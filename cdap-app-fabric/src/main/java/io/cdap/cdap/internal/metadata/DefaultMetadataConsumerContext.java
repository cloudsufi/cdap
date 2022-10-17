/*
 * Copyright © 2022 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.cdap.internal.metadata;

import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.spi.metadata.MetadataConsumerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * Provides an initialized default context for MetadataConsumer implementing {@link MetadataConsumerContext}
 */
public class DefaultMetadataConsumerContext implements MetadataConsumerContext {

  private final Map<String, String> properties;
  private static final Logger LOG = LoggerFactory.getLogger(DefaultMetadataConsumerContext.class);

  /**
   * @param cConf An instance of an injected ${@link CConfiguration}.
   * @param metadataConsumerName name of the Metadata Consumer extension
   */
  DefaultMetadataConsumerContext(CConfiguration cConf, String metadataConsumerName) {
    String prefix = String.format("%s.%s.", Constants.MetadataConsumer.METADATA_CONSUMER_PREFIX, metadataConsumerName);
    LOG.info("======== prefix is {}", prefix);
    this.properties = Collections.unmodifiableMap(cConf.getPropsWithPrefix(prefix));
    LOG.info("======== props in const is {}", this.properties);
  }

  @Override
  public Map<String, String> getProperties() {
    LOG.info("======== retreiving props {}", this.properties);
    return this.properties;
  }
}
