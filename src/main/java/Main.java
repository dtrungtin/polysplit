import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTReader;

import de.incentergy.geometry.PolygonSplitter;
import de.incentergy.geometry.impl.GreedyPolygonSplitter;

public class Main {
	public static void main(String[] args) {
		Map<String, String> env  = System.getenv();
        String apifyToken = env.get("APIFY_TOKEN");
        String defaultKeyValueStoreId = env.get("APIFY_DEFAULT_KEY_VALUE_STORE_ID");

		try {
	        HttpClient client = new DefaultHttpClient();
	        
	        HttpGet request = new HttpGet("https://api.apify.com/v2/key-value-stores/" + defaultKeyValueStoreId + "/records/INPUT?disableRedirect=true&token=" + apifyToken);
	        HttpResponse response = client.execute(request);

	        String json = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
	        Gson gson = new Gson();
	        ApifyInput res = gson.fromJson(json, ApifyInput.class);
	        System.out.println(gson.toJson(res));
	        
			PolygonSplitter polygonSplitter = new GreedyPolygonSplitter();
			Polygon polygon = (Polygon) new WKTReader().read("POLYGON ((0 0, 100 0, 90 50, 10 50, 0 0))");
			List<Polygon> parts = polygonSplitter.split(polygon, 2);
			
			System.out.println(parts.get(0));
			System.out.println(parts.get(1));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class ApifyInput {
	private String polygon;
}
