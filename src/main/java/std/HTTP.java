package std;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;



public class HTTP implements ConstantKeys {

  public static CloseableHttpClient getHTTPClient(SimpleConfiguration connectedSystemConfiguration, String contentType) {
    final String token = connectedSystemConfiguration.getValue(API_KEY);
    final String org = connectedSystemConfiguration.getValue(ORGANIZATION);

    List<Header> defaultHeaders = new ArrayList<>();
    defaultHeaders.add(new BasicHeader("Content-Type", contentType));
    defaultHeaders.add(new BasicHeader("Authorization", "Bearer " + token));
    if (org != null) defaultHeaders.add(new BasicHeader("OpenAI-Organization", org));

    HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
    return httpClientBuilder.setDefaultHeaders(defaultHeaders).build();

  }

  public static HttpResponse testAuth(SimpleConfiguration connectedSystemConfiguration) throws IOException {
    final String testURL = "https://api.openai.com/v1/models";
    return get(connectedSystemConfiguration, testURL);
  }

  public static HttpResponse executeRequest(CloseableHttpClient client, HttpRequestBase request) throws IOException {
    try (CloseableHttpResponse response = client.execute(request)) {
      HashMap<String,Object> responseEntity = new ObjectMapper()
          .readValue(EntityUtils.toString(response.getEntity()), new TypeReference<HashMap<String,Object>>() {});
      return new HttpResponse(response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase(), responseEntity);
    }
  }


  public static HttpResponse get(SimpleConfiguration connectedSystemConfiguration, String url) throws IOException {
    HttpGet get = new HttpGet(url);
    CloseableHttpClient client = getHTTPClient(connectedSystemConfiguration, "application/json");
    return executeRequest(client, get);
  }

  public static HttpResponse post(SimpleConfiguration connectedSystemConfiguration, String url, StringEntity jsonEntity ) throws IOException {
    HttpPost post = new HttpPost(url);
    post.setEntity(jsonEntity);
    CloseableHttpClient client = getHTTPClient(connectedSystemConfiguration, "application/json");
    return executeRequest(client, post);
  }

  public static HttpResponse multipartPost(SimpleConfiguration connectedSystemConfiguration, String url,
      HttpEntity requestEntity) throws IOException {
    HttpPost post = new HttpPost(url);
    post.setEntity(requestEntity);

    CloseableHttpClient client = getHTTPClient(connectedSystemConfiguration, "multipart/form-data");
    return executeRequest(client, post);
  }


}
