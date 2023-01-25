package com.appian.openai.templates.apis;

import java.io.IOException;

import com.appian.connectedsystems.simplified.sdk.SimpleIntegrationTemplate;
import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.IntegrationResponse;
import com.appian.connectedsystems.templateframework.sdk.TemplateId;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyPath;
import com.appian.connectedsystems.templateframework.sdk.metadata.IntegrationTemplateRequestPolicy;
import com.appian.connectedsystems.templateframework.sdk.metadata.IntegrationTemplateType;
import com.appian.openai.templates.Execution.OpenAIExecute;
import com.appian.openai.templates.UI.OpenAIUIBuilder;

import std.ConstantKeys;

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

    restBuilder.setIntegrationConfiguration(integrationConfiguration);
    return integrationConfiguration.setProperties(restBuilder.build());
  }

  @Override
  protected IntegrationResponse execute(
      SimpleConfiguration integrationConfiguration,
      SimpleConfiguration connectedSystemConfiguration,
      ExecutionContext executionContext) {

    OpenAIExecute execute = new OpenAIExecute(integrationConfiguration, connectedSystemConfiguration, executionContext);
    try {
      return execute.buildExecution();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
