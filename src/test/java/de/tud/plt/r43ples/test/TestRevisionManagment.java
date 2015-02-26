package de.tud.plt.r43ples.test;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import de.tud.plt.r43ples.exception.InternalErrorException;
import de.tud.plt.r43ples.management.Config;
import de.tud.plt.r43ples.management.ResourceManagement;
import de.tud.plt.r43ples.management.RevisionManagement;
import de.tud.plt.r43ples.management.SampleDataSet;
import de.tud.plt.r43ples.webservice.Endpoint;


public class TestRevisionManagment {
	
	private final static String graph_test = "http://test.com/dataset1";
    private final String format = "application/sparql-results+xml";
    private final Endpoint ep = new Endpoint();
	private String result;
	private String expected;

	@BeforeClass
	public static void setUpBeforeClass() throws ConfigurationException, InternalErrorException{
		XMLUnit.setIgnoreWhitespace(true);
		XMLUnit.setNormalize(true);
		Config.readConfig("r43ples.test.conf");
		SampleDataSet.createSampleDataset1(graph_test);
	}
	
	@Test
	public void test_reference_uri() throws InternalErrorException {
		String res = RevisionManagement.getReferenceUri(graph_test, "master");
		Assert.assertEquals(graph_test+"-master", res);
	}
	
	@Test
	public void test_revision_uri() throws InternalErrorException {
		String uri = RevisionManagement.getRevisionUri(graph_test, "4");
		Assert.assertEquals(graph_test+"-revision-4", uri);
	}
	
	@Test
	public void test_master_number() {
		String revNumberMaster = RevisionManagement.getMasterRevisionNumber(graph_test);
		Assert.assertEquals("5", revNumberMaster);
	}
	
	@Test
	public void testSelectMaster() throws SAXException, IOException, InternalErrorException {
        String query = "SELECT ?s ?p ?o "
        		+ "FROM <"+graph_test+"> REVISION \"master\""
        		+ "WHERE {?s ?p ?o} ORDER BY ?s ?p ?o";
        
        result = ep.sparql(format, query).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev5.xml");
        assertXMLEqual(expected, result);
        
        query = "SELECT ?s ?p ?o "
        		+ "FROM <"+graph_test+"> REVISION \"MASTER\""
        		+ "WHERE {?s ?p ?o} ORDER BY ?s ?p ?o";
        
        result = ep.sparql(format, query).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev5.xml");
        assertXMLEqual(expected, result);   
	}
	
	@Test
	public void testSelectWithRewriting() throws SAXException, IOException, InternalErrorException {
        String query_template = "OPTION r43ples:SPARQL_JOIN%n"
        		+ "SELECT ?s ?p ?o FROM <"+graph_test+"> REVISION \"%d\"%n"
        		+ "WHERE {?s ?p ?o} ORDER BY ?s ?p ?o";
        
        result = ep.sparql(format, String.format(query_template, 0)).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev0.xml");
        assertXMLEqual(expected, result);
        
        result = ep.sparql(format, String.format(query_template, 1)).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev1.xml");
        assertXMLEqual(expected, result);
        
        result = ep.sparql(format, String.format(query_template, 2)).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev2.xml");
        assertXMLEqual(expected, result);
        
        result = ep.sparql(format, String.format(query_template, 3)).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev3.xml");
        assertXMLEqual(expected, result);
        
        result = ep.sparql(format, String.format(query_template, 4)).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev4.xml");
        assertXMLEqual(expected, result);
        
        result = ep.sparql(format, String.format(query_template, 5)).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev5.xml");
        assertXMLEqual(expected, result);
	}

	@Test
	public void testSelect() throws SAXException, IOException, InternalErrorException {
        String query_template = ""
        		+ "SELECT ?s ?p ?o FROM <"+graph_test+"> REVISION \"%d\"%n"
        		+ "WHERE {?s ?p ?o} ORDER By ?s ?p ?o";
        
        result = ep.sparql(format, String.format(query_template, 0)).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev0.xml");
        assertXMLEqual(expected, result);
        
        result = ep.sparql(format, String.format(query_template, 1)).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev1.xml");
        assertXMLEqual(expected, result);
        
        result = ep.sparql(format, String.format(query_template, 2)).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev2.xml");
        assertXMLEqual(expected, result);
        
        result = ep.sparql(format, String.format(query_template, 3)).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev3.xml");
        assertXMLEqual(expected, result);
        
        result = ep.sparql(format, String.format(query_template, 4)).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev4.xml");
        assertXMLEqual(expected, result);
        
        result = ep.sparql(format, String.format(query_template, 5)).getEntity().toString();
        expected = ResourceManagement.getContentFromResource("dataset1/response-test-rev5.xml");
        assertXMLEqual(expected, result);
	}
	
	@Test
	public void testSelect2Pattern() throws SAXException, IOException, InternalErrorException {
		String query = "PREFIX : <http://test.com/> "
				+ "SELECT DISTINCT ?p1 ?p2 "
				+ "FROM <"+ graph_test + "> REVISION \"%d\" "
				+ "WHERE {"
				+ "	?p1 :knows ?t."
				+ "	?p2 :knows ?t."
				+ " FILTER (?p1!=?p2)"
				+ "} ORDER BY ?p1 ?p2"; 
		
		expected = ResourceManagement.getContentFromResource("2patterns/response-rev3.xml");
		result = ep.sparql(format, String.format(query,3)).getEntity().toString();
		assertXMLEqual(expected, result);
		
		expected = ResourceManagement.getContentFromResource("2patterns/response-rev4.xml");
		result = ep.sparql(format, String.format(query,4)).getEntity().toString();
		assertXMLEqual(expected, result);
	}
	
	@Test
	public void testSelect2Pattern_sparql_join() throws SAXException, IOException, InternalErrorException {
		String query = "OPTION r43ples:SPARQL_JOIN\n"
				+ "PREFIX : <http://test.com/> "
				+ "SELECT DISTINCT ?p1 ?p2 "
				+ "FROM <"+ graph_test + "> REVISION \"%d\" "
				+ "WHERE {"
				+ "	?p1 :knows ?t."
				+ "	?p2 :knows ?t."
				+ " FILTER (?p1!=?p2)"
				+ "} ORDER BY ?p1 ?p2"; 
		
		expected = ResourceManagement.getContentFromResource("2patterns/response-rev3.xml");
		result = ep.sparql(format, String.format(query,3)).getEntity().toString();
		assertXMLEqual(expected, result);
		
		expected = ResourceManagement.getContentFromResource("2patterns/response-rev4.xml");
		result = ep.sparql(format, String.format(query,4)).getEntity().toString();
		assertXMLEqual(expected, result);
	}
	
	
	@Test
	public void testBranching() throws InternalErrorException {
		RevisionManagement.createBranch(graph_test, "2", "testBranch", "test_user", "branching as junit test");

		RevisionManagement.createNewRevision(graph_test, "<a> <b> <c>", "", "test_user", "test_commitMessage", "testBranch");
		String revNumber = RevisionManagement.getRevisionNumber(graph_test, "testBranch");
		Assert.assertEquals("2.0-0", revNumber);
		
		RevisionManagement.createNewRevision(graph_test, "<a> <b> <d>", "", "test_user", "test_commitMessage", "testBranch");
		String revNumber2 = RevisionManagement.getRevisionNumber(graph_test, "testBranch");
		Assert.assertEquals("2.0-1", revNumber2);
		
		RevisionManagement.createBranch(graph_test, "2.0-1", "testBranch2", "test_user", "branching as junit test");
		RevisionManagement.createNewRevision(graph_test, "<a> <b> <e>", "", "test_user", "test_commitMessage", "testBranch2");
		String revNumber3 = RevisionManagement.getRevisionNumber(graph_test, "testBranch2");
		Assert.assertEquals("2.0-1.0-0", revNumber3);
		
		RevisionManagement.createBranch(graph_test, "2.0-1", "testBranch2a", "test_user", "branching as junit test");
		RevisionManagement.createNewRevision(graph_test, "<a> <b> <f>", "", "test_user", "test_commitMessage", "testBranch2a");
		String revNumber4 = RevisionManagement.getRevisionNumber(graph_test, "testBranch2a");
		Assert.assertEquals("2.0-1.1-0", revNumber4);
	}
	
	
	@Test
	public void test_minus() throws SAXException, IOException, InternalErrorException {
		String query = "PREFIX : <http://test.com/> "
				+ "SELECT DISTINCT ?p1 ?p2 "
				+ "FROM <"+ graph_test + "> REVISION \"%d\" "
				+ "WHERE {"
				+ "	?p1 :knows ?p2."
				+ "	MINUS {?p1 :knows :Danny}"
				+ "} ORDER BY ?p1 ?p2"; 
		
		expected = ResourceManagement.getContentFromResource("minus/response-rev2.xml");
		result = ep.sparql(format, String.format(query,2)).getEntity().toString();
		assertXMLEqual(expected, result);
		
		expected = ResourceManagement.getContentFromResource("minus/response-rev3.xml");
		result = ep.sparql(format, String.format(query,3)).getEntity().toString();
		assertXMLEqual(expected, result);
		
		expected = ResourceManagement.getContentFromResource("minus/response-rev4.xml");
		result = ep.sparql(format, String.format(query,4)).getEntity().toString();
		assertXMLEqual(expected, result);
	}
	
	@Test
	public void test_minus_sparql_join() throws SAXException, IOException, InternalErrorException {
		String query = "OPTION r43ples:SPARQL_JOIN\n"
				+ "PREFIX : <http://test.com/> "
				+ "SELECT DISTINCT ?p1 ?p2 "
				+ "FROM <"+ graph_test + "> REVISION \"%d\" "
				+ "WHERE {"
				+ "	?p1 :knows ?p2."
				+ "	MINUS {?p1 :knows :Danny}"
				+ "} ORDER BY ?p1 ?p2"; 
		
		expected = ResourceManagement.getContentFromResource("minus/response-rev2.xml");
		result = ep.sparql(format, String.format(query,2)).getEntity().toString();
		assertXMLEqual(expected, result);
		
		expected = ResourceManagement.getContentFromResource("minus/response-rev3.xml");
		result = ep.sparql(format, String.format(query,3)).getEntity().toString();
		assertXMLEqual(expected, result);
		
		expected = ResourceManagement.getContentFromResource("minus/response-rev4.xml");
		result = ep.sparql(format, String.format(query,4)).getEntity().toString();
		assertXMLEqual(expected, result);
	}
	
}
