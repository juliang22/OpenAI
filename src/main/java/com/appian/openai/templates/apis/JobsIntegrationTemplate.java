package com.appian.openai.templates.apis;

import java.util.HashMap;
import java.util.Map;

import com.appian.connectedsystems.simplified.sdk.SimpleIntegrationTemplate;
import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.IntegrationResponse;
import com.appian.connectedsystems.templateframework.sdk.TemplateId;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyPath;
import com.appian.connectedsystems.templateframework.sdk.diagnostics.IntegrationDesignerDiagnostic;
import com.appian.connectedsystems.templateframework.sdk.metadata.IntegrationTemplateRequestPolicy;
import com.appian.connectedsystems.templateframework.sdk.metadata.IntegrationTemplateType;
import com.appian.openai.templates.UI.OpenAIUIBuilder;
import com.appian.openai.templates.UI.UIBuilder;

import std.ConstantKeys;

@TemplateId(name = "JobsIntegrationTemplate")
@IntegrationTemplateType(IntegrationTemplateRequestPolicy.READ_AND_WRITE)
public class JobsIntegrationTemplate extends SimpleIntegrationTemplate implements ConstantKeys {

  OpenAIUIBuilder restBuilder = new OpenAIUIBuilder(this, JOBS);

  @Override
  protected SimpleConfiguration getConfiguration(
      SimpleConfiguration integrationConfiguration,
      SimpleConfiguration connectedSystemConfiguration,
      PropertyPath propertyPath,
      ExecutionContext executionContext) {

    // Logic for Posts simplified
   /* LocalTypeDescriptor innerOne = localType("innerOne").properties(
        textProperty("textOne").label("textOne").build())
        .build();

    LocalTypeDescriptor innerTwo = localType("innerTwo").properties(
            textProperty("textTwo").label("textTwo").build())
        .build();

    LocalTypeDescriptor metaDataType = localType("METADATA_TYPE")
        .properties(
            textProperty("METADATA_NAME")
                .label("Metadata Name")
                .description("The name or key of the metadata")
                .placeholder("category")
                .isExpressionable(true)
                .build(),
            textProperty("METADATA_VALUE")
                .label("Metadata Value")
                .description("The value of the metadata")
                .placeholder("api")
                .isExpressionable(true)
                .build()
        ).build();
    LocalTypeDescriptor nested = localType("layered").properties(
        localTypeProperty(innerTwo).build()
*//*        listTypeProperty("lsity").itemType(TypeReference.from(metaDataType)).build()*//*
    ).build();


    List<PropertyDescriptor> innerOneProperties = new ArrayList<>(innerOne.getProperties());
*//*    innerOneProperties.addAll(innerTwo.getProperties());*//*
    innerOneProperties.addAll(nested.getProperties());
    LocalTypeDescriptor merged = localType("merged").properties(innerOneProperties).build();


    List<PropertyDescriptor> propertyDescriptors = Arrays.asList(
        localTypeProperty(merged).isExpressionable(true).displayHint(DisplayHint.EXPRESSION).build()
    );

    return integrationConfiguration.setProperties(propertyDescriptors.toArray(new PropertyDescriptor[0]));
*/

    restBuilder.setIntegrationConfiguration(integrationConfiguration);
    return integrationConfiguration.setProperties(restBuilder.build());

  }

  @Override
  protected IntegrationResponse execute(
      SimpleConfiguration integrationConfiguration,
      SimpleConfiguration connectedSystemConfiguration,
      ExecutionContext executionContext) {
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
