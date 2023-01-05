package com.appian.openai.templates.apis;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.appian.connectedsystems.simplified.sdk.SimpleIntegrationTemplate;
import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.IntegrationError;
import com.appian.connectedsystems.templateframework.sdk.IntegrationResponse;
import com.appian.connectedsystems.templateframework.sdk.TemplateId;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyPath;
import com.appian.connectedsystems.templateframework.sdk.connectiontesting.TestConnectionResult;
import com.appian.connectedsystems.templateframework.sdk.diagnostics.IntegrationDesignerDiagnostic;
import com.appian.connectedsystems.templateframework.sdk.metadata.IntegrationTemplateRequestPolicy;
import com.appian.connectedsystems.templateframework.sdk.metadata.IntegrationTemplateType;
import com.appian.openai.templates.Execution.Execute;
import com.appian.openai.templates.UI.OpenAIUIBuilder;
import com.appian.openai.templates.UI.UIBuilder;
import com.google.gson.Gson;

import std.ConstantKeys;
import std.HTTP;
import std.HttpResponse;

@TemplateId(name = "OpenAIIntegrationTemplate")
@IntegrationTemplateType(IntegrationTemplateRequestPolicy.READ_AND_WRITE)
public class OpenAIIntegrationTemplate extends SimpleIntegrationTemplate implements ConstantKeys {

  OpenAIUIBuilder restBuilder = new OpenAIUIBuilder(this, OPENAI);

  @Override
  protected SimpleConfiguration getConfiguration(
      SimpleConfiguration integrationConfiguration,
      SimpleConfiguration connectedSystemConfiguration,
      PropertyPath propertyPath,
      ExecutionContext executionContext) {

    try {
      HTTP.testAuth(connectedSystemConfiguration);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    restBuilder.setIntegrationConfiguration(integrationConfiguration);
    return integrationConfiguration.setProperties(restBuilder.build());
  }

  @Override
  protected IntegrationResponse execute(
      SimpleConfiguration integrationConfiguration,
      SimpleConfiguration connectedSystemConfiguration,
      ExecutionContext executionContext) {

    Execute execute = new Execute(integrationConfiguration, connectedSystemConfiguration);
    execute.build();

    // If autogenerated 'text' property is submitted, create error
    if (execute.getError() != null) {
      IntegrationError error = execute.getError().build();
      return IntegrationResponse.forError(error).build();
    }


    Map<String,Object> requestDiagnostic = new HashMap<>();
    String csValue = connectedSystemConfiguration.getValue("");
    requestDiagnostic.put("csValue", csValue);
    String integrationValue = integrationConfiguration.getValue("");
    requestDiagnostic.put("integrationValue", integrationValue);
    Map<String,Object> result = new HashMap<>();


    // Important for debugging to capture the amount of time it takes to interact
    // with the external system. Since this integration doesn't interact
    // with an external system, we'll just log the calculation time of concatenating the strings
    final long start = System.currentTimeMillis();
    result.put("hello", "world");
    result.put("concat", csValue + integrationValue);
    final long end = System.currentTimeMillis();

    final long executionTime = end - start;
    final IntegrationDesignerDiagnostic diagnostic = IntegrationDesignerDiagnostic.builder()
        .addExecutionTimeDiagnostic(executionTime)
        .addRequestDiagnostic(requestDiagnostic)
        .build();

    return IntegrationResponse.forSuccess(result).withDiagnostic(diagnostic).build();

  }
}
