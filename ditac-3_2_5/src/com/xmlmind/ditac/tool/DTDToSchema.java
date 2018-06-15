/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.tool;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import com.xmlmind.util.ThrowableUtil;
import com.xmlmind.util.StringList;
import com.xmlmind.util.FileUtil;
import com.xmlmind.util.XMLUtil;
import com.xmlmind.util.XMLText;

/**
 * Command-line tool allowing to "upgrade" DITA documents conforming 
 * to a standard DITA 1.3 DTD to the corresponding W2C XML schema or 
 * RELAX NG schema.
 */
public final class DTDToSchema {
    public static void main(String[] args) {
        int argCount = args.length;
        if (argCount < 2) {
            usage(null);
        }

        boolean xsd = false;
        if ("-rng".equals(args[0])) {
            xsd = false;
        } else if ("-xsd".equals(args[0])) {
            xsd = true;
        } else {
            usage(null);
        }

        for (int i = 1; i < argCount; ++i) {
            File inFile = new File(args[i]);

            if (inFile.exists()) {
                try {
                    if (inFile.isDirectory()) {
                        processDir(xsd, inFile);
                    } else {
                        processFile(xsd, inFile);
                    }
                } catch (IOException e) {
                    System.err.println("Cannot process '" + inFile + 
                                       "': " + ThrowableUtil.reason(e));
                    System.exit(2);
                }
            } else {
                usage("'" + args[i] + "', not a file or directory");
            }
        }

    }

    private static void usage(String error) {
        if (error != null) {
            System.err.println("*** error: " + error);
        }

        System.err.println(
        "Usage: java -cp ditac.jar com.xmlmind.ditac.tool.DTDToSchema\n" +
        "  -rng|-xsd [ in_dita_file|in_dir_containing_dita_files ]+\n" +
        "\"Upgrade\" specified DITA documents conforming to a standard\n" +
        "DITA 1.3 DTD to the corresponding W3C XML schema or\n" +
        "RELAX NG schema.\n" +
        "Processes files or directories. Files are modified in place.\n" +
        "Directories are recursively processed. All the '.ditamap', '.dita'\n" +
        "and '.ditaval' files found in specified directories are processed.\n" +
        "Options:\n" +
        "-rng Upgrade to RELAX NG schema.\n" +
        "-xsd Upgrade to W3C XML schema.");

        System.exit(1);
    }

    private static void processDir(boolean xsd, File inDir) 
        throws IOException {
        System.out.println("Processing DITA files in '" + inDir + "'...");

        File[] inFiles = FileUtil.checkedListFiles(inDir);
        if (inFiles != null) {
            for (File inFile : inFiles) {
                if (inFile.isDirectory()) {
                    processDir(xsd, inFile);
                } else {
                    String ext = FileUtil.getExtension(inFile);
                    if (ext != null &&
                        (ext.equalsIgnoreCase("dita") ||
                         ext.equalsIgnoreCase("ditamap") ||
                         ext.equalsIgnoreCase("ditaval"))) {
                        processFile(xsd, inFile);
                    }
                }
            }
        }
    }

    private static final String[] DTD_PUBLIC_IDS = {
        "-//OASIS//DTD DITA 1.3 Machinery Task//EN",
        "-//OASIS//DTD DITA Machinery Task//EN",

        "-//OASIS//DTD DITA 1.3 DITAVAL//EN",
        "-//OASIS//DTD DITA DITAVAL//EN",

        "-//OASIS//DTD DITA 1.3 Concept//EN",
        "-//OASIS//DTD DITA Concept//EN",

        "-//OASIS//DTD DITA 1.3 Composite//EN",
        "-//OASIS//DTD DITA Composite//EN",

        "-//OASIS//DTD DITA 1.3 General Task//EN",
        "-//OASIS//DTD DITA General Task//EN",

        "-//OASIS//DTD DITA 1.3 Glossary//EN",
        "-//OASIS//DTD DITA Glossary//EN",

        "-//OASIS//DTD DITA 1.3 Glossary Entry//EN",
        "-//OASIS//DTD DITA Glossary Entry//EN",

        "-//OASIS//DTD DITA 1.3 Glossary Group//EN",
        "-//OASIS//DTD DITA Glossary Group//EN",

        "-//OASIS//DTD DITA 1.3 Map//EN",
        "-//OASIS//DTD DITA Map//EN",

        "-//OASIS//DTD DITA 1.3 Reference//EN",
        "-//OASIS//DTD DITA Reference//EN",

        "-//OASIS//DTD DITA 1.3 Task//EN",
        "-//OASIS//DTD DITA Task//EN",

        "-//OASIS//DTD DITA 1.3 Topic//EN",
        "-//OASIS//DTD DITA Topic//EN",

        "-//OASIS//DTD DITA 1.3 Troubleshooting//EN",
        "-//OASIS//DTD DITA Troubleshooting//EN",

        "-//OASIS//DTD DITA 1.3 BookMap//EN",
        "-//OASIS//DTD DITA BookMap//EN",

        "-//OASIS//DTD DITA 1.3 Base Map//EN",
        "-//OASIS//DTD DITA Base Map//EN",

        "-//OASIS//DTD DITA 1.3 Base Topic//EN",
        "-//OASIS//DTD DITA Base Topic//EN",

        "-//OASIS//DTD DITA 1.3 Classification Map//EN",
        "-//OASIS//DTD DITA Classification Map//EN",

        "-//OASIS//DTD DITA 1.3 Subject Scheme Map//EN",
        "-//OASIS//DTD DITA Subject Scheme Map//EN"
    };

    private static final String[] XSD_LOCATIONS = {
        "urn:oasis:names:tc:dita:spec:machinery:xsd:machinerytask.xsd:1.3",
        "urn:oasis:names:tc:dita:spec:machinery:xsd:machinerytask.xsd",

        "urn:oasis:names:tc:dita:xsd:ditaval.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:ditaval.xsd",

        "urn:oasis:names:tc:dita:xsd:concept.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:concept.xsd",

        "urn:oasis:names:tc:dita:xsd:ditabase.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:ditabase.xsd",

        "urn:oasis:names:tc:dita:xsd:generalTask.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:generalTask.xsd",

        "urn:oasis:names:tc:dita:xsd:glossary.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:glossary.xsd",

        "urn:oasis:names:tc:dita:xsd:glossentry.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:glossentry.xsd",

        "urn:oasis:names:tc:dita:xsd:glossgroup.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:glossgroup.xsd",

        "urn:oasis:names:tc:dita:xsd:map.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:map.xsd",

        "urn:oasis:names:tc:dita:xsd:reference.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:reference.xsd",

        "urn:oasis:names:tc:dita:xsd:task.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:task.xsd",

        "urn:oasis:names:tc:dita:xsd:topic.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:topic.xsd",

        "urn:oasis:names:tc:dita:xsd:troubleshooting.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:troubleshooting.xsd",

        "urn:oasis:names:tc:dita:xsd:bookmap.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:bookmap.xsd",

        "urn:oasis:names:tc:dita:xsd:basemap.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:basemap.xsd",

        "urn:oasis:names:tc:dita:xsd:basetopic.xsd:1.3",
        "urn:oasis:names:tc:dita:xsd:basetopic.xsd",

        "urn:oasis:names:tc:dita:spec:classification:xsd:classifyMap.xsd:1.3",
        "urn:oasis:names:tc:dita:spec:classification:xsd:classifyMap.xsd",

        "urn:oasis:names:tc:dita:spec:classification:xsd:subjectScheme.xsd:1.3",
        "urn:oasis:names:tc:dita:spec:classification:xsd:subjectScheme.xsd"
    };

    private static final String[] RNG_HREFS = {
        "urn:oasis:names:tc:dita:spec:machinery:rng:machinerytask.rng:1.3",
        "urn:oasis:names:tc:dita:spec:machinery:rng:machinerytask.rng",

        "urn:oasis:names:tc:dita:rng:ditaval.rng:1.3",
        "urn:oasis:names:tc:dita:rng:ditaval.rng",

        "urn:oasis:names:tc:dita:rng:concept.rng:1.3",
        "urn:oasis:names:tc:dita:rng:concept.rng",

        "urn:oasis:names:tc:dita:rng:ditabase.rng:1.3",
        "urn:oasis:names:tc:dita:rng:ditabase.rng",

        "urn:oasis:names:tc:dita:rng:generalTask.rng:1.3",
        "urn:oasis:names:tc:dita:rng:generalTask.rng",

        "urn:oasis:names:tc:dita:rng:glossary.rng:1.3",
        "urn:oasis:names:tc:dita:rng:glossary.rng",

        "urn:oasis:names:tc:dita:rng:glossentry.rng:1.3",
        "urn:oasis:names:tc:dita:rng:glossentry.rng",

        "urn:oasis:names:tc:dita:rng:glossgroup.rng:1.3",
        "urn:oasis:names:tc:dita:rng:glossgroup.rng",

        "urn:oasis:names:tc:dita:rng:map.rng:1.3",
        "urn:oasis:names:tc:dita:rng:map.rng",

        "urn:oasis:names:tc:dita:rng:reference.rng:1.3",
        "urn:oasis:names:tc:dita:rng:reference.rng",

        "urn:oasis:names:tc:dita:rng:task.rng:1.3",
        "urn:oasis:names:tc:dita:rng:task.rng",

        "urn:oasis:names:tc:dita:rng:topic.rng:1.3",
        "urn:oasis:names:tc:dita:rng:topic.rng",

        "urn:oasis:names:tc:dita:rng:troubleshooting.rng:1.3",
        "urn:oasis:names:tc:dita:rng:troubleshooting.rng",

        "urn:oasis:names:tc:dita:rng:bookmap.rng:1.3",
        "urn:oasis:names:tc:dita:rng:bookmap.rng",

        "urn:oasis:names:tc:dita:rng:basemap.rng:1.3",
        "urn:oasis:names:tc:dita:rng:basemap.rng",

        "urn:oasis:names:tc:dita:rng:basetopic.rng:1.3",
        "urn:oasis:names:tc:dita:rng:basetopic.rng",

        "urn:oasis:tc:tc:dita:spec:classification:rng:classifyMap.rng:1.3",
        "urn:oasis:tc:tc:dita:spec:classification:rng:classifyMap.rng",

        "urn:oasis:names:tc:dita:spec:classification:rng:subjectScheme.rng:1.3",
        "urn:oasis:names:tc:dita:spec:classification:rng:subjectScheme.rng"
    };

    private static void processFile(boolean xsd, File inFile)
        throws IOException {
        System.out.println("Processing '" + inFile + "'...");
        
        String[] encoding = new String[1];
        String source = loadXML(inFile, encoding);
        int sourceLength = source.length();

        // ---

        int docTypeStart = source.indexOf("<!DOCTYPE");
        int docTypeEnd = -1;
        if (docTypeStart >= 0) {
            docTypeEnd = source.indexOf('>', docTypeStart+9);
            if (docTypeEnd >= 0) {
                // May be '>' is in the middle of an internal subset.
                //
                // Example:
                //
                // <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN"
                // "http://docs.oasis-open.org/dita/dtd/topic.dtd" [
                // <!ENTITY nbsp "&#160;"> 
                // ]>
                //
                // Skip DITA file having a DTD internal subset.
                int pos = source.lastIndexOf('[', docTypeEnd);
                if (pos >= 0) {
                    pos = source.indexOf(']', pos+1);
                    if (pos >= 0) {
                        docTypeEnd = source.indexOf('>', pos+1);
                        if (docTypeEnd >= 0) {
                            System.out.println("DTD has an internal subset;" +
                                               " skipping '" + inFile + "'.");
                            return;
                        }
                    }
                }
            }
        }

        if (docTypeEnd < 0) {
            System.out.println("No <!DOCTYPE>; skipping '" + inFile + "'.");
            return;
        }
        ++docTypeEnd; // After '>'.

        // ---

        String docType = source.substring(docTypeStart, docTypeEnd);
        /*
        System.out.println("Found " + docType + " in '" + inFile + "'...");
        */

        int publicIdStart = docType.indexOf("PUBLIC");
        int publicIdEnd = -1;
        if (publicIdStart >= 0) {
            int docTypeLength = docType.length();
            int pos = publicIdStart+6;
            while (pos < docTypeLength && 
                   XMLText.isXMLSpace(docType.charAt(pos))) {
                ++pos;
            }

            char quote = '\0';
            if (pos < docTypeLength) {
                quote = docType.charAt(pos);
                if (quote != '"' && quote != '\'') {
                    quote = '\0';
                }
            }

            if (quote != '\0') {
                publicIdStart = pos+1; // After start quote.

                pos = docType.indexOf(quote, publicIdStart);
                if (pos >= 0) {
                    publicIdEnd = pos; // At end quote.
                }
            }
        }

        if (publicIdEnd < 0) {
            System.out.println("No DTD PUBLIC ID; skipping '" + inFile + "'.");
            return;
        }

        String publicId = docType.substring(publicIdStart, publicIdEnd);
        /*
        System.out.println("Replacing '" + publicId + "' in '" + 
                           inFile + "'...");
        */

        int schemaIndex = StringList.indexOf(DTD_PUBLIC_IDS, publicId);
        if (schemaIndex < 0) {
            System.out.println("'" + publicId + 
                               "', unknown DTD PUBLIC ID; skipping '" + 
                               inFile + "'.");
            return;
        }

        // ---
        
        int afterStartTag = -1;

        if (xsd) {
            int close = docTypeEnd; // After '>'.

            for (;;) {
                int open = source.indexOf('<', close);
                if (open >= 0 && open+1 < sourceLength) {
                    char openChar = source.charAt(open+1);
                    if (openChar == '!') {
                        close = source.indexOf("-->", open+2);
                        if (close < 0) {
                            // Give up.
                            break;
                        }
                        close += 3;
                    } else if (openChar == '?') {
                        close = source.indexOf("?>", open+2);
                        if (close < 0) {
                            // Give up.
                            break;
                        }
                        close += 2;
                    } else if (XMLText.isNameChar(openChar)) {
                        open += 2;
                        while (open < sourceLength && 
                               XMLText.isNameChar(source.charAt(open))) {
                            ++open;
                        }

                        if (source.indexOf('>', open) >= 0) {
                            afterStartTag = open;
                        }

                        // Done.
                        break;
                    } else {
                        // Give up.
                        break;
                    }
                } else {
                    // Give up.
                    break;
                }
            }

            if (afterStartTag < 0) {
                System.out.println("No root element; skipping '" + 
                                   inFile + "'.");
                return;
            }
        }

        // ---

        StringBuilder buffer = new StringBuilder(source);

        if (xsd) {
            buffer.insert(
                afterStartTag, 
                " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                " xsi:noNamespaceSchemaLocation=\"" + 
                XSD_LOCATIONS[schemaIndex] + "\"");
        }

        buffer.delete(docTypeStart, docTypeEnd);

        if (xsd) {
            while (XMLText.isXMLSpace(buffer.charAt(docTypeStart))) {
                buffer.deleteCharAt(docTypeStart);
            }
        } else {
            buffer.insert(docTypeStart, 
                          "<?xml-model href=\"" + 
                          RNG_HREFS[schemaIndex] + "\"?>");
        }

        // ---

        File tempFile = new File(inFile.getPath() + 
                                 (xsd? "_XSD.TEMP" : "_RNG.TEMP"));
        FileUtil.saveString(buffer.toString(), tempFile, encoding[0]);

        File bakFile = new File(inFile.getPath() + "_DTD.BAK");
        FileUtil.checkedRename(inFile, bakFile);

        FileUtil.checkedRename(tempFile, inFile);
    }

    private static String loadXML(File file, String[] encoding) 
        throws IOException {
        String loaded = null;

        FileInputStream in = new FileInputStream(file);
        try {
            loaded = XMLUtil.loadText(in, /*fallbackEncoding*/ "UTF-8", 
                                      encoding);
        } finally {
            in.close();
        }

        return loaded;
    }
}
