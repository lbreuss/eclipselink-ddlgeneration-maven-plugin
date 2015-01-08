package ch.lbreuss.mojo.eclipselink;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.eclipse.persistence.dynamic.DynamicClassLoader;

/**
 * The EclipselinkDdlGenerationMojo generates DDL files from a persistence unit.
 * <p>
 * The JPA 2.1 API is used to generate a schema, by the standard JPA loading mechanism and the EclipseLink DDL
 * generation process, without the usage of a JDBC connection.
 * <p>
 * Usage Details:
 * <ul>
 * <li>Put the persistence unit into src/main/resources/META-INF/persistence.xml
 * <li>Execute phase generate-resources
 * <li>The resulting files are found under target/generated/sql/
 * <li>You may control all aspects of the DDL generation process by configuring the generic properties.
 * </ul>
 * <p>
 * Technical Details:
 * <ul>
 * <li>Uses JPA 2.1 Persistence.generateSchema() (i.e. no EntityManager, no JDBC connection)
 * <li>A custom classloader hierarchy is used to load the persistence unit.
 * <li>The custom classloader is set as the contextClassLoader of a new thread, that does the effective work.
 * </ul>
 * 
 * @goal generate-ddl
 * @phase generate-resources
 * 
 * @author jim, lbreuss
 */
public class EclipselinkDdlGenerationMojo extends AbstractMojo {

    // List of the most common parameters. They may be overriden by their
    // respective persistenceUnitProperties.

    /**
     * The name of the persistence unit to be processed.
     * 
     * @parameter
     * @required
     */
    private String persistenceUnitName;

    /**
     * The input directory will be added to a custom context classloader, which searches for and loads the
     * persistence unit.
     * <p>
     * E.g. to use a persistence unit <tt>src/main/resources/META-INF/persistence.xml</tt>, specify inputDir
     * as <tt>src/main/resources</tt>
     * <p>
     * 
     * @parameter default-value="src/main/resources"
     */
    private File inputDir;

    /**
     * The JDBC driver class.
     * 
     * @parameter default-value="org.h2.Driver"
     */
    private String jdbcDriver;

    /**
     * The JDBC URL.
     * <p>
     * H2 embedded in-memory DB is used as a default to generate DDL scripts.
     * 
     * @parameter default-value="jdbc:h2:mem:db"
     */
    private String jdbcURL;

    /**
     * @parameter
     */
    private String jdbcUser;

    /**
     * @parameter
     */
    private String jdbcPassword;

    /**
     * Output directory for the generated DDL files.
     * <p>
     * Default: target/generated/sql
     * 
     * @parameter default-value="${project.build.directory}/generated/sql"
     */
    private File outputDir;

    /**
     * The DDL output filename for the create statements.
     * 
     * @parameter default-value=createDDL.sql
     */
    private String createDdlFilename;

    /**
     * The DDL output filename for the create statements.
     * 
     * @parameter default-value=dropDDL.sql
     */
    private String dropDdlFilename;

    /**
     * javax.persistence properties to be supplied directly to the DDL generator.
     * <p>
     * <a href="http://wiki.eclipse.org/EclipseLink/Examples/JPA/DDL">EclipseLink /Examples/JPA/DDL</a>
     * <p>
     * Note:
     * <ul>
     * <li>These properties will override the value of explicit configuration parameters or their default.
     * <li>The old eclipselink properties will override the new JPA 2.1 javax.persistence properties. Don't
     * mix them.
     * </ul>
     * <p>
     * For a complete list of persistence unit properties see <a href=
     * "http://www.eclipse.org/eclipselink/api/2.5/org/eclipse/persistence/config/PersistenceUnitProperties.html"
     * >PersistenceUnitProperties</a>
     * <p>
     * For an explicit list of EclipseLink DDL generation properties regarding JPA 2.1 have a look at <a href=
     * "https://wiki.eclipse.org/EclipseLink/Release/2.5/JPA21#DDL_generation"
     * >https://wiki.eclipse.org/EclipseLink/Release/2.5/JPA21</a>
     * 
     * @parameter
     */
    private Properties properties;

    /**
     * Method description
     *
     *
     * @return
     * @throws MojoExecutionException
     */
    private ClassLoader buildEntityClassLoader() throws MalformedURLException, MojoExecutionException {

        List<URL> urls = new ArrayList<URL>();

        // Previous version of the plugin always added the /target path to the
        // classloader search path.
        // The compile phase copies the src/main/resources/ to target/.
        // /**
        // * @parameter expression="${project}"
        // */
        // private MavenProject mavenProject;
        // File classesFile = new
        // File(this.mavenProject.getBuild().getOutputDirectory());

        // if specified, add the inputDir to the classpath of the
        // URLClassLoader, but not the target/classes/ dir.
        if (inputDir == null) {
            throw new MojoExecutionException(
                    "No inputDir defined. You should not see this message, as inputDir defaults to src/main/resources/");
        }
        urls.add(inputDir.toURI().toURL());
        getLog().debug("Added inputDir to classloader search path: " + inputDir);

        ClassLoader urlClassLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]), Thread.currentThread()
                .getContextClassLoader());

        // for Entities with access="VIRTUAL", the EclipseLink
        // DynamicClassLoader is needed.
        DynamicClassLoader dynClassLoader = new DynamicClassLoader(urlClassLoader);

        return dynClassLoader;
    }

    /**
     * Method description
     *
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    // the cast of Properties to Map
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        try {

            Map<String, String> ddlGenerationProperties = new TreeMap<String, String>();

            // override with local transaction
            ddlGenerationProperties.put(PersistenceUnitProperties.TRANSACTION_TYPE, "RESOURCE_LOCAL");
            ddlGenerationProperties.put(PersistenceUnitProperties.JTA_DATASOURCE, null);
            ddlGenerationProperties.put(PersistenceUnitProperties.NON_JTA_DATASOURCE, null);
            ddlGenerationProperties.put(PersistenceUnitProperties.VALIDATION_MODE, "NONE");

            // Enable DDL Generation
            // ddlGenerationProperties.put(PersistenceUnitProperties.DDL_GENERATION,
            // PersistenceUnitProperties.DROP_AND_CREATE);
            // eclipselink.ddl-generation, drop-and-create-tables
            // ddlGenerationProperties.put(PersistenceUnitProperties.DDL_GENERATION_MODE,
            // PersistenceUnitProperties.DDL_SQL_SCRIPT_GENERATION);
            // eclipselink.ddl-generation.output-mode, sql-script
            //
            // use JPA 2.1 constants insted of EclipseLink constants:
            ddlGenerationProperties.put(PersistenceUnitProperties.SCHEMA_GENERATION_SCRIPTS_ACTION,
                    PersistenceUnitProperties.SCHEMA_GENERATION_DROP_AND_CREATE_ACTION);
            ddlGenerationProperties.put(PersistenceUnitProperties.SCHEMA_GENERATION_DATABASE_ACTION,
                    PersistenceUnitProperties.SCHEMA_GENERATION_NONE_ACTION);

            // JDBC config
            // -----------
            ddlGenerationProperties.put(PersistenceUnitProperties.JDBC_DRIVER, jdbcDriver);
            ddlGenerationProperties.put(PersistenceUnitProperties.JDBC_URL, jdbcURL);

            if (jdbcUser != null) {
                ddlGenerationProperties.put(PersistenceUnitProperties.JDBC_USER, jdbcUser);
            }
            if (jdbcPassword != null) {
                ddlGenerationProperties.put(PersistenceUnitProperties.JDBC_PASSWORD, jdbcPassword);
            }

            // IO config
            // ---------
            // Merge explicit properties from configuraton section
            getLog().debug("outputDir: " + outputDir);
            if (outputDir == null) {
                throw new MojoExecutionException(
                        "No outputDir specified. You should not see this message, as outputDir defaults to target/generated/sql/");
            }
            // make sure the outputDir path exists under every condition and
            // phase
            if (!outputDir.mkdirs()) {
                throw new Exception("Cannot create outputDir " + outputDir.getAbsolutePath());
            }
            ddlGenerationProperties.put(PersistenceUnitProperties.APP_LOCATION, outputDir.getAbsolutePath());
            if (createDdlFilename != null && !createDdlFilename.isEmpty()) {
                ddlGenerationProperties.put(PersistenceUnitProperties.SCHEMA_GENERATION_SCRIPTS_CREATE_TARGET,
                        createDdlFilename);
            }
            if (dropDdlFilename != null && !dropDdlFilename.isEmpty()) {
                ddlGenerationProperties.put(PersistenceUnitProperties.SCHEMA_GENERATION_SCRIPTS_DROP_TARGET,
                        dropDdlFilename);
            }

            // Merge general properties from configuration section.
            // ----------------------------------------------------
            // These override the previous config, which might be default values for e.g. jdbc.url.
            if (properties != null) {
                for (Entry<String, String> e : ddlGenerationProperties.entrySet()) {
                    if (properties.containsKey(e.getKey())) {
                        // resolve property collision, warn about overwriting values.
                        String v1 = e.getValue();
                        String v2 = properties.get(e.getKey()).toString();
                        if (!v1.equals(v2)) {
                            StringBuffer msg = new StringBuffer("property ").append(e.getKey()).append(" ");
                            msg.append("will overwrite the existing value '").append(v1).append("' with '").append(v2)
                                    .append("'");
                            getLog().warn(msg);
                        }
                    }
                }
                ddlGenerationProperties.putAll((Map) properties);
            }

            // Create an instance of the generator
            DDLGeneratorThread generator = new DDLGeneratorThread(persistenceUnitName, buildEntityClassLoader(),
                    ddlGenerationProperties, getLog());

            // Now do the work
            generator.start();
            generator.join();

        } catch (Exception e) {
            throw new MojoExecutionException("Exception", e);
        }
    }
}
