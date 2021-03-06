package de.tud.plt.r43ples.merging;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import de.tud.plt.r43ples.exception.InternalErrorException;
import de.tud.plt.r43ples.management.R43plesMergeCommit;
import de.tud.plt.r43ples.management.RevisionGraph;
import de.tud.plt.r43ples.management.RevisionManagement;
import de.tud.plt.r43ples.merging.management.StrategyManagement;
import de.tud.plt.r43ples.merging.model.structure.Patch;
import de.tud.plt.r43ples.merging.model.structure.PatchGroup;

public class RebaseControl {
	
	/** The logger. **/
	private static Logger logger = Logger.getLogger(RebaseControl.class);
	private String graphName;
	private String branchNameB;
	private String branchNameA;
	private PatchGroup patchGroup;
	
	
	
	
	public RebaseControl(final String graphName, final String branchNameA, final String branchNameB) {
		this.graphName = graphName;
		this.branchNameA = branchNameA;
		this.branchNameB = branchNameB;
	}
	
	public RebaseControl(final R43plesMergeCommit commit) {
		this.graphName = commit.graphName;
		this.branchNameA = commit.branchNameA;
		this.branchNameB = commit.branchNameB;
	}

	/** simple checks if rebase could be possible for these two branches of a graph
	 * 
	 * @throws InternalErrorException throws an error if it is not possible
	 */
	public void checkIfRebaseIsPossible() throws InternalErrorException {
		// Check if graph already exists
		if (!RevisionManagement.checkGraphExistence(graphName)){
			logger.error("Graph <"+graphName+"> does not exist.");
			throw new InternalErrorException("Graph <"+graphName+"> does not exist.");
		}
	
		RevisionGraph graph = new RevisionGraph(graphName);
		// Check if A and B are different revisions
		if (graph.getRevisionNumber(branchNameA).equals(graph.getRevisionNumber(branchNameB))) {
			// Branches are equal - throw error
			throw new InternalErrorException("Specified branches are equal");
		}
		
		// Check if both are terminal nodes
		if (!(graph.hasBranch(branchNameA) && graph.hasBranch(branchNameB))) {
			throw new InternalErrorException("Non terminal nodes were used ");
		}
	}
	
	
	/**for each revision in branchA , create a patch */
	public PatchGroup createPatchGroupOfBranch(String revisionGraph, String basisRevisionUri, LinkedList<String> revisionList) {
		
		LinkedHashMap<String, Patch> patchMap = new LinkedHashMap<String, Patch>();
		
		Iterator<String> rIter  = revisionList.iterator();
		
		while(rIter.hasNext()) {
			String revisionUri = rIter.next();
			String commitUri = StrategyManagement.getCommitUri(revisionGraph, revisionUri);
			
			String addSet = RevisionManagement.getAddSetURI(revisionUri, revisionGraph);
			String deleteSet = RevisionManagement.getDeleteSetURI(revisionUri, revisionGraph);;
			
			String patchNumber = StrategyManagement.getRevisionNumber(revisionGraph, revisionUri);
			String patchUser = StrategyManagement.getCommitUserUri(revisionGraph, commitUri);
			String patchMessage = StrategyManagement.getCommitMessage(revisionGraph, commitUri);
			
			patchMap.put(patchNumber, new Patch(patchNumber, patchUser, patchMessage, addSet, deleteSet));			
		}
		
		String basisRevisionNumber = StrategyManagement.getRevisionNumber(revisionGraph, basisRevisionUri);
		
		patchGroup = new PatchGroup(basisRevisionNumber, patchMap);
		
		logger.debug("patchGroup initial successful!" + patchGroup.getPatchMap().size());
		return patchGroup;
	}
	
	
	/**
	 * force rebase begin, for each patch in patch group will a new revision created 
	 * @throws InternalErrorException 
	 * */
	public String forceRebaseProcess() throws InternalErrorException{
		
		logger.debug("patchGroup 1:" + patchGroup.getBasisRevisionNumber());
		logger.debug("patchGroup 2:" + patchGroup.getPatchMap().size());

		LinkedHashMap<String, Patch> patchMap = patchGroup.getPatchMap();
		String basisRevisionNumber = patchGroup.getBasisRevisionNumber();
				
		Iterator<Entry<String, Patch>> pIter = patchMap.entrySet().iterator();
		
		while(pIter.hasNext()) {
			Entry<String, Patch> pEntry = pIter.next();
			Patch patch = pEntry.getValue();
		
			String newRevisionNumber = RevisionManagement.createNewRevisionWithPatch(
					graphName, patch.getAddedSetUri(), patch.getRemovedSetUri(),
					patch.getPatchUser(), patch.getPatchMessage(), basisRevisionNumber);
			
			basisRevisionNumber = newRevisionNumber;
		}
		return basisRevisionNumber;	
	}
}
