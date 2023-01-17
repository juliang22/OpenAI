package com.appian.openai.templates.Execution;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.IntegrationError.IntegrationErrorBuilder;
import com.appian.connectedsystems.templateframework.sdk.configuration.Document;
import com.appian.connectedsystems.templateframework.sdk.configuration.DocumentPropertyDescriptor;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyState;
import com.appian.connectedsystems.templateframework.sdk.diagnostics.IntegrationDesignerDiagnostic;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import std.ConstantKeys;
import std.HTTP;
import std.HttpResponse;
import std.Util;

public class Execute implements ConstantKeys {

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
  protected long start;
  protected Map<String, Object> builtRequestBody = new HashMap<>();
  protected HttpResponse HTTPResponse;
  protected Map<String,Object> requestDiagnostic;


  public Execute(SimpleConfiguration integrationConfiguration, SimpleConfiguration connectedSystemConfiguration,
      ExecutionContext executionContext) {
    this.start = System.currentTimeMillis();
    this.connectedSystemConfiguration = connectedSystemConfiguration;
    this.integrationConfiguration = integrationConfiguration;
    this.executionContext = executionContext;
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

  public IntegrationErrorBuilder getError() { return this.error; }
  public void buildPathNameWithPathVars() {
    List<String> pathVars = Util.getPathVarsStr(pathNameModified);
    if (pathVars.size() == 0) return;

    pathVars.forEach(key -> {
      String val = integrationConfiguration.getValue(key);
      pathNameModified = pathNameModified.replace("{"+key+"}", val);
    });
  }

  public void setDiagnostics() {
    Map<String,Object> requestDiagnostic = new HashMap<>();
    requestDiagnostic.put("Endpoint: ", pathNameUnmodified);
    requestDiagnostic.put("Endpoint with Path Params: ", pathNameModified);
    if (this.reqBodyKey != null) {
      requestDiagnostic.put("Request Body", this.builtRequestBody);
    }

    this.requestDiagnostic = requestDiagnostic;
  }

  public Map<String,Object> getDiagnostics() {return this.requestDiagnostic;}

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

    //TODO: Document handling
    if (HTTPResponse != null) {
      response.put("Response",HTTPResponse.getResponse());
      response.put("Status Code: ", HTTPResponse.getStatusCode());
      if (HTTPResponse.getDocument() != null) {
        response.put("Document: ", HTTPResponse.getDocument());
      }
    }
    return response;
  }

  public void build() throws IOException {
    switch (restOperation) {
      case GET:
      case DELETE:
        executeGetOrDelete();
        break;
      case POST:
      case PATCH:
        executePostOrPatch();
        break;

      //  custom endpoint
      case (JSONLINES):
        executeJsonLines();
        break;
    }
  }


  public Map<String,Object> parseReqBodyJSON(String key, PropertyState val) {

    Set<String> notNested = new HashSet<>(Arrays.asList("STRING", "INTEGER", "BOOLEAN"));
    Map<String, Object> propertyMap = new HashMap<>();

    // Base case: if the value does not have nested values, insert the value into the map
    if (notNested.contains(val.getType().getTypeDisplayName())) {
      if (val.getValue().toString().equals("text")) { // Set error if autogenerated key is in Req Body
        setError(
            "Autogenerated property with value 'text' must be removed before sending request",
            "Please remove all autogenerated properties from request body before executing request.",
            "Autogenerated properties are marked 'text', 'true', and '100' for string, boolean, and integer " +
                "properties, respectively. Make sure to update these autogenerated properties before making the request."
        );
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

  public void buildRequestBodyJSON(HashMap<String, PropertyState> reqBodyProperties) {
    // Converting PropertyState request body from ui into Map<String, Object> where objects could be more nested JSON
    reqBodyProperties.forEach((key, val) -> {

      // If flat level value has nested values, recursively insert those values, otherwise, insert the value
      Set<String> notNested = new HashSet<>(Arrays.asList("STRING", "INTEGER", "BOOLEAN", "DOCUMENT"));

      if (val.getValue().toString().equals("text")) { // Set error if autogenerated key is in Req Body
        setError(
            "Autogenerated property with value 'text' must be removed before sending request",
            "Please remove all autogenerated properties from request body before executing request.",
            "Autogenerated properties are marked 'text', 'true', and '100' for string, boolean, and integer " +
                "properties, respectively. Make sure to update these autogenerated properties before making the request."
        );
      }

      // flatValue could be a string or more nested Json of type Map<String, Object>
      Object flatValue = notNested.contains(val.getType().getTypeDisplayName()) ?
          val.getValue() : parseReqBodyJSON(key, val).get(key);

      // Build the request body json
      builtRequestBody.put(key, flatValue);
    });
  }

  public void setError(String title, String message, String detail) {
    error = new IntegrationErrorBuilder().title(title).message(message).detail(detail);
  }

  public void executeGetOrDelete() throws IOException {
    this.HTTPResponse = HTTP.get(connectedSystemConfiguration, pathNameModified);
  }


  public void executePostOrPatch() throws IOException {

    HashMap<String, PropertyState> reqBodyProperties = integrationConfiguration.getValue(reqBodyKey);
    if (reqBodyProperties == null || reqBodyProperties.size() <= 0) {
      RequestBody body = RequestBody.create("", null);
      HTTPResponse = HTTP.post(connectedSystemConfiguration, pathNameModified, body);
      return;
    }

    buildRequestBodyJSON(reqBodyProperties);
    if (getError() != null) return;


    // Checking if there are documents to add to the request body
    Set<DocumentPropertyDescriptor> documents = new HashSet<>();
    integrationConfiguration.getProperties().forEach(property -> {
      if (property instanceof DocumentPropertyDescriptor && integrationConfiguration.getValue(property.getKey()) != null) {
        documents.add((DocumentPropertyDescriptor)property);
      }
    });

    // If there are documents, send multipart post
    if (documents.size() > 0) {
      MultipartEntityBuilder builder = MultipartEntityBuilder.create();
      builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);


      // adding file components to the multipart request body
      Map<String,File> files = new HashMap<>();
      documents.forEach(docProperty -> {
        String docName = docProperty.getKey();
        Document doc = integrationConfiguration.getValue(docName);
        InputStream inputStream = doc.getInputStream();

        try {
          String fileNameWithoutExtension = doc.getFileName().substring(0, doc.getFileName().lastIndexOf("."));
          File tempFile = File.createTempFile(fileNameWithoutExtension, "." + doc.getExtension());
          tempFile.deleteOnExit();
          files.put(docName, tempFile);
          try (FileOutputStream out = new FileOutputStream(tempFile)) {
            IOUtils.copy(inputStream, out);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });

      HTTPResponse = HTTP.multipartPost(connectedSystemConfiguration, pathNameModified, builtRequestBody, files);
    } else { // No image: Just sent request body as content-type/json
      String jsonString = new ObjectMapper().writeValueAsString(builtRequestBody);
      RequestBody body = RequestBody.create(jsonString, MediaType.get("application/json; charset=utf-8"));
      HTTPResponse = HTTP.post(connectedSystemConfiguration, pathNameModified, body);
    }
  }

  public void executeJsonLines() {
    // Build request body and throw error if there is an autogenerated property ("text") in a val
    HashMap<String, PropertyState> reqBodyProperties = integrationConfiguration.getValue(reqBodyKey);
    buildRequestBodyJSON(reqBodyProperties);
    if (getError() != null) return;


    JsonObject jsonObject = gson.toJsonTree(builtRequestBody).getAsJsonObject();
    JsonArray jsonArray = jsonObject.getAsJsonArray("toJsonLines");
    // TODO: fix setError
    if (jsonArray == null) {
        setError(
            "Incorrect formatting.",
            "'toJsonLines' key does not exist.",
            "Refresh the page and click 'Generate Example Expression' to get correct format."
        );
        return;
    }

    StringBuilder jsonLines = new StringBuilder();
    for (JsonElement element : jsonArray) {
      jsonLines.append(element.toString()).append("\n");
    }


    ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonLines.toString().getBytes(StandardCharsets.UTF_8));
    String fileName = builtRequestBody.get(OUTPUT_FILENAME).toString();
    Long folderID = integrationConfiguration.getValue(FOLDER);
    Document document = executionContext.getDocumentDownloadService().downloadDocument(inputStream, folderID, fileName);
    HTTPResponse = new HttpResponse(200, "JSON Lines file successfully created.", null);
    HTTPResponse.setDocument(document);


  }
}
