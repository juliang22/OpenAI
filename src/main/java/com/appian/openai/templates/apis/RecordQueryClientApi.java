package com.appian.openai.templates.apis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.appian.connectedsystems.simplified.sdk.SimpleClientApi;
import com.appian.connectedsystems.simplified.sdk.SimpleClientApiRequest;
import com.appian.connectedsystems.templateframework.sdk.ClientApiResponse;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.TemplateId;
import com.appiancorp.suiteapi.process.ProcessDesignService;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.Request;
import std.ConstantKeys;
import std.OpenAIHelpers;
import std.Util;

@TemplateId(name = "recordQuery")
public class RecordQueryClientApi extends SimpleClientApi implements ConstantKeys {

  @Override
  protected ClientApiResponse execute(SimpleClientApiRequest simpleRequest, ExecutionContext executionContext) {

    // Set up Services
    ProcessDesignService pds = Util.getProcessDesignService("admin.user"); // TODO: Change to get username from CSP or serverlet
    ObjectMapper objectMapper = new ObjectMapper();

    // Getting Payload
    Map<String,Object> payload = simpleRequest.getPayload();
    String queryStr = payload.get("queryStr").toString();

    // Getting Record names and uuids
    List<String> recordXMLs = ((List)payload.get("records"));
    Map<String, Map<String, String>> recordsInfoMap = new HashMap();
    for (String recordXML : recordXMLs) {
      String recordUuid = Util.getRecordUuid(recordXML);
      String recordName = Util.getRecordName(recordUuid, pds);
      Map<String, String> recordInfo = new HashMap<>();
      recordInfo.put("uuid", recordUuid);
      recordInfo.put("name", recordName);
      recordInfo.put("xml", recordXML);
      recordsInfoMap.put(recordName, recordInfo);
    }

    // First Call
    try {
      String firstCallJson = Util.createFirstFunctionCallJson(recordsInfoMap, queryStr, objectMapper);
      Request request = OpenAIHelpers.buildOkHTTPRequest(firstCallJson);
      String selectedRecord = OpenAIHelpers.functionCallHTTPRequest(simpleRequest.getConnectedSystemConfiguration(), request,
          objectMapper);;

      List<Integer> fakeIds = new ArrayList<>(Arrays.asList(1,2,3,4,5));
      Map<String, Object> fakeResponse = new HashMap<>();
      fakeResponse.put("fakeIdData", fakeIds);
      fakeResponse.put("returnedRecord", recordsInfoMap.get(selectedRecord).get("xml"));

      Map<String, Object> filterObj = new HashMap<>();
      filterObj.put("filterField", "fakeFilterField1");
      filterObj.put("filterOperator", "fakeFilerOperator1");
      filterObj.put("filterValue", "fakeFilterValue1");
      List<Map<String, Object>> filters = new ArrayList<>();
      filters.add(filterObj);
      fakeResponse.put("filters", filters);
      return new ClientApiResponse(fakeResponse);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }





    /*    List<String> fieldNames = Util.getFields(uuid, pds);*/





  }
}
