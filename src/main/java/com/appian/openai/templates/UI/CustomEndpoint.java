package com.appian.openai.templates.UI;

public class CustomEndpoint {
  String apiType;
  String restOperation;
  String path;
  String summary;

  CustomEndpoint(String apiType, String restOperation, String path, String summary) {
    this.apiType = apiType;
    this.restOperation = restOperation;
    this.path = path;
    this.summary = summary;
  }

  public String getCustomEndpoint() {
    return apiType + ":" + restOperation + ":" + path + ":" + summary;
  }

  public String getApiType() {
    return apiType;
  }

  public String getRestOperation() {
    return restOperation;
  }

  public String getPath() {
    return path;
  }

  public String getSummary() {
    return summary;
  }
}
