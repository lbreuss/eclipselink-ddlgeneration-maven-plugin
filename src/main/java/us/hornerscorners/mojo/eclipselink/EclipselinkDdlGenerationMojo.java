/*
 * @(#)EclipselinkDdlGenerationMojo.java
 * Date 2012-07-26
 * Version 1.0
 * Author Jim Horner
 * Copyright (c)2012
 */


package us.hornerscorners.mojo.eclipselink;

import java.io.File;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.apache.maven.project.MavenProject;
import org.eclipse.persistence.dynamic.DynamicClassLoader;


/**
 * The EclipselinkDdlGenerationMojo generates DDL files from a persistence unit.
 * <p>
 * An EclipseLink EntityManager is instantiated to generate the DDL files by the standard
 * JPA loading mechanism and the EclipseLink DDL generation process.
 * <p>
 * Note: The plugin needs to run in the process-classes phase after the compile phase,
 * because the compile phase copies the /src/main/resources/ to /target/classes/,
 * which per default is part of the classpath.
 * <p>
 * To run the plugin in an earlier phase, use inputDir to point at the root of your META-INF/persistence.xml
 * 
 * @goal execute
 * @phase process-classes
 * 
 * @author jim, lbreuss
 */
public class EclipselinkDdlGenerationMojo extends AbstractMojo {

    /**
     * @parameter
     * @required
     */
    private String createFilename;

    /**
     * @parameter
     * @required
     */
    private String deleteFilename;

    /**
     * @parameter
     * @required
     */
    private String jdbcDriver;

    /**
     * @parameter
     * @required
     */
    private String jdbcPassword;

    /**
     * @parameter
     * @required
     */
    private String jdbcUrl;

    /**
     * @parameter
     * @required
     */
    private String jdbcUser;

    /**
     * @parameter expression="${project}"
     */
    private MavenProject mavenProject;

    /**
     * @parameter
     * @required
     */
    private File outputDir;

    /**
     * root directory where the persistence.xml is searched for.
     * <p>
     * E.g. to use a persistence unit <tt>src/main/resources/META-INF/persistence.xml</tt>, 
     * specify inputDir as <tt>src/main/resources</tt>
     * <p>
     * Setting this input directory enables to use the plugin before the compile phase. 
     * @parameter
     */
    private File inputDir;

    /**
     * @parameter
     * @required
     */
    private String unitName;

    /**
     * Method description
     *
     *
     * @return
     */
    private ClassLoader buildEntityClassLoader() throws MalformedURLException {

        List<URL> urls = new ArrayList<URL>();

        File classesFile =
            new File(this.mavenProject.getBuild().getOutputDirectory());

        // if specified, add the inputDir to the classpath of the URLClassLoader, but not the target/classes/ dir.
        if(inputDir != null) {
            urls.add(inputDir.toURI().toURL());
        } else {
            urls.add(classesFile.toURI().toURL());	
        }
        
        ClassLoader urlClassLoader = new URLClassLoader(
                urls.toArray(new URL[urls.size()]),
                Thread.currentThread().getContextClassLoader());
        
        // for Entities with access="VIRTUAL", the EclipseLink DynamicClassLoader is needed.
        DynamicClassLoader dynClassLoader = new DynamicClassLoader(urlClassLoader);

        return dynClassLoader;
    }

    /**
     * Method description
     *
     *
     */

    /**
     * Method description
     *
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        try {

            DDLGenerator generator = new DDLGenerator(this.unitName,
                                         buildEntityClassLoader());

            // configure jdbc connection pool
            generator.setJdbcDriver(this.jdbcDriver);
            generator.setJdbcPassword(this.jdbcPassword);
            generator.setJdbcUrl(this.jdbcUrl);
            generator.setJdbcUser(this.jdbcUser);

            // configure output parameters
            generator.setOutputDir(this.outputDir);
            generator.setCreateFile(this.createFilename);
            generator.setDeleteFile(this.deleteFilename);

            generator.start();
            generator.join();

        } catch (Exception e) {

            throw new MojoExecutionException("Exception", e);

        }
    }
}
