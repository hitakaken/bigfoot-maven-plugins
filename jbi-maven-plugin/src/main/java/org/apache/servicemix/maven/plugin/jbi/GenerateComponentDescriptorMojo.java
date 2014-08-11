/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.maven.plugin.jbi;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.plexus.util.FileUtils;

import static org.apache.servicemix.maven.plugin.jbi.DependencyInformation.*;

/**
 * A Mojo used to build the jbi.xml file.
 * 
 * @author <a href="gnodet@apache.org">Guillaume Nodet</a>
 * @version $Id: GenerateComponentDescriptorMojo 314956 2005-10-12 16:27:15Z
 *          brett $
 * @goal generate-jbi-component-descriptor
 * @phase generate-resources
 * @requiresDependencyResolution runtime
 * @description generates the jbi.xml deployment descriptor
 */
public class GenerateComponentDescriptorMojo extends AbstractJbiMojo {

    public static final String UTF_8 = "UTF-8";

    /**
     * Whether the application.xml should be generated or not.
     * 
     * @parameter
     */
    private Boolean generateJbiDescriptor = Boolean.TRUE;

    /**
     * The component class name.
     * 
     * @parameter
     * @required
     */
    private String component;

    /**
     * The bootstrap class name.
     * 
     * @parameter
     */
    private String bootstrap;

    /**
     * The component type.
     * 
     * @parameter
     * @required
     */
    private String type;

    /**
     * The component name.
     * 
     * @parameter expression="${project.artifactId}"
     */
    private String name;

    /**
     * The destination of the default bootstrap.
     * 
     * @parameter expression="${project.build.directory}/classes/org/apache/servicemix/common/DefaultBootstrap.class"
     */
    private File defaultBootstrapFile;

    /**
     * The component description.
     * 
     * @parameter expression="${project.name}"
     */
    private String description;

    /**
     * Character encoding for the auto-generated application.xml file.
     * 
     * @parameter
     */
    private String encoding = UTF_8;

    /**
     * Directory where the application.xml file will be auto-generated.
     * 
     * @parameter expression="${project.build.directory}"
     */
    private String generatedDescriptorLocation;

    /**
     * The component class loader delegation
     * 
     * @parameter expression="parent-first"
     */
    private String componentClassLoaderDelegation;

    /**
     * The bootstrap class loader delegation
     * 
     * @parameter expression="parent-first"
     */
    private String bootstrapClassLoaderDelegation;
    
    /**
     * A list of dependency types to include in component classpath.
     * Default: jar, bundle, jbi-component
     *
     * @parameter
     */
    private List componentTypes = new ArrayList(Arrays.asList(new Object[]{"jar", "bundle", "jbi-component"}));

    public void execute() throws MojoExecutionException, MojoFailureException {

        getLog().debug(
                " ======= GenerateComponentDescriptorMojo settings =======");
        getLog().debug("workDirectory[" + workDirectory + "]");
        getLog().debug("generateJbiDescriptor[" + generateJbiDescriptor + "]");
        getLog().debug("type[" + type + "]");
        getLog().debug("component[" + component + "]");
        getLog().debug("bootstrap[" + bootstrap + "]");
        getLog().debug("name[" + name + "]");
        getLog().debug("description[" + description + "]");
        getLog().debug("encoding[" + encoding + "]");
        getLog().debug(
                "generatedDescriptorLocation[" + generatedDescriptorLocation
                        + "]");

        if (!generateJbiDescriptor.booleanValue()) {
            getLog().debug("Generation of jbi.xml is disabled");
            return;
        }

        if (bootstrap == null) {
            injectBootStrap();
        }

        // Generate jbi descriptor and copy it to the build directory
        getLog().info("Generating jbi.xml");
        try {
            generateJbiDescriptor();
        } catch (JbiPluginException e) {
            throw new MojoExecutionException("Failed to generate jbi.xml", e);
        }

        try {
            FileUtils.copyFileToDirectory(new File(generatedDescriptorLocation,
                    JBI_DESCRIPTOR), new File(getWorkDirectory(), META_INF));
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Unable to copy jbi.xml to final destination", e);
        }
    }

    /**
     * Helper method used to inject the BaseBootstrap from servicemix-commons
     * into the component this allows to you bypass actually having to create a
     * bootstrap
     * 
     * @throws MojoExecutionException
     * 
     */
    private void injectBootStrap() throws MojoExecutionException {

        try {
            URL defaultBootStrap = getClassLoader().getResource(
                    "org/apache/servicemix/common/DefaultBootstrap.class");
            FileUtils.copyURLToFile(defaultBootStrap, defaultBootstrapFile);
            bootstrap = "org.apache.servicemix.common.DefaultBootstrap";
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Unable to copy DefaultBootstrap.class to "
                            + defaultBootstrapFile.getAbsolutePath(), e);
        }
    }

    /**
     * Generates the deployment descriptor if necessary.
     */
    protected void generateJbiDescriptor() throws JbiPluginException {
        File outputDir = new File(generatedDescriptorLocation);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File descriptor = new File(outputDir, JBI_DESCRIPTOR);

        List uris = new ArrayList();
        DependencyInformation info = new DependencyInformation();
        info.setFilename(LIB_DIRECTORY + "/" + project.getBuild().getFinalName() + ".jar");
        info.setVersion(project.getVersion());
        info.setName(project.getArtifactId());
        info.setType("jar");
        uris.add(info);

        ArtifactFilter filter = new ArtifactFilter() {
            public boolean include(Artifact artifact) {
                return !artifact.isOptional() &&
                        (artifact.getScope() == Artifact.SCOPE_RUNTIME || artifact.getScope() == Artifact.SCOPE_COMPILE);
            }
        };


        JbiResolutionListener listener = resolveProject();
        Set<Artifact> includes = new HashSet<Artifact>();
        Set<Artifact> excludes = new HashSet<Artifact>();
        for (Iterator iter = project.getArtifacts().iterator(); iter.hasNext();) {
            Artifact artifact = (Artifact) iter.next();
            if (filter.include(artifact)) {
                MavenProject project = null;
                try {
                    project = projectBuilder.buildFromRepository(artifact, remoteRepos, localRepo);
                } catch (ProjectBuildingException e) {
                    getLog().warn("Unable to determine packaging for dependency : "
                                    + artifact.getArtifactId() + " assuming jar");
                }
                String type = project != null ? project.getPackaging() : artifact.getType();
                if (SHARED_LIBRARY_TYPE.equals(type)) {
                    // exclude children, but not the shared library itself
                    excludeBranch(listener.getNode(artifact), excludes);
                    excludes.remove(artifact);
                    includes.add(artifact);
                } else if (componentTypes.contains(type)) {
                    includes.add(artifact);
                }
            }
        }
        pruneTree(listener.getRootNode(), excludes);
        // print(listener.getRootNode(), "");
        for (Artifact artifact : retainArtifacts(includes, listener)) {
            MavenProject project = null;
            try {
                project = projectBuilder.buildFromRepository(artifact, remoteRepos, localRepo);
            } catch (ProjectBuildingException e) {
                getLog().warn("Unable to determine packaging for dependency : "
                                + artifact.getArtifactId() + " assuming jar");
            }
            String type = project != null ? project.getPackaging() : artifact.getType();
            info = new DependencyInformation();
            info.setFilename(LIB_DIRECTORY + "/" + artifact.getFile().getName());
            info.setVersion(artifact.getVersion());
            info.setName(artifact.getArtifactId());
            info.setType(type);
            uris.add(info);
        }

        JbiComponentDescriptorWriter writer = new JbiComponentDescriptorWriter(encoding);
        writer.write(descriptor, component, bootstrap, type, name, description,
                componentClassLoaderDelegation, bootstrapClassLoaderDelegation,
                uris);
    }

}
