#
# Copyright © 2023 Cask Data, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
#
@Namespaceadmin
Feature: NameSpaceAdmin - Validate nameSpace admin design time scenarios

  @Namespaceadmin
  Scenario: Validate user is able to create new namespace preferences and able to delete the added namespace preferences successfully
    Given Open Datafusion Project to configure pipeline
    Then Click on the Hamburger bar on the left panel
    Then Click on NameSpace Admin link from the menu
    Then Click "preferences" tab from Configuration page for "default" Namespace
    Then Click on edit namespace preferences to set namespace preferences
    Then Set system preferences with key: "keyValue" and value: "systemPreferences1"
    Then Click on the Save & Close preferences button
    Then Click on edit namespace preferences to set namespace preferences
    Then Delete the preferences
    Then Click on the Save & Close preferences button

  Scenario: Validate user is able to add multiple namespace preferences inside namespace admin successfully
    Given Open Datafusion Project to configure pipeline
    Then Click on the Hamburger bar on the left panel
    Then Click on NameSpace Admin link from the menu
    Then Click "preferences" tab from Configuration page for "default" Namespace
    Then Click on edit namespace preferences to set namespace preferences
    Then Set system preferences with key: "keyValue" and value: "systemPreferences2"
    Then Click on the Save & Close preferences button
    Then Click on edit namespace preferences to set namespace preferences
    Then Delete the preferences
    Then Delete the preferences
    Then Click on the Save & Close preferences button

  Scenario: Validate user is able reset the namespace preferences added inside namespace admin successfully
    Given Open Datafusion Project to configure pipeline
    Then Click on the Hamburger bar on the left panel
    Then Click on NameSpace Admin link from the menu
    Then Click "preferences" tab from Configuration page for "default" Namespace
    Then Click on edit namespace preferences to set namespace preferences
    Then Set system preferences with key: "keyValue" and value: "systemPreferences1"
    Then Reset the preferences
    Then Verify the reset is successful for added preferences