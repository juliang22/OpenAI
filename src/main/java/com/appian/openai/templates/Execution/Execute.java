package com.appian.openai.templates.Execution;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.InputStreamBody;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.IntegrationError.IntegrationErrorBuilder;
import com.appian.connectedsystems.templateframework.sdk.configuration.Document;
import com.appian.connectedsystems.templateframework.sdk.configuration.DocumentPropertyDescriptor;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyState;
import com.appian.connectedsystems.templateframework.sdk.diagnostics.IntegrationDesignerDiagnostic;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.image.CreateImageEditRequest;
import com.theokanning.openai.image.CreateImageRequest;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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
  protected IntegrationErrorBuilder error = null;
  protected Gson gson;
  protected String reqBodyKey;
  protected long start;
  protected  Map<String, Object> builtRequestBody = new HashMap<>();;
  protected HttpResponse HTTPResponse;
  protected Map<String,Object> requestDiagnostic;


  public Execute(SimpleConfiguration integrationConfiguration, SimpleConfiguration connectedSystemConfiguration) {
    this.start = System.currentTimeMillis();
    this.connectedSystemConfiguration = connectedSystemConfiguration;
    this.integrationConfiguration = integrationConfiguration;
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
    }
    return response;
  }

  public void build() throws IOException {
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

  public void executeGet() throws IOException {
    this.HTTPResponse = HTTP.get(connectedSystemConfiguration, pathNameModified);
  }


  public void executePostOrPatch() throws IOException {

    HashMap<String, PropertyState> reqBodyProperties = integrationConfiguration.getValue(reqBodyKey);

    if (reqBodyProperties != null && reqBodyProperties.size() > 0) {
      reqBodyProperties.entrySet().forEach(property -> {
        String key = property.getKey();
        PropertyState val = property.getValue();

        // If flat level value has nested values, recursively insert those values, otherwise, insert the value
        Set<String> notNested = new HashSet<>(Arrays.asList("STRING", "INTEGER", "BOOLEAN", "DOCUMENT"));

        autogeneratedKeyInReqBodyError(val.getValue().toString()); // Set error if autogenerated key is in Req Body

        Object flatValue = notNested.contains(val.getType().getTypeDisplayName()) ?
            val.getValue() : buildReqBodyJSON(key, val).get(key);

        // Build the request body json
        builtRequestBody.put(key, flatValue);
      });
    }

    // Checking if there are documents to add to the request body
    Set<DocumentPropertyDescriptor> documents = new HashSet<>();
    integrationConfiguration.getProperties().forEach(property -> {
      if (property instanceof DocumentPropertyDescriptor && integrationConfiguration.getValue(property.getKey()) != null) {
        documents.add((DocumentPropertyDescriptor)property);
      }
    });

    if (documents.size() > 0) {
      MultipartEntityBuilder builder = MultipartEntityBuilder.create();
      builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);


      // adding file components to the multipart request body
      documents.forEach(docProperty -> {
        String docName = docProperty.getKey();
        Document doc = integrationConfiguration.getValue(docName);
        InputStream inputStream = doc.getInputStream();

        File tempFile = null;
        try {
          tempFile = File.createTempFile(doc.getFileName().replaceAll(".png",""), "." + doc.getExtension());
          tempFile.deleteOnExit();
          try (FileOutputStream out = new FileOutputStream(tempFile)) {
            IOUtils.copy(inputStream, out);
          } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
/*        builder.addBinaryBody("image", tempFile);*/

/*        OpenAiService service = new OpenAiService("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");
        CreateImageEditRequest req = CreateImageEditRequest.builder()
            .prompt("penguin on the moon")
            .size("256x256")
            .n(1)
            .responseFormat("url")
            .build();
*//*        service.createImageEdit(req, tempFile, null);*//*
        service.createImageEdit(req, tempFile.getPath(), null);*/



        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.MINUTES)
            .connectTimeout(2, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .callTimeout(2, TimeUnit.MINUTES).build();
        RequestBody requestBody = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", "tree on the moon")
            .addFormDataPart("image", doc.getFileName(),
                RequestBody.create(MediaType.parse("image/png"), tempFile))
            .build();

        Request request = new Request.Builder()
            .header("Authorization", "Bearer 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")
            .url("https://api.openai.com/v1/images/edits")
            .post(requestBody)
            .build();

        try (Response response = client.newCall(request).execute()) {
          if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

          System.out.println(response.body().string());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }


      });


      // Adding text components to the multipart request body
/*      builtRequestBody.forEach((key, val) -> {
        // Adding json to request body
        String jsonEntity = null;
        try {
          jsonEntity = new StringEntity(new ObjectMapper().writeValueAsString(val)).toString();
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }
        builder.addTextBody(key, jsonEntity, ContentType.APPLICATION_JSON);
      });*/

      HTTPResponse = HTTP.multipartPost(connectedSystemConfiguration, pathNameModified, builder.build());
    } else { // No image: send as content-type json
      StringEntity jsonEntity = new StringEntity(new ObjectMapper().writeValueAsString(builtRequestBody));
      HTTPResponse = HTTP.post(connectedSystemConfiguration, pathNameModified, jsonEntity);
    }
  }
}
