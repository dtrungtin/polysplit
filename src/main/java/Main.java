import java.util.List;

import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

import de.incentergy.geometry.PolygonSplitter;
import de.incentergy.geometry.impl.GreedyPolygonSplitter;

public class Main {
	public static void main(String[] args) {
		try {
			PolygonSplitter polygonSplitter = new GreedyPolygonSplitter();
			Polygon polygon = (Polygon) new WKTReader().read("POLYGON ((0 0, 100 0, 90 50, 10 50, 0 0))");
			List<Polygon> parts = polygonSplitter.split(polygon, 2);
			
			System.out.println(parts.get(0));
			System.out.println(parts.get(1));
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}
}
