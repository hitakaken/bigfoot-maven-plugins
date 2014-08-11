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

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * A Mojo used to build the jbi service assembly zip file
 *
 * @goal jbi-service-assembly
 * @phase package
 * @requiresDependencyResolution runtime
 * @description injects additional libraries into service assembly
 */
public class GenerateServiceAssemblyMojo extends AbstractJbiMojo {

    /**
     * The Zip archiver.
     *
     * @parameter expression="${component.org.codehaus.plexus.archiver.Archiver#jar}"
     * @component role="org.codehaus.plexus.archiver.Archiver" roleHint="jar"
     * @required
     */
    private JarArchiver jarArchiver;

    /**
     * The directory for the generated JBI component.
     *
     * @parameter expression="${project.build.directory}"
     * @required
     */
    private File outputDirectory;

    /**
     * The name of the generated war.
     *
     * @parameter expression="${project.build.finalName}.zip"
     * @required
     */
    private String finalName;

    /**
     * The maven archive configuration to use.
     *
     * @parameter
     */
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            injectDependentServiceUnits();
            File sa = new File(outputDirectory, finalName);
            createArchive(sa);
            project.getArtifact().setFile(sa);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to inject dependencies", e);
        }
    }

    private void createArchive(File installerFile) throws JbiPluginException {
        try {
            getLog().info("Generating service assembly " + installerFile.getAbsolutePath());
            MavenArchiver archiver = new MavenArchiver();
            archiver.setArchiver(jarArchiver);
            archiver.setOutputFile(installerFile);
            Manifest m = createManifest();
            String version = getProject().getArtifact().getVersion();
            // add Implementation-Version attribute in the MANIFEST
            m.getMainSection().addConfiguredAttribute(new Manifest.Attribute("Implementation-Version", version));

            getLog().info("Add Implementation-Version: " + version);

            jarArchiver.addConfiguredManifest(m);

            File classesDir = new File(getProject().getBuild().getOutputDirectory());
            if ( classesDir.exists() )
                jarArchiver.addDirectory(classesDir, null, DirectoryScanner.DEFAULTEXCLUDES);

            jarArchiver.addDirectory(workDirectory, null, DirectoryScanner.DEFAULTEXCLUDES);
            archiver.createArchive(getProject(), archive);
        } catch (Exception e) {
            throw new JbiPluginException("Error creating shared library: "
                    + e.getMessage(), e);
        }
    }

    private void injectDependentServiceUnits() throws JbiPluginException, ArtifactResolutionException, ArtifactNotFoundException {
        Set artifacts = project.getArtifacts();
        for (Iterator iter = artifacts.iterator(); iter.hasNext();) {
            Artifact artifact = (Artifact) iter.next();
            // TODO: utilise appropriate methods from project builder
            ScopeArtifactFilter filter = new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME);
            if (!artifact.isOptional() && filter.include(artifact) && (artifact.getDependencyTrail().size() == 2)) {
                MavenProject project = null;
                try {
                    project = projectBuilder.buildFromRepository(artifact, remoteRepos, localRepo);
                } catch (ProjectBuildingException e) {
                    getLog().warn("Unable to determine packaging for dependency : "
                                    + artifact.getArtifactId()
                                    + " assuming jar");
                }
                if (project != null && project.getPackaging().equals("jbi-service-unit")) {
                    try {
                    	String suName = getServiceUnitName(project);
                    	String path = artifact.getFile().getAbsolutePath();
                    	if (suName != null && suName.length() > 0) {
                    		path = path.substring(0, path.lastIndexOf(File.separator)) + File.separator + suName;
                    	} else {
                    		path = path.substring(0, path.lastIndexOf('.')) + ".zip";
                    	}
                    	FileUtils.copyFileToDirectory(new File(path), workDirectory);
                    } catch (IOException e) {
                        throw new JbiPluginException(e);
                    }
                }
            }
        }
    }
}
