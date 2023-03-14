package com.appian.openai.templates.apis;

import static std.HTTP.getHTTPClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.appian.connectedsystems.simplified.sdk.SimpleClientApi;
import com.appian.connectedsystems.simplified.sdk.SimpleClientApiRequest;
import com.appian.connectedsystems.templateframework.sdk.ClientApiResponse;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.TemplateId;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import std.ConstantKeys;

@TemplateId(name = "chatCompletion")
public class ChatBotClientApi extends SimpleClientApi implements ConstantKeys {

  @Override
  protected ClientApiResponse execute(SimpleClientApiRequest simpleRequest, ExecutionContext executionContext) {

    String requestBodyStr = new Gson().toJson(simpleRequest.getPayload());
    RequestBody requestBody = RequestBody.create(requestBodyStr, MediaType.get("application/json; charset=utf-8"));
    Request request = new Request.Builder().url("https://api.openai.com/v1/chat/completions").post(requestBody).build();
    OkHttpClient client = getHTTPClient(simpleRequest.getConnectedSystemConfiguration(), "application/json");
    HashMap<String, Object> responseEntity;
    try (Response response = client.newCall(request).execute()) {
      ResponseBody body = response.body();
      responseEntity = new ObjectMapper().readValue(body.string(), new TypeReference<HashMap<String,Object>>() {});
    } catch (IOException e) {
      responseEntity = new HashMap<String, Object>() {{ put("error", e); }};
    }
    return new ClientApiResponse(responseEntity);
  }
}
