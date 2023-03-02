package com.appian.openai.templates.UI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.appian.connectedsystems.simplified.sdk.SimpleIntegrationTemplate;
import com.appian.connectedsystems.templateframework.sdk.configuration.BooleanDisplayMode;
import com.appian.connectedsystems.templateframework.sdk.configuration.DisplayHint;
import com.appian.connectedsystems.templateframework.sdk.configuration.LocalTypeDescriptor;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyDescriptor;
import com.appian.connectedsystems.templateframework.sdk.configuration.RefreshPolicy;
import com.appian.connectedsystems.templateframework.sdk.configuration.TextPropertyDescriptor;
import com.appian.connectedsystems.templateframework.sdk.configuration.TypeReference;
import com.appian.openai.templates.OpenAICSP;

import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import std.Util;

public class OpenAIUIBuilder extends UIBuilder {

  public OpenAIUIBuilder(SimpleIntegrationTemplate simpleIntegrationTemplate, String api) {
    super();
    setOpenAPI(api);
    setSimpleIntegrationTemplate(simpleIntegrationTemplate);

    List<CustomEndpoint> customEndpoints = Collections.singletonList(new CustomEndpoint(OPENAI, JSONLINES, "/JSONLines",
        "Creates a JSON Lines file from Appian data."));
    setDefaultEndpoints(customEndpoints);
  }

  public void setOpenAPI(String api) {
    switch (api) {
      case OPENAI:
        this.openAPI = OpenAICSP.openaiOpenApi;
        break;
    }
    this.paths = openAPI.getPaths();
    this.api = api;
  }

  public PropertyDescriptor<?>[] build() {

    // If no endpoint is selected, just build the api dropdown
    String selectedEndpoint = integrationConfiguration.getValue(CHOSEN_ENDPOINT);
    TextPropertyDescriptor searchBar = simpleIntegrationTemplate.textProperty(SEARCH)
        .label("Sort Operations Dropdown")
        .refresh(RefreshPolicy.ALWAYS)
        .instructionText("Sort the operations dropdown below with a relevant search query.")
        .placeholder("chat")
        .build();
    List<PropertyDescriptor<?>> result = new ArrayList<>(Arrays.asList(searchBar, endpointChoiceBuilder()));
    if (selectedEndpoint == null) {
      return result.toArray(new PropertyDescriptor<?>[0]);
    }

    // If a user switched to another api after they selected an endpoint, set the endpoint and search to null
    // Else if a user selects api then a corresponding endpoint, update label and description accordingly
    String[] selectedEndpointStr = selectedEndpoint.split(":");
    String apiType = selectedEndpointStr[0];
    String restOperation = selectedEndpointStr[1];
    String pathName = selectedEndpointStr[2];
    String pathSummary = selectedEndpointStr[3];
    if (!apiType.equals(api)) {
      integrationConfiguration.setValue(CHOSEN_ENDPOINT, null).setValue(SEARCH, "");
    } else {
      // The key of the request body is dynamic so when I need to get it in the execute function:
      // key = integrationConfiguration.getProperty(REQ_BODY).getLabel();
      // integrationConfiguration.getProperty(key)
      // TODO: put below in buildRestCall()
      String KEY_OF_REQ_BODY = Util.removeSpecialCharactersFromPathName(pathName);
      result.add(simpleIntegrationTemplate.textProperty(REQ_BODY).label(KEY_OF_REQ_BODY).isHidden(true).build());

      // Building the result with path variables, request body, and other functionality needed to make the request
      buildRestCall(restOperation, result, pathName);
    }
    return result.toArray(new PropertyDescriptor<?>[0]);
  }

  public void buildRestCall(String restOperation, List<PropertyDescriptor<?>> result, String pathName) {
    setPathName(pathName);
    setPathVarsUI();
    if (getPathVarsUI().size() > 0) {
      result.addAll(getPathVarsUI());
    }

    switch (restOperation) {
      case (GET):
        buildGet(result);
        buildFileCatcher(result);
        break;
      case (POST):
        buildPost(result);
        buildFileCatcher(result);
        break;
      case (PATCH):
        buildPatch(result);
        break;
      case (DELETE):
        buildDelete(result);
        break;

      //  custom endpoint
      case (JSONLINES):
        buildJsonLines(result);
        break;
    }
  }

  public void buildGet(List<PropertyDescriptor<?>> result) {
  }

  public void buildPost(List<PropertyDescriptor<?>> result) {

    if (paths.get(pathName).getPost().getRequestBody() == null) {
      result.add(NO_REQ_BODY_UI);
      return;
    }

    MediaType documentType = openAPI.getPaths().get(pathName).getPost().getRequestBody().getContent().get("multipart/form-data");
    Schema<?> schema = (documentType == null) ?
        paths.get(pathName).getPost().getRequestBody().getContent().get("application/json").getSchema() :
        documentType.getSchema();
    Set<String> required = new HashSet<>(schema.getRequired());

    // Control fields you want to remove from specific paths
    Map<String, List<String>> removeFieldsFromReqBody = new HashMap<>();
    removeFieldsFromReqBody.put("/completions", Arrays.asList("stream"));
    removeFieldsFromReqBody.put("/chat/completions", Arrays.asList("stream"));

    // Build req body
    ReqBodyUIBuilder(result, schema.getProperties(), required, removeFieldsFromReqBody);
  }

  public void buildPatch(List<PropertyDescriptor<?>> result) {
  }

  public void buildDelete(List<PropertyDescriptor<?>> result) {
  }

  public void buildJsonLines(List<PropertyDescriptor<?>> result) {

    // Location to save JSONLines folder
    result.add(simpleIntegrationTemplate.folderProperty(FOLDER)
        .label("Save to Folder")
        .placeholder("Save the generated JSONLines file to this folder")
        .isExpressionable(true)
        .isRequired(true)
        .build());

    // Request body for user to insert Appian values to be converted to JSONLines file
    LocalTypeDescriptor properties = simpleIntegrationTemplate.localType("JSONLINESFORMAT")
        .properties(simpleIntegrationTemplate.textProperty("prompt")
            .isExpressionable(true)
            .displayHint(DisplayHint.EXPRESSION)
            .refresh(RefreshPolicy.ALWAYS)
            .placeholder("(Required) The prompt(s) to generate completions for, encoded as a string, array of strings, array of" +
                " tokens, or array of token arrays.")
            .build(), simpleIntegrationTemplate.textProperty("completion")
            .isExpressionable(true)
            .displayHint(DisplayHint.EXPRESSION)
            .placeholder("(Required) Expected result, given the preceding prompt.")
            .refresh(RefreshPolicy.ALWAYS)
            .build())
        .build();

    LocalTypeDescriptor listOfProperties = simpleIntegrationTemplate.localType(REQ_BODY_PROPERTIES)
        .properties(simpleIntegrationTemplate.textProperty(OUTPUT_FILENAME)
            .isExpressionable(true)
            .label("OutputFileName")
            .placeholder("Name of the output file to be saved (don't include any special characters or extension endings)")
            .refresh(RefreshPolicy.ALWAYS)
            .build(), simpleIntegrationTemplate.localTypeProperty(properties)
            .key(JSONLINES + "hidden")
            .isHidden(true)
            .refresh(RefreshPolicy.ALWAYS)
            .build(), simpleIntegrationTemplate.listTypeProperty("toJsonLines")
            .refresh(RefreshPolicy.ALWAYS)
            .itemType(TypeReference.from(properties))
            .build())
        .build();

    result.add(simpleIntegrationTemplate.localTypeProperty(listOfProperties)
        .key(Util.removeSpecialCharactersFromPathName(pathName))
        .displayHint(DisplayHint.EXPRESSION)
        .isExpressionable(true)
        .label("Request Body")
        .description("Values will be converted to a JSONLines file and saved to Appian.")
        .instructionText("Enter list of values in the form of {toJsonLines: {'prompt': '<prompt text>', 'completion': '<ideal " +
            "generated text>'}, {'prompt': '<prompt text>', 'completion': '<ideal generated text>'}}")
        .refresh(RefreshPolicy.ALWAYS)
        .build());
  }

  public void buildFileCatcher(List<PropertyDescriptor<?>> result) {
    result.add(simpleIntegrationTemplate.booleanProperty(IS_FILE_EXPECTED)
        .label("Will there be a file returned in the response?")
        .displayMode(BooleanDisplayMode.RADIO_BUTTON)
        .refresh(RefreshPolicy.ALWAYS)
        .isExpressionable(true)
        .build());
    PropertyDescriptor<?> isFileExpected = integrationConfiguration.getProperty(IS_FILE_EXPECTED);
    if (isFileExpected != null) {
      if (integrationConfiguration.getValue(IS_FILE_EXPECTED).equals(true)) {
        result.add(simpleIntegrationTemplate.folderProperty(FOLDER)
            .isExpressionable(true)
            .isRequired(true)
            .label("Response File Save Location")
            .instructionText("Choose the folder you would like to save the response file to.")
            .build());
        result.add(simpleIntegrationTemplate.textProperty(SAVED_FILENAME)
            .isExpressionable(true)
            .isRequired(true)
            .label("Response File Name")
            .instructionText("Choose the name of the file received in the response and the extension. ex. 'sampleFileName.png'")
            .build());
      } else {
        // clearing folder and filename when no document is expected to be returned
        integrationConfiguration.setValue(FOLDER, null);
        integrationConfiguration.setValue(SAVED_FILENAME, null);
      }

    }
  }

}
