package de.tud.plt.r43ples.merging.control;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.update.UpdateAction;

import de.tud.plt.r43ples.exception.InternalErrorException;
import de.tud.plt.r43ples.management.MergeQueryTypeEnum;
import de.tud.plt.r43ples.management.ResolutionState;
import de.tud.plt.r43ples.management.RevisionManagement;
import de.tud.plt.r43ples.management.SDDTripleStateEnum;
import de.tud.plt.r43ples.merging.management.BranchManagement;
import de.tud.plt.r43ples.merging.management.ProcessManagement;
import de.tud.plt.r43ples.merging.model.structure.CommitModel;
import de.tud.plt.r43ples.merging.model.structure.Difference;
import de.tud.plt.r43ples.merging.model.structure.DifferenceGroup;
import de.tud.plt.r43ples.merging.model.structure.DifferenceModel;
import de.tud.plt.r43ples.merging.model.structure.HighLevelChangeModel;
import de.tud.plt.r43ples.merging.model.structure.IndividualModel;
import de.tud.plt.r43ples.merging.model.structure.IndividualStructure;
import de.tud.plt.r43ples.merging.model.structure.TableEntrySemanticEnrichmentAllIndividuals;
import de.tud.plt.r43ples.merging.model.structure.TableModel;
import de.tud.plt.r43ples.merging.model.structure.TableRow;
import de.tud.plt.r43ples.merging.model.structure.TreeNode;
import de.tud.plt.r43ples.merging.model.structure.Triple;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;

import javax.ws.rs.core.Response;


public class MergingControl {
	private static Logger logger = Logger.getLogger(MergingControl.class);
	private static DifferenceModel differenceModel = new DifferenceModel();
	private static List<TreeNode> treeList = new ArrayList<TreeNode>();
	private static TableModel tableModel = new TableModel();
	
	/** The each individual of Triple Table*/
	private static TableModel individualTableModel = new TableModel();
	
	private static HighLevelChangeModel highLevelChangeModel = new HighLevelChangeModel();
	
	/** The individual model of branch A. **/
	private static IndividualModel individualModelBranchA;
	/** The individual model of branch B. **/
	private static IndividualModel individualModelBranchB;	
	/** The properties array list. **/
	private static ArrayList<String> propertyList;
	/** Merg Query Model. **/
	private static CommitModel commitModel;
	
	/** The revision number of the branch A. **/
	private static String revisionNumberBranchA;
	/** The revision number of the branch B. **/
	private static String revisionNumberBranchB;
	

	
	public static String getHtmlOutput(String graphName) {
		MustacheFactory mf = new DefaultMustacheFactory();
	    Mustache mustache = mf.compile("templates/mergingView.mustache");
	    StringWriter sw = new StringWriter();
	    
	    Map<String, Object> scope = new HashMap<String, Object>();
	    scope.put("graphName", graphName);	    
	    mustache.execute(sw, scope);		
		return sw.toString();
	}
	
	public static String getMenuHtmlOutput() {
		List<String> graphList = RevisionManagement.getRevisedGraphs();
	
		MustacheFactory mf = new DefaultMustacheFactory();
	    Mustache mustache = mf.compile("templates/merging.mustache");
	    StringWriter sw = new StringWriter();
	    
	    Map<String, Object> scope = new HashMap<String, Object>();
	    scope.put("merging_active", true);
		scope.put("graphList", graphList);
		
	    mustache.execute(sw, scope);		
		return sw.toString();
	}
	
	public static String getViewHtmlOutput() throws TemplateException, IOException {	
		Map<String, Object> scope = new HashMap<String, Object>();
		StringWriter sw = new StringWriter();
		freemarker.template.Template temp = null; 
		String name = "mergingView3.ftl";
		try {  
            // 通过Freemarker的Configuration读取相应的Ftl  
            Configuration cfg = new Configuration();  
            // 设定去哪里读取相应的ftl模板  
            cfg.setClassForTemplateLoading(MergingControl.class, "/templates");
            // 在模板文件目录中寻找名称为name的模板文件  
            temp = cfg.getTemplate(name);  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
		
		/**conList fuer conflict triple
		 * diffList fuer deference triple*/
		List<TreeNode> conList = new ArrayList<TreeNode>();
		List<TreeNode> diffList = new ArrayList<TreeNode>();
		Iterator<TreeNode> itG = treeList.iterator();
	 	
		/**create conList and diffList*/
	 	String conStatus = "0";
	 	while(itG.hasNext()){
	 		TreeNode node = itG.next();
	 		
	 		if(node.status == true){
	 			conStatus = "1";
		 		conList.add(node);
	 		}else{
	 			diffList.add(node);
	 		}	 		
	 	}
	 	
	 	logger.info("commitGraphname: " + commitModel.getGraphName());
	 	scope.put("tableRowList", tableModel.getTripleRowList());
	 	scope.put("graphName", commitModel.getGraphName());	
		scope.put("conList",conList);
		scope.put("diffList",diffList);
		scope.put("conStatus", conStatus);
		scope.put("propertyList", propertyList);		
		
		temp.process(scope,sw);		
		return sw.toString();		
	}
	
	public static String getUpdatedViewHtmlOutput() throws TemplateException, IOException, ConfigurationException, InternalErrorException {	
		Map<String, Object> scope = new HashMap<String, Object>();
		StringWriter sw = new StringWriter();
		freemarker.template.Template temp = null; 
		String name = "mergingView3.ftl";
		try {  
            // 通过Freemarker的Configuration读取相应的Ftl  
            Configuration cfg = new Configuration();  
            // 设定去哪里读取相应的ftl模板  
            cfg.setClassForTemplateLoading(MergingControl.class, "/templates");
            // 在模板文件目录中寻找名称为name的模板文件  
            temp = cfg.getTemplate(name);  
        } catch (IOException e) {  
            e.printStackTrace();  
        } 
		
		/** updated tree structure, table structure, property list and individual model*/
		ProcessManagement.createDifferenceTree(differenceModel, treeList);
		
		ProcessManagement.createTableModel(differenceModel, tableModel);
		
		logger.info("updated tableModel fertig!");
		
		ProcessManagement.createHighLevelChangeRenamingModel(highLevelChangeModel, differenceModel);
		
		// Create the individual models of both branches
		individualModelBranchA = ProcessManagement.createIndividualModelOfRevision(commitModel.getGraphName(), commitModel.getBranch1(), differenceModel);
		
		individualModelBranchB = ProcessManagement.createIndividualModelOfRevision(commitModel.getGraphName(), commitModel.getBranch2(), differenceModel);
		
		
		// Create the property list of revisions
		propertyList = ProcessManagement.getPropertiesOfRevision(commitModel.getGraphName(), commitModel.getBranch1(), commitModel.getBranch2());
		
		
		/**conList fuer conflict triple
		 * diffList fuer deference triple*/
		List<TreeNode> conList = new ArrayList<TreeNode>();
		List<TreeNode> diffList = new ArrayList<TreeNode>();
		Iterator<TreeNode> itG = treeList.iterator();
	 	
		/**create conList and diffList*/
	 	String conStatus = "0";
	 	while(itG.hasNext()){
	 		TreeNode node = itG.next();
	 		
	 		if(node.status == true){
	 			conStatus = "1";
		 		conList.add(node);
	 		}else{
	 			diffList.add(node);
	 		}	 		
	 	}
	 	
	 	scope.put("tableRowList", tableModel.getTripleRowList());
	 	scope.put("graphName", commitModel.getGraphName());	
		scope.put("conList",conList);
		scope.put("diffList",diffList);
		scope.put("conStatus", conStatus);
		scope.put("propertyList", propertyList);		
		
		temp.process(scope,sw);
		logger.info("updated view fertig!");
		return sw.toString();		
	}
	
	
	
	public static String getBranchInformation(String graph) throws IOException {
		List<String> branchList = BranchManagement.getAllBranchNamesOfGraph(graph);
		StringBuilder branchInformation = new StringBuilder();
		for(String branchName:branchList){
			branchInformation.append("<option value="+"\""+branchName+"\""+">"+branchName+"</option>");
		}
		System.out.println("branch success created");
		return branchInformation.toString();
	}
	
	public static void getMergeProcess(Response response, String graphName, String branchNameA, String branchNameB) throws IOException, ConfigurationException, InternalErrorException{
		//ob diese satz richt ist oder nicht?
		if (response.getStatusInfo() == Response.Status.CONFLICT){
			logger.info("Merge query produced conflicts.");
			
			ProcessManagement.readDifferenceModel(response.getEntity().toString(), differenceModel);
			
			
			ProcessManagement.createDifferenceTree(differenceModel, treeList);
			
			ProcessManagement.createTableModel(differenceModel, tableModel);
			
			ProcessManagement.createHighLevelChangeRenamingModel(highLevelChangeModel, differenceModel);
			
			// Save the current revision numbers
			revisionNumberBranchA = RevisionManagement.getRevisionNumber(graphName, branchNameA);
			revisionNumberBranchB = RevisionManagement.getRevisionNumber(graphName, branchNameB);
			
			// Create the individual models of both branches
			individualModelBranchA = ProcessManagement.createIndividualModelOfRevision(graphName, branchNameA, differenceModel);
			logger.info("Individual Model A Test : " + individualModelBranchA.getIndividualStructures().keySet().toString());
			Iterator<Entry<String, IndividualStructure>> itEnt = individualModelBranchA.getIndividualStructures().entrySet().iterator();
			while(itEnt.hasNext()){
				Entry<String,IndividualStructure> entryInd = itEnt.next();
				logger.info("Individual Sturcture Uri Test" + entryInd.getValue().getIndividualUri());
				logger.info("Individual Sturcture Triples Test" + entryInd.getValue().getTriples().keySet().toString());

				
			}

			individualModelBranchB = ProcessManagement.createIndividualModelOfRevision(graphName, branchNameB, differenceModel);
			logger.info("Individual Model B Test : " + individualModelBranchB.getIndividualStructures().keySet().toString());

			
			
			// Create the property list of revisions
			propertyList = ProcessManagement.getPropertiesOfRevision(graphName, branchNameA, branchNameB);
			
			Iterator<String> pit = propertyList.iterator();
			while(pit.hasNext()){
				logger.info("propertyList Test : " + pit.next().toString());
			}
			
			
			
		} else if (response.getStatusInfo() == Response.Status.CREATED){
			logger.info("Merge query produced no conflicts. Merged revision was created.");
			
		} else {
			// error occurred
		}		
		
	}
	
	public static String getIndividualView(String individual) throws TemplateException, IOException{
		Map<String, Object> scope = new HashMap<String, Object>();
		StringWriter sw = new StringWriter();
		freemarker.template.Template temp = null; 
		String name = "individualView.ftl";
		try {  
            // 通过Freemarker的Configuration读取相应的Ftl  
            Configuration cfg = new Configuration();  
            // 设定去哪里读取相应的ftl模板  
            cfg.setClassForTemplateLoading(MergingControl.class, "/templates");
            // 在模板文件目录中寻找名称为name的模板文件  
            temp = cfg.getTemplate(name);  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
			 	
		scope.put("individualTableList", MergingControl.createTableModelSemanticEnrichmentAllIndividualsList());
		
		temp.process(scope,sw);		
		return sw.toString();	
		
	}
	/**
	 * @param individualA individual of Branch A
	 * @param individualB individual of Branch B
	 * return response of updated triple table by individual 
	 * */
	public static String getIndividualFilter(String individualA , String individualB) throws ConfigurationException, TemplateException, IOException {
		List<TableRow> updatedTripleRowList = ProcessManagement.createIndividualTableList(individualA, 
				individualB, individualModelBranchA, individualModelBranchB, tableModel);
		
		Iterator<TableRow> ite = updatedTripleRowList.iterator();
		while(ite.hasNext()){
			TableRow t = ite.next();
			logger.info("updated table list : "+ t.getSubject() +"--"+ t.getConflicting());
		}
		
		Map<String, Object> scope = new HashMap<String, Object>();
		StringWriter sw = new StringWriter();
		freemarker.template.Template temp = null; 
		String name = "individualFilterTable.ftl";
		try {  
            // 通过Freemarker的Configuration读取相应的Ftl  
            Configuration cfg = new Configuration();  
            // 设定去哪里读取相应的ftl模板  
            cfg.setClassForTemplateLoading(MergingControl.class, "/templates");
            // 在模板文件目录中寻找名称为name的模板文件  
            temp = cfg.getTemplate(name);  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
			 	
		scope.put("updatedTripleRowList", updatedTripleRowList);
		
		temp.process(scope,sw);
		
		return sw.toString();	
		
		
	}
	
	/**@param properties :  property list of Filter by property
	 * return response of updated triple table*/
	
	public static String updateTripleTable(String properties) throws TemplateException, IOException{
		
		Map<String, Object> scope = new HashMap<String, Object>();
		StringWriter sw = new StringWriter();
		freemarker.template.Template temp = null; 
		String name = "tripleTable.ftl";
		
		try {  
            // 通过Freemarker的Configuration读取相应的Ftl  
            Configuration cfg = new Configuration();  
            // 设定去哪里读取相应的ftl模板  
            cfg.setClassForTemplateLoading(MergingControl.class, "/templates");
            // 在模板文件目录中寻找名称为name的模板文件  
            temp = cfg.getTemplate(name);  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
			 	
		
		String[] propertyArray = properties.split(",");
		List<TableRow> TripleRowList = tableModel.getTripleRowList();
		List<TableRow> updatedTripleRowList = new ArrayList<TableRow>();
		for(String property: propertyArray) {
			Iterator<TableRow> itu = TripleRowList.iterator();
			while(itu.hasNext()){
				TableRow tableRow = itu.next();
				if(tableRow.getPredicate().equals(property)) {
					updatedTripleRowList.add(tableRow);
				}
			}					
		}
		
		scope.put("tableRowList", updatedTripleRowList);
		
		temp.process(scope,sw);	
		
		return sw.toString();	
		
	}
	
	/**
	 * ##########################################################################################################################################################################
	 * ##########################################################################################################################################################################
	 * ##                                                                                                                                                                      ##
	 * ## push Process : get push Result                                                                                                                              ##
	 * ##                                                                                                                                                                      ##
	 * ##########################################################################################################################################################################
	 * ##########################################################################################################################################################################
	 */
		
	/**
	 * Push the changes to the remote repository.
	 * @param triplesId id of triples, what als added in end version selected
	 * @throws InternalErrorException 
	 * 
	 * @throws IOException 
	 */
	
	public static String updateMergeQuery (String triplesId) throws IOException, InternalErrorException {
		updateDifferenceModel(triplesId);
		String user = commitModel.getUser();
		String message = commitModel.getMessage();
		String graphName = commitModel.getGraphName();
		String sdd = commitModel.getSddName();
		
		//create Triples
		Model wholeContentModel = ProcessManagement.getWholeContentOfRevision(graphName, revisionNumberBranchB);
		logger.debug("Whole model as N-Triples: \n" + ProcessManagement.writeJenaModelToNTriplesString(wholeContentModel));
		
		logger.info("whole model: " + ProcessManagement.writeJenaModelToNTriplesString(wholeContentModel));

		// Update dataset with local data
		ArrayList<String> list = ProcessManagement.getAllTriplesDividedIntoInsertAndDelete(differenceModel, wholeContentModel);
		
		logger.debug("INSERT: \n" + list.get(0));
		logger.debug("DELETE: \n" + list.get(1));
		
		logger.info("insert Triple: "+list.get(0));
		logger.info("delete Triple: "+list.get(1));

		
		String updateQueryInsert = String.format(
				  "INSERT DATA { %n"
				+ "	%s %n"
				+ "}", list.get(0));
		UpdateAction.parseExecute(updateQueryInsert, wholeContentModel);
		
		String updateQueryDelete = String.format(
				  "DELETE DATA { %n"
				+ " %s %n"
				+ "}", list.get(1));
		UpdateAction.parseExecute(updateQueryDelete, wholeContentModel);
		
		String triples = ProcessManagement.writeJenaModelToNTriplesString(wholeContentModel);
		logger.debug("Updated model as N-Triples: \n" + triples); 
		
		logger.info("updated whole model: "+ triples);
		
		String mergeQuery = ProcessManagement.createMergeQuery(graphName, sdd, user, message, MergeQueryTypeEnum.MANUAL, revisionNumberBranchA, revisionNumberBranchB, triples);
		logger.info("UpdatedmergeQuery:"+mergeQuery);
		
		return mergeQuery;
		
	}
	
	
	
	public static void createCommitModel(String graphName, String sddName, String user, String message, String branch1, String branch2){
		MergingControl.commitModel = new CommitModel(graphName, sddName, user, message, branch1, branch2);
	}
	
	
	/** update difference model nach checkebox in triple table*/
	
	public static void updateDifferenceModel(String triplesId) {
		String[] idArray = triplesId.split(",");
			
		Iterator<Entry<String, DifferenceGroup>> iterDM = differenceModel.getDifferenceGroups().entrySet().iterator();
		while(iterDM.hasNext()) {
			Entry<String, DifferenceGroup> entryDG = (Entry<String, DifferenceGroup>) iterDM.next();
			DifferenceGroup differ = (DifferenceGroup) entryDG.getValue();
			Iterator<Entry<String, Difference>> iterDIF = differ.getDifferences().entrySet().iterator();
			while(iterDIF.hasNext()){
				Entry<String, Difference> entryDF = iterDIF.next();
				//get triple
				String tripleString = entryDF.getKey();
				
				logger.info("tripleString : "+ tripleString);
				Difference difference = entryDF.getValue();
				difference.setResolutionState(ResolutionState.RESOLVED);
				difference.setTripleResolutionState(SDDTripleStateEnum.DELETED);
				logger.info("test:" + difference.getTripleResolutionState().toString());
				for (String id : idArray) {
					logger.info("tripleId: "+ id);
					Triple checkedTriple = tableModel.getManuellTriple().get(id);
					logger.info("checked triple: "+ ProcessManagement.tripleToString(checkedTriple));
					if (ProcessManagement.tripleToString(checkedTriple).equals(tripleString)){
						difference.setTripleResolutionState(SDDTripleStateEnum.ADDED);
						logger.info("test:" + difference.getTripleResolutionState().toString());
					}				
				}

				
			}
		}
 			
		
		Iterator<Entry<String, DifferenceGroup>> iterD = differenceModel.getDifferenceGroups().entrySet().iterator();
		while(iterD.hasNext()) {
			Entry<String, DifferenceGroup> entryDG = (Entry<String, DifferenceGroup>) iterD.next();
			DifferenceGroup differ = (DifferenceGroup) entryDG.getValue();
			Iterator<Entry<String, Difference>> iterDIF = differ.getDifferences().entrySet().iterator();
			while(iterDIF.hasNext()){
				Entry<String, Difference> entryDF = iterDIF.next();
				//get triple
				String tripleString = entryDF.getKey();
				Difference difference = entryDF.getValue();
				
				logger.info("updated difference model: " + tripleString + difference.getTripleResolutionState() + difference.getResolutionState().toString());
			}
		}		
		
	}
	
	
	
	
	/**
	 * ##########################################################################################################################################################################
	 * ##########################################################################################################################################################################
	 * ##                                                                                                                                                                      ##
	 * ## Semantic enrichment - individuals.                                                                                                                                   ##
	 * ##                                                                                                                                                                      ##
	 * ##########################################################################################################################################################################
	 * ##########################################################################################################################################################################
	 */
	
	
	/**
	 * Create the semantic enrichment List of all individuals.
	 */
	public static List<TableEntrySemanticEnrichmentAllIndividuals> createTableModelSemanticEnrichmentAllIndividualsList() {
		List<TableEntrySemanticEnrichmentAllIndividuals> individualTableList = new ArrayList<TableEntrySemanticEnrichmentAllIndividuals>();
		
		if(individualModelBranchA == null || individualModelBranchB == null) {
			return individualTableList ;
		}

		// Get key sets
		ArrayList<String> keySetIndividualModelBranchA = new ArrayList<String>(individualModelBranchA.getIndividualStructures().keySet());
		ArrayList<String> keySetIndividualModelBranchB = new ArrayList<String>(individualModelBranchB.getIndividualStructures().keySet());
		
		// Iterate over all individual URIs of branch A
		@SuppressWarnings("unchecked")
		Iterator<String> iteKeySetIndividualModelBranchA = ((ArrayList<String>) keySetIndividualModelBranchA.clone()).iterator();
		while (iteKeySetIndividualModelBranchA.hasNext()) {
			String currentKeyBranchA = iteKeySetIndividualModelBranchA.next();
			
			// Add all individual URIs to table model which are in both branches
			if (keySetIndividualModelBranchB.contains(currentKeyBranchA)) {
				TableEntrySemanticEnrichmentAllIndividuals tableEntry = new TableEntrySemanticEnrichmentAllIndividuals(individualModelBranchA.getIndividualStructures().get(currentKeyBranchA), individualModelBranchB.getIndividualStructures().get(currentKeyBranchA), new Object[]{currentKeyBranchA, currentKeyBranchA});
				
				individualTableList.add(tableEntry);
				// Remove key from branch A key set copy
				keySetIndividualModelBranchA.remove(currentKeyBranchA);
				// Remove key from branch B key set copy
				keySetIndividualModelBranchB.remove(currentKeyBranchA);
			}
		}
		
		// Iterate over all individual URIs of branch A (will only contain the individuals which are not in B)
		Iterator<String> iteKeySetIndividualModelBranchAOnly = keySetIndividualModelBranchA.iterator();
		while (iteKeySetIndividualModelBranchAOnly.hasNext()) {
			String currentKeyBranchA = iteKeySetIndividualModelBranchAOnly.next();
			TableEntrySemanticEnrichmentAllIndividuals tableEntry = new TableEntrySemanticEnrichmentAllIndividuals(individualModelBranchA.getIndividualStructures().get(currentKeyBranchA), new IndividualStructure(null), new Object[]{currentKeyBranchA, ""});
			individualTableList.add(tableEntry);

		}
		
		// Iterate over all individual URIs of branch B (will only contain the individuals which are not in A)
		Iterator<String> iteKeySetIndividualModelBranchBOnly = keySetIndividualModelBranchB.iterator();
		while (iteKeySetIndividualModelBranchBOnly.hasNext()) {
			String currentKeyBranchB = iteKeySetIndividualModelBranchBOnly.next();
			TableEntrySemanticEnrichmentAllIndividuals tableEntry = new TableEntrySemanticEnrichmentAllIndividuals(new IndividualStructure(null), individualModelBranchB.getIndividualStructures().get(currentKeyBranchB), new Object[]{"", currentKeyBranchB});
			individualTableList.add(tableEntry);


		}
		
		return individualTableList;
	}
	
}











