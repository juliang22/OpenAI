package std;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HTTP implements ConstantKeys {

  public static OkHttpClient getHTTPClient(SimpleConfiguration connectedSystemConfiguration, String contentType) {
    final String token = connectedSystemConfiguration.getValue(API_KEY);
    final String org = connectedSystemConfiguration.getValue(ORGANIZATION);

    List<Header> defaultHeaders = new ArrayList<>();
    defaultHeaders.add(new BasicHeader("Content-Type", contentType));
    defaultHeaders.add(new BasicHeader("Authorization", "Bearer " + token));
    if (org != null)
      defaultHeaders.add(new BasicHeader("OpenAI-Organization", org));

/*    HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
    return httpClientBuilder.setDefaultHeaders(defaultHeaders).build();*/
    return new OkHttpClient.Builder().connectTimeout(2, TimeUnit.MINUTES)
        .connectTimeout(2, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .callTimeout(2, TimeUnit.MINUTES)
        .addInterceptor(chain -> {
          Request.Builder newRequest = chain.request()
              .newBuilder()
              .addHeader("Content-Type", contentType)
              .addHeader("Authorization", "Bearer " + token);
          if (org != null)
            newRequest.addHeader("OpenAI-Organization", org);
          return chain.proceed(newRequest.build());
        })
        .build();
  }

  public static HttpResponse testAuth(SimpleConfiguration connectedSystemConfiguration) throws IOException {
    final String testURL = "https://api.openai.com/v1/models";
    return get(connectedSystemConfiguration, testURL);
  }

  public static HttpResponse executeRequest(OkHttpClient client, Request request) throws IOException {

    try (Response response = client.newCall(request).execute()) {
      HashMap<String,Object> responseEntity = new ObjectMapper().readValue(response.body().string(),
          new TypeReference<HashMap<String,Object>>() {
          });
      return new HttpResponse(response.code(), response.message(), responseEntity);
    }

/*    try (CloseableHttpResponse response = client.execute(request)) {
      HashMap<String,Object> responseEntity = new ObjectMapper()
          .readValue(EntityUtils.toString(response.getEntity()), new TypeReference<HashMap<String,Object>>() {});
      return new HttpResponse(response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase(), responseEntity);
    }*/
  }

  public static HttpResponse get(SimpleConfiguration connectedSystemConfiguration, String url) throws IOException {
    Request request = new Request.Builder().url(url).build();
    OkHttpClient client = getHTTPClient(connectedSystemConfiguration, "application/json");
    return executeRequest(client, request);
  }

  public static HttpResponse post(SimpleConfiguration connectedSystemConfiguration, String url, RequestBody body)
      throws IOException {
    Request request = new Request.Builder().url(url).post(body).build();
    OkHttpClient client = getHTTPClient(connectedSystemConfiguration, "application/json");
    return executeRequest(client, request);
  }

  public static HttpResponse multipartPost(
      SimpleConfiguration connectedSystemConfiguration, String url, Map<String,Object> requestBody, Map<String,File> files)
      throws IOException {

    MultipartBody.Builder multipartBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);

    requestBody.forEach((key, val) -> {
      if (val instanceof String) {
        multipartBuilder.addFormDataPart(key, ((String)val));
      } else {
        String jsonString = null;
        try {
          jsonString = new ObjectMapper().writeValueAsString(val);
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }
        multipartBuilder.addFormDataPart(key, jsonString);
      }
    });

    files.forEach((fileName, file) -> {
      /*      multipartBuilder.addFormDataPart(fileName, file.getName(), RequestBody.create(MediaType.parse("image/png"), file));*/

      RequestBody requestFile = RequestBody.create(file, MediaType.parse("multipart/form-data"));
      MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
      multipartBuilder.addPart(filePart);
    });

    OkHttpClient client = getHTTPClient(connectedSystemConfiguration, "multipart/form-data");
    Request request = new Request.Builder().url(url).post(multipartBuilder.build()).build();
    return executeRequest(client, request);
  }

}
