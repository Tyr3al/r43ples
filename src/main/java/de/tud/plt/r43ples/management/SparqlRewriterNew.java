package de.tud.plt.r43ples.management;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.NodeFactory;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.sparql.core.TriplePath;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.expr.E_OneOf;
import com.hp.hpl.jena.sparql.expr.ExprList;
import com.hp.hpl.jena.sparql.expr.ExprVar;
import com.hp.hpl.jena.sparql.path.P_Alt;
import com.hp.hpl.jena.sparql.path.P_Link;
import com.hp.hpl.jena.sparql.path.P_OneOrMore1;
import com.hp.hpl.jena.sparql.syntax.Element;
import com.hp.hpl.jena.sparql.syntax.ElementFilter;
import com.hp.hpl.jena.sparql.syntax.ElementGroup;
import com.hp.hpl.jena.sparql.syntax.ElementMinus;
import com.hp.hpl.jena.sparql.syntax.ElementNamedGraph;
import com.hp.hpl.jena.sparql.syntax.ElementPathBlock;
import com.hp.hpl.jena.sparql.syntax.ElementTriplesBlock;
import com.hp.hpl.jena.sparql.syntax.ElementUnion;
import com.hp.hpl.jena.sparql.util.ExprUtils;
import com.hp.hpl.jena.vocabulary.RDF;

import de.tud.plt.r43ples.exception.InternalErrorException;
import de.tud.plt.r43ples.revisionTree.Revision;
import de.tud.plt.r43ples.revisionTree.Tree;

/**
 * Rewrites SPARQL queries in order to reflect old revisions.
 * 
 * @author Markus Graube
 *
 */
public class SparqlRewriterNew {

	/** The logger. **/
	private static final Logger logger = Logger.getLogger(RevisionManagement.class);

	private static final String rmo = "http://eatld.et.tu-dresden.de/rmo#";
	private static final String prov = "http://www.w3.org/ns/prov#";

	private static final Node rmo_Revision = NodeFactory.createURI(rmo + "Revision");
	private static final Node rmo_deltaRemoved = NodeFactory.createURI(rmo + "deltaRemoved");
	private static final Node rmo_deltaAdded = NodeFactory.createURI(rmo + "deltaAdded");
	private static final Node rmo_fullGraph = NodeFactory.createURI(rmo + "fullGraph");
	private static final Node rmo_references = NodeFactory.createURI(rmo + "references");
	private static final Node prov_wasDerivedFrom = NodeFactory.createURI(prov + "wasDerivedFrom");

	// TODO make instance variable
	private static int statement_i = 0;
	
	private static final Pattern pattern = Pattern.compile("FROM\\s*<(?<graph>\\S*)>\\s*REVISION\\s*\"(?<revision>\\S*)\"", Pattern.MULTILINE + Pattern.CASE_INSENSITIVE);

	
	
	public static String rewriteQuery(final String query_r43ples) throws InternalErrorException {

		statement_i = 0;
		
		ExprList expression_list_revision_path = new ExprList();
		Node last_revision = null;

		Matcher m = pattern.matcher(query_r43ples);
		String query_sparql = query_r43ples;
		while (m.find()) {
			String graphName = m.group("graph");
			String referenceName = m.group("revision").toLowerCase();
			m.reset();
			
			String revisionNumber = RevisionManagement.getRevisionNumber(graphName, referenceName);

			Tree tree =  new Tree(graphName);
			LinkedList<Revision> list = tree.getPathToRevision(revisionNumber);
			logger.debug("Path to revision: " + list.toString());
			last_revision = NodeFactory.createURI(list.get(0).getRevisionUri());
			list.removeLast();
			for (Revision ns : list) {
				expression_list_revision_path.add(ExprUtils.nodeToExpr(NodeFactory.createURI(ns.getRevisionUri())));
			}

			query_sparql = m.replaceFirst("");
			m = pattern.matcher(query_sparql);
		}

		// creates the Query
		Query qe = QueryFactory.create(query_sparql);
		ElementGroup eg_orginal = (ElementGroup) qe.getQueryPattern();

		// stores the modified elements
		ElementGroup eg_modified = new ElementGroup();
		ElementGroup eg = getRewrittenElementGroup(expression_list_revision_path, last_revision,
				eg_orginal);
		for (Iterator<Element> iterator = eg.getElements().iterator(); iterator.hasNext();) {
			eg_modified.addElement(iterator.next());
		}

		qe.setDistinct(true);
		qe.setQueryPattern(eg_modified);
		query_sparql = qe.serialize();
		logger.debug("Rewritten query: \n" + query_sparql);
		return query_sparql;
	}

	/**
	 * @param expression_list_revision_path
	 * @param expression_list_last_revision
	 * @param eg_orginal
	 * @return rewritten element group
	 */
	private static ElementGroup getRewrittenElementGroup(ExprList expression_list_revision_path,
			Node last_revision, ElementGroup eg_orginal) {
		ElementGroup eg_modified = new ElementGroup();
		for (Element element : eg_orginal.getElements()) {
			try {
				if (element.getClass().equals(ElementMinus.class)) {
					ElementMinus elementMinus = (ElementMinus) element;
					ElementGroup elementgroup = (ElementGroup) elementMinus.getMinusElement();
					Element minusPart = getRewrittenElementGroup(expression_list_revision_path,
							last_revision, elementgroup);
					ElementMinus em = new ElementMinus(minusPart);
					eg_modified.addElement(em);
				} else {
					ElementPathBlock epb = (ElementPathBlock) element;
					Iterator<TriplePath> itPatternElts = epb.patternElts();

					while (itPatternElts.hasNext()) {
						TriplePath triplePath = itPatternElts.next();
						statement_i += 1;
						ElementGroup eg = getRewrittenTriplePath(expression_list_revision_path,
								last_revision, triplePath);
						for (Iterator<Element> iterator = eg.getElements().iterator(); iterator.hasNext();) {
							eg_modified.addElement(iterator.next());
						}
					}
				}
			} catch (ClassCastException e) {
				eg_modified.addElement(element);
			}
		}
		return eg_modified;
	}

	/**
	 * @param expression_list_revision_path
	 * @param expression_list_last_revision
	 * @param triplePath
	 * @return rewritten triple path element
	 */
	private static ElementGroup getRewrittenTriplePath(ExprList expression_list_revision_path,
			Node last_revision, TriplePath triplePath) {
		{
			ElementGroup eg_modified = new ElementGroup();
			
			ElementTriplesBlock block_triple_path = new ElementTriplesBlock();
			block_triple_path.addTriple(triplePath.asTriple());
			

			Node g_delete_set_full_graph = Var.alloc("g_delete_set_full_graph_" + statement_i);
			Node g_add_set = Var.alloc("g_add_set_" + statement_i);
			Node g_delta_set_old = Var.alloc("g_delta_set_old_" + statement_i);
			Node g_revisiongraph = NodeFactory.createURI(Config.revision_graph);			
			
			Node var_reference = Var.alloc("reference_" + statement_i);
			Node var_r_delete_set = Var.alloc("r_delete_set_" + statement_i);
			Node var_r_add_set = Var.alloc("r_add_set_" + statement_i);
			Node var_r_delta_set_old = Var.alloc("r_delta_set_old_" + statement_i);

			ElementGroup eg_fullgraph = new ElementGroup();
			eg_fullgraph.addTriplePattern(new Triple(var_reference, rmo_references, last_revision));
			eg_fullgraph.addTriplePattern(new Triple(var_reference, rmo_fullGraph, g_delete_set_full_graph));

			ElementUnion eg_union = new ElementUnion();
			eg_union.addElement(eg_fullgraph);			
			
			ElementGroup eg_revisiongraph = new ElementGroup();
			eg_revisiongraph.addElement(eg_union);

			if (!expression_list_revision_path.isEmpty()) {
				ElementGroup eg_delete_set = new ElementGroup();
				eg_delete_set.addTriplePattern(new Triple(var_r_delete_set, RDF.type.asNode(), rmo_Revision));
				eg_delete_set.addTriplePattern(new Triple(var_r_delete_set, rmo_deltaRemoved, g_delete_set_full_graph));
				eg_delete_set.addElementFilter(new ElementFilter(new E_OneOf(new ExprVar(var_r_delete_set),
						expression_list_revision_path)));
				eg_union.addElement(eg_delete_set);

			
				eg_revisiongraph.addTriplePattern(new Triple(var_r_add_set, RDF.type.asNode(), rmo_Revision));
				eg_revisiongraph.addTriplePattern(new Triple(var_r_add_set, rmo_deltaAdded, g_add_set));
				eg_revisiongraph.addElementFilter(new ElementFilter(new E_OneOf(new ExprVar(var_r_add_set),
						expression_list_revision_path)));
				
				ElementGroup eg_ebp = new ElementGroup();
				ElementPathBlock ebp = new ElementPathBlock();				
				ebp.addTriplePath(new TriplePath(var_r_delta_set_old, new P_Link(RDF.type.asNode()), rmo_Revision));
				ebp.addTriplePath(new TriplePath(var_r_delta_set_old, new P_Alt(new P_Link(rmo_deltaRemoved), new P_Link(rmo_deltaAdded)), g_delta_set_old));
				ebp.addTriplePath(new TriplePath(var_r_add_set, new P_OneOrMore1(new P_Link(prov_wasDerivedFrom)),
						var_r_delta_set_old));
				eg_ebp.addElement(ebp);
				eg_ebp.addElementFilter(new ElementFilter(new E_OneOf(new ExprVar(var_r_delta_set_old),
						expression_list_revision_path)));
				ElementMinus eg_minus = new ElementMinus(eg_ebp);
				
				eg_revisiongraph.addElement(eg_minus);
			}
			eg_modified.addElement(new ElementNamedGraph(g_revisiongraph, eg_revisiongraph));
			eg_modified.addElement(new ElementNamedGraph(g_delete_set_full_graph, block_triple_path));
			eg_modified.addElement(new ElementMinus(new ElementNamedGraph(g_add_set, block_triple_path)));
			eg_modified.addElement(new ElementNamedGraph(g_delta_set_old, block_triple_path));
			return eg_modified;
		}
	}

}
