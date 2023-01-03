/*
 * Copyright © 2021 Cask Data, Inc.
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

package io.cdap.cdap.data2.metadata.lineage.field;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.cdap.cdap.api.lineage.field.EndPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A Gson deserializer that caches {@link EndPointField} and reuse them.
 * This class is NOT thread safe. It is supposed to be use for deserializing one {@link FieldLineageInfo} object only.
 */
@NotThreadSafe
public class EndpointFieldDeserializer implements JsonDeserializer<EndPointField> {

  private final Map<EndPointField, EndPointField> endpointFields = new HashMap<>();
  private static final Logger LOG = LoggerFactory.getLogger(EndpointFieldDeserializer.class);

  @Override
  public EndPointField deserialize(JsonElement json, Type typeOfT,
                                   JsonDeserializationContext context) throws JsonParseException {
    EndPointField endPointField = null;
    try {
      JsonObject obj = json.getAsJsonObject();
      LOG.info("obj - {}", obj);
      EndPoint endPoint = context.deserialize(obj.getAsJsonObject("endPoint"), EndPoint.class);
      LOG.info("endpoint - {}", endPoint);
      if (obj.getAsJsonPrimitive("field") != null) {
        String field = obj.getAsJsonPrimitive("field").getAsString();
        LOG.info("field - {}", field);
        //EndPoint(name = null, namespace=null, props = {})

        endPointField = new EndPointField(endPoint, field);
      }
    } catch (Exception e) {
      LOG.error("-----------------caught the exception--------------------------");
      LOG.error(e.getMessage());
    }
    EndPointField finalEndPointField = endPointField;
    return endpointFields.computeIfAbsent(endPointField, k -> finalEndPointField);
  }
}
