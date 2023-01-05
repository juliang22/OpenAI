package com.appian.openai.templates.Execution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.IntegrationError.IntegrationErrorBuilder;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyState;
import com.google.gson.Gson;

import std.ConstantKeys;
import std.Util;

public class Execute implements ConstantKeys {

  protected String pathNameUnmodified;
  protected String pathNameModified;
  protected String api;
  protected String restOperation;
  protected SimpleConfiguration integrationConfiguration;
  protected SimpleConfiguration connectedSystemConfiguration;
  protected IntegrationErrorBuilder error = null;
  protected Gson gson;
  protected String reqBodyKey;

  public Execute(SimpleConfiguration integrationConfiguration, SimpleConfiguration connectedSystemConfiguration) {
    this.connectedSystemConfiguration = connectedSystemConfiguration;
    this.integrationConfiguration = integrationConfiguration;
    String[] pathData = integrationConfiguration.getValue(CHOSEN_ENDPOINT).toString().split(":");
    this.api = pathData[0];
    this.restOperation = pathData[1];
    this.pathNameUnmodified = pathData[2];
    this.pathNameModified = pathData[2];
    this.gson = new Gson();
    this.reqBodyKey = integrationConfiguration.getProperty(REQ_BODY).getLabel();
    buildPathNameWithPathVars();
  }

  public IntegrationErrorBuilder getError() { return this.error; }
  public void buildPathNameWithPathVars() {
    List<String> pathVars = Util.getPathVarsStr(pathNameModified);
    if (pathVars.size() == 0) return;

    pathVars.forEach(key -> {
      String val = integrationConfiguration.getValue(key);
      pathNameModified = pathNameModified.replace("{"+key+"}", val);
    });
  }

  public void build() {
    switch (restOperation) {
      case GET:
        executeGet();
        break;
      case POST:
      case PATCH:
        executePostOrPatch();
        break;
  /*    case (DELETE):
        executeDelete();*/
    }
  }

  public void executeGet() {

    pathNameModified += "?";

    // Pagination
    // TODO: pagination with next parameter
    int pageSize = integrationConfiguration.getValue(PAGESIZE) != null ? integrationConfiguration.getValue(PAGESIZE) : 0;
    if (pageSize > 0) {
      pathNameModified = pathNameModified + "pageSize=" + pageSize + "&";
    }

    // Included Resources
    String includedResourcesKey = Util.removeSpecialCharactersFromPathName(pathNameUnmodified) + INCLUDED_RESOURCES;
    Map<String, PropertyState> includedMap = integrationConfiguration.getValue(includedResourcesKey);
    if (includedMap != null && includedMap.size() > 0) {
      AtomicBoolean firstIncluded = new AtomicBoolean(true);
      includedMap.entrySet().forEach(entry -> {
        if (entry.getValue().getValue().equals(true)) {
          pathNameModified += firstIncluded.get() ? "include=" + entry.getKey() + "," : entry.getKey() + ",";
          firstIncluded.set(false);
        }
      });
      pathNameModified = Util.removeLastChar(pathNameModified) + "&";
    }

    // Sorting
    String sortField = integrationConfiguration.getValue(SORT);
    String sortOrder = integrationConfiguration.getValue(SORT_ORDER);
    if (sortField != null && sortOrder != null) {
      pathNameModified += sortOrder.equals("-") ?
          "sort=-" + sortField + "&" :
          "sort=" + sortField + "&";
    }

    // Filtering
    String filterField = Util.filterRules(integrationConfiguration.getValue(FILTER_FIELD));
    String filterOperator = Util.filterRules(integrationConfiguration.getValue(FILTER_OPERATOR));
    String filterValue = Util.filterRules(integrationConfiguration.getValue(FILTER_VALUE));
    if (filterField != null && filterOperator  != null && filterValue != null) {
      pathNameModified += "filter=" + filterField + ":" + filterOperator + ":" + filterValue + "&";
    }

    // Include Total
    pathNameModified = pathNameModified + "includeTotal=true";

    // If none of the above options were set or if options have been set and there are no more edits required to the pathName
/*    String lastChar = pathNameModified.substring(pathNameModified.length() - 1);
    if (lastChar.equals("&") || lastChar.equals("?")) {
      pathNameModified = Util.removeLastChar(pathNameModified);
    }*/

    System.out.println(pathNameModified);
  }

  public Map<String,Object> buildReqBodyJSON(String key, PropertyState val) {

    Set<String> notNested = new HashSet<>(Arrays.asList("STRING", "INTEGER", "BOOLEAN"));
    Map<String, Object> propertyMap = new HashMap<>();

    // Base case: if the value does not have nested values, insert the value into the map
    if (notNested.contains(val.getType().getTypeDisplayName())) {
      autogeneratedKeyInReqBodyError(val.getValue().toString()); // Set error if autogenerated key is in Req Body
      propertyMap.put(key, val.getValue());
    } else { // The value does have nested values
      // If the nested value is an array, recursively add to that array and put array in the map
      if (val.getValue() instanceof ArrayList) {
        List<Map<String, Object>> propertyArr = new ArrayList<>();
        ((ArrayList<?>)val.getValue()).forEach(property -> {
          Map<String,Object> nestedVal = buildReqBodyJSON(property.toString(), ((PropertyState)property));
          propertyArr.add((Map<String,Object>)nestedVal.get(property.toString()));
        });
        propertyMap.put(key, propertyArr);
      } else {
        // If value is an object, recursively add nested elements to a map
        ((Map<String,PropertyState>)val.getValue()).forEach((innerKey, innerVal) -> {
          // If map already contains the key to nested maps of values, add key/val pair to that map
          Map<String,Object> newKeyVal = buildReqBodyJSON(innerKey, innerVal);
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

  public void autogeneratedKeyInReqBodyError(String key) {
    if (key.equals("text")) {
    error = new IntegrationErrorBuilder()
        .title("Autogenerate property with value 'text' must be removed before sending request")
        .message("Please remove all autogenerated properties from request body before executing request.")
        .detail("Autogenerated properties are marked 'text', 'true', and '100' for string, boolean, and integer " +
            "properties, respectively. Make sure to update these autogenerated properties before making the request.");
    }
  }


  public void executePostOrPatch() {

    Map<String, PropertyState> reqBodyProperties = integrationConfiguration.getValue(reqBodyKey);
    Map<String, Object> reqBodyJsonBuilder = new HashMap<>();
    reqBodyProperties.entrySet().forEach(property -> {
      String key = property.getKey();
      PropertyState val = property.getValue();

      // If flat level value has nested values, recursively insert those values, otherwise, insert the value
      Set<String> notNested = new HashSet<>(Arrays.asList("STRING", "INTEGER", "BOOLEAN"));

      autogeneratedKeyInReqBodyError(val.getValue().toString()); // Set error if autogenerated key is in Req Body

      Object flatValue = notNested.contains(val.getType().getTypeDisplayName()) ?
          val.getValue() :
          buildReqBodyJSON(key, val).get(key);

      // If there is no error, build the request body json
      reqBodyJsonBuilder.put(key, flatValue);
    });

    // creating request body format that Guidewire expects
    HashMap<String, Map<String, Object>> jsonAttributes = new HashMap<>();
    jsonAttributes.put("attributes", reqBodyJsonBuilder);
    HashMap<String,HashMap<String,Map<String,Object>>> jsonData = new HashMap<>();
    jsonData.put("data", jsonAttributes);

    String JSON = gson.toJson(jsonData);
    System.out.println(JSON);
  }


}
