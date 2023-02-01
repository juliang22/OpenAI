package com.appian.openai.templates;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.simplified.sdk.connectiontesting.SimpleTestableConnectedSystemTemplate;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.TemplateId;
import com.appian.connectedsystems.templateframework.sdk.connectiontesting.TestConnectionResult;

import io.swagger.v3.oas.models.OpenAPI;
import std.ConstantKeys;
import std.HTTP;
import std.HttpResponse;
import std.Util;

@TemplateId(name="OpenAICSP")
public class OpenAICSP extends SimpleTestableConnectedSystemTemplate implements ConstantKeys {


  public static final ClassLoader classLoader = OpenAICSP.class.getClassLoader();

  public static final OpenAPI openaiOpenApi = Util.getOpenApi("com/appian/openai/templates/openai" +
      ".yaml", classLoader);

  @Override
  protected SimpleConfiguration getConfiguration(
      SimpleConfiguration simpleConfiguration, ExecutionContext executionContext) {

    return simpleConfiguration.setProperties(
        textProperty(API_KEY)
            .label("API Key")
            .description("Enter your OpenAI API Key. Visit https://beta.openai.com/account/api-keys to get an API key for your " +
                "account.")
            .masked(true)
            .isRequired(true)
            .isImportCustomizable(true)
            .build(),
        textProperty(ORGANIZATION)
            .label("Organization")
            .description("For users who belong to multiple organizations, you can pass a header to specify which organization is used for an API request. Usage from these API requests will count against the specified organization's subscription quota.")
            .build()
    );
  }

  @Override
  protected TestConnectionResult testConnection(SimpleConfiguration simpleConfiguration, ExecutionContext executionContext) {
    try {
      HttpResponse response = HTTP.testAuth(simpleConfiguration);
      if (response.getStatusCode() == 200) {
        return TestConnectionResult.success();
      }
      return TestConnectionResult.error(response.getResponse().get("error").toString());
    } catch (Exception e) {
      return TestConnectionResult.error(e.getMessage());
    }
  }
}
