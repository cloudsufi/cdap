# <!--
#   Copyright © 2014-2021 Cask Data, Inc.
#
#   Licensed under the Apache License, Version 2.0 (the "License"); you may not
#   use this file except in compliance with the License. You may obtain a copy of
#   the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
#   License for the specific language governing permissions and limitations under
#   the License.
#   -->
# Dockerfile content for $CDAP_SRC/Dockerfile
FROM us-east1-docker.pkg.dev/cloud-data-fusion-images-ap/cdf/cloud-data-fusion:latest

# FROM gcr.io/cdapio/cdap:latest

# Build argument for the CDAP version
ARG CDAP_VERSION
# ARG CDAP_COMMON_VERSION
ARG TWILL_VERSION

# Define the service-specific library directory for Watchdog
ENV WATCHDOG_LIB_DIR="/opt/cdap/master/services/Watchdog/lib"

# Create the Watchdog lib directory
RUN mkdir -p ${WATCHDOG_LIB_DIR}

# Remove old versions of the specific JARs being updated.
# The filenames are derived from the groupId:artifactId and version.
# RUN rm -f "/opt/cdap/master/lib/io.cdap.cdap.cdap-watchdog-${CDAP_VERSION}.jar" \
#  && rm -f "/opt/cdap/master/lib/io.cdap.cdap.cdap-common-${CDAP_VERSION}.jar" \
#  && rm -f "/opt/cdap/master/lib/io.cdap.cdap.cdap-messaging-spi-${CDAP_VERSION}.jar" \
#  && rm -f "/opt/cdap/master/lib/io.cdap.cdap.cdap-storage-spi-${CDAP_VERSION}.jar" \
#  && rm -f "/opt/cdap/master/lib/io.cdap.cdap.cdap-metadata-spi-${CDAP_VERSION}.jar" \
#  && rm -f "/opt/cdap/master/lib/io.cdap.cdap.cdap-elastic-${CDAP_VERSION}.jar" \
#  && rm -f "/opt/cdap/master/lib/io.cdap.cdap.cdap-security-${CDAP_VERSION}.jar" \
#  && rm -f "/opt/cdap/master/lib/io.cdap.cdap.cdap-tms-${CDAP_VERSION}.jar" \
#  && rm -f "/opt/cdap/master/lib/io.cdap.cdap.cdap-securestore-api-${CDAP_VERSION}.jar" \
#  && rm -f "/opt/cdap/master/lib/io.cdap.cdap.cdap-log-publisher-spi-${CDAP_VERSION}.jar" \
#  && rm -f "/opt/cdap/master/lib/io.cdap.cdap.cdap-watchdog-spi-${CDAP_VERSION}.jar" \
#  && rm -f "/opt/cdap/master/lib/io.cdap.cdap.cdap-data-fabric-${CDAP_VERSION}.jar" \
#  && rm -f "/opt/cdap/master/lib/io.cdap.twill.twill-core-${TWILL_VERSION}.jar"

# Copy the newly built JARs from the local Maven targets into the image.
COPY "cdap-watchdog/target/cdap-watchdog-${CDAP_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
COPY "cdap-common/target/cdap-common-${CDAP_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
COPY "cdap-messaging-spi/target/cdap-messaging-spi-${CDAP_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
COPY "cdap-storage-spi/target/cdap-storage-spi-${CDAP_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
COPY "cdap-metadata-spi/target/cdap-metadata-spi-${CDAP_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
COPY "cdap-elastic/target/cdap-elastic-${CDAP_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
COPY "cdap-security/target/cdap-security-${CDAP_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
COPY "cdap-tms/target/cdap-tms-${CDAP_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
COPY "cdap-securestore-spi/target/cdap-securestore-spi-${CDAP_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
COPY "cdap-log-publisher-spi/target/cdap-log-publisher-spi-${CDAP_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
COPY "cdap-watchdog-api/target/cdap-watchdog-api-${CDAP_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
COPY "cdap-data-fabric/target/cdap-data-fabric-${CDAP_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
COPY "twill-core-${TWILL_VERSION}.jar" "${WATCHDOG_LIB_DIR}/"
# Ensure correct permissions
RUN chmod -R 755 /opt/cdap
