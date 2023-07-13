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
import std.ConstantKeys;
import std.Util;



public class ParseOpenAPIsTests  implements ConstantKeys {


  // Use this function to find all the summaries that have changed between api versions. A bug was created where the endpoint
  // properties keys depend on the summary of the path operation and thus if it changes when a new api is loaded, the
  // saved integration will show a red error saying 'This value is no longer a valid choice' for the endpoint. This function
  // identifies the new summary changes so that we can manually go into the new api and paste in the old value from the original
  // summary. Not the best solution, but we'd rather have more work from the developer than have the user see red error for no
  // reason. When loading in a new api, use this function to identify the changed summaries, manually update the new api yaml to
  // include the old api's summaries, reference the new yaml file in OpenAICSP, check if any integrations are red in the sample
  // app after loading in the new version, and delete the old yaml file.
  /*@Test
  public void findChangedSummary() {
    ClassLoader classLoader = OpenAICSP.class.getClassLoader();

    OpenAPI newOpenAIAPI = Util.getOpenApi("com/appian/openai/templates/openai.yaml", classLoader);
    OpenAPI oldOpenAIAPI = Util.getOpenApi("com/appian/openai/templates/openai.yaml", classLoader);

    Map<String, String> oldSummaries = new HashMap<>();
    oldOpenAIAPI.getPaths().forEach((pathName, path) -> {
      Map<String,Operation> operations = new HashMap<>();
      operations.put(GET, path.getGet());
      operations.put(POST, path.getPost());
      operations.put(PATCH, path.getPatch());
      operations.put(DELETE, path.getDelete());

      operations.forEach((restOperation, openAPIOperation) -> {
        if (openAPIOperation != null) {
          // filter out deprecated endpoints
          if (openAPIOperation.getDeprecated() != null && openAPIOperation.getDeprecated()) return;
          String newSummary = "";
          switch (restOperation) {
            case GET:
              newSummary = newOpenAIAPI.getPaths().get(pathName).getGet().getSummary();
              break;
            case POST:
              newSummary = newOpenAIAPI.getPaths().get(pathName).getPost().getSummary();
              break;
            case PATCH:
              newSummary = newOpenAIAPI.getPaths().get(pathName).getPatch().getSummary();
              break;
            case DELETE:
              newSummary = newOpenAIAPI.getPaths().get(pathName).getDelete().getSummary();
              break;
          }

          String oldSummary = openAPIOperation.getSummary();

          if (newSummary != "" && newSummary != null && !newSummary.equals(oldSummary)) {
            oldSummaries.put(pathName, oldSummary);
          }
        }
      });
    });

    oldSummaries.forEach((pathName, summary) -> {
      System.out.println(pathName);
      System.out.println("————");
      System.out.println(summary);
      System.out.println("————");
    });
  }*/


}
