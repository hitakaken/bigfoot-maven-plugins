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
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.servicemix.common.packaging.Consumes;
import org.apache.servicemix.common.packaging.Provides;
import org.apache.servicemix.common.packaging.ServiceUnitAnalyzer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A dummy implementation of the ServiceUnitAnalyzer that allows you to generate
 * the consumes and provides from a simple XML file
 *
 */
public class JbiServiceFileAnalyzer implements ServiceUnitAnalyzer {

    public static final String JBI_NAMESPACE = "http://java.sun.com/xml/ns/jbi";

    private final List consumes = new ArrayList();

    private final List provides = new ArrayList();

    public List getConsumes() {
        return consumes;
    }

    public List getProvides() {
        return provides;
    }

    public void init(File explodedServiceUnitRoot) {

    }

    public void setJbiServicesFile(File jbiServicesFile) throws MojoExecutionException {
        parseXml(jbiServicesFile);
    }

    private void parseXml(File jbiServicesFile) throws MojoExecutionException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(jbiServicesFile);

            // Stop at first services child node that is found
            NodeList childNodes = doc.getChildNodes();
            Node servicesNode = null;
            for (int i = 0; i < childNodes.getLength(); i++) {
                if (XmlDescriptorHelper.isElement(childNodes.item(i), JBI_NAMESPACE,
                    "services")) {
                    servicesNode = childNodes.item(i);
                    break;
                }
            }
            if (servicesNode != null) {
                // We will process the children
                Element servicesElement = (Element) servicesNode;
                NodeList children = servicesElement.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element) {
                        Element childElement = (Element) children.item(i);
                        if (XmlDescriptorHelper.isElement(childElement,
                                JBI_NAMESPACE, "consumes")) {
                            Consumes newConsumes = new Consumes();
                            newConsumes.setEndpointName(XmlDescriptorHelper
                                    .getEndpointName(childElement));
                            newConsumes.setInterfaceName(XmlDescriptorHelper
                                    .getInterfaceName(childElement));
                            newConsumes.setServiceName(XmlDescriptorHelper
                                    .getServiceName(childElement));
                            consumes.add(newConsumes);
                        } else if (XmlDescriptorHelper.isElement(childElement,
                                JBI_NAMESPACE, "provides")) {
                            Provides newProvides = new Provides();
                            newProvides.setEndpointName(XmlDescriptorHelper
                                    .getEndpointName(childElement));
                            newProvides.setInterfaceName(XmlDescriptorHelper
                                    .getInterfaceName(childElement));
                            newProvides.setServiceName(XmlDescriptorHelper
                                    .getServiceName(childElement));
                            provides.add(newProvides);
                        }
                    }
                }
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Unable to parse "
                    + jbiServicesFile.getAbsolutePath());
        }
    }
}
