package std;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.appian.connectedsystems.templateframework.sdk.configuration.RefreshPolicy;
import com.appian.connectedsystems.templateframework.sdk.configuration.TextPropertyDescriptor;

public interface ConstantKeys {

  // CSP Auth
  String API_KEY = "apiKey";
  String ORGANIZATION = "organization";


  String CHOSEN_ENDPOINT = "chosenEndpoint";
  String ROOT_URL = "https://api.openai.com/v1";

  // API Types
  String JOBS = "jobs";
  String CLAIMS = "claims";
  String POLICIES = "policies";
  String ACCOUNTS = "accounts";

  String OPENAI = "openai";

  //Rest call types
  String GET = "GET";

  String POST = "POST";
  String PATCH = "PATCH";
  String DELETE = "DELETE";

  Set<String> PATHS_TO_REMOVE = new HashSet<>(Arrays.asList("/answers", "/classifications", "/engines", "/engines/{engine_id}",
   "/engines/{engine_id}/search"));

  String NO_REQ_BODY = "noReqBody";

  TextPropertyDescriptor NO_REQ_BODY_UI = new TextPropertyDescriptor.TextPropertyDescriptorBuilder()
      .key(NO_REQ_BODY)
      .isReadOnly(true)
      .instructionText("No Request Body is required to execute this POST")
      .build();
  String INCLUDED_RESOURCES = "includedResources";

  String REQ_BODY = "reqBody";
  String REQ_BODY_PROPERTIES = "reqBodyProperties";
  String DOCUMENT = "document";
  String PAGESIZE = "pagesize";
  String PADDING = "padding";
  String SORT = "sort";
  String SORT_ORDER = "sortOrder";
  String FILTER_FIELD = "filterField";
  String FILTER_OPERATOR = "filterOperator";
  String FILTER_VALUE = "filterComparator";
  Map<String, String> FILTERING_OPTIONS = new HashMap<String, String>() {{
    put("=", "eq");
    put("≠", "ne");
    put("<", "lt");
    put(">", "gt");
    put("≤", "le");
    put("≥", "ge");
    put("In", "in");
    put("Not In", "ni");
    put("Starts With", "sw");
    put("Contains", "cn");
  }};

  String SEARCH = "search";
}
