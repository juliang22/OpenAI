package com.appian.openai.templates.UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.appian.connectedsystems.simplified.sdk.SimpleIntegrationTemplate;
import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.configuration.Choice;
import com.appian.connectedsystems.templateframework.sdk.configuration.DisplayHint;
import com.appian.connectedsystems.templateframework.sdk.configuration.DocumentPropertyDescriptor;
import com.appian.connectedsystems.templateframework.sdk.configuration.LocalTypeDescriptor;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyDescriptor;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyDescriptorBuilder;
import com.appian.connectedsystems.templateframework.sdk.configuration.RefreshPolicy;
import com.appian.connectedsystems.templateframework.sdk.configuration.TextPropertyDescriptor;
import com.appian.connectedsystems.templateframework.sdk.configuration.TypeReference;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.ExtractedResult;
import std.ConstantKeys;
import std.Util;

public abstract class UIBuilder implements ConstantKeys {
  protected String api;
  protected String pathName;
  protected List<PropertyDescriptor<?>> pathVarsUI = new ArrayList<>();
  protected OpenAPI openAPI = null;

  protected Paths paths;
  protected List<String> choicesForSearch = new ArrayList<>();
  protected List<Choice> defaultChoices = new ArrayList<>();
  protected SimpleIntegrationTemplate simpleIntegrationTemplate;
  protected SimpleConfiguration integrationConfiguration;

  // Methods to implement when building out the API specific details of each request
  public abstract void buildRestCall(String restOperation, List<PropertyDescriptor<?>> result, String pathName);

  public abstract void buildGet(List<PropertyDescriptor<?>> result);

  public abstract void buildPost(List<PropertyDescriptor<?>> result);

  public abstract void buildPatch(List<PropertyDescriptor<?>> result);

  public abstract void buildDelete(List<PropertyDescriptor<?>> result);

  public void setSimpleIntegrationTemplate(SimpleIntegrationTemplate simpleIntegrationTemplate) {
    this.simpleIntegrationTemplate = simpleIntegrationTemplate;
  }

  public void setIntegrationConfiguration(SimpleConfiguration integrationConfiguration) {
    this.integrationConfiguration = integrationConfiguration;
  }

  public void setPathName(String pathName) {
    this.pathName = pathName;
  }

  // Find all occurrences of variables inside path (ex. {claimId})
  protected void setPathVarsUI() {
    List<String> pathVars = Util.getPathVarsStr(pathName);
    pathVars.forEach(key -> {
      TextPropertyDescriptor ui = simpleIntegrationTemplate.textProperty(key)
          .instructionText("")
          .isRequired(true)
          .isExpressionable(true)
          .refresh(RefreshPolicy.ALWAYS)
          .placeholder("1")
          .label(Util.camelCaseToTitleCase(key))
          .build();
      pathVarsUI.add(ui);
    });
  }

  public List<PropertyDescriptor<?>> getPathVarsUI() {
    return pathVarsUI;
  }

  public void ReqBodyUIBuilder(List<PropertyDescriptor<?>> result, Map<?,?> properties, Set<String> required) {
    LocalTypeDescriptor.Builder builder = simpleIntegrationTemplate.localType(REQ_BODY_PROPERTIES);
    properties.forEach((key, item) -> {
      Schema<?> itemSchema = (Schema<?>)item;
      String keyStr = key.toString();

      // If the property is a document. This creates the property outside of the request body to use the native Appian
      // document/file picker
      if (itemSchema.getFormat() != null && itemSchema.getFormat().equals("binary")) {
        DocumentPropertyDescriptor document = simpleIntegrationTemplate.documentProperty(keyStr)
            .label("Document " + Character.toUpperCase(keyStr.charAt(0)) + keyStr.substring(1))
            .isRequired(required.contains(keyStr))
            .isExpressionable(true)
            .refresh(RefreshPolicy.ALWAYS)
            .instructionText(itemSchema.getDescription())
            .build();
        result.add(document);
      } else {
        LocalTypeDescriptor property = parseRequestBody(keyStr, itemSchema, required);
        if (property != null) {
          builder.properties(property.getProperties());
        }
      }
    });

    result.add(simpleIntegrationTemplate.localTypeProperty(builder.build())
        .key(Util.removeSpecialCharactersFromPathName(pathName))
        .displayHint(DisplayHint.EXPRESSION)
        .isExpressionable(true)
        .label("Request Body")
        .description("Enter values to the properties below to send new or updated data to OpenAI. Not all properties are " +
            "required. Make sure to remove any unnecessary autogenerated properties. By default, null values will not be added " +
            "to the request. Use a space between apostrophes for sending empty text.")
        .instructionText(AUTOGENERATED_ERROR_DETAIL)
        .refresh(RefreshPolicy.ALWAYS)
        .build());
  }

  public StringBuilder parseOneOf(Schema<?> property) {
    StringBuilder propertyType = new StringBuilder();
    if (property instanceof StringSchema) {
      propertyType.append("String");
      propertyType.append(property.getExample() != null ?
          "\n   Example: " + "'" + property.getExample().toString().replaceAll("\n", "") + "'" :
          "");
    } else if (property instanceof IntegerSchema) {
      propertyType.append("Integer");
      propertyType.append(
          property.getExample() != null ? "\n   Example: " + property.getExample().toString().replaceAll("\n", "") : "");
    } else if (property instanceof BooleanSchema) {
      propertyType.append("Boolean");
      propertyType.append(
          property.getExample() != null ? "\n   Example: " + property.getExample().toString().replaceAll("\n", "") : "");
    } else if (property instanceof ArraySchema) {
      propertyType.append("Array of ").append(parseOneOf(property.getItems()));
      propertyType.append(
          property.getExample() != null ? "\n   Example: " + property.getExample().toString().replaceAll("\n", "") : "");
    }
    return propertyType;
  }

  public LocalTypeDescriptor parseRequestBody(String key, Schema<?> item, Set<?> requiredProperties) {

    LocalTypeDescriptor.Builder builder = simpleIntegrationTemplate.localType(key);

    if (item.getOneOf() != null) {    // property could be one of many things
      String description = item.getDescription() != null ? item.getDescription().replaceAll("\\n", "") : "";
      StringBuilder oneOfStrBuilder = new StringBuilder(
          description + "\n" + "'" + key + "'" + " can be one of the following " + "types: ");
      int propertyNum = 1;
      for (Schema<?> property : item.getOneOf()) {
        oneOfStrBuilder.append("\n").append(propertyNum++).append(". ").append(parseOneOf(property));
      }
      return builder.property(simpleIntegrationTemplate.textProperty(key)
          .placeholder(oneOfStrBuilder.toString())
          .isExpressionable(true)
          .refresh(RefreshPolicy.ALWAYS)
          .build()).build();
    } else if (item.getType().equals("object")) {

      if (item.getProperties() == null)
        return null;

      item.getProperties().forEach((innerKey, innerItem) -> {
        Schema<?> innerItemSchema = (Schema<?>)innerItem;
        Set<?> innerRequiredProperties =
            innerItemSchema.getRequired() != null ? new HashSet<>(innerItemSchema.getRequired()) : requiredProperties;
        LocalTypeDescriptor nested = parseRequestBody(innerKey, innerItemSchema, innerRequiredProperties);
        if (nested != null) {
          builder.properties(nested.getProperties());
        }
      });

      return simpleIntegrationTemplate.localType(key + "Builder")
          .properties(simpleIntegrationTemplate.localTypeProperty(builder.build()).refresh(RefreshPolicy.ALWAYS).build())
          .build();

    } else if (item.getType().equals("array")) {

      if (item.getItems() == null || item.getItems().getProperties() == null)
        return null;

      item.getItems().getProperties().forEach((innerKey, innerItem) -> {
        Schema<?> innerItemSchema = (Schema<?>)innerItem;
        Set<?> innerRequiredProperties =
            innerItemSchema.getRequired() != null ? new HashSet<>(innerItemSchema.getRequired()) : requiredProperties;
        LocalTypeDescriptor nested = parseRequestBody(innerKey, innerItemSchema, innerRequiredProperties);
        if (nested != null) {
          builder.properties(nested.getProperties());
        }
      });

      return simpleIntegrationTemplate.localType(key + "Builder").properties(
          // The listProperty needs a typeReference to a localProperty -> Below a localProperty is created
          // and hidden for use by the listProperty
          simpleIntegrationTemplate.localTypeProperty(builder.build())
              .key(key + "hidden")
              .isHidden(true)
              .refresh(RefreshPolicy.ALWAYS)
              .build(), simpleIntegrationTemplate.listTypeProperty(key)
              .refresh(RefreshPolicy.ALWAYS)
              .itemType(TypeReference.from(builder.build()))
              .build()).build();

    } else {
      // Base case: Create new property field depending on the type
      PropertyDescriptorBuilder<?> newProperty;
      switch (item.getType()) {
        case ("boolean"):
          newProperty = simpleIntegrationTemplate.booleanProperty(key);
          break;
        case ("integer"):
          newProperty = simpleIntegrationTemplate.integerProperty(key);
          break;
        case ("number"):
          newProperty = simpleIntegrationTemplate.doubleProperty(key);
          break;
        default:
          newProperty = simpleIntegrationTemplate.textProperty(key);
          break;
      }

      String isRequired = requiredProperties != null && requiredProperties.contains(key) ?
          "(Required) " + item.getDescription().replaceAll("\n", "") :
          item.getDescription().replaceAll("\n", "");
      return simpleIntegrationTemplate.localType(key + "Container")
          .property(newProperty.isExpressionable(true)
              .displayHint(DisplayHint.EXPRESSION)
              .refresh(RefreshPolicy.ALWAYS)
              .placeholder(isRequired)
              .build())
          .build();
    }
  }

  // Runs on initialization to set the default paths for the dropdown as well as a list of strings of choices used for
  // sorting when a query is entered
  public void setDefaultEndpoints(List<CustomEndpoint> customEndpoints) {
    // Build search choices when no search query has been entered
    // Check if rest call exists on path and add each rest call of path to list of choices
    Map<String,Operation> operations = new HashMap<>();

    paths.forEach((pathName, path) -> {
      if (PATHS_TO_REMOVE.contains(pathName))
        return;

      operations.put(GET, path.getGet());
      operations.put(POST, path.getPost());
      operations.put(PATCH, path.getPatch());
      operations.put(DELETE, path.getDelete());

      operations.forEach((restOperation, openAPIOperation) -> {
        if (openAPIOperation != null) {
          String name = restOperation + " - " + openAPIOperation.getSummary();
          String value = api + ":" + restOperation + ":" + pathName + ":" + openAPIOperation.getSummary();

          // Builds up choices for search on initial run with all paths
          choicesForSearch.add(value);

          // Choice UI built
          defaultChoices.add(Choice.builder().name(name).value(value).build());
        }
      });
    });

    // if there are custom endpoints that aren't from the openAPI spec, add them to the choices here
    if (customEndpoints.size() > 0) {
      customEndpoints.forEach(endpoint -> {
        String name = endpoint.getRestOperation() + " - " + endpoint.getSummary();

        // Builds up choices for search on initial run with all paths
        choicesForSearch.add(endpoint.getCustomEndpoint());

        // Choice UI built
        defaultChoices.add(Choice.builder().name(name).value(endpoint.getCustomEndpoint()).build());
      });
    }
  }

  // Sets the choices for endpoints with either default choices or sorted choices based off of the search query
  public TextPropertyDescriptor endpointChoiceBuilder() {

    // If there is a search query, sort the dropdown with the query
    String searchQuery = integrationConfiguration.getValue(SEARCH);
    ArrayList<Choice> choices = new ArrayList<>();
    if (searchQuery != null && !searchQuery.equals("") && !choicesForSearch.isEmpty()) {
      List<ExtractedResult> extractedResults = FuzzySearch.extractSorted(searchQuery, choicesForSearch);
      extractedResults.forEach(choice -> {
        String restType = choice.getString().split(":")[1];
        String restOperation = choice.getString().split(":")[3];
        choices.add(Choice.builder().name(restType + " - " + restOperation).value(choice.getString()).build());
      });
    }

    // If an endpoint is selected, the instructionText will update to the REST call and path name
    Object chosenEndpoint = integrationConfiguration.getValue(CHOSEN_ENDPOINT);
    String instructionText =
        chosenEndpoint != null ? chosenEndpoint.toString().split(":")[1] + "  " + chosenEndpoint.toString().split(":")[2] : "";
    return simpleIntegrationTemplate.textProperty(CHOSEN_ENDPOINT)
        .isRequired(true)
        .refresh(RefreshPolicy.ALWAYS)
        .label("Select Endpoint")
        .transientChoices(true)
        .instructionText(instructionText)
        .choices(choices.size() > 0 ? choices.toArray(new Choice[0]) : defaultChoices.toArray(new Choice[0]))
        .build();
  }
}
