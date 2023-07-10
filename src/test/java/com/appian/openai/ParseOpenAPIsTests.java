package com.appian.openai;

import static org.junit.Assert.assertNotNull;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.appian.connectedsystems.templateframework.sdk.configuration.Choice;
import com.appian.openai.templates.OpenAICSP;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import std.Util;

public class ParseOpenAPIsTests {

/*  @Test
  public void writeNewOpenAPIFile() throws JsonProcessingException {
    ClassLoader classLoader = OpenAICSP.class.getClassLoader();
    OpenAPI openaiOpenApi = Util.getOpenApi("com/appian/openai/templates/openai.yaml", classLoader);
    ObjectMapper objectMapper = new ObjectMapper();

    OpenAPI newOPenapi = new OpenAPIV3Parser().readContents(objectMapper.writeValueAsString(openaiOpenApi), null, null).getOpenAPI();
    System.out.println(newOPenapi.getPaths());
  }*/




}
