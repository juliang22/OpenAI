package std;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.configuration.Document;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyDescriptor;
import com.appian.openai.templates.Execution.Execute;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HTTP implements ConstantKeys {
protected Execute executionService;

  public HTTP(Execute executionService) {
    this.executionService = executionService;
  }

  public static OkHttpClient getHTTPClient(SimpleConfiguration connectedSystemConfiguration, String contentType) {
    final String token = connectedSystemConfiguration.getValue(API_KEY);
    final String org = connectedSystemConfiguration.getValue(ORGANIZATION);

    return new OkHttpClient.Builder().connectTimeout(2, TimeUnit.MINUTES)
        .connectTimeout(2, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .callTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .addInterceptor(chain -> {
          Request.Builder newRequest = chain.request()
              .newBuilder()
              .addHeader("Content-Type", contentType)
              .addHeader("Authorization", "Bearer " + token);
          if (org != null)
            newRequest.addHeader("OpenAI-Organization", org);
          return chain.proceed(newRequest.build());
        })
        .build();
  }

  public static HttpResponse testAuth(SimpleConfiguration connectedSystemConfiguration) throws IOException {
    final String testURL = "https://api.openai.com/v1/models";
    Request request = new Request.Builder().url(testURL).build();
    OkHttpClient client = getHTTPClient(connectedSystemConfiguration, "application/json");
    try (Response response = client.newCall(request).execute()) {
      ResponseBody body = response.body();
      if (body == null) {
        return new HttpResponse(204,
            "Null value returned", new HashMap<String, Object>(){{put("Error","Response is empty");}});
      }
      HashMap<String,Object> responseEntity = new ObjectMapper().readValue(body.string(), new TypeReference<HashMap<String,Object>>() {});
      return new HttpResponse(response.code(), response.message(), responseEntity);
    }
  }

  public HttpResponse executeRequest(OkHttpClient client, Request request) throws IOException {

    try (Response response = client.newCall(request).execute()) {
      // Check if null value is returned
      ResponseBody body = response.body();
      if (body == null) {
        return new HttpResponse(204,
            "Null value returned", new HashMap<String, Object>(){{put("Error","Response is empty");}});
      }

      // Set response properties
      int code = response.code();
      String message = response.message();
      String bodyStr = body.string();
      ObjectMapper mapper = new ObjectMapper();
      HashMap<String,Object> responseEntity = new HashMap<>();

      // If the response is in JSONLines Format
      if (executionService.getPathNameUnmodified().equals("/files/{file_id}/content") && code == 200) {
        String[] jsonObjects = bodyStr.split("\n");
        List<Map<String, Object>> responseList = new ArrayList<>();
        for (String jsonObject : jsonObjects) {
          HashMap<String,Object> inner = new HashMap<>(mapper.readValue(jsonObject, new TypeReference<HashMap<String,Object>>() {
          }));
          responseList.add(inner);
        }
        responseEntity.put("Response", responseList);
      } else {
        // Normal json response sent back
        responseEntity.putAll(mapper.readValue(bodyStr, new TypeReference<HashMap<String,Object>>() {}));
      }

      // Set error if error is returned in response
      if (code > 400 || !response.isSuccessful()) {
        executionService.setError("Error Code: " + code, message, bodyStr);
      }

      // If OpenAI returns a document back, capture the document
      Object data = responseEntity.get("data");
      if (data instanceof List &&
          ((List<?>)data).size() > 0 &&
          ((List<?>)data).get(0) instanceof Map &&
          ((Map<?,?>)((List<?>)data).get(0)).containsKey("b64_json")) {

        // If there is an incoming file, save it in the desired location with the desired name
        // Set errors if no name or file location has been chosen
        PropertyDescriptor<?> hasSaveFolder = executionService.getIntegrationConfiguration().getProperty(FOLDER);
        PropertyDescriptor<?> hasSaveFileName = executionService.getIntegrationConfiguration().getProperty(SAVED_FILENAME);
        if (hasSaveFolder == null) {
          executionService.setError(FILE_SAVING_ERROR_TITLE, FOLDER_LOCATION_ERROR_MESSAGE, "");
          return new HttpResponse(code, message, responseEntity);
        } else if (hasSaveFileName == null) {
          executionService.setError(FILE_SAVING_ERROR_TITLE, FILE_NAME_ERROR_MESSAGE, "");
          return new HttpResponse(code, message, responseEntity);
        }

        // Extracting files from the response body and saving them to Appian
        Long folderID = executionService.getIntegrationConfiguration().getValue(FOLDER);
        String fileName = executionService.getIntegrationConfiguration().getValue(SAVED_FILENAME);
        List<Document> documents = new ArrayList<>();
        AtomicInteger index = new AtomicInteger(1);
        ((List<?>)data).forEach(doc -> {
          if (((Map<?,?>)doc).get("b64_json") != null) {
            // decoding doc
            String bytesStr = ((String)((Map<?,?>)doc).get("b64_json"));
            byte[] decodedBytes = Base64.getDecoder().decode(bytesStr);
            InputStream inputStream = new ByteArrayInputStream(decodedBytes);

            // If there's more than one document to be saved, add a number to the end
            String fileNameWithIndex = ((List<?>)data).size() > 1 ?
                fileName.substring(0, fileName.lastIndexOf(".")) + index.getAndIncrement() + fileName.substring(fileName.lastIndexOf(".")) :
                fileName;

            // adding doc to map to be returned to Appian
            Document document = executionService
                .getExecutionContext()
                .getDocumentDownloadService()
                .downloadDocument(inputStream, folderID, fileNameWithIndex);
            documents.add(document);
          }
        });

        return new HttpResponse(code, message, responseEntity, documents);
      }

      // If no document, just return the response
      return new HttpResponse(code, message, responseEntity);
    }
  }

  public HttpResponse get(String url) throws IOException {
    Request request = new Request.Builder().url(url).build();
    OkHttpClient client = getHTTPClient(executionService.getConnectedSystemConfiguration(), "application/json");
    return executeRequest(client, request);
  }

  public HttpResponse post(String url, RequestBody body)
      throws IOException {
    Request request = new Request.Builder().url(url).post(body).build();
    OkHttpClient client = getHTTPClient(executionService.getConnectedSystemConfiguration(), "application/json");
    return executeRequest(client, request);
  }

  public HttpResponse multipartPost(String url, Map<String,Object> requestBody, Map<String,File> files)
      throws IOException {

    MultipartBody.Builder multipartBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);

    // Adding text request body json to the multipart builder
    requestBody.forEach((key, val) -> {
      if (val instanceof String) {
        multipartBuilder.addFormDataPart(key, ((String)val));
      } else {
        String jsonString;
        try {
          jsonString = new ObjectMapper().writeValueAsString(val);
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }
        multipartBuilder.addFormDataPart(key, jsonString);
      }
    });

    // Adding files to the multipart builder
    files.forEach((fileName, file) -> {
      RequestBody requestFile = RequestBody.create(file, MediaType.parse("multipart/form-data"));
      MultipartBody.Part filePart = MultipartBody.Part.createFormData(fileName, file.getName(), requestFile);
      multipartBuilder.addPart(filePart);
    });

    // Getting the client/request and executing the request
    OkHttpClient client = getHTTPClient(executionService.getConnectedSystemConfiguration(), "multipart/form-data");
    Request request = new Request.Builder().url(url).post(multipartBuilder.build()).build();
    return executeRequest(client, request);
  }

  public HttpResponse delete(String url) throws IOException {
    Request request = new Request.Builder().url(url).delete().build();
    OkHttpClient client = getHTTPClient(executionService.getConnectedSystemConfiguration(), "application/json");
    return executeRequest(client, request);
  }

}
