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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.xbean.spring.context.FileSystemXmlApplicationContext;
import org.springframework.beans.factory.DisposableBean;

/**
 * Starts a ServiceMix JBI container in embedded mode using the servicemix.xml
 * 
 * @author <a href="pdodds@apache.org">Philip Dodds</a>
 * @version $Id: GenerateComponentDescriptorMojo 314956 2005-10-12 16:27:15Z
 *          brett $
 * @goal embeddedServicemix
 * @execute phase="compile"
 * @requiresDependencyResolution runtime
 * @description Starts a local servicemix instance using the servicemix config
 *              provided
 */
public class ServiceMixEmbeddedMojo extends AbstractJbiMojo {

    /**
     * @parameter default-value="${basedir}/src/main/resources/servicemix.xml"
     */
    private File servicemixConfig;

    private FileSystemXmlApplicationContext context;

    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            startServiceMix();

            while (true) {
                Thread.sleep(1000);
            }

        } catch (InterruptedException e) {
            throw new MojoFailureException("Interrupted");
        } finally {
            if (context instanceof DisposableBean) {
                try {
                    ((DisposableBean) context).destroy();
                } catch (Exception e) {
                    // Ignore
                }
            }
        } 

    }

    private void startServiceMix() throws MojoExecutionException {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClassLoader());
            context = new FileSystemXmlApplicationContext("file:///"
                    + servicemixConfig.getAbsolutePath());
            context.getBean("jbi");
        } catch (Exception e) {
            throw new MojoExecutionException(
                    "Unable to start the ServiceMix container", e);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

}
