/*
 * Copyright © 2018 Cask Data, Inc.
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

package io.cdap.cdap.datapipeline;

import io.cdap.cdap.etl.api.lineage.field.FieldOperation;
import io.cdap.cdap.etl.api.lineage.field.FieldReadOperation;
import io.cdap.cdap.etl.api.lineage.field.FieldTransformOperation;
import io.cdap.cdap.etl.api.lineage.field.FieldWriteOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Validate the input and output fields of operations.
 *
 * For each operation, the input must be coming from the input schema that stage receives
 * or it must be one of the output of operations recorded by that stage prior to the
 * current operation.
 * For example, consider that stage has input schema as [a, b, c] and it records
 * following operations in the order
 *
 * OP1: [a] -> [x]
 * OP2: [x] -> [y]
 * OP3: [z] -> [d]
 * OP4: [c] -> [z]
 *
 * In the above case, OP1 has valid input [a] which is coming from input schema, OP2 has valid input
 * [x] which is generated by OP1 which is recorded before OP2. However OP3 has invalid input [z], since
 * it is not part of the input schema and it is not outputted by any other operation occurring prior to
 * OP3, even though [z] is created by OP4 which occurs after OP3.
 *
 * For each operation, the output generated by it is valid if it is not generated by a non-existing field.
 * Non-existing fields mean a field is neither in the input schema nor in the generated fields.
 *
 * For example, in above case if the stage output schema is [x, y, z] then the output [d] created
 * by OP3 is not the part of schema and it is also not used as an input by any subsequent operations
 * of OP3, so it is treated as invalid.
 *
 * It is also possible to generate the redundant outputs by operations.
 * For example, consider we add two more operations to the above list:
 *
 * OP1: [a] -> [x]
 * OP2: [x] -> [y]
 * OP3: [z] -> [d]
 * OP4: [c] -> [z]
 * OP5: [c] -> [x]
 * OP6: [a, c] -> [z]
 *
 * In this case the output field [z] created by OP4 is redundant (and so is invalid), since the
 * field [z] of the output schema will always come from OP6 and [z] is not used as input by any
 * operation subsequent of OP6. However note that even OP1 and OP5 both outputs [x], OP1 output is
 * not considered as invalid, since its used as input by OP2 which happens before OP5.
 */
public class StageOperationsValidator {
  private static final Logger LOG = LoggerFactory.getLogger(StageOperationsValidator.class);

  private final List<FieldOperation> operations;
  private final Set<String> stageInputs;
  private final Set<String> stageOutputs;
  private final Map<String, List<String>> invalidOutputs;
  private final Map<String, List<String>> invalidInputs;


  private StageOperationsValidator(List<FieldOperation> operations, Set<String> stageInputs,
                                   Set<String> stageOutputs) {
    this.operations = operations;
    this.stageInputs = stageInputs;
    this.stageOutputs = stageOutputs;
    this.invalidInputs = new HashMap<>();
    this.invalidOutputs = new HashMap<>();
  }

  /**
   * Validate the inputs and outputs for a stage.
   */
  void validate() {
    // Fields input to the stage are valid
    Set<String> validInputsSoFar = new HashSet<>(stageInputs);

    // Map of field name to the list of operations that generated that field.
    // Map will contain fields that are yet to be validated
    Map<String, List<FieldOperation>> unusedOutputs = new HashMap<>();

    // Map of field name to the list of operations that generated that field.
    // Map will contain fields that are redundant
    // For example: if following operations are recorded by stage
    //
    // OP1: [a, b] -> [d]
    // OP2: [b] -> [d]
    // OP3: [d] -> [e]
    //
    // output d of OP1 is redundant, since OP3 will always read d generated by OP2
    // so following map will contain d -> [OP1]
    Map<String, List<FieldOperation>> redundantOutputs = new HashMap<>();
    for (FieldOperation pipelineOperation : operations) {
      switch (pipelineOperation.getType()) {
        case READ:
          FieldReadOperation read = (FieldReadOperation) pipelineOperation;
          updateInvalidOutputs(Collections.emptyList(), unusedOutputs, redundantOutputs);
          validInputsSoFar.addAll(read.getOutputFields());
          for (String field : read.getOutputFields()) {
            List<FieldOperation> origins = unusedOutputs.computeIfAbsent(field, k -> new ArrayList<>());
            origins.add(pipelineOperation);
          }
          break;
        case TRANSFORM:
          FieldTransformOperation transform = (FieldTransformOperation) pipelineOperation;
          validateInputs(pipelineOperation.getName(), transform.getInputFields(), validInputsSoFar);
          updateInvalidOutputs(transform.getInputFields(), unusedOutputs, redundantOutputs);
          validInputsSoFar.addAll(transform.getOutputFields());
          for (String field : transform.getOutputFields()) {
            List<FieldOperation> origins = unusedOutputs.computeIfAbsent(field, k -> new ArrayList<>());
            origins.add(pipelineOperation);
          }
          break;
        case WRITE:
          FieldWriteOperation write = (FieldWriteOperation) pipelineOperation;
          validateInputs(pipelineOperation.getName(), write.getInputFields(), validInputsSoFar);
          updateInvalidOutputs(write.getInputFields(), unusedOutputs, redundantOutputs);
          break;
      }
    }

    // At this point unusedOutputs map should only contain those fields as keys which are not used
    // by any operation in the stage as an input. However those fields can still be part of output schema.
    // We want to remove such keys which are part of output schema as well.

    // We cannot simply do "unusedOutputs.removeAll(stageInputOutput.getOutputs()))"
    // Consider following case assuming d is part of output schema:
    // OP1: [a, b] -> [d]
    // OP2: [b] -> [d]
    // Here outout d from OP1 is redundant, since the d in output schema will always come from OP2.
    // However d will not be in the redundantOutputs map, as we only put the redundant fields if they
    // appear in input of some operation. Such redundancy should cause validation checks to fail.
    Iterator<Map.Entry<String, List<FieldOperation>>> iterator = unusedOutputs.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, List<FieldOperation>> next = iterator.next();
      String field = next.getKey();
      List<FieldOperation> origins = next.getValue();

      if (stageOutputs.contains(field)) {
        if (origins.size() > 1) {
          // size will be greater than 1 only if its redundant
          origins.remove(origins.size() - 1);
        } else {
          iterator.remove();
        }
      } else {
        // If this field is not in the output schema of the stage, it is valid.
        // For example, a Joiner joins two datasets D1,D2 based on the joiner key D1.K1, D2.K2, and
        // decides to drop the joiner key in the output schema. The operation
        // [D1.K1, D2.K2] ->[K1, K2] is a valid even though K1,K2 are not in the output schema.

        // this means the field has redundant, TODO: CDAP-15785 revist redundant validation
        if (origins.size() > 1) {
          continue;
        }

        // Origin is empty means this is an generated field, it can be considered valid.
        // If the size is 1, this field has no redundant operations, it is valid.
        iterator.remove();
      }
    }

    this.invalidOutputs.putAll(unusedOutputs.entrySet().stream().collect(
      Collectors.toMap(Map.Entry::getKey,
                       e -> e.getValue().stream().map(FieldOperation::getName).collect(Collectors.toList()))));
    this.invalidOutputs.putAll(redundantOutputs.entrySet().stream().collect(
      Collectors.toMap(Map.Entry::getKey,
                       e -> e.getValue().stream().map(FieldOperation::getName).collect(Collectors.toList()))));
  }

  /**
   * Validate the input fields.
   *
   * @param operationName name of the current operation
   * @param inputsToValidate input fields for the current operation to be validated
   * @param validInputsSoFar cumulative set of valid input fields, this contains the
   *                         fields belonging to the input schema and outputs of all
   *                         operations happening before the current operation
   */
  private void validateInputs(String operationName, List<String> inputsToValidate, Set<String> validInputsSoFar) {
    for (String field : inputsToValidate) {
      // check if field is valid input. input is valid if it is in the validInputsSoFar set
      if (validInputsSoFar.contains(field)) {
        continue;
      }
      List<String> originsWithInvalidInput = invalidInputs.computeIfAbsent(field, k -> new ArrayList<>());
      originsWithInvalidInput.add(operationName);
    }
  }

  /**
   * Remove valid outputs from the map outputs which are yet to validate.
   *
   * @param operationInputs inputs to the operation, these can be marked as used and can be removed
   *                        from outputsToValidate
   * @param outputsToValidate cumulative outputs which are not yet validated
   * @param redundantOutputs redundant outputs will be added to this map
   */
  private void updateInvalidOutputs(List<String> operationInputs, Map<String, List<FieldOperation>> outputsToValidate,
                                    Map<String, List<FieldOperation>> redundantOutputs) {
    for (String operationInput : operationInputs) {
      // current operation input field can be removed from outputsToValidate
      // as it is being consumed by current operation
      List<FieldOperation> origins = outputsToValidate.get(operationInput);
      // origins can be null if the field is coming directly from the input schema
      if (origins == null) {
        continue;
      }

      if (origins.size() > 1) {
        // field is outputted by multiple operations.
        // all occurrences of outputs are redundant except the last one
        // for example:
        // OP1: [a, b] -> [c, d]
        // OP2: [c] -> [d]
        // OP3: [d] -> [e]
        // We are currently processing field d and its in the outputsToValidate map as d -> [OP1, OP2]
        // Since OP3 will always read the output of OP2, the output field d for OP1 is redundant
        // Add entry d -> [OP1] in the redundant map
        List<FieldOperation> redundantOrigins =
          redundantOutputs.computeIfAbsent(operationInput, k -> new ArrayList<>());
        redundantOrigins.addAll(origins.subList(0, origins.size() - 1));
      }
      // remove the field so that any occurrence of it later on can be processed as a new field.
      outputsToValidate.remove(operationInput);
    }
  }

  @Nullable
  public InvalidFieldOperations getStageInvalids() {
    if (invalidInputs.isEmpty() && invalidOutputs.isEmpty()) {
      return null;
    }

    return new InvalidFieldOperations(invalidInputs, invalidOutputs);
  }

  /**
   * Builder for {@link StageOperationsValidator}.
   */
  public static class Builder {
    private final List<FieldOperation> pipelineOperations;
    // input fields to the stage
    private final Set<String> stageInputs;
    // output fields from the stage
    private final Set<String> stageOutputs;

    public Builder(List<FieldOperation> pipelineOperations) {
      this.pipelineOperations = Collections.unmodifiableList(new ArrayList<>(pipelineOperations));
      this.stageInputs = new HashSet<>();
      this.stageOutputs = new HashSet<>();
    }

    /**
     * Add field from input schema of the stage.
     *
     * @param input field from input schema
     * @return this
     */
    public Builder addStageInput(String input) {
      this.stageInputs.add(input);
      return this;
    }

    /**
     * Add collection of fields from input schema of the stage.
     *
     * @param inputs fields from input schema of the stage
     * @return this
     */
    public Builder addStageInputs(Collection<String> inputs) {
      this.stageInputs.addAll(inputs);
      return this;
    }

    /**
     * Add field from output schema of the stage.
     *
     * @param output field from output schema
     * @return this
     */
    public Builder addStageOutput(String output) {
      this.stageOutputs.add(output);
      return this;
    }

    /**
     * Add collection of fields which are part of output schema of the stage.
     *
     * @param outputs fields from the output schema
     * @return this
     */
    public Builder addStageOutputs(Collection<String> outputs) {
      this.stageOutputs.addAll(outputs);
      return this;
    }

    /**
     * @return an instance of {@link StageOperationsValidator}
     */
    public StageOperationsValidator build() {
      return new StageOperationsValidator(pipelineOperations, stageInputs, stageOutputs);
    }
  }
}
