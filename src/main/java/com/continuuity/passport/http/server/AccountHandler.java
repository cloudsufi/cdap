package com.continuuity.passport.http.server;

import com.continuuity.passport.core.meta.Account;
import com.continuuity.passport.core.meta.AccountSecurity;
import com.continuuity.passport.core.meta.UsernamePasswordApiKeyCredentials;
import com.continuuity.passport.core.meta.VPC;
import com.continuuity.passport.core.status.AuthenticationStatus;
import com.continuuity.passport.impl.AuthenticatorImpl;
import com.continuuity.passport.impl.DataManagementServiceImpl;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.shiro.util.StringUtils;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Annotations for endpoints, method types and data types for handling Http requests
 * Note: Jersey has a limitation of not allowing multiple resource handlers share the same path.
 *       As a result we are needing to have all the code in a single file. This will be potentially
 *       huge. Need to find a work-around.
 */


@Path("/passport/v1/account/")
public class AccountHandler {

  @Path("{id}")
  @GET
  @Produces("application/json")
  public Response getAccountInfo(@PathParam("id") int id){

    Account account = DataManagementServiceImpl.getInstance().getAccount(id);
    if (account != null){
      return Response.ok(account.toString()).build();
    }
    else {
      return Response.status(Response.Status.NOT_FOUND)
        .entity(Utils.getJsonError("Account not found"))
        .build();
    }
  }



  @Path("{id}/password")
  @PUT
  @Produces("application/json")
  @Consumes("application/json")
  public Response changePassword(@PathParam("id") int id, String data){

    try {
      JsonParser parser = new JsonParser();
      JsonElement element = parser.parse(data);
      JsonObject jsonObject = element.getAsJsonObject();

      String oldPassword = jsonObject.get("old_password") == null? null : jsonObject.get("old_password").getAsString();
      String newPassword = jsonObject.get("new_password") == null? null : jsonObject.get("new_password").getAsString();

      if ( (oldPassword == null ) || (oldPassword.isEmpty()) ||
        (newPassword == null) || (newPassword.isEmpty()) ) {
        return Response.status(Response.Status.BAD_REQUEST)
          .entity(Utils.getJson("FAILED", "Must pass in old_password and new_password"))
          .build();
     }

      DataManagementServiceImpl.getInstance().changePassword(id,oldPassword,newPassword);
      //Contract for the api is to return updated account to avoid a second call from the caller to get the
      // updated account
      Account account = DataManagementServiceImpl.getInstance().getAccount(id);
      if ( account !=null) {
        return Response.ok(account.toString()).build();
      }
      else {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(Utils.getJson("FAILED", "Failed to get updated account"))
          .build();
      }
    }
    catch (Exception e){
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJson("FAILED","Download confirmation failed",e))
        .build();
    }
  }

  @Path("{id}/downloaded")
  @PUT
  @Produces("application/json")
  public Response confirmDownload(@PathParam("id") int id){

    try {

      DataManagementServiceImpl.getInstance().confirmDownload(id);
      //Contract for the api is to return updated account to avoid a second call from the caller to get the
      // updated account
      Account account = DataManagementServiceImpl.getInstance().getAccount(id);
      if ( account !=null) {
        return Response.ok(account.toString()).build();
      }
      else {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(Utils.getJson("FAILED", "Failed to get updated account"))
          .build();
      }
    }
    catch (Exception e){
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(Utils.getJson("FAILED","Download confirmation failed",e))
        .build();
    }
  }

  @Path ("{id}/update")
  @PUT
  @Produces("application/json")
  @Consumes("application/json")
  public Response updateAccount(@PathParam("id")int id, String data){

    try {
      JsonParser parser = new JsonParser();
      JsonElement element = parser.parse(data);
      JsonObject jsonObject = element.getAsJsonObject();

      Map<String,Object> updateParams = new HashMap<String,Object>();

      String firstName = jsonObject.get("first_name") == null? null : jsonObject.get("first_name").getAsString();
      String lastName = jsonObject.get("last_name") == null? null : jsonObject.get("last_name").getAsString();
      String company = jsonObject.get("company") == null? null : jsonObject.get("company").getAsString();

      //TODO: Find a better way to update the map
      if ( firstName != null ) {
        updateParams.put("first_name",firstName);
      }

      if ( lastName != null ) {
        updateParams.put("last_name",lastName);
      }

      if ( company != null ) {
        updateParams.put("company",company);
      }

      DataManagementServiceImpl.getInstance().updateAccount(id,updateParams);
      //Contract for the api is to return updated account to avoid a second call from the caller to get the
      // updated account
      Account account = DataManagementServiceImpl.getInstance().getAccount(id);
      if ( account !=null) {
        return Response.ok(account.toString()).build();
      }
      else {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(Utils.getJson("FAILED", "Failed to get updated account"))
          .build();
      }
    }
    catch(Exception e) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(Utils.getJson("FAILED", "Account Update Failed", e))
        .build();
    }
  }

  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response createAccount(String data) {

    try{
      JsonParser parser = new JsonParser();
      JsonElement element = parser.parse(data);
      JsonObject jsonObject = element.getAsJsonObject();

      String firstName = jsonObject.get("first_name") == null? null : jsonObject.get("first_name").getAsString();
      String lastName = jsonObject.get("first_name") == null? null : jsonObject.get("last_name").getAsString();
      String emailId = jsonObject.get("email_id") == null? null : jsonObject.get("email_id").getAsString();
      String company = jsonObject.get("company") == null? null : jsonObject.get("company").getAsString();

      if ( (firstName == null) || (lastName == null) || (emailId == null) || (company == null) ){
        return Response.status(Response.Status.BAD_REQUEST)
          .entity(Utils.getJson("FAILED", "First/last name or email id or company is missing")).build();
      }
      else {
        Account account = DataManagementServiceImpl.getInstance().registerAccount(new Account(firstName,
          lastName,company,emailId));
        return Response.ok(account.toString()).build();
      }
    }
    catch (Exception e){
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(Utils.getJson("FAILED", "Account Creation Failed", e))
        .build();
    }
  }

  @Path("{id}/confirmed")
  @PUT
  @Produces("application/json")
  @Consumes("application/json")
  public Response confirmAccount(String data, @PathParam("id") int id){
    try{
      JsonParser parser = new JsonParser();
      JsonElement element = parser.parse(data);
      JsonObject jsonObject = element.getAsJsonObject();
      JsonElement password = jsonObject.get("password");
      String accountPassword = StringUtils.EMPTY_STRING;

      if(password !=null){
        accountPassword = password.getAsString();
      }

      if ( accountPassword.isEmpty()){
        return Response.status(Response.Status.BAD_REQUEST)
          .entity(Utils.getJson("FAILED","Password is missing")).build();
      }
      else {
        AccountSecurity security = new AccountSecurity(id, accountPassword);
        DataManagementServiceImpl.getInstance().confirmRegistration(security);
        //Contract for the api is to return updated account to avoid a second call from the caller to get the
        // updated account
        Account account = DataManagementServiceImpl.getInstance().getAccount(id);
        if ( account !=null) {
          return Response.ok(account.toString()).build();
        }
        else {
          return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(Utils.getJson("FAILED", "Failed to get updated account"))
            .build();
        }
      }
    }
    catch (Exception e){
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(Utils.getJson("FAILED","Account Confirmation Failed",e))
        .build();
    }
  }


  @Path("{id}/vpc/create")
  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response createVPC(String data, @PathParam("id")int id)  {

    try {
      JsonParser parser = new JsonParser();
      JsonElement element = parser.parse(data);
      JsonObject jsonObject = element.getAsJsonObject();

      String vpcName  = jsonObject.get("vpc_name") == null ? null : jsonObject.get("vpc_name").getAsString();
      String vpcLabel = jsonObject.get("vpc_label") == null ? null : jsonObject.get("vpc_label").getAsString();

      if ( (vpcName!= null) && (!vpcName.isEmpty()) && (vpcLabel!=null) && ( !vpcLabel.isEmpty()) ){
        VPC vpc= DataManagementServiceImpl.getInstance().addVPC(id, new VPC(vpcName,vpcLabel));
        return Response.ok(vpc.toString()).build();
      }
      else {
        return Response.status(Response.Status.BAD_REQUEST)
          .entity(Utils.getJson("FAILED", "VPC creation failed. vpc_name is missing"))
          .build();
      }
    }
    catch (Exception e ){
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(Utils.getJson("FAILED", "VPC Creation Failed", e))
        .build();

    }
  }

  @Path("{id}/vpc")
  @GET
  @Produces("application/json")
  public Response getVPC(@PathParam("id") int id) {

    try{
      List<VPC> vpcList = DataManagementServiceImpl.getInstance().getVPC(id);
      Gson gson = new Gson();
      if (vpcList.isEmpty()) {
        return Response.ok("[]").build();
      }
      else {
        return Response.ok(gson.toJson(vpcList)).build();
      }
    }
    catch(Exception e){
      return Response.status(Response.Status.BAD_REQUEST)
        .entity(Utils.getJsonError("VPC get Failed", e))
        .build();
    }
  }

  @Path("authenticate")
  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response authenticate(String data){

    JsonParser parser = new JsonParser();
    JsonElement element = parser.parse(data);
    JsonObject jsonObject = element.getAsJsonObject();

    String password = jsonObject.get("password") == null? null : jsonObject.get("password").getAsString();
    String emailId = jsonObject.get("email_id") == null? null : jsonObject.get("email_id").getAsString();


    try {
      AuthenticationStatus status = AuthenticatorImpl.getInstance()
        .authenticate(new UsernamePasswordApiKeyCredentials(emailId, password,
          StringUtils.EMPTY_STRING));
      if (status.getType().equals(AuthenticationStatus.Type.AUTHENTICATED)) {
        //TODO: Better naming for authenticatedJson?
        return Response.ok(Utils.getAuthenticatedJson(status.getMessage())).build();
      }
      else {
        return Response.status(Response.Status.UNAUTHORIZED).entity(
          Utils.getAuthenticatedJson("Authentication Failed." , "Either user doesn't exist or password doesn't match"))
          .build();
      }
    } catch (Exception e) {

      return    Response.status(Response.Status.UNAUTHORIZED).entity(
        Utils.getAuthenticatedJson("Authentication Failed.",e.getMessage())).build();
    }
  }
}
