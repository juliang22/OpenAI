package com.appian.openai.templates.UI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.appian.connectedsystems.simplified.sdk.SimpleIntegrationTemplate;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyDescriptor;
import com.appian.connectedsystems.templateframework.sdk.configuration.RefreshPolicy;
import com.appian.connectedsystems.templateframework.sdk.configuration.TextPropertyDescriptor;
import com.appian.openai.templates.OpenAICSP;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import std.Util;

public class OpenAIUIBuilder extends UIBuilder{

  public OpenAIUIBuilder(SimpleIntegrationTemplate simpleIntegrationTemplate, String api) {
    super();
    setOpenAPI(api);
    setSimpleIntegrationTemplate(simpleIntegrationTemplate);
    setDefaultEndpoints();
  }

  public void setOpenAPI(String api) {
    switch (api) {
      case POLICIES:
        this.openAPI = OpenAICSP.policiesOpenApi;
        break;
      case CLAIMS:
        this.openAPI = OpenAICSP.claimsOpenApi;
        break;
      case JOBS:
        this.openAPI = OpenAICSP.jobsOpenApi;
        break;
      case ACCOUNTS:
        this.openAPI = OpenAICSP.accountsOpenApi;
        break;
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
        .label("Sort Endpoints Dropdown")
        .refresh(RefreshPolicy.ALWAYS)
        .instructionText("Sort the endpoints dropdown below with a relevant search query.")
        .placeholder("fine-tune model")
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
        break;
      case (POST):
        buildPost(result);
        break;
      case (PATCH):
        buildPatch(result);
        break;
      case (DELETE):
        buildDelete(result);
        break;
    }

  }

  public void buildGet(List<PropertyDescriptor<?>> result) {

    Operation get = paths.get(pathName).getGet();

  }

  public void buildPost(List<PropertyDescriptor<?>> result) {

    if (paths.get(pathName).getPost().getRequestBody() == null) {
      result.add(NO_REQ_BODY_UI);
      return;
    }

    MediaType documentType = openAPI.getPaths().get(pathName).getPost().getRequestBody().getContent().get("multipart/form-data");
    Schema<?> schema = (documentType == null) ?
        paths.get(pathName)
            .getPost()
            .getRequestBody()
            .getContent()
            .get("application/json")
            .getSchema() :
        paths.get(pathName)
            .getPost()
            .getRequestBody()
            .getContent()
            .get("multipart/form-data")
            .getSchema();

    Set<String> required = new HashSet<>(schema.getRequired());
    ReqBodyUIBuilder(result, schema.getProperties(), required);


  }

  public void buildPatch(List<PropertyDescriptor<?>> result) {
  }

  public void buildDelete(List<PropertyDescriptor<?>> result) {
  }
}
