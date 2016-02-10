package de.tud.plt.r43ples.webservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.log4j.Logger;
import org.glassfish.jersey.server.mvc.Template;

import de.tud.plt.r43ples.exception.InternalErrorException;
import de.tud.plt.r43ples.management.GitRepositoryState;
import de.tud.plt.r43ples.management.RevisionManagement;
import de.tud.plt.r43ples.management.SampleDataSet;
import de.tud.plt.r43ples.visualisation.VisualisationBatik;
import de.tud.plt.r43ples.visualisation.VisualisationD3;

@Path("/")
public class Misc {
	
	private final static Logger logger = Logger.getLogger(Misc.class);
	
	/**
	 * Landing page
	 *
	 */
	@GET
	@Template(name = "/home.mustache")
	@Produces(MediaType.TEXT_HTML)
	public final Map<String, Object> getLandingPage() {
		logger.info("Get Landing page");
		Map<String, Object> htmlMap = new HashMap<String, Object>();
		htmlMap.put("version", Endpoint.class.getPackage().getImplementationVersion() );
		htmlMap.put("git", GitRepositoryState.getGitRepositoryState());	
		return htmlMap;
	}
	
	@GET
	@Path("help")
	@Template(name = "/help.mustache")
	@Produces(MediaType.TEXT_HTML)
	public final Map<String, Object> getHelpPage() {
		logger.info("Get Landing page");
		Map<String, Object> htmlMap = new HashMap<String, Object>();
		htmlMap.put("help_active", true);
		htmlMap.put("version", Endpoint.class.getPackage().getImplementationVersion() );
		htmlMap.put("git", GitRepositoryState.getGitRepositoryState());	
		return htmlMap;
	}
	
	/**
	 * Creates sample datasets
	 * @return information provided as HTML response
	 * @throws InternalErrorException 
	 */
	@Path("createSampleDataset")
	@GET
	@Template(name = "/exampleDatasetGeneration.mustache")
	public final Map<String, Object> createSampleDataset(@QueryParam("dataset") @DefaultValue("all") final String graph) throws InternalErrorException {
		List<String> graphs = new ArrayList<>();
		
		if (graph.equals("1") || graph.equals("all")){
			graphs.add(SampleDataSet.createSampleDataset1().graphName);
		}
		if (graph.equals("2") || graph.equals("all")){
			graphs.add(SampleDataSet.createSampleDataset2().graphName);
		}
		if (graph.equals("merging") || graph.equals("all")){
			graphs.add(SampleDataSet.createSampleDataSetMerging().graphName);
		}
		if (graph.equals("merging-classes") || graph.equals("all")){
			graphs.add(SampleDataSet.createSampleDataSetMergingClasses());
		}
		if (graph.equals("renaming") || graph.equals("all")){
			graphs.add(SampleDataSet.createSampleDataSetRenaming());
		}
		if (graph.equals("complex-structure") || graph.equals("all")){
			graphs.add(SampleDataSet.createSampleDataSetComplexStructure());
		}
		if (graph.equals("rebase") || graph.equals("all")){
			graphs.add(SampleDataSet.createSampleDataSetRebase());
		}
		if (graph.equals("forcerebase") || graph.equals("all")){
			graphs.add(SampleDataSet.createSampleDataSetForceRebase());
		}
		if (graph.equals("fastforward") || graph.equals("all")){
			graphs.add(SampleDataSet.createSampleDataSetFastForward());
		}
		Map<String, Object> htmlMap = new HashMap<String, Object>();
	    htmlMap.put("graphs", graphs);
	    
		return htmlMap;			
	}
	
	

	/**
	 * Provide revision information about R43ples system.
	 * 
	 * @param graph
	 *            Provide only information about this graph (if not null)
	 * @return RDF model of revision information
	 */
	@Path("revisiongraph")
	@GET
	@Produces({ "text/turtle", "application/rdf+xml", MediaType.APPLICATION_JSON, MediaType.TEXT_HTML,
			MediaType.APPLICATION_SVG_XML, "application/ld+json" })
	public final Response getRevisionGraph(@HeaderParam("Accept") final String format_header,
			@QueryParam("format") final String format_query, @QueryParam("graph") @DefaultValue("") final String graph) {
		String format = (format_query != null) ? format_query : format_header;
		logger.info("Get Revision Graph: " + graph + " (format: " + format+")");
		
		ResponseBuilder response = Response.ok();
		if (format.equals("batik")) {
			response.type(MediaType.TEXT_HTML);
			response.entity(VisualisationBatik.getHtmlOutput(graph));
		} else if (format.equals("d3")) {
			response.entity(VisualisationD3.getHtmlOutput(graph));
		}
		else {
			response.entity(RevisionManagement.getRevisionInformation(graph, format));
			response.type(format);
		}
		return response.build();
	}
	
	/**
	 * Provides content of graph in the attached triple store
	 * 
	 * @return list of graphs which are under revision control
	 */
	@Path("contentOfGraph")
	@GET
	@Produces({ "text/turtle", "application/rdf+xml", MediaType.APPLICATION_JSON, MediaType.TEXT_HTML,
		MediaType.APPLICATION_SVG_XML, "application/ld+json" })
	public final Response getContentOfGraph(
			@HeaderParam("Accept") final String format_header,
			@QueryParam("format") @DefaultValue("application/json") final String format_query,
			@QueryParam("graph") final String graphName) {
		logger.info("Get Content of graph " + graphName);
		String format = (format_query != null) ? format_query : format_header;
		logger.debug("format: " + format);
		String result = RevisionManagement.getContentOfGraphByConstruct(graphName, format);
		ResponseBuilder response = Response.ok();
		return response.entity(result).type(format).build();
	}
	
	

	

}
