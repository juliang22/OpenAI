package com.appian.openai.templates.Execution;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.IntegrationError;
import com.appian.connectedsystems.templateframework.sdk.IntegrationResponse;
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
  public IntegrationResponse buildExecution() throws IOException {
    try {
      switch (restOperation) {
        case GET:
          executeGet();
          break;
        case DELETE:
          executeDelete();
          break;
        case POST:
        case PATCH:
          executePost();
          break;

        // custom endpoint to build JSONLines file from Appian data
        case (JSONLINES):
          executeJsonLines();
          break;
      }
    } catch (IOException e) {
      return IntegrationResponse.forError(new IntegrationError.IntegrationErrorBuilder()
              .title(e.getCause().toString())
              .message(e.getMessage())
              .build())
          .build();
    }

    // If autogenerated 'text' property is submitted, null value submitted, or other errors created, return error
    if (getError() != null) {
      IntegrationError error = getError().build();
      return getDiagnosticsUI() != null ?
          IntegrationResponse.forError(error).withDiagnostic(getDiagnosticsUI()).build() :
          IntegrationResponse.forError(error).build();
    }

    return IntegrationResponse.forSuccess(
            getResponse()).withDiagnostic(getDiagnosticsUI())
        .build();
  }

  @Override
  public void executePatch() {  }

  @Override
  public void executeDelete() throws IOException {
    this.HTTPResponse = httpService.delete(pathNameModified);
  }

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

    Object fileName = builtRequestBody.get(OUTPUT_FILENAME);
    if (fileName == null) {
      setError("File name has not been set.", "Set file name before executing the request", "");
      return;
    }
    ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonLines.toString().getBytes(StandardCharsets.UTF_8));
    Long folderID = integrationConfiguration.getValue(FOLDER);
    Document document = executionContext.getDocumentDownloadService().downloadDocument(inputStream, folderID, fileName.toString());
    HTTPResponse = new HttpResponse(
        200,
        "JSON Lines file successfully created.",
        new HashMap<String, Object>() {{ put("Response", "Document successfully created"); }},
        Collections.singletonList(document));
  }
}
