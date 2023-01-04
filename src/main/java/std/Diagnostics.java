package std;

import java.util.HashMap;
import java.util.Map;

public class Diagnostics {
    long start;
    long end;
    Map<String, Object> requestDiagnostic;
    public Diagnostics(){
        requestDiagnostic = new HashMap<>();
    }

    public void startTiming(){
        start = System.currentTimeMillis();
    }

    public void stopTiming(){
        end = System.currentTimeMillis();
    }

    public void add(String key, Object value){
        requestDiagnostic.put(key, value);
    }

    public void addAll(Map<String, String> map){
        requestDiagnostic.putAll(map);
    }

    public long getTiming(){
        return end - start;
    }

    public Map<String, Object> getDiagnostics(){
        return requestDiagnostic;
    }


}

