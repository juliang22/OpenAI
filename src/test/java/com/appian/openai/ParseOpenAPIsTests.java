package com.appian.openai;

import static org.junit.Assert.assertNotNull;
import static std.HTTP.getHTTPClient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.appian.connectedsystems.templateframework.sdk.configuration.Choice;
import com.appian.openai.templates.OpenAICSP;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import std.ConstantKeys;
import std.OpenAIHelpers;
import std.Util;





public class ParseOpenAPIsTests  implements ConstantKeys {




  @Test
  public void JsonStuff() throws RuntimeException, IOException {
    ClassLoader classLoader = OpenAICSP.class.getClassLoader();
    String firstCallJson = "com/appian/openai/templates/firstCall.json";
    ObjectMapper objectMapper = new ObjectMapper();


    try (InputStream input = classLoader.getResourceAsStream(firstCallJson)) {
      String content = IOUtils.toString(input, StandardCharsets.UTF_8);
      Map<String, Object> map = objectMapper.readValue(content, Map.class);
      // Access nested maps step by step
      Map<String, Object> parametersMap = (Map<String, Object>) map.get("parameters");
      Map<String, Object> propertiesMap = (Map<String, Object>) parametersMap.get("properties");
      Map<String, Object> primaryTableMap = (Map<String, Object>) propertiesMap.get("primary_table");

      // Update the "enum" key
      primaryTableMap.put("enum", Arrays.asList("hello", "world"));
      System.out.println(parametersMap);

    }
  }

  @Test
  public void testFunctionCalling() throws JsonProcessingException {
    Map<String, Object> functionReqBody = new HashMap<>();
    functionReqBody.put("model", "gpt-3.5-turbo-0301");

    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(OpenAIHelpers.createMessage("system", "You are a function calling chatbot"));
    messages.add(OpenAIHelpers.createMessage("user", "hello!"));

    functionReqBody.put("messages", messages);

    ObjectMapper objectMapper = new ObjectMapper();

    RequestBody requestBody = RequestBody.create(objectMapper.writeValueAsString(functionReqBody), MediaType.get("application/json; charset=utf-8"));
    String shhhDontTell = "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";
    Request request = new Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .addHeader("Accept", "application/json; charset=utf-8")
        .post(requestBody)
        .build();

    OkHttpClient client = new OkHttpClient.Builder().connectTimeout(2, TimeUnit.MINUTES)
        .connectTimeout(2, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .callTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .addInterceptor(chain -> {
          Request.Builder newRequest = chain.request()
              .newBuilder()
              .addHeader("Content-Type", "application/json")
              .addHeader("Authorization", "Bearer " + shhhDontTell);
          return chain.proceed(newRequest.build());
        })
        .build();


    // TODO: clean up with serialize class structure for openai response
    try (Response response = client.newCall(request).execute()) {
      if (response.isSuccessful()) {
        String bodyStr = response.body().string();
        Map<String, Object> map = objectMapper.readValue(bodyStr, Map.class);

        Map<String, Object> responseEntity = new HashMap<>();
        if (map.get("error") != null) {
          responseEntity = map;
        } else {
          ArrayList<String> payload = new ArrayList<>();
          ((List<?>)map.get("choices")).forEach(choice -> {
            String msg = (String)((Map<?,?>)((Map<?,?>)choice).get("message")).get("content");
            payload.add(msg);
          });
          responseEntity.put("messages", payload);
        }
        System.out.println(responseEntity);

      } else {
        System.err.println("Request failed with status code: " + response.code());
      }


    } catch (IOException e) {
      System.out.println(e);
    }

    System.out.println();
  }


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
