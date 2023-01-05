package std;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;



public class HTTP implements ConstantKeys {

  public static CloseableHttpClient getHTTPClient(SimpleConfiguration connectedSystemConfiguration) {
    final String token = connectedSystemConfiguration.getValue(API_KEY);
    final String org = connectedSystemConfiguration.getValue(ORGANIZATION);

    List<Header> defaultHeaders = new ArrayList<>();
    defaultHeaders.add(new BasicHeader("Content-Type", "application/json"));
    defaultHeaders.add(new BasicHeader("Authorization", "Bearer " + token));
    if (org != null) defaultHeaders.add(new BasicHeader("OpenAI-Organization", org));
    try (CloseableHttpClient client = HttpClientBuilder.create().setDefaultHeaders(defaultHeaders).build()) {
      return client;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static HttpResponse testAuth(SimpleConfiguration connectedSystemConfiguration) throws IOException {
    final String testURL = "https://api.openai.com/v1/models";
    HttpGet get = new HttpGet(testURL);

    try (CloseableHttpResponse response = getHTTPClient(connectedSystemConfiguration).execute(get)) {
      HashMap<String,Object> responseEntity = new ObjectMapper()
          .readValue(EntityUtils.toString(response.getEntity()), new TypeReference<HashMap<String,Object>>() {});
      return new HttpResponse(response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase(), responseEntity);
    }
  }
}
