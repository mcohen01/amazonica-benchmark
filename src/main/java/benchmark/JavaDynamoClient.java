package benchmark;

import com.amazonaws.services.dynamodb.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodb.model.AttributeValue;
import com.amazonaws.services.dynamodb.model.GetItemRequest;
import com.amazonaws.services.dynamodb.model.Key;
import com.amazonaws.services.dynamodb.model.PutItemRequest;

import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;

public class JavaDynamoClient {

    public static boolean putItem (AmazonDynamoDBClient client, String tableName, Map item) {
        PutItemRequest request = new PutItemRequest();
        request.setTableName(tableName);        
        request.setItem(toAttrValue(item));
        client.putItem(request);
        return true;
    }

    private static Map toAttrValue(Map<String, Object> item) {
        Iterator<String> iter = item.keySet().iterator();
        Map m = new HashMap();
        while (iter.hasNext()) {
            String key = iter.next();
            m.put(key, new AttributeValue((String) item.get(key)));
        }
        return m;
    }

    public static Map getItem (AmazonDynamoDBClient client, String tableName, String key) {
        GetItemRequest request = new GetItemRequest();
        request.setTableName(tableName);
        Key k = new Key();
        k.setHashKeyElement(new AttributeValue(key));
        request.setKey(k);
        return client.getItem(request).getItem();
    }



}
