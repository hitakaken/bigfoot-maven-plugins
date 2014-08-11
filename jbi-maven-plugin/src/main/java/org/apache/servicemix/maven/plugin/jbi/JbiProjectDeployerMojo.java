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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.servicemix.jbi.management.task.DeployServiceAssemblyTask;
import org.apache.servicemix.jbi.management.task.InstallComponentTask;
import org.apache.servicemix.jbi.management.task.InstallSharedLibraryTask;
import org.apache.servicemix.jbi.management.task.ShutDownComponentTask;
import org.apache.servicemix.jbi.management.task.ShutDownServiceAssemblyTask;
import org.apache.servicemix.jbi.management.task.StartComponentTask;
import org.apache.servicemix.jbi.management.task.StartServiceAssemblyTask;
import org.apache.servicemix.jbi.management.task.StopComponentTask;
import org.apache.servicemix.jbi.management.task.StopServiceAssemblyTask;
import org.apache.servicemix.jbi.management.task.UndeployServiceAssemblyTask;
import org.apache.servicemix.jbi.management.task.UninstallComponentTask;
import org.apache.servicemix.jbi.management.task.UninstallSharedLibraryTask;

/**
 * A Mojo that can take any project and determine its JBI dependencies and then
 * install it and its dependencies using the JBI deployment tasks
 * 
 * @author <a href="pdodds@apache.org">Philip Dodds</a>
 * @version $Id: GenerateComponentDescriptorMojo 314956 2005-10-12 16:27:15Z
 *          brett $
 * @goal projectDeploy
 * @requiresDependencyResolution runtime
 * @description Starts a ServiceMix instance and installs the project (and all
 *              dependencies) to it
 */
public class JbiProjectDeployerMojo extends AbstractDeployableMojo {

    public static final String JBI_SHARED_LIBRARY = "jbi-shared-library";

    public static final String JBI_COMPONENT = "jbi-component";

    public static final String JBI_SERVICE_ASSEMBLY = "jbi-service-assembly";

    private List deploymentTypes;

    /**
     * @parameter default-value="true" expression="${deployDependencies}"
     */
    private boolean deployDependencies;

    /**
     * @parameter default-value="false" expression="${forceUpdate}"
     */
    private boolean forceUpdate;

    /**
     * @parameter default-value="true" expression="${deferExceptions}"
     */
    private boolean deferExceptions;

    /**
     * @parameter default-value="true" expression="${deployChildren}"
     */
    private boolean deployChildren;

    public void execute() throws MojoExecutionException, MojoFailureException {
        deployProject();
    }

    protected void deployProject() throws MojoExecutionException {
        if (!deployChildren
                && !getDeployablePackagingTypes().contains(
                        project.getPackaging())) {
            throw new MojoExecutionException(
                    "Project must be of packaging type ["
                            + getDeployablePackagingTypes() + "]");
        }

        try {
            Stack dependencies = new Stack();
            if (deployChildren) {
                resolveArtifact(project.getArtifact(), dependencies);
            } else {
                dependencies.add(resolveDeploymentPackage(project, project
                        .getArtifact()));
                List artifactList = new ArrayList();
                artifactList.addAll(project.getArtifacts());
                Collections.sort(artifactList, new ArtifactDepthComparator());
                for (Iterator iter = artifactList.iterator(); iter.hasNext();) {
                    Artifact artifact = (Artifact) iter.next();
                    resolveArtifact(artifact, dependencies);
                }
            }

            getLog()
                    .info(
                            "------------------ Deployment Analysis --------------------");
            getLog().info(
                    project.getName() + " has " + (dependencies.size() - 1)
                            + " child dependencies");

            for (Iterator iterator = dependencies.iterator(); iterator
                    .hasNext();) {
                getLog().info(" - " + iterator.next());
            }

            getLog()
                    .info(
                            "-----------------------------------------------------------");

            if (deployDependencies) {
                // We need to stop all the dependencies first
                if (!deferExceptions) {
                    for (Iterator iterator = dependencies.iterator(); iterator
                            .hasNext();) {
                        JbiDeployableArtifact jbiDeployable = (JbiDeployableArtifact) iterator
                                .next();

                        if (forceUpdate && isDeployed(jbiDeployable)) {
                            stopDependency(jbiDeployable);
                            undeployDependency(jbiDeployable);
                        }
                    }
                }

                // Now we can walk the dependencies bottom up - re-deploying and
                // starting them
                while (!dependencies.empty()) {
                    JbiDeployableArtifact jbiDeployable = (JbiDeployableArtifact) dependencies
                            .pop();
                    if (forceUpdate || !isDeployed(jbiDeployable)) {
                        deployDependency(jbiDeployable, deferExceptions);
                        if (!deferExceptions) {
                            startDependency(jbiDeployable);
                        }
                    }
                }
            } else {
                JbiDeployableArtifact jbiDeployable = (JbiDeployableArtifact) dependencies
                        .firstElement();
                if (isDeployed(jbiDeployable)) {
                    stopDependency(jbiDeployable);
                    undeployDependency(jbiDeployable);
                }
                deployDependency(jbiDeployable, deferExceptions);
                startDependency(jbiDeployable);
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Unable to deploy project, "
                    + e.getMessage(), e);
        }

    }

    protected void startDependency(JbiDeployableArtifact jbiDeployable) {
        getLog().info("Starting " + jbiDeployable.getName());
        if (JBI_SERVICE_ASSEMBLY.equals(jbiDeployable.getType())) {
            StartServiceAssemblyTask startTask = new StartServiceAssemblyTask();
            initializeJbiTask(startTask);
            startTask.setName(jbiDeployable.getName());
            startTask.execute();
        }
        if (JBI_COMPONENT.equals(jbiDeployable.getType())) {
            StartComponentTask startTask = new StartComponentTask();
            initializeJbiTask(startTask);
            startTask.setName(jbiDeployable.getName());
            startTask.execute();
        }
    }

    protected void undeployDependency(JbiDeployableArtifact jbiDeployable) {
        getLog().info("Undeploying " + jbiDeployable.getFile());
        if (JBI_SHARED_LIBRARY.equals(jbiDeployable.getType())) {
            UninstallSharedLibraryTask sharedLibraryTask = new UninstallSharedLibraryTask();
            initializeJbiTask(sharedLibraryTask);
            sharedLibraryTask.setName(jbiDeployable.getName());
            sharedLibraryTask.execute();
        } else if (JBI_SERVICE_ASSEMBLY.equals(jbiDeployable.getType())) {
            UndeployServiceAssemblyTask serviceAssemblyTask = new UndeployServiceAssemblyTask();
            initializeJbiTask(serviceAssemblyTask);
            serviceAssemblyTask.setName(jbiDeployable.getName());
            serviceAssemblyTask.execute();
        }
        if (JBI_COMPONENT.equals(jbiDeployable.getType())) {
            UninstallComponentTask componentTask = new UninstallComponentTask();
            initializeJbiTask(componentTask);
            componentTask.setName(jbiDeployable.getName());
            componentTask.execute();
        }
    }

    protected boolean isDeployed(JbiDeployableArtifact jbiDeployable) {
        IsDeployedTask isDeployedTask = new IsDeployedTask();
        isDeployedTask.setType(jbiDeployable.getType());
        isDeployedTask.setName(jbiDeployable.getName());
        initializeJbiTask(isDeployedTask);
        isDeployedTask.execute();
        boolean deployed = isDeployedTask.isDeployed();
        if (deployed) {
            getLog().info(jbiDeployable.getName() + " is deployed");
        } else {
            getLog().info(jbiDeployable.getName() + " is not deployed");
        }
        return deployed;
    }

    protected void stopDependency(JbiDeployableArtifact jbiDeployable) {
        getLog().info("Stopping " + jbiDeployable.getName());
        if (JBI_SERVICE_ASSEMBLY.equals(jbiDeployable.getType())) {
            StopServiceAssemblyTask stopTask = new StopServiceAssemblyTask();
            initializeJbiTask(stopTask);
            stopTask.setName(jbiDeployable.getName());
            stopTask.execute();

            ShutDownServiceAssemblyTask shutdownTask = new ShutDownServiceAssemblyTask();
            initializeJbiTask(shutdownTask);
            shutdownTask.setName(jbiDeployable.getName());
            shutdownTask.execute();
        }
        if (JBI_COMPONENT.equals(jbiDeployable.getType())) {
            StopComponentTask stopTask = new StopComponentTask();
            initializeJbiTask(stopTask);
            stopTask.setName(jbiDeployable.getName());
            stopTask.execute();

            ShutDownComponentTask shutdownTask = new ShutDownComponentTask();
            initializeJbiTask(shutdownTask);
            shutdownTask.setName(jbiDeployable.getName());
            shutdownTask.execute();
        }
    }

    protected void deployDependency(JbiDeployableArtifact jbiDeployable,
            boolean doDeferExceptions) throws MojoExecutionException {

        getLog().info(
                "Deploying " + jbiDeployable.getType() + " from "
                        + jbiDeployable.getFile());
        if (JBI_SHARED_LIBRARY.equals(jbiDeployable.getType())) {
            InstallSharedLibraryTask componentTask = new InstallSharedLibraryTask();
            initializeJbiTask(componentTask);
            componentTask.setFile(jbiDeployable.getFile());
            componentTask.setDeferExceptions(doDeferExceptions);
            componentTask.execute();
        } else if (JBI_SERVICE_ASSEMBLY.equals(jbiDeployable.getType())) {
            DeployServiceAssemblyTask componentTask = new DeployServiceAssemblyTask();
            initializeJbiTask(componentTask);
            componentTask.setFile(jbiDeployable.getFile());
            componentTask.setDeferExceptions(doDeferExceptions);
            componentTask.execute();
        }
        if (JBI_COMPONENT.equals(jbiDeployable.getType())) {
            InstallComponentTask componentTask = new InstallComponentTask();
            initializeJbiTask(componentTask);
            componentTask.setFile(jbiDeployable.getFile());
            componentTask.setDeferExceptions(doDeferExceptions);
            componentTask.execute();
        }

    }

    protected List getDeployablePackagingTypes() {
        if (deploymentTypes == null) {
            deploymentTypes = new ArrayList();
            deploymentTypes.add(JBI_SHARED_LIBRARY);
            deploymentTypes.add(JBI_SERVICE_ASSEMBLY);
            deploymentTypes.add(JBI_COMPONENT);
        }
        return deploymentTypes;
    }

    private Collection resolveArtifact(Artifact artifact, 
                                       Stack dependencies) throws ArtifactResolutionException, ArtifactNotFoundException {
        MavenProject project = null;
        try {
            project = projectBuilder.buildFromRepository(artifact, remoteRepos,
                    localRepo, false);
        } catch (ProjectBuildingException e) {
            getLog().warn(
                    "Unable to determine packaging for dependency : "
                            + artifact.getArtifactId() + " assuming jar");
        }

        if (project != null) {
            if (getDeployablePackagingTypes().contains(project.getPackaging())) {
                getLog().debug(
                        "Checking for dependency from project "
                                + project.getArtifactId());
                JbiDeployableArtifact deployableArtifact = resolveDeploymentPackage(
                        project, artifact);
                if (!dependencies.contains(deployableArtifact)) {
                    getLog().debug(
                            "Adding dependency from project "
                                    + project.getArtifactId());
                    dependencies.push(deployableArtifact);
                }
                List artifactList = new ArrayList();
                artifactList.addAll(project.getArtifacts());
                Collections.sort(artifactList, new ArtifactDepthComparator());
                for (Iterator iter = artifactList.iterator(); iter.hasNext();) {
                    resolveArtifact((Artifact) iter.next(), dependencies);
                }
            } else {
                getLog().debug(
                        "Ignoring non-jbi dependency: "
                                + project.getArtifactId() + " of type "
                                + project.getPackaging());
            }
        }
        return dependencies;
    }

    protected JbiDeployableArtifact resolveDeploymentPackage(
            MavenProject project, 
            Artifact artifact) throws ArtifactResolutionException, ArtifactNotFoundException {
        Artifact jbiArtifact = factory.createArtifactWithClassifier(artifact
                .getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                "zip", getExtension(project));
        resolver.resolve(jbiArtifact, remoteRepos, localRepo);
        return new JbiDeployableArtifact(project.getArtifactId(), project
                .getPackaging(), jbiArtifact.getFile().getAbsolutePath());
    }

    protected String getExtension(MavenProject project2) {
        if (project2.getPackaging().equals(JBI_SERVICE_ASSEMBLY)) {
            return "";
        } else {
            return "installer";
        }
    }

    protected class ArtifactDepthComparator implements Comparator {

        public int compare(Object arg0, Object arg1) {
            int size1 = ((Artifact) arg0).getDependencyTrail().size();
            int size2 = ((Artifact) arg1).getDependencyTrail().size();
            if (size1 == size2) {
                return 0;
            }
            if (size1 > size2) {
                return 1;
            } else {
                return -1;
            }
        }

    }

    protected class JbiDeployableArtifact {
        private String file;

        private String type;

        private String name;

        public JbiDeployableArtifact(String name, String type, String file) {
            this.name = name;
            this.file = file;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getFile() {
            return file;
        }

        public String getType() {
            return type;
        }

        public String toString() {
            return type + " : " + file;
        }

        public boolean equals(Object obj) {
            if (obj instanceof JbiDeployableArtifact) {
                return ((JbiDeployableArtifact) obj).toString().equals(this.toString());
            } else {
                return false;
            }
        }
        
        public int hashCode() {
            return toString().hashCode();
        }
    }
}
