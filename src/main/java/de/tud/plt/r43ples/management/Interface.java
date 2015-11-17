package de.tud.plt.r43ples.management;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import de.tud.plt.r43ples.exception.InternalErrorException;
import de.tud.plt.r43ples.exception.QueryErrorException;
import de.tud.plt.r43ples.merging.MergeManagement;
import de.tud.plt.r43ples.merging.MergeQueryTypeEnum;
import de.tud.plt.r43ples.merging.MergeResult;
import de.tud.plt.r43ples.merging.control.FastForwardControl;
import de.tud.plt.r43ples.merging.management.StrategyManagement;
import de.tud.plt.r43ples.triplestoreInterface.TripleStoreInterfaceSingleton;
import de.tud.plt.r43ples.webservice.Endpoint;

public class Interface {

	/** default logger for this class */
	private final static Logger logger = Logger.getLogger(Interface.class);
	
	private static final int patternModifier = Pattern.DOTALL + Pattern.MULTILINE + Pattern.CASE_INSENSITIVE;
	
	/**
	 * 
	 * @param query R43ples query string
	 * @param format serialisation format of the result 
	 * @param query_rewriting option if query rewriting should be enabled
	 * @return string containing result of the query
	 * @throws InternalErrorException
	 */
	public static String sparqlSelectConstructAsk(final String query, final String format, final boolean query_rewriting)
			throws InternalErrorException {
		String result;
		if (query_rewriting) {
			String query_rewritten = SparqlRewriter.rewriteQuery(query);
			result = TripleStoreInterfaceSingleton.get().executeSelectConstructAskQuery(query_rewritten, format);
		}
		else {
			result = getSelectConstructAskResponseClassic(query, format);
		}
		return result;
	}
	
	/**
	 * @param query
	 * @param format
	 * @return
	 * @throws InternalErrorException 
	 */
	private static String getSelectConstructAskResponseClassic(final String query, final String format) throws InternalErrorException {
		final Pattern patternSelectFromPart = Pattern.compile(
				"(?<type>FROM|GRAPH)\\s*<(?<graph>[^>]*)>\\s*REVISION\\s*\"(?<revision>[^\"]*)\"",
				Pattern.DOTALL + Pattern.MULTILINE + Pattern.CASE_INSENSITIVE);

		String queryM = query;

		Matcher m = patternSelectFromPart.matcher(queryM);
		while (m.find()) {
			String graphName = m.group("graph");
			String type = m.group("type");
			String revisionNumber = m.group("revision").toLowerCase();
			String newGraphName;

			// if no revision number is declared use the MASTER as default
			if (revisionNumber == null) {
				revisionNumber = "master";
			}
			if (revisionNumber.equalsIgnoreCase("master")) {
				// Respond with MASTER revision - nothing to be done - MASTER revisions are already created in the named graphs
				newGraphName = graphName;
			} else {
				if (RevisionManagement.isBranch(graphName, revisionNumber)) {
					newGraphName = RevisionManagement.getReferenceGraph(graphName, revisionNumber);
				} else {
					// Respond with specified revision, therefore the revision must be generated - saved in graph <RM-TEMP-graphName>
					newGraphName = graphName + "-temp";
					RevisionManagement.generateFullGraphOfRevision(graphName, revisionNumber, newGraphName);
				}
			}

			queryM = m.replaceFirst(type + " <" + newGraphName + ">");
			m = patternSelectFromPart.matcher(queryM);
			
		}
		String response = TripleStoreInterfaceSingleton.get().executeSelectConstructAskQuery(queryM, format);
		return response;
	}
	
	
	public static void sparqlUpdate(final String query, final String user, final String commitMessage)
			throws InternalErrorException {

		final Pattern patternUpdateRevision = Pattern.compile(
				"(?<action>INSERT|DELETE|WHERE)(?<data>\\s*DATA){0,1}\\s*\\{\\s*GRAPH\\s*<(?<graph>[^>]*)>\\s*REVISION\\s*\"(?<revision>[^\"]*)\"",
				patternModifier);
		final Pattern patternEmptyGraphPattern = Pattern.compile(
				"GRAPH\\s*<(?<graph>[^>]*)>\\s*\\{\\s*\\}",
				patternModifier);
		final Pattern patternGraphWithRevision = Pattern.compile(
				"GRAPH\\s*<(?<graph>[^>]*)>\\s*REVISION\\s*\"(?<revision>[^\"]*)\"",
				patternModifier);
		logger.debug("SPARQL Update detected");
		
		// write to add and delete sets
		// (replace graph names in query)
		String queryM = query;
		HashMap<String, String> nextRevisionNumbers = new HashMap<String, String>();
		Matcher m = patternUpdateRevision.matcher(queryM);
		
		while (m.find()) {
			String graphName = m.group("graph");
			String revisionName = m.group("revision").toLowerCase(); 	// can contain revision numbers or reference names
			String action = m.group("action");															
			
			if (action.equalsIgnoreCase("WHERE")) {
				// TODO: replace generateFullGraphOfRevision with query rewriting option
				String tempGraphName = graphName + "-temp";
				RevisionManagement.generateFullGraphOfRevision(graphName, revisionName, tempGraphName);
				queryM = m.replaceFirst(String.format("WHERE { GRAPH <%s>", tempGraphName));
			}
			else {
				if (!RevisionManagement.isBranch(graphName, revisionName)) {
					throw new InternalErrorException("Revision is not referenced by a branch");
				}
				String newRevisionNumber;
				if (nextRevisionNumbers.containsKey(graphName)) {
					newRevisionNumber = nextRevisionNumbers.get(graphName);
				}
				else {
					newRevisionNumber = RevisionManagement.getNextRevisionNumber(graphName);
					nextRevisionNumbers.put(graphName, newRevisionNumber);
				}
				String addSetGraphUri = graphName + "-addSet-" + newRevisionNumber;
				String removeSetGraphUri = graphName + "-deleteSet-" + newRevisionNumber;
				
				String data = m.group("data");
				if (data == null)
					data = "";
				if (action.equalsIgnoreCase("INSERT")) {
					queryM = m.replaceFirst(String.format("INSERT %s { GRAPH <%s>", data, addSetGraphUri));
				} else if (action.equalsIgnoreCase("DELETE")) {
					queryM = m.replaceFirst(String.format("INSERT %s { GRAPH <%s>", data, removeSetGraphUri));
				}
			}
			m = patternUpdateRevision.matcher(queryM);
		}
		
		// Remove empty insert clauses which otherwise will lead to errors
		m = patternEmptyGraphPattern.matcher(queryM);
		queryM = m.replaceAll("");

		TripleStoreInterfaceSingleton.get().executeUpdateQuery(queryM);

		queryM = query;
		m = patternGraphWithRevision.matcher(queryM);
		while (m.find()) {
			String graphName = m.group("graph");
			String revisionName = m.group("revision").toLowerCase();	// can contain revision
																		// numbers or reference
																		// names
			// General variables
			String newRevisionNumber = nextRevisionNumbers.get(graphName);
			String referenceFullGraph = RevisionManagement.getReferenceGraph(graphName, revisionName);
			String addSetGraphUri = graphName + "-addSet-" + newRevisionNumber;
			String removeSetGraphUri = graphName + "-deleteSet-" + newRevisionNumber;

			RevisionManagement.addNewRevisionFromChangeSet(user, commitMessage, graphName, revisionName, newRevisionNumber,
					referenceFullGraph, addSetGraphUri, removeSetGraphUri);
			
			queryM = m.replaceAll(String.format("GRAPH <%s> ", graphName));
			m = patternGraphWithRevision.matcher(queryM);
		}
	}


	
	public static String sparqlCreateGraph(final String query) throws QueryErrorException {
		final Pattern patternCreateGraph = Pattern.compile(
				"CREATE\\s*(?<silent>SILENT)?\\s*GRAPH\\s*<(?<graph>[^>]*)>",
				patternModifier);
		String graphName = null;
		Matcher m = patternCreateGraph.matcher(query);
		boolean found = false;
		while (m.find()) {
			found = true;
			graphName = m.group("graph");
//			String silent = m.group("silent");
			
			// Create graph
			TripleStoreInterfaceSingleton.get().executeCreateGraph(graphName);
		    
		    if (RevisionManagement.getMasterRevisionNumber(graphName) == null)
		    {
			    // Add R43ples information
			    RevisionManagement.putGraphUnderVersionControl(graphName);
			}
		}
		if (!found) {
			throw new QueryErrorException("Query doesn't contain a correct CREATE query:\n" + query);
		}
		return graphName;
	}
	
	public static void sparqlDropGraph(final String query) throws QueryErrorException {
		final Pattern patternDropGraph = Pattern.compile(
				"DROP\\s*(?<silent>SILENT)?\\s*GRAPH\\s*<(?<graph>[^>]*)>",
				patternModifier);
		Matcher m = patternDropGraph.matcher(query);
		boolean found = false;
		while (m.find()) {
			found = true;
			String graphName = m.group("graph");
			RevisionManagement.purgeRevisionInformation(graphName);
		}
		if (!found) {
			throw new QueryErrorException("Query contain errors:\n" + query);
		}
	}
	
	public static void sparqlTagOrBranch(final String sparqlQuery, final String user, final String commitMessage)
			throws InternalErrorException, QueryErrorException {
		final Pattern patternBranchOrTagQuery = Pattern.compile(
				"(?<action>TAG|BRANCH)\\s*GRAPH\\s*<(?<graph>[^>]*)>\\s*REVISION\\s*\"(?<revision>[^\"]*)\"\\s*TO\\s*\"(?<name>[^\"]*)\"",
				patternModifier);
		Matcher m = patternBranchOrTagQuery.matcher(sparqlQuery);

		boolean foundEntry = false;
		while (m.find()) {
			foundEntry = true;
			String action = m.group("action");
			String graphName = m.group("graph");
			String revisionNumber = m.group("revision").toLowerCase();
			String referenceName = m.group("name").toLowerCase();
			if (action.equals("TAG")) {
				RevisionManagement.createTag(graphName, revisionNumber, referenceName, user, commitMessage);
			} else if (action.equals("BRANCH")) {
				RevisionManagement.createBranch(graphName, revisionNumber, referenceName, user, commitMessage);
			} else {
				throw new QueryErrorException("Error in query: " + sparqlQuery);
			}	    
		}
		if (!foundEntry) {
			throw new QueryErrorException("Error in query: " + sparqlQuery);
		}
	}
	
	
	
	/**
	 * 
	 * 
	 * @return if fast-forward was successful
	 * @throws InternalErrorException 
	 */
	public static boolean sparqlFastForwardMerge(final String sparqlQuery, final String user, final String commitMessage) throws InternalErrorException
	{
		final Pattern patternFastForwardQuery =  Pattern.compile(
				"MERGE\\s*FF\\s*GRAPH\\s*<(?<graph>[^>]*?)>\\s*(\\s*(?<sdd>SDD)?\\s*<(?<sddURI>[^>]*?)>)?\\s*BRANCH\\s*\"(?<branchNameA>[^\"]*?)\"\\s*INTO\\s*\"(?<branchNameB>[^\"]*?)\"",
				patternModifier);
		Matcher m = patternFastForwardQuery.matcher(sparqlQuery);
		if (!m.find())
			throw new InternalErrorException("Error in query: " + sparqlQuery);
			
		String graphName = m.group("graph");
		String branchNameA = m.group("branchNameA").toLowerCase();
		String branchNameB = m.group("branchNameB").toLowerCase();
		String revisionGraph = RevisionManagement.getRevisionGraph(graphName);
		
		if (!FastForwardControl.fastForwardCheck(revisionGraph, branchNameA, branchNameB)) {
			return false;
		}
		String branchUriA = RevisionManagement.getBranchUri(revisionGraph, branchNameA);
		String branchUriB = RevisionManagement.getBranchUri(revisionGraph, branchNameB);
		
		String fullGraphUriA = RevisionManagement.getFullGraphUri(revisionGraph, branchUriA);
		String fullGraphUriB = RevisionManagement.getFullGraphUri(revisionGraph, branchUriB);

		String revisionUriA = RevisionManagement.getRevisionUri(revisionGraph, branchNameA);
		String revisionUriB = RevisionManagement.getRevisionUri(revisionGraph, branchNameB);
		
		StrategyManagement.moveBranchReference(revisionGraph, branchUriB, revisionUriB, revisionUriA);
		// TODO: add reference commit with user and commit message
		StrategyManagement.updateRevisionOfBranch(revisionGraph, graphName, branchUriB, revisionUriB, revisionUriA);	
		StrategyManagement.fullGraphCopy(fullGraphUriA, fullGraphUriB);
		return true;
	}
	
	
	public static MergeResult sparqlThreeWayMerge(final String sparqlQuery, final String user, final String commitMessage, final String format) throws InternalErrorException {
		final Pattern patternMergeQuery =  Pattern.compile(
				"MERGE\\s*(?<action>AUTO|MANUAL)?\\s*GRAPH\\s*<(?<graph>[^>]*?)>\\s*(SDD\\s*<(?<sdd>[^>]*?)>)?\\s*BRANCH\\s*\"(?<branchNameA>[^\"]*?)\"\\s*INTO\\s*\"(?<branchNameB>[^\"]*?)\"(\\s*(?<with>WITH)?\\s*\\{(?<triples>.*)\\})?",
				patternModifier);
		Matcher m = patternMergeQuery.matcher(sparqlQuery);
		
		if (!m.find())
			throw new InternalErrorException("Error in query: " + sparqlQuery);
		
		String action = m.group("action");
		String graphName = m.group("graph");
		String sdd = m.group("sdd");
		String branchNameA = m.group("branchNameA").toLowerCase();
		String branchNameB = m.group("branchNameB").toLowerCase();
		String with = m.group("with");
		String triples = m.group("triples");
		
		String revisionGraph = RevisionManagement.getRevisionGraph(graphName);
		String revisionUriA = RevisionManagement.getRevisionUri(revisionGraph, branchNameA);
		String revisionUriB = RevisionManagement.getRevisionUri(revisionGraph, branchNameB);
		
		logger.debug("action: " + action);
		logger.debug("graph: " + graphName);
		logger.debug("sdd: " + sdd);
		logger.debug("branchNameA: " + branchNameA);
		logger.debug("branchNameB: " + branchNameB);
		logger.debug("with: " + with);
		logger.debug("triples: " + triples);
		
		MergeResult mresult = new MergeResult(graphName, branchNameA, branchNameB);
		
		if (!RevisionManagement.checkGraphExistence(graphName)){
			logger.error("Graph <"+graphName+"> does not exist.");
			throw new InternalErrorException("Graph <"+graphName+"> does not exist.");
		}
			
		
		// Check if A and B are different revisions
		if (RevisionManagement.getRevisionNumber(revisionGraph, branchNameA).equals(RevisionManagement.getRevisionNumber(revisionGraph, branchNameB))) {
			// Branches are equal - throw error
			throw new InternalErrorException("Specified branches are equal: " + sparqlQuery);
		}
		
		// Check if both are terminal nodes
		if (!(RevisionManagement.isBranch(graphName, branchNameA) && RevisionManagement.isBranch(graphName, branchNameB))) {
			throw new InternalErrorException("Non terminal nodes were used: " + sparqlQuery);
		}

		
		// Differ between MERGE query with specified SDD and without SDD			
		String usedSDDURI = Endpoint.getSDD(graphName, sdd);

		// Get the common revision with shortest path
		String commonRevision = MergeManagement.getCommonRevisionWithShortestPath(revisionGraph, revisionUriA, revisionUriB);
		
		// Create the revision progress for A and B
		String graphNameA = graphName + "-RM-REVISION-PROGRESS-A";
		String graphNameB = graphName + "-RM-REVISION-PROGRESS-B";
		String graphNameDiff = graphName + "-RM-DIFFERENCE-MODEL";
		mresult.graphDiff = graphNameDiff;
		String uriA = "http://eatld.et.tu-dresden.de/branch-A";
		String uriB = "http://eatld.et.tu-dresden.de/branch-B";
		
		MergeManagement.createRevisionProgresses(revisionGraph, graphName,
				MergeManagement.getPathBetweenStartAndTargetRevision(revisionGraph, graphName, commonRevision, revisionUriA), graphNameA, uriA, 
				MergeManagement.getPathBetweenStartAndTargetRevision(revisionGraph, graphName, commonRevision, revisionUriB), graphNameB, uriB);
		
		// Create difference model
		MergeManagement.createDifferenceTripleModel(graphName,  graphNameDiff, graphNameA, uriA, graphNameB, uriB, usedSDDURI);
		
		// Differ between the different merge queries
		if ((action != null) && (action.equalsIgnoreCase("AUTO")) && (with == null) && (triples == null)) {
			logger.debug("AUTO MERGE query detected");
			// Create the merged revision
			mresult.newRevisionNumber = MergeManagement.createMergedRevision(graphName, branchNameA, branchNameB, user, commitMessage, graphNameDiff, graphNameA, uriA, graphNameB, uriB, usedSDDURI, MergeQueryTypeEnum.AUTO, "");
		} else if ((action != null) && (action.equalsIgnoreCase("MANUAL")) && (with != null) && (triples != null)) {
			logger.debug("MANUAL MERGE query detected");
			// Create the merged revision
			mresult.newRevisionNumber = MergeManagement.createMergedRevision(graphName, branchNameA, branchNameB, user, commitMessage, graphNameDiff, graphNameA, uriA, graphNameB, uriB, usedSDDURI, MergeQueryTypeEnum.MANUAL, triples);
		} else if ((action == null) && (with != null) && (triples != null)) {
			logger.debug("MERGE WITH query detected");
			// Create the merged revision
			mresult.newRevisionNumber = MergeManagement.createMergedRevision(graphName, branchNameA, branchNameB, user, commitMessage, graphNameDiff, graphNameA, uriA, graphNameB, uriB, usedSDDURI, MergeQueryTypeEnum.WITH, triples);
		} else if ((action == null) && (with == null) && (triples == null)) {
			logger.debug("MERGE query detected");
			// Check if difference model contains conflicts
			String queryASK = String.format(
					  "ASK { %n"
					+ "	GRAPH <%s> { %n"
					+ " 	?ref <http://eatld.et.tu-dresden.de/sddo#isConflicting> \"true\"^^<http://www.w3.org/2001/XMLSchema#boolean> . %n"
					+ "	} %n"
					+ "}", graphNameDiff);
			if (TripleStoreInterfaceSingleton.get().executeAskQuery(queryASK)) {
				// Difference model contains conflicts
				// Return the conflict model to the client
				mresult.hasConflict = true;
				mresult.conflictModel = RevisionManagement.getContentOfGraphByConstruct(graphNameDiff, format);
				
			} else {
				// Difference model contains no conflicts
				// Create the merged revision
				mresult.newRevisionNumber = MergeManagement.createMergedRevision(graphName, branchNameA, branchNameB, user, commitMessage, graphNameDiff, graphNameA, uriA, graphNameB, uriB, usedSDDURI, MergeQueryTypeEnum.COMMON, "");
			}
		} else {
			throw new InternalErrorException("This is not a valid MERGE query: " + sparqlQuery);
		}
		return mresult;
		
	}
	
	
	


}
