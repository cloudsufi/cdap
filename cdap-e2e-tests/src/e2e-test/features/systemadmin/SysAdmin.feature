@Sysadmin
Feature: Sysadmin - Validate system admin page flow

  @Sysadmin
  Scenario:Validate user is able to create new system preferences inside system admin
    Given Open Datafusion Project to configure pipeline
    Then Click on the "System Admin" link from menu
    Then Click on the Configuration link on the System admin page
    Then Open "systemPreferences" option from Configuration page
    Then Click on edit system preferences to set system preferences
    Then Enter key value pairs to set preferences: "keyValue" with values from json: "test1"
    Then Click on Save and Close button to save the preferences

  Scenario:Validate user is able edit the system preferences inside system admin
    Given Open Datafusion Project to configure pipeline
    Then Click on the "System Admin" link from menu
    Then Click on the Configuration link on the System admin page
    Then Open "systemPreferences" option from Configuration page
    Then Click on edit system preferences to set system preferences
    Then Enter key value pairs to set preferences: "keyValue" with values from json: "test1"
    Then Click on Save and Close button to save the preferences
    Then Open "systemPreferences" option from Configuration page
    Then Click on edit system preferences to set system preferences
    Then Enter key value pairs to set preferences: "keyValue" with values from json: "test2"
    Then Click on Save and Close button to save the preferences

  Scenario:Validate user is able reset the system preferences added inside system admin
    Given Open Datafusion Project to configure pipeline
    Then Click on the "System Admin" link from menu
    Then Click on the Configuration link on the System admin page
    Then Open "systemPreferences" option from Configuration page
    Then Click on edit system preferences to set system preferences
    Then Enter key value pairs to set preferences: "keyValue" with values from json: "test1"
    Then Click on Reset button to reset the added preferences
    Then Verify the reset is successful for added preferences

  Scenario:Validate user is able to delete the system preferences added inside system admin
    Given Open Datafusion Project to configure pipeline
    Then Click on the "System Admin" link from menu
    Then Click on the Configuration link on the System admin page
    Then Open "systemPreferences" option from Configuration page
    Then Click on edit system preferences to set system preferences
    Then Enter key value pairs to set preferences: "keyValue" with values from json: "test2"
    Then Click on Save and Close button to save the preferences
    Then Open "systemPreferences" option from Configuration page
    Then Click on edit system preferences to set system preferences
    Then Click on Delete button to delete the added preferences
    Then Click on Save and Close button to save the preferences

  Scenario:Validate user is able to successfully reload system artifacts using reload
    Given Open Datafusion Project to configure pipeline
    Then Click on the "System Admin" link from menu
    Then Click on the Configuration link on the System admin page
    Then Click on Reload System Artifacts from the System admin page
    Then Click on Reload button on popup to reload the System Artifacts successfully
    Then Open "systemPreferences" option from Configuration page

  Scenario:Validate user is able to open compute profile page and select a provisioner
    Given Open Datafusion Project to configure pipeline
    Then Click on the "System Admin" link from menu
    Then Click on the Configuration link on the System admin page
    Then Click on the Compute Profile from the System admin page
    Then Click on create compute profile button
    Then Select a provisioner: "existingDataProc" for the compute profile
    Then Click on close button of compute profile page
