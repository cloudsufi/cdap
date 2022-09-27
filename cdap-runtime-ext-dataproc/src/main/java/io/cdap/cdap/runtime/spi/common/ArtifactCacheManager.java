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

package io.cdap.cdap.runtime.spi.common;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.common.base.Joiner;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages the custom_time and Temporary Holds that need to be set/unset/updated on cached program artifacts in GCS.
 * Also maintains a usage counter of each of the cached artifacts in the same GCS bucket.
 */
public class ArtifactCacheManager {

  private static final Gson GSON = new Gson();
  private static final Logger LOG = LoggerFactory.getLogger(ArtifactCacheManager.class);

  private static final String CACHED_ARTIFACTS_USAGE_COUNT = "cached-artifacts-usage-count.json";
  private static final String CACHED_ARTIFACTS_LIST_FOR_RUNID = "cached-artifacts-used.json";
  private static final String CONTENT_TYPE_JSON = "application/json";
  public static final int MAX_RETRIES_FOR_CACHE_COUNTER_OPERATION = 10;
  public static final int MAX_RETRIES_TO_FETCH_CACHED_ARTIFACTS_FOR_RUN = 3;

  /**
   * Increases cache usage counter for all the files(cached artifacts) in the GCS bucket.
   */
  public static void recordCacheUsageForArtifacts(Storage client, String bucket, Set<String> files,
                                                  String runRootPath, String cachePath) {
    try {
      String cachedArtifactsListFilePath = getPath(runRootPath, CACHED_ARTIFACTS_LIST_FOR_RUNID);
      storeCachedArtifactsForRun(client, bucket, files, cachedArtifactsListFilePath);
      String cacheCountFilePath = getPath(cachePath, CACHED_ARTIFACTS_USAGE_COUNT);
      changeUsageCountForArtifacts(client, bucket, files, cachePath, cacheCountFilePath, 1);
    } catch (Exception e) {
      LOG.error("Error in recordCacheUsageForArtifacts", e);
    }
  }

  /**
   * Decreases cache usage counter for all the files(cached artifacts) in the GCS bucket.
   * Also updates custom_time and removes Temporary Hold if the artifact is not being used by any program run.
   */
  public static void releaseCacheUsageForArtifacts(Storage client, String bucket, String runRootPath) {
    try {
      String cachedArtifactsListFilePath = getPath(runRootPath, CACHED_ARTIFACTS_LIST_FOR_RUNID);
      Set<String> files = getCachedArtifactsForRun(client, bucket, cachedArtifactsListFilePath);
      String cachedArtifactsPath = getPath(DataprocUtils.CDAP_GCS_ROOT, DataprocUtils.CDAP_CACHED_ARTIFACTS);
      String cacheCountFilePath = getPath(cachedArtifactsPath, CACHED_ARTIFACTS_USAGE_COUNT);
      changeUsageCountForArtifacts(client, bucket, files, cachedArtifactsPath, cacheCountFilePath, -1);
    } catch (Exception e) {
      LOG.error("Error in releaseCacheUsageForArtifacts", e);
    }
  }

  private static void changeUsageCountForArtifacts(Storage client, String bucket, Set<String> files, String cachePath,
                                                   String filePath, int changeValue) {
    if (files.isEmpty()) {
      LOG.debug("No Cached Artifacts found for this run!");
      return;
    }
    BlobId blobId = BlobId.of(bucket, filePath);
    for (int i = 0; i < MAX_RETRIES_FOR_CACHE_COUNTER_OPERATION; i++) {
      try {
        Blob blob = client.get(blobId);
        Map<String, Integer> artifactCount;
        if (blob == null || !blob.exists()) {
          LOG.debug("Creating ArtifactsCacheCounter {} at {}", CACHED_ARTIFACTS_USAGE_COUNT, filePath);
          artifactCount = new HashMap<>();
          BlobInfo createBlob = BlobInfo.newBuilder(blobId).setContentType(CONTENT_TYPE_JSON).build();
          try {
            client.create(createBlob, GSON.toJson(artifactCount).getBytes(StandardCharsets.UTF_8),
                          Storage.BlobTargetOption.doesNotExist());
          } catch (StorageException e) {
            if (e.getCode() != HttpURLConnection.HTTP_PRECON_FAILED) {
              throw e;
            }
          }
          blob = client.get(blobId);
        }
        artifactCount = GSON.fromJson(new String(blob.getContent(), StandardCharsets.UTF_8),
                                      new TypeToken<Map<String, Integer>>() {
                                      }.getType());
        for (String file : files) {
          int count = artifactCount.getOrDefault(file, 0) + changeValue;
          if (count <= 0) {
            if (count < 0) {
              LOG.error("Cache usage count less than 0 for {} in {}", file, getPath(cachePath,
                                                                                    CACHED_ARTIFACTS_USAGE_COUNT));
            }
            artifactCount.remove(file);
            setCustomTimeOnArtifactAndReleaseHold(client, bucket, file, cachePath);
          } else {
            artifactCount.put(file, count);
          }
        }
        try {
          WritableByteChannel writer = blob.writer(Storage.BlobWriteOption.generationMatch());
          writer.write(ByteBuffer.wrap(GSON.toJson(artifactCount).getBytes(StandardCharsets.UTF_8)));
          writer.close();
        } catch (StorageException | IOException e) {
          LOG.error("Exception while writing to artifacts cache counter file", e);
          throw e;
        }
        break;
      } catch (Exception e) {
        LOG.error("Exception while updating artifacts cache counter, retrying operation.", e);
      }
    }
  }

  private static void setCustomTimeOnArtifactAndReleaseHold(Storage client, String bucket, String file, String path) {
    BlobInfo blobInfo = BlobInfo.newBuilder(bucket, getPath(path, file))
      .setCustomTime(System.currentTimeMillis())
      .setTemporaryHold(false).build();
    client.update(blobInfo);
  }

  private static void storeCachedArtifactsForRun(Storage client, String bucket, Set<String> files, String filePath) {
    BlobInfo createBlob = BlobInfo.newBuilder(bucket, filePath).setContentType(CONTENT_TYPE_JSON).build();
    try {
      client.create(createBlob, GSON.toJson(files).getBytes(StandardCharsets.UTF_8),
                    Storage.BlobTargetOption.doesNotExist());
    } catch (StorageException e) {
      if (e.getCode() != HttpURLConnection.HTTP_PRECON_FAILED) {
        throw e;
      }
    }
  }

  private static Set<String> getCachedArtifactsForRun(Storage client, String bucket, String filePath) {
    BlobId blobId = BlobId.of(bucket, filePath);
    Set<String> files = null;
    for (int i = 0; i < MAX_RETRIES_TO_FETCH_CACHED_ARTIFACTS_FOR_RUN; i++) {
      try {
        Blob blob = client.get(blobId);
        files = GSON.fromJson(new String(blob.getContent(), StandardCharsets.UTF_8),
                              new TypeToken<Set<String>>() {
                              }.getType());
      } catch (StorageException e) {
        if (i == MAX_RETRIES_TO_FETCH_CACHED_ARTIFACTS_FOR_RUN - 1) {
          throw e;
        }
      }
    }
    return files;
  }

  private static String getPath(String... pathSubComponents) {
    return Joiner.on("/").join(pathSubComponents);
  }

}
