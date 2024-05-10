package std;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import com.appian.connectedsystems.templateframework.sdk.configuration.Choice;

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

    // Fixing legacy mistake where value/key of endpoint was set with endpoint summary information. API summaries are
    // variable to change leading to issues when the API yaml is updated. This cause the key to change leading to issues
    // when the endpoint that changed was the selected endpoint from the user. This check will leave the chosenEndpoint's
    // value as is and not update to value without the summary
    public static boolean isLegacyAPIWithSummaryAsValue(String chosenEndpoint, String value) {
        String[] split = chosenEndpoint.split(":");
        String restOperation = value.split(":")[1];
        String pathName = value.split(":")[2];
        if (chosenEndpoint != null && split[1].equals(restOperation) && split[2].equals(pathName) && split.length == 4) {
            return true;
        }
        return false;
    }

}
