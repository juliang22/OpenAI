package std;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.IntegrationError;
import com.appian.connectedsystems.templateframework.sdk.IntegrationResponse;
import com.appian.connectedsystems.templateframework.sdk.diagnostics.IntegrationDesignerDiagnostic;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

public class Util {

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

    public static String filterRules(String str) {
        return str == null ?
            null :
            str.replaceAll(" ", "%20").replaceAll(":","::");
    }

    public static String removeLastChar(String str) {
        return str.substring(0, str.length() - 1);
    }

}
