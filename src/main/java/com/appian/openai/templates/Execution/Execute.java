package com.appian.openai.templates.Execution;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.IntegrationError.IntegrationErrorBuilder;
import com.appian.connectedsystems.templateframework.sdk.configuration.Document;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyState;
import com.appian.connectedsystems.templateframework.sdk.diagnostics.IntegrationDesignerDiagnostic;
import com.google.gson.Gson;

import std.ConstantKeys;
import std.HTTP;
import std.HttpResponse;
import std.Util;

public abstract class Execute implements ConstantKeys {

  protected String pathNameUnmodified;
  protected String pathNameModified;
  protected String api;
  protected String restOperation;
  protected SimpleConfiguration integrationConfiguration;
  protected SimpleConfiguration connectedSystemConfiguration;
  protected ExecutionContext executionContext;
  protected IntegrationErrorBuilder error = null;
  protected Gson gson;
  protected String reqBodyKey;
  protected Long start;
  protected Map<String, Object> builtRequestBody = new HashMap<>();
  protected HttpResponse HTTPResponse;
  protected Map<String,Object> requestDiagnostic;
  protected HTTP httpService;

  public abstract void buildExecution() throws IOException;
  public abstract void executeGet() throws IOException ;
  public abstract void executePost() throws IOException ;
  public abstract void executePatch() throws IOException ;
  public abstract void executeDelete() throws IOException ;

  public Execute(
      SimpleConfiguration integrationConfiguration,
      SimpleConfiguration connectedSystemConfiguration,
      ExecutionContext executionContext) {
    this.start = System.currentTimeMillis();
    this.connectedSystemConfiguration = connectedSystemConfiguration;
    this.integrationConfiguration = integrationConfiguration;
    this.executionContext = executionContext;
    this.httpService = new HTTP(this);
    String[] pathData = integrationConfiguration.getValue(CHOSEN_ENDPOINT).toString().split(":");
    this.api = pathData[0];
    this.restOperation = pathData[1];
    this.pathNameUnmodified = pathData[2];
    this.pathNameModified = ROOT_URL + pathData[2];
    this.gson = new Gson();
    this.reqBodyKey = integrationConfiguration.getProperty(REQ_BODY) != null ?
            integrationConfiguration.getProperty(REQ_BODY).getLabel() :
            null;
    buildPathNameWithPathVars();
  }

  // Getting Appian execution details
  public SimpleConfiguration getConnectedSystemConfiguration() {
    return connectedSystemConfiguration;
  }
  public SimpleConfiguration getIntegrationConfiguration() {
    return integrationConfiguration;
  }
  public ExecutionContext getExecutionContext() {
    return executionContext;
  }

  // Error setting/getting
  public IntegrationErrorBuilder getError() { return this.error; }
  public void setError(String title, String message, String detail) {
    error = new IntegrationErrorBuilder().title(title).message(message).detail(detail);
  }

  // Getting pathName with user inputted path parameters
  public void buildPathNameWithPathVars() {
    List<String> pathVars = Util.getPathVarsStr(pathNameModified);
    if (pathVars.size() == 0) return;

    pathVars.forEach(key -> {
      String val = integrationConfiguration.getValue(key);
      pathNameModified = pathNameModified.replace("{"+key+"}", val);
    });
  }

  // getting/setting diagnostics
  public Map<String,Object> getDiagnostics() {return this.requestDiagnostic;}

  public void setDiagnostics() {
    Map<String,Object> requestDiagnostic = new HashMap<>();
    requestDiagnostic.put("Endpoint: ", pathNameUnmodified);
    requestDiagnostic.put("Endpoint with Path Params: ", pathNameModified);
    if (this.reqBodyKey != null) {
      requestDiagnostic.put("Request Body", this.builtRequestBody);
    }
    this.requestDiagnostic = requestDiagnostic;
  }

  public IntegrationDesignerDiagnostic getDiagnosticsUI() {
    setDiagnostics();
    return IntegrationDesignerDiagnostic.builder()
        .addExecutionTimeDiagnostic(System.currentTimeMillis() - start)
        .addRequestDiagnostic(getDiagnostics())
        .addResponseDiagnostic(getResponse())
        .build();
  }

  public Map<String,Object> getResponse() {
    Map<String,Object> response = new HashMap<>();

    if (HTTPResponse != null) {
      response.put("Response", HTTPResponse.getResponse());
      response.put("Status Code: ", HTTPResponse.getStatusCode());

      // If files were returned from the http response, add them to Appian response in designer
      List<Document> documents = HTTPResponse.getDocuments();
      if (documents == null) return response;
      if (documents.size() == 1) {
        documents.forEach(doc -> response.put("Document: ", doc));
      } else {
        AtomicInteger index = new AtomicInteger(1);
        documents.forEach(doc -> response.put("Document " + index.getAndIncrement() + ":", doc));
      }
    }


    return response;
  }

  // buildRequestBodyJSON() helper function to recursively extract user inputted values from Appian property descriptors
  public Map<String,Object> parseReqBodyJSON(String key, PropertyState val) {

    Set<String> notNested = new HashSet<>(Arrays.asList("STRING", "INTEGER", "BOOLEAN"));
    Map<String, Object> propertyMap = new HashMap<>();

    // Base case: if the value does not have nested values, insert the value into the map
    if (notNested.contains(val.getType().getTypeDisplayName())) {
      if (val.getValue() == null) {
        setError("No value set for: "+ key, "Set value for " + key + " or remove it from the request body.", "");
      } else if (val.getValue().toString().equals("text")) { // Set error if autogenerated key is in Req Body
        setError(AUTOGENERATED_ERROR_TITLE, AUTOGENERATED_ERROR_MESSAGE, AUTOGENERATED_ERROR_DETAIL);
      }
      propertyMap.put(key, val.getValue());
    } else { // The value does have nested values
      // If the nested value is an array, recursively add to that array and put array in the map
      if (val.getValue() instanceof ArrayList) {
        List<Map<String, Object>> propertyArr = new ArrayList<>();
        ((ArrayList<?>)val.getValue()).forEach(property -> {
          Map<String,Object> nestedVal = parseReqBodyJSON(property.toString(), ((PropertyState)property));
          propertyArr.add((Map<String,Object>)nestedVal.get(property.toString()));
        });
        propertyMap.put(key, propertyArr);
      } else {
        // If value is an object, recursively add nested elements to a map
        ((Map<String,PropertyState>)val.getValue()).forEach((innerKey, innerVal) -> {
          // If map already contains the key to nested maps of values, add key/val pair to that map
          Map<String,Object> newKeyVal = parseReqBodyJSON(innerKey, innerVal);
          if (propertyMap.containsKey(key)) {
            ((Map<String, Object>)propertyMap.get(key)).put(innerKey, newKeyVal.get(innerKey));
          } else {
            propertyMap.put(key, newKeyVal);
          }
        });
      }
    }
    return propertyMap;
  }

  // Builds request body json from Appian property descriptors
  public void buildRequestBodyJSON(HashMap<String, PropertyState> reqBodyProperties) {
    // Converting PropertyState request body from ui into Map<String, Object> where objects could be more nested JSON
    reqBodyProperties.entrySet().forEach(prop -> {
      String key = prop.getKey();
      PropertyState val = prop.getValue();

      // If flat level value has nested values, recursively insert those values, otherwise, insert the value
      Set<String> notNested = new HashSet<>(Arrays.asList("STRING", "INTEGER", "BOOLEAN", "DOCUMENT"));

      if (val == null || val.getValue() == null || val.getValue().equals("")) {
        setError("No value set for: "+ key, "Set value for " + key + " or remove it from the request body.", "");
      } else if (val.getValue().toString().equals("text")) { // Set error if autogenerated key is in Req Body
         setError(AUTOGENERATED_ERROR_TITLE, AUTOGENERATED_ERROR_MESSAGE, AUTOGENERATED_ERROR_DETAIL);
      } else {
        // flatValue could be a string or more nested Json of type Map<String, Object>
        Object flatValue = notNested.contains(val.getType().getTypeDisplayName()) ?
            val.getValue() : parseReqBodyJSON(key, val).get(key);

        // Build the request body json
        builtRequestBody.put(key, flatValue);
      }
    });
  }

}
