package de.tud.plt.r43ples.visualisation;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpException;

import att.grappa.Attribute;
import att.grappa.Edge;
import att.grappa.Graph;
import att.grappa.Node;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import de.tud.plt.r43ples.exception.InternalServerErrorException;
import de.tud.plt.r43ples.management.Config;
import de.tud.plt.r43ples.management.GitRepositoryState;
import de.tud.plt.r43ples.management.RevisionManagement;
import de.tud.plt.r43ples.management.TripleStoreInterface;


public class GraphVizVisualisation {
	
	public static String getGraphVizOutput(String namedGraph) throws IOException, HttpException {
		Graph graph =  new Graph("RevisionGraph of " + namedGraph);
		String query_nodes = RevisionManagement.prefixes + String.format(""
				+ "SELECT DISTINCT ?revision ?number "
				+ "FROM <%s> "
				+ "WHERE {"
				+ " ?revision a rmo:Revision;"
				+ "		rmo:revisionOf <%s>;"
				+ "		rmo:revisionNumber ?number."
				+ "}", Config.revision_graph, namedGraph );
		
		ResultSet resultSet_nodes = TripleStoreInterface.executeSelectQuery(query_nodes);
		if (!resultSet_nodes.hasNext())
			throw new InternalServerErrorException("Specified graph '"+namedGraph +"' does not have any revision");
		
		while (resultSet_nodes.hasNext()) {
			QuerySolution qs = resultSet_nodes.next();
			String rev = qs.getResource("revision").toString();
			String number = qs.getLiteral("number").toString();
			Node newNode = new Node(graph, rev);
			newNode.setAttribute(Attribute.LABEL_ATTR, number+" | "+rev);
			newNode.setAttribute(Attribute.SHAPE_ATTR, Attribute.RECORD_SHAPE);
			graph.addNode(newNode);
		}		
		
		String query_edge = RevisionManagement.prefixes + String.format(""
				+ "SELECT DISTINCT ?revision ?next_revision "
				+ "FROM <%s> "
				+ "WHERE {"
				+ " ?revision a rmo:Revision;"
				+ "		rmo:revisionOf <%s>."
				+ "	?next_revision a rmo:Revision;"
				+ "		prov:wasDerivedFrom ?revision."
				+ "}", Config.revision_graph, namedGraph );
		
		ResultSet resultSet_edge = TripleStoreInterface.executeSelectQuery(query_edge);
		while (resultSet_edge.hasNext()) {
			QuerySolution qs = resultSet_edge.next();
			String rev = qs.getResource("revision").toString();
			String next = qs.getResource("next_revision").toString();
			Node newNode = graph.findNodeByName(rev);
			Node nextNode = graph.findNodeByName(next);
			graph.addEdge(new Edge(graph, newNode, nextNode));
		}
		
		String query_reference = RevisionManagement.prefixes + String.format(""
				+ "SELECT DISTINCT ?revision ?label "
				+ "FROM <%s> "
				+ "WHERE {"
				+ " ?revision a rmo:Revision;"
				+ "		rmo:revisionOf <%s>."
				+ "	?reference a rmo:Reference;"
				+ "		rmo:references ?revision;"
				+ "		rdfs:label ?label."
				+ "}", Config.revision_graph, namedGraph );
		
		ResultSet resultSet_reference = TripleStoreInterface.executeSelectQuery(query_reference);
		while (resultSet_reference.hasNext()) {
			QuerySolution qs = resultSet_reference.next();
			String rev = qs.getResource("revision").toString();
			String reference = qs.getLiteral("label").toString();
			Node refNode = new Node(graph, reference);
			refNode.setAttribute(Attribute.SHAPE_ATTR, Attribute.DIAMOND_SHAPE);
			if (reference.equals("master")){
				refNode.setAttribute(Attribute.COLOR_ATTR, "red");
			}
			Node revNode = graph.findNodeByName(rev);
			graph.addEdge(new Edge(graph, refNode, revNode));
		}
		
		
		StringWriter sw = new StringWriter();
		graph.printGraph(sw);
	    return sw.toString();
	}

	public static String getGraphVizHtmlOutput(String graphName) throws IOException, HttpException {
		MustacheFactory mf = new DefaultMustacheFactory();
	    Mustache mustache = mf.compile("templates/graphvisualisation.mustache");
	    StringWriter sw = new StringWriter();
	    
	    Map<String, Object> scope = new HashMap<String, Object>();
	    scope.put("graphName", graphName);
	    scope.put("graphViz", getGraphVizOutput(graphName));
	    scope.put("git", GitRepositoryState.getGitRepositoryState());
	    
	    mustache.execute(sw, scope);		
		return sw.toString();
	}	
}
