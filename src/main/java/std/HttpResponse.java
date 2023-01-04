package std;

import java.util.HashMap;
import java.util.Map;

import com.appian.connectedsystems.templateframework.sdk.configuration.Document;

public class HttpResponse {
    private Map<String, Object> response;
    private int statusCode;
    private String statusLine;
    private Document document;

    public HttpResponse(int statusCode, String statusLine, HashMap<String, Object> result) {
        this.response = result;
        this.statusLine = statusLine;
        this.statusCode = statusCode;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public Document getDocument() {
        return this.document;
    }
    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusLine(){
        return statusLine;
    }

    public Map<String, Object> getResponse(){
        return response;
    }
}
