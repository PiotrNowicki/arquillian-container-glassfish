/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.container.glassfish.embedded_3_1;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;

/**
 * GlassfishConfiguration
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
public class GlassFishConfiguration implements ContainerConfiguration
{
   private int bindHttpPort = 8181;
   private String instanceRoot = null;
   private String installRoot = null;
   private boolean configurationReadOnly = true;
   private String configurationXml;
   private String sunResourcesXml;
   
   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.client.container.ContainerConfiguration#validate()
    */
   public void validate() throws ConfigurationException
   {
      new GlassFishConfigProcessor().processConfig();
   }
   
   public int getBindHttpPort()
   {
      return bindHttpPort;
   }

   public void setBindHttpPort(int bindHttpPort)
   {
      this.bindHttpPort = bindHttpPort;
   }

   public String getInstanceRoot() 
   {
      return instanceRoot;
   }
   
   public void setInstanceRoot(String instanceRoot)
   {
      this.instanceRoot = instanceRoot;
   }
   
   public String getInstallRoot()
   {
      return installRoot;
   }
   
   public void setInstallRoot(String installRoot)
   {
      this.installRoot = installRoot;
   }
   
   /**
    * @return the configurationReadOnly
    */
   public boolean isConfigurationReadOnly()
   {
      return configurationReadOnly;
   }
   
   /**
    * @param configurationReadOnly the configurationReadOnly to set
    */
   public void setConfigurationReadOnly(boolean configurationReadOnly)
   {
      this.configurationReadOnly = configurationReadOnly;
   }

   public String getConfigurationXml()
   {
      return configurationXml;
   }

   public void setConfigurationXml(String configurationXml)
   {
      this.configurationXml = configurationXml;
   }

   public List<String> getSunResourcesXml()
   {
       if(sunResourcesXml == null){
           return Collections.emptyList();
       }
       List<String> resources = new ArrayList<String>();
       for(String resource : sunResourcesXml.split(","))
       {
           resources.add(resource.trim());
       }
      return resources;
   }

   public void setSunResourcesXml(String sunResourcesXml)
   {
      this.sunResourcesXml = sunResourcesXml;
   }

   /**
    * A utility class that encapsulates the logic to verify the properties of a
    * {@link GlassFishConfiguration} object. The class also processes and
    * converts the values of any properties into a form expected by the embedded
    * Glassfish runtime.
    * 
    * This class is nested, as it is should access and mutate the current
    * {@link GlassFishConfiguration} object. Returning a new
    * GlassfishConfiguration object is out of question, as we cannot assign a
    * new {@link GlassFishConfiguration} instance to itself, viz.
    * {@link GlassFishConfiguration#validate()} should mutate the config, or
    * delegate the mutation to an inner class - {@link GlassFishConfigProcessor}
    * in this case.
    * 
    * The class is not static, as we need to access the instance members of the
    * {@link GlassFishConfiguration} object.
    * 
    * The class is not private, for unit-tests to access it, if needed.
    * 
    * @author Vineet Reynolds
    * 
    */
   class GlassFishConfigProcessor
   {

      /**
       * Processes a {@link GlassfishConfiguration} object, to ensure that
       * configuration properties have valid values. Values may also be
       * converted from a canonical form specified in arquillian.xml, to a form
       * expected by the Glassfish container.
       * 
       * <b>Note:</b> This method is mutating by nature, and will possibly
       * modify the enclosing {@link GlassFishConfiguration} instance.
       * 
       * @throws RuntimeException
       *            when an invalid value is specified for a property in the
       *            Glassfish configuration.
       */
      public void processConfig() throws RuntimeException
      {
         if (installRoot != null)
         {
            verifyInstallRoot(installRoot);
         }
         if (instanceRoot != null)
         {
            if (configurationXml != null)
            {
               verifyInstanceRoot(instanceRoot, false);
            }
            else
            {
               verifyInstanceRoot(instanceRoot, true);
            }
         }
         if (configurationXml != null)
         {
            verifyConfigurationXml(configurationXml);
            /*
             * Convert the configurationXml property from
             * an abstract file path (/opt/glassfish3/... or C:\\glassfish3\... or
             * C:/glassfish3/...) to a file URI (file:/...).
             * This will mutate the GlassFishConfiguration object.
             */
            URI configurationXmlURI = convertFilePathToURI(configurationXml);
            configurationXml = configurationXmlURI.toString();
         }

         List<String> sunResourcesXml = getSunResourcesXml();
         if (sunResourcesXml.size() > 0)
         {
            for(String sunResourceXml : sunResourcesXml)
            {
               verifySunResourcesXml(sunResourceXml);
            }
         }
      }

      /**
       * Verifies whether the installRoot is a valid Glassfish installation
       * root. In Glassfish 3.1, an installRoot should ideally contain the
       * domains and lib sub-directories.
       * 
       * @param installRoot
       *           The location of the installRoot
       */
      private void verifyInstallRoot(String installRoot)
      {
         File installRootPath = new File(installRoot);
         if (installRootPath.isDirectory())
         {
            File[] requiredDirs = installRootPath.listFiles(new InstallRootFilter());
            if (requiredDirs.length != 2)
            {
               throw new RuntimeException(
                     "The Glassfish installRoot directory does not appear to be valid. It does not contain the 'domains' and 'lib' sub-directories.");
            }
         }
         else
         {
            throw new RuntimeException("The installRoot property should be a directory. Instead, it was a file.");
         }
      }

      /**
       * Verifies whether the instanceRoot is a valid Glassfish instance root.
       * In Glassfish 3.1, an instanceRoot should ideally contain the docroot
       * and config sub-directories. Also, if the config sub-directory contains
       * the domain.xml file, then a warning is to be logged when the
       * {@code ignoreConfigXml} parameter is false.
       * 
       * @param instanceRoot
       *           The location of the Glassfish instanceRoot.
       * @param ignoreConfigXml
       *           Should the presence of a domain.xml file in the config
       *           directory be ignored? This is meant to be <code>false</code>
       *           when the <code>configurationXml</code> property in
       *           <code>arquillian.xml</code> is provided with a value.
       */
      private void verifyInstanceRoot(String instanceRoot, boolean ignoreConfigXml)
      {
         File instanceRootPath = new File(instanceRoot);
         if (instanceRootPath.isDirectory())
         {
            File[] requiredDirs = instanceRootPath.listFiles(new InstanceRootFilter(ignoreConfigXml));
            if (requiredDirs.length != 2)
            {
               throw new RuntimeException(
                     "The Glassfish instanceRoot directory does not appear to be valid. It should contain the 'config' and 'docroot' sub-directories. The 'config' sub-directory must also contain a domain.xml file, if the configurationXml property is ommitted from the arquillian config. Other files specified in domain.xml may also be required for initializing the Glassfish runtime.");
            }
         }
         else
         {
            throw new RuntimeException("The instanceRoot property should be a directory. Instead, it was a file.");
         }
      }

      /**
       * Verifies the location of the configurationXml file. Also attempts to
       * convert the file path to a URI, so that this conversion may latter be
       * performed.
       * 
       * @param configurationXml
       *           The location of the configurationXml file.
       */
      private void verifyConfigurationXml(String configurationXml)
      {
         try
         {
            File configXmlPath = new File(configurationXml);
            if (!configXmlPath.exists())
            {
               throw new RuntimeException("The configurationXml property does not appear to be a valid file path.");
            }
            URI configXmlURI = configXmlPath.toURI();
            if (!configXmlURI.isAbsolute())
            {
               throw new RuntimeException("The configurationXml property should contain a URI scheme.");
            }
         }
         catch (IllegalArgumentException argEx)
         {
            throw new RuntimeException(
                  "A valid URI could not be composed from the provided configurationXml property.", argEx);
         }
      }

      /**
       * Verifies whether the value of the <code>sunResourcesXml</code> property
       * is a valid file path.
       * 
       * @param sunResourcesXml
       *           The file path to the <code>sun-resources.xml</code> file,
       *           that is later used in an <code>asadmin add-resources</code>
       *           command.
       */
      private void verifySunResourcesXml(String sunResourcesXml)
      {
         File sunResourcesXmlPath = new File(sunResourcesXml);
         if (!sunResourcesXmlPath.exists())
         {
            throw new RuntimeException("The sunResourcesXml property does not appear to be a valid file path.");
         }
      }

      /**
       * Converts a file path to a {@link URI} (usually with a
       * <code>file:</code> scheme).
       * 
       * @param path
       *           The filepath to be converted
       * @return A URI representing the file path
       */
      private URI convertFilePathToURI(String path)
      {
         File filePath = new File(path);
         URI filepathURI = filePath.toURI();
         return filepathURI;
      }

   }

}

/**
 * A {@link FileFilter} that is used to verify whether a directory contains the
 * <code>domains</code> and <code>lib</code> sub-directories that is typical of
 * a Glassfish installation root.
 * 
 * @author Vineet Reynolds
 * 
 */
class InstallRootFilter implements FilenameFilter
{

   /**
    * Tests whether the {@link name} parameter refers to the
    * <code>domains</code> or <code>lib</code> sub-directories.
    */
   @Override
   public boolean accept(File dir, String name)
   {
      return (name.equals("domains") || name.equals("lib"));
   }

}

/**
 * A {@link FileFilter} that is used to verify whether a directory contains the
 * <code>config</code> and <code>docroot</code> sub-directories that is typical
 * of a Glassfish instance root.
 * 
 * @author Vineet Reynolds
 * 
 */
class InstanceRootFilter implements FileFilter
{
   private static final Logger logger = Logger.getLogger(InstanceRootFilter.class.getName());

   /**
    * Specifies whether the test for <code>domain.xml</code> file in a
    * <code>config</code> sub-directory should be ignored. This is true, when
    * the
    * <code>instanceRoot<code> property is specified in arquillian.xml, but not
    * <code>configurationXml</code>. When both properties are specified, we
    * would want to warn the user that his configurationXml file might be
    * ignored by embedded Glassfish.
    */
   private boolean ignoreConfigXml;

   public InstanceRootFilter(boolean ignoreConfigXml)
   {
      this.ignoreConfigXml = ignoreConfigXml;
   }

   /**
    * Tests whether the {@link pathname} parameter refers to the
    * <code>docroot</code> or <code>config</code> sub-directories. Also logs a
    * warning when the <code>config</code> sub-directory contains a
    * <code>domain.xml</code> file and the {@link ignoreConfigXml} attribute is
    * false.
    */
   @Override
   public boolean accept(File pathname)
   {
      if (pathname.getName().equals("docroot"))
      {
         return true;
      }
      if (pathname.getName().equals("config") && pathname.isDirectory())
      {
         if (ignoreConfigXml)
         {
            return true;
         }
         else
         {
            List<String> filesInConfigDir = Arrays.asList(pathname.list());
            if (filesInConfigDir.contains("domain.xml"))
            {
               logger.warning("A domain.xml file was found in the instanceRoot. The file specified in the configurationXml property of arquillian.xml might be ignored by embedded Glassfish.");
            }
            return true;
         }
      }
      return false;
   }

}
