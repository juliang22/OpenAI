package std;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import com.appian.openai.templates.OpenAICSP;
import com.appiancorp.process.expression.ExpressionEvaluationException;
import com.appiancorp.services.ServiceContext;
import com.appiancorp.services.ServiceContextFactory;
import com.appiancorp.services.exceptions.InvalidUserException;
import com.appiancorp.suiteapi.common.ServiceLocator;
import com.appiancorp.suiteapi.process.ProcessDesignService;
import com.appiancorp.suiteapi.type.TypedValue;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

public class Util implements ConstantKeys {

    public static List<String> getPathVarsStr(String pathName) {
        Matcher m = Pattern.compile("[^{*}]+(?=})").matcher(pathName);
        List<String> pathVars = new ArrayList<>();

        while (m.find()) {
            pathVars.add(m.group());
        }
        return pathVars;
    }

    public static String camelCaseToTitleCase(String str) {
        return Pattern.compile("(?=[A-Z])").splitAsStream(str)
            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
            .collect(Collectors.joining(" "));
    }

    public static String removeSpecialCharactersFromPathName(String pathName) {
        return pathName.replace("/", "").replace("{", "").replace("}", "");
    }

    public static OpenAPI getOpenApi(String api, ClassLoader classLoader) {
        try (InputStream input = classLoader.getResourceAsStream(api)) {
            String content = IOUtils.toString(input, StandardCharsets.UTF_8);
            ParseOptions parseOptions = new ParseOptions();
            parseOptions.setResolve(true); // implicit
            parseOptions.setResolveFully(true);
/*            parseOptions.setResolveCombinators(false);*/
            return new OpenAPIV3Parser().readContents(content, null, parseOptions).getOpenAPI();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getRecordUuid(String recordXmlString) {
        String uuidPattern = "a:uuid=\"([^\"]+)\"";

        Pattern pattern = Pattern.compile(uuidPattern);
        Matcher matcher = pattern.matcher(recordXmlString);

        if (matcher.find()) {
            String uuidValue = matcher.group(1);
            //            System.out.println("Extracted UUID: " + uuidValue);
            return uuidValue;
        } else {
            throw new RuntimeException("UUID not found in the input string.");
        }
    }


    public static String getRecordName(String recordUuid, ProcessDesignService pds) {
        String getRecordNameExpression = String.format(GET_RECORD_NAME, recordUuid);
        try {
            TypedValue recordNameAppian = pds.evaluateExpression(getRecordNameExpression);
            String[] recordNameWithIdArr = recordNameAppian.getValue().toString().split(" ");
            return Arrays.stream(recordNameWithIdArr)
                .skip(2)
                .collect(Collectors.joining(" "));
        } catch (ExpressionEvaluationException e) {
            throw new RuntimeException(e);
        }

    }

    public static List<String> getFields(String recordUuid, ProcessDesignService pds) {
        try {
            String getRecordFieldsExpression = String.format(GET_RECORD_FIELD_NAMES_QUERY, recordUuid);

            //            System.out.println("expression:" + queryRecordExpression);
            TypedValue recordFieldsAppian = pds.evaluateExpression(getRecordFieldsExpression);

            //            System.out.println("query result class:"+expressionResult.getValue().getClass()+" value:"+expressionResult.getValue());
            List<String> fieldNamesList = Arrays.asList((String[]) recordFieldsAppian.getValue());
            //            System.out.println("field names:" + String.join(" * ", fieldNamesList));

            return fieldNamesList;
        } catch (ExpressionEvaluationException e) {
            throw new RuntimeException(e);
        }
    }

    public static ProcessDesignService getProcessDesignService(String userName) {
        try {
            // TODO: figure out why non-admin service context throws error on record queries
            ServiceContext sc = ServiceContextFactory.getServiceContext(userName);
            /*      ServiceContext sc = ServiceLocator.getAdministratorServiceContext();*/
            //            ContentService cs = ServiceLocator.getContentService(sc);
            return ServiceLocator.getProcessDesignService(sc);

        } catch (InvalidUserException e) {
            throw new RuntimeException("Provide a valid username in the Connected System configuration.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String createFirstFunctionCallJson(Map<String, Map<String, String>> recordsInfoMap,
        String queryStr, ObjectMapper objectMapper) throws RuntimeException, IOException {
        ClassLoader classLoader = OpenAICSP.class.getClassLoader();
        String firstCallJson = "com/appian/openai/templates/firstCall.json";

        Set<String> recordNames = recordsInfoMap.keySet();
        try (InputStream input = classLoader.getResourceAsStream(firstCallJson)) {
            String content = IOUtils.toString(input, StandardCharsets.UTF_8);
            Map<String, Object> map = objectMapper.readValue(content, Map.class);
            // Access nested maps step by step
            Map<String, Object> parametersMap = (Map<String, Object>) map.get("parameters");
            Map<String, Object> propertiesMap = (Map<String, Object>) parametersMap.get("properties");
            Map<String, Object> primaryTableMap = (Map<String, Object>) propertiesMap.get("primary_table");

            // Update the "enum" key
            primaryTableMap.put("enum", recordNames);



            //functions
            List<Map<String, Object>> functions = new ArrayList<>();
            functions.add(map);

            // messages
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(OpenAIHelpers.createMessage("system", "Only use functions and parameters that you have been provided, " +
                "do not make up your own."));
            messages.add(OpenAIHelpers.createMessage("user", queryStr));

            // Build up OpenAI request
            Map<String, Object> functionReqBody = new HashMap<>();
            functionReqBody.put("model", "gpt-3.5-turbo-0613");
            functionReqBody.put("messages", messages);
            functionReqBody.put("functions", functions);
            Map<String, String> function_call = new HashMap<>();
            function_call.put("name", "get_primary_table");
            functionReqBody.put("function_call", function_call);

            return objectMapper.writeValueAsString(functionReqBody);
        }
    }
}
