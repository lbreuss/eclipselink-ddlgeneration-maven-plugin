/**
 * 
 */
package ch.lbreuss.mojo.eclipselink;

import java.util.Map;

import javax.persistence.Persistence;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.persistence.config.PersistenceUnitProperties;

/**
 * The DDL Generator Thread.
 * 
 * @author lbreuss
 *
 */
public class DDLGeneratorThread extends Thread {

	private Log log;

	private String persistenceUnitName;

	private Map<String, String> ddlGenerationProperties;

	/**
	 * @return the ddlGenerationProperties. You may add to these properties
	 *         before the thread is started.
	 */
	public Map<String, String> getDdlGenerationProperties() {
		return ddlGenerationProperties;
	}

	/**
	 * @param persistenceUnitName
	 *            the persistence unit name
	 * @param cl
	 *            The custom context classloader, that will find and load the
	 *            persistence unit files.
	 * @param ddlGenerationProperties
	 *            persistence unit properties for DDL generation.
	 */
	public DDLGeneratorThread(String persistenceUnitName, ClassLoader cl,
			Map<String, String> ddlGenerationProperties, Log log) {
		super();
		this.persistenceUnitName = persistenceUnitName;
		this.ddlGenerationProperties = ddlGenerationProperties;
		this.log = log;
		setContextClassLoader(cl);
	}

	@Override
	public void run() {
		log.info("Generating schema for persistence unit "
				+ persistenceUnitName);
		if (log.isDebugEnabled()) {
			log.debug("ddlGenerationProperties:\n"
					+ ddlGenerationProperties.toString()
							.replaceAll("^\\{|\\}$", "").replaceAll(", ", "\n"));
		}

		Persistence
				.generateSchema(persistenceUnitName, ddlGenerationProperties);
		log.info("DDL files have been written to "
				+ ddlGenerationProperties
						.get(PersistenceUnitProperties.APP_LOCATION));
	}

}
