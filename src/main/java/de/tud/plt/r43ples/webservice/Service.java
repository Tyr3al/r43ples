package de.tud.plt.r43ples.webservice;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import javax.ws.rs.core.UriBuilder;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.mvc.mustache.MustacheMvcFeature;

import com.hp.hpl.jena.query.Dataset;

import de.tud.plt.r43ples.management.Config;
import de.tud.plt.r43ples.management.TripleStoreInterface;


/**
 * R43ples Web Service.
 * Main Class starting the web server on grizzly.
 * 
 * @author Stephan Hensel
 * @author Markus Graube
 *
 */
public class Service {

	/** The logger */
	private static Logger logger = Logger.getLogger(Service.class);
	/** The HTTP server. **/
	private static HttpServer server;
	/** The TDB dataset. **/
	public static Dataset dataset;
	
	
	/**
	 * Starts the server.
	 * 
	 * @param args
	 * @throws ConfigurationException
	 * @throws URISyntaxException
	 * @throws IOException 
	 */
	public static void main(String[] args) {
		try {
			Config.readConfig("r43ples.conf");
			start();
			logger.info("Press enter to quit the server");
			System.in.read();
		} catch (Exception e) {
			e.printStackTrace();
			stop();
		}
	}

	
	/**
	 * Starts the server. It is possible to enable a secure connection.
	 * 
	 * @throws ConfigurationException
	 * @throws URISyntaxException
	 * @throws IOException 
	 */
	public static void start() throws ConfigurationException, URISyntaxException, IOException {
		logger.info("Starting R43ples on grizzly...");
		URI BASE_URI = null;
	
		ClassLoader classLoader = Service.class.getClassLoader();
		
		// Choose if the endpoint should be SSL secured
		if (Config.service_secure) {
			BASE_URI = UriBuilder.fromUri(Config.service_uri).port(Config.service_port).path("r43ples").build();
		
			ResourceConfig rc = new ResourceConfig()
				.registerClasses(Endpoint.class)
				.property(MustacheMvcFeature.TEMPLATE_BASE_PATH, "templates")
				.register(MustacheMvcFeature.class)
				.register(ExceptionMapper.class);

			SSLContextConfigurator sslCon = new SSLContextConfigurator();
			sslCon.setKeyStoreFile(Paths.get(classLoader.getResource(Config.ssl_keystore).toURI()).toString());
			sslCon.setKeyStorePass(Config.ssl_password);
			sslCon.setTrustStoreFile(Paths.get(classLoader.getResource(Config.ssl_keystore).toURI()).toString());
			sslCon.setTrustStorePass(Config.ssl_password);
			
			logger.info("SSL context validated: " + Boolean.toString(sslCon.validateConfiguration()));
	
			server = GrizzlyHttpServerFactory.createHttpServer(BASE_URI, rc, true, new SSLEngineConfigurator(sslCon, false, false, false));

			server.getServerConfiguration().addHttpHandler(
			        new CLStaticHttpHandler(Service.class.getClassLoader(),"webapp/"), "/static/");

			server.start();
			
			logger.info("Connection is secure.");
		} else {
			BASE_URI = UriBuilder.fromUri(Config.service_uri).port(Config.service_port).path("r43ples").build();
			
			ResourceConfig rc = new ResourceConfig()
				.registerClasses(Endpoint.class)
				.property(MustacheMvcFeature.TEMPLATE_BASE_PATH, "templates")
				.register(MustacheMvcFeature.class)
				.register(ExceptionMapper.class);
			
			server = GrizzlyHttpServerFactory.createHttpServer(BASE_URI, rc);
			
			server.getServerConfiguration().addHttpHandler(
			        new CLStaticHttpHandler(Service.class.getClassLoader(),"webapp/"), "/static/");
			
			server.start();
			
			logger.info("Connection is not secure.");
		}
		
		logger.info(String.format("Server started - R43ples endpoint available under: %s/sparql", BASE_URI));
		
		logger.info("Version: "+ Service.class.getPackage().getImplementationVersion());
		
		TripleStoreInterface.init(Config.database_directory);
	}
	
	
	/**
	 * Stops the server.
	 */
	public static void stop() {
		logger.info("Server shutdown ...");
		TripleStoreInterface.close();
		server.shutdown();
	}
	
}
