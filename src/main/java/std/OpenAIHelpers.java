package std;

import static std.HTTP.getHTTPClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class OpenAIHelpers {

  public static Map<String,String> createMessage(String role, String content) {
    Map<String, String> message = new HashMap<>();
    message.put("role", role);
    message.put("content", content);
    return message;
  }


  public static Request buildOkHTTPRequest(String firstCallJson) {
    RequestBody requestBody = RequestBody.create(firstCallJson, MediaType.get("application/json; charset=utf-8"));
    return new Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .addHeader("Accept", "application/json; charset=utf-8")
        .post(requestBody)
        .build();
  }

  public static String functionCallHTTPRequest(SimpleConfiguration connectedSystem, Request request,
      ObjectMapper objectMapper) {

    OkHttpClient client = getHTTPClient(connectedSystem, "application/json");
    HashMap<String, Object> responseEntity = new HashMap<>();
    try (Response response = client.newCall(request).execute()) {
      ResponseBody body = response.body();
      HashMap<String, Object> responseObj = new ObjectMapper().readValue(body.string(), new TypeReference<HashMap<String,Object>>() {});
      if (responseObj.get("error") != null) {
        return responseObj.get("error").toString();
      } else {

        // TODO: Structure this with deserialized classes
        ArrayList<String> payload = new ArrayList<>();
        String returnedArgs =
            ((Map)((Map)((Map)((List)responseObj.get("choices")).get(0)).get("message")).get("function_call")).get("arguments").toString();
        Map<String, String> returnedRecordMap = objectMapper.readValue(returnedArgs, Map.class);
        return returnedRecordMap.get("primary_table");
      }
    } catch (IOException e) {
      return e.getMessage();
    }
  }

}
