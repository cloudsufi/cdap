/*
 * Copyright © 2023 Cask Data, Inc.
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

package io.cdap.cdap.etl.api.relational;

import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.engine.sql.StandardSQLCapabilities;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

public class SqlDialectConversionExpressionFactory implements ExpressionFactory<String> {

    SqlDialect sourceDialect;
    SqlDialect destinationDialect;
    @Nullable Schema schema;

    private static final Set<Capability> CAPABILITIES = Collections.unmodifiableSet(
            new HashSet<Capability>() {{
                add(StringExpressionFactoryType.SQL);
                add(StandardSQLCapabilities.BIGQUERY);
                add(StandardSQLCapabilities.SPARK);
            }}
    );

    public SqlDialectConversionExpressionFactory(SqlDialect src, SqlDialect dest) {
        this(src, dest, null);
    }

    public SqlDialectConversionExpressionFactory(SqlDialect src, SqlDialect dest, @Nullable Schema schema) {
        sourceDialect = src;
        destinationDialect = dest;
        this.schema = schema;
    }

    /**
     * Gets the expression factory type, which in this case is SQL.
     * @return {@link StringExpressionFactoryType}.SQL.
     */
    @Override
    public ExpressionFactoryType<String> getType() {
        return StringExpressionFactoryType.SQL;
    }

    /**
     * Accepts a SQL string in the source SQL dialect and validates it.
     * If the SQL string is valid, converts it to the destination SQL dialect and wraps it in an {@link Expression}.
     * @param expression A SQL string valid in the source dialect
     * @return Either a valid SQL string expression in the destination dialect or
     * an {@link InvalidExtractableExpression}
     */
    @Override
    public Expression compile(String expression) {
        return null;
    }

    /**
     * Get the set of capabilities supported.
     * @return a set of all capabilities supported by this SQL engine.
     */
    @Override
    public Set<Capability> getCapabilities() {
        return CAPABILITIES;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }
}
