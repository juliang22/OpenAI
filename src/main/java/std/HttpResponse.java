package std;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.appian.connectedsystems.templateframework.sdk.configuration.Document;

public class HttpResponse {
    private Map<String, Object> response;
    private int statusCode;
    private String statusLine;
    private List<Document> documents;

    public HttpResponse(int statusCode, String statusLine, HashMap<String, Object> result) {
        this.response = result;
        this.statusLine = statusLine;
        this.statusCode = statusCode;
    }

    public HttpResponse(int statusCode, String statusLine, HashMap<String, Object> result, List<Document> documents) {
        this.response = result;
        this.statusLine = statusLine;
        this.statusCode = statusCode;
        this.documents = documents;
    }

    public void setDocuments(Document document) {
        this.documents.add(document);
    }

    public List<Document> getDocuments() {
        return this.documents != null ? documents : null;
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
