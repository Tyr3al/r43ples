package de.tud.plt.r43ples.visualisation;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;


public class VisualisationD3 {
	
	public static String getGraphVizHtmlOutput2(String graphName) {
		MustacheFactory mf = new DefaultMustacheFactory();
	    Mustache mustache = mf.compile("templates/graphvisualisation_d3.mustache");
	    StringWriter sw = new StringWriter();
	    
	    Map<String, Object> scope = new HashMap<String, Object>();
	    scope.put("graphName", graphName);
	    
	    mustache.execute(sw, scope);		
		return sw.toString();
	}	
}
