package com.appian.openai.templates;

import com.appian.connectedsystems.simplified.sdk.SimpleConnectedSystemTemplate;
import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.TemplateId;

import io.swagger.v3.oas.models.OpenAPI;
import std.ConstantKeys;
import std.Util;

@TemplateId(name="OpenAICSP")
public class OpenAICSP extends SimpleConnectedSystemTemplate implements ConstantKeys {


  public static final ClassLoader classLoader = OpenAICSP.class.getClassLoader();
  public static final OpenAPI claimsOpenApi = Util.getOpenApi("com/appian/openai/templates/claims.yaml", classLoader);
  public static final OpenAPI policiesOpenApi = Util.getOpenApi("com/appian/openai/templates/policies" +
      ".yaml", classLoader);
  public static final OpenAPI jobsOpenApi = Util.getOpenApi("com/appian/openai/templates/jobs.yaml", classLoader);
  public static final OpenAPI accountsOpenApi = Util.getOpenApi("com/appian/openai/templates/accounts" +
      ".yaml", classLoader);

  public static final OpenAPI openaiOpenApi = Util.getOpenApi("com/appian/openai/templates/openai" +
      ".yaml", classLoader);

  @Override
  protected SimpleConfiguration getConfiguration(
      SimpleConfiguration simpleConfiguration, ExecutionContext executionContext) {

    return simpleConfiguration.setProperties(
        textProperty(USERNAME)
            .label("Username")
            .description("Enter your openai username")
            .build(),
        textProperty(PASSWORD)
            .label("Password")
            .description("Enter your openai password")
            .masked(true)
            .build()
    );
  }
}
