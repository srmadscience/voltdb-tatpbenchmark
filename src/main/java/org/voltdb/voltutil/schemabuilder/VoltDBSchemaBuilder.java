/**
 * Copyright (c) 2015-2019 YCSB contributors. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package org.voltdb.voltutil.schemabuilder;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.voltdb.client.Client;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcCallException;

/**
 * Utility class to build a schema.
 * 
 */
public final class VoltDBSchemaBuilder {

  private static final String PROCEDURE = "Procedure ";
  private static final String WAS_NOT_FOUND = " was not found";

  private final String jarFileName = "ycsb-procs.jar";

  private Logger logger = LoggerFactory.getLogger(VoltDBSchemaBuilder.class);

  Client voltClient;

  private String[] ddlStatements;

  private String[] procStatements;

  private String[] jarFiles;
  
  String procPackageName;

  /**
   * Utility class to build the schema.
   * 
   * @author srmadscience / VoltDB
   *
   */
  public VoltDBSchemaBuilder(String[] ddlStatements, String[] procStatements, String jarFileName, Client voltClient, String procPackageName) {
    super();
    this.ddlStatements = ddlStatements;
    this.procStatements = procStatements;
    this.voltClient = voltClient;
    this.procPackageName = procPackageName;
    
    jarFiles = makeJarFiles(procStatements);
  }

  private String[] makeJarFiles(String[] procStatements) {

    ArrayList<String> jarFileAL = new ArrayList<String>();

    for (int i = 0; i < procStatements.length; i++) {

       String thisProcStringNoNewLines = procStatements[i].toUpperCase().replace(System.lineSeparator(), " ");

      if (thisProcStringNoNewLines.indexOf(" FROM CLASS ") > -1) {
        String[] thisProcStringAsWords = procStatements[i].replace(".", " ").split(" ");

        if (thisProcStringAsWords[thisProcStringAsWords.length - 1].endsWith(";")) {
          jarFileAL.add(thisProcStringAsWords[thisProcStringAsWords.length - 1].replace(";", ""));

        } else {
          logger.error("Parsing of '" + procStatements[i] + "' went wrong; can't find proc name");
        }

      }
    }

    String[] jarFileList = new String[jarFileAL.size()];
    jarFileList = jarFileAL.toArray(jarFileList);

    return jarFileList;
  }

  /**
   * See if we think Schema already exists...
   * 
   * @return true if the 'Get' procedure exists and takes one string as a
   *         parameter.
   */
  public boolean schemaExists(String testProcName, Object[] testParams) {

    boolean schemaExists = false;

    try {
      ClientResponse response = voltClient.callProcedure(testProcName, testParams);

      if (response.getStatus() == ClientResponse.SUCCESS) {
        // Database exists...
        schemaExists = true;
      } else {
        // If we'd connected to a copy of VoltDB without the schema and tried to
        // call Get
        // we'd have got a ProcCallException
        logger.error("Error while calling schemaExists(): " + response.getStatusString());
        schemaExists = false;
      }
    } catch (ProcCallException pce) {
      schemaExists = false;

      // Sanity check: Make sure we've got the *right* ProcCallException...
      if (!pce.getMessage().equals(PROCEDURE + testProcName + WAS_NOT_FOUND)) {
        logger.error("Got unexpected Exception while calling schemaExists()", pce);
      }

    } catch (Exception e) {
      logger.error("Error while creating classes.", e);
      schemaExists = false;
    }

    return schemaExists;
  }

  /**
   * Load classes and DDL required by YCSB.
   * 
   * @throws Exception
   */
  public synchronized void loadClassesAndDDLIfNeeded(String testProcName, Object[] testParams) throws Exception {

    if (schemaExists(testProcName, testParams)) {
      return;
    }

    File tempDir = Files.createTempDirectory("voltdbSchema").toFile();

    if (!tempDir.canWrite()) {
      throw new Exception(
          "Temp Directory (from Files.createTempDirectory()) '" + tempDir.getAbsolutePath() + "' is not writable");
    }

    ClientResponse cr;

    for (int i = 0; i < ddlStatements.length; i++) {
      try {
        cr = voltClient.callProcedure("@AdHoc", ddlStatements[i]);
        if (cr.getStatus() != ClientResponse.SUCCESS) {
          throw new Exception("Attempt to execute '" + ddlStatements[i] + "' failed:" + cr.getStatusString());
        }
        System.out.println(ddlStatements[i]);
        logger.info(ddlStatements[i]);
      } catch (Exception e) {

        if (e.getMessage().indexOf("object name already exists") > -1) {
          // Someone else has done this...
          return;
        }

        throw (e);
      }
    }

    logger.info("Creating JAR file in " + tempDir + File.separator + jarFileName);
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    JarOutputStream newJarFile = new JarOutputStream(new FileOutputStream(tempDir + File.separator + jarFileName),
        manifest);

    for (int i = 0; i < jarFiles.length; i++) {
      InputStream is = getClass().getResourceAsStream("/" + procPackageName.replace(".", "/") + "/" + jarFiles[i] + ".class");
      System.out.println(procPackageName.replace(".", "/") + "/" + jarFiles[i]);
      add(procPackageName.replace(".", "/") + "/" + jarFiles[i]+ ".class", is, newJarFile);
    }

    newJarFile.close();
    File file = new File(tempDir + File.separator + jarFileName);

    byte[] jarFileContents = new byte[(int) file.length()];
    FileInputStream fis = new FileInputStream(file);
    fis.read(jarFileContents);
    fis.close();
    logger.info("Calling @UpdateClasses to load JAR file containing procedures");

    cr = voltClient.callProcedure("@UpdateClasses", jarFileContents, null);
    if (cr.getStatus() != ClientResponse.SUCCESS) {
      throw new Exception("Attempt to execute UpdateClasses failed:" + cr.getStatusString());
    }

    for (int i = 0; i < procStatements.length; i++) {
      logger.info(procStatements[i]);
      cr = voltClient.callProcedure("@AdHoc", procStatements[i]);
      if (cr.getStatus() != ClientResponse.SUCCESS) {
        throw new Exception("Attempt to execute '" + procStatements[i] + "' failed:" + cr.getStatusString());
      }
    }

  }

  /**
   * Add an entry to our JAR file.
   * 
   * @param fileName
   * @param source
   * @param target
   * @throws IOException
   */
  private void add(String fileName, InputStream source, JarOutputStream target) throws IOException {
    BufferedInputStream in = null;
    try {

      JarEntry entry = new JarEntry(fileName.replace("\\", "/"));
      entry.setTime(System.currentTimeMillis());
      target.putNextEntry(entry);
      in = new BufferedInputStream(source);

      byte[] buffer = new byte[1024];
      while (true) {
        int count = in.read(buffer);
        if (count == -1) {
          break;
        }

        target.write(buffer, 0, count);
      }
      target.closeEntry();
    } finally {
      if (in != null) {
        in.close();
      }

    }
  }

}
