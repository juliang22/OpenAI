package com.appian.openai.templates.Execution;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.configuration.Document;
import com.appian.connectedsystems.templateframework.sdk.configuration.DocumentPropertyDescriptor;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import std.HttpResponse;

public class OpenAIExecute extends Execute{

  public OpenAIExecute(
      SimpleConfiguration integrationConfiguration,
      SimpleConfiguration connectedSystemConfiguration,
      ExecutionContext executionContext)  {
    super(integrationConfiguration, connectedSystemConfiguration, executionContext);

  }

  @Override
  public void buildExecution() throws IOException {
    switch (restOperation) {
      case GET:
      case DELETE:
        executeGet();
        break;
      case POST:
      case PATCH:
        executePost();
        break;

      //  custom endpoint
      case (JSONLINES):
        executeJsonLines();
        break;
    }
  }

  @Override
  public void executePatch() {  }

  @Override
  public void executeDelete() {  }

  @Override
  public void executeGet() throws IOException {
    this.HTTPResponse = httpService.get(pathNameModified);
  }

  @Override
  public void executePost() throws IOException {

    HashMap<String,PropertyState> reqBodyProperties = integrationConfiguration.getValue(reqBodyKey);
    if (reqBodyProperties == null || reqBodyProperties.size() <= 0) {
      RequestBody body = RequestBody.create("", null);
      HTTPResponse = httpService.post(pathNameModified, body);
      return;
    }

    // Parse through user inputs and extract the values out of Appian property descriptors
    buildRequestBodyJSON(reqBodyProperties);
    if (getError() != null) return;

    // Checking if there are documents to add to the request body
    Set<DocumentPropertyDescriptor> documents = new HashSet<>();
    integrationConfiguration.getProperties().forEach(property -> {
      if (property instanceof DocumentPropertyDescriptor &&
          integrationConfiguration.getProperty(property.getKey()) != null &&
          integrationConfiguration.getValue(property.getKey()) != null) {
        documents.add((DocumentPropertyDescriptor)property);
      }
    });

    // If there are documents, send multipart post
    if (documents.size() > 0) {
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

      HTTPResponse = httpService.multipartPost(pathNameModified, builtRequestBody, files);
    } else { // No image: Just sent request body as content-type/json
      String jsonString = new ObjectMapper().writeValueAsString(builtRequestBody);
      RequestBody body = RequestBody.create(jsonString, MediaType.get("application/json; charset=utf-8"));
      HTTPResponse = httpService.post(pathNameModified, body);
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
    HTTPResponse.setDocuments(document);
  }


}
