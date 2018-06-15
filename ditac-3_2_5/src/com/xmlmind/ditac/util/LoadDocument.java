/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.File;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.BufferedInputStream;
import java.net.URL;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ErrorHandler;
import org.xml.sax.XMLReader;
import org.xml.sax.InputSource;
import org.w3c.dom.Document;
import com.xmlmind.util.ThrowableUtil;
import com.xmlmind.util.FileUtil;
import com.xmlmind.util.URLUtil;
import com.xmlmind.util.XMLUtil;
import com.xmlmind.util.Console;

public final class LoadDocument {
    private LoadDocument() {}

    // -----------------------------------------------------------------------

    private static boolean[] addElementPointer = new boolean[1];

    public static void setAddingElementPointer(boolean add) {
        synchronized (addElementPointer) {
            addElementPointer[0] = add;
        }
    }

    public static boolean isAddingElementPointer() {
        synchronized (addElementPointer) {
            return addElementPointer[0];
        }
    }

    // -----------------------------------------------------------------------

    private static SAXToDOMFactory[] saxToDOMFactory = new SAXToDOMFactory[] {
        SAXToDOMFactory.INSTANCE
    };

    public static void setSAXToDOMFactory(SAXToDOMFactory factory) {
        if (factory == null) {
            factory = SAXToDOMFactory.INSTANCE;
        }
        synchronized (saxToDOMFactory) {
            saxToDOMFactory[0] = factory;
        }
    }

    public static SAXToDOMFactory getSAXToDOMFactory() {
        synchronized (saxToDOMFactory) {
            return saxToDOMFactory[0];
        }
    }

    // -----------------------------------------------------------------------

    public static Document load(File file, boolean validate, Console console) 
        throws IOException {
        return load(FileUtil.fileToURL(file), validate, console);
    }

    public static Document load(URL url, boolean validate, Console console) 
        throws IOException {
        boolean validationRequested = validate;
        XMLModel[] rngInfo = new XMLModel[1];
        InputStream in = null;

        Document doc;
        try {
            in = detectRNG(url, rngInfo);
            if (rngInfo[0] != null) {
                // Do not let the XML parser attempt to valid the loaded doc.
                validate = false;
            }

            doc = load(in, url, validate, console);
        } finally {
            if (in != null) {
                in.close();
            }
        }

        if (rngInfo[0] != null) {
            applyRNG(rngInfo[0], doc, url, validationRequested, console);
        }

        return doc;
    }

    private static InputStream detectRNG(URL url, XMLModel[] rngInfo) 
        throws IOException {
        rngInfo[0] = null;

        byte[] bytes = new byte[8192];

        PushbackInputStream in = 
          new PushbackInputStream(URLUtil.openStreamNoCache(url), bytes.length);
        int byteCount = in.read(bytes, 0, bytes.length);

        String encoding =
            XMLUtil.detectEncoding(bytes, byteCount, /*bomLength*/ null);
        if (encoding == null) {
            encoding = "UTF-8";
        }

        String text = null;
        try {
            text = new String(bytes, 0, byteCount, encoding);
        } catch (UnsupportedEncodingException ignored) {}

        if (byteCount > 0) {
            in.unread(bytes, 0, byteCount);
        }

        int start = text.indexOf(XMLModel.PI_START);
        if (start >= 0) {
            start += XMLModel.PI_START.length();

            int end = text.indexOf("?>", start);
            if (end > start) {
                String error = "???";

                String piData = text.substring(start, end).trim();
                if (piData.length() > 0) {
                    try {
                        rngInfo[0] = XMLModel.parse(piData, url);
                    } catch (Exception e) {
                        error = ThrowableUtil.reason(e);
                    }
                }

                if (rngInfo[0] == null) {
                    throw new IOException(
                        Msg.msg("cannotParse", 
                                (XMLModel.PI_START + " " + piData + "?>"),
                                error));
                }

                if (!rngInfo[0].isRNG() && !rngInfo[0].isRNC()) {
                    rngInfo[0] = null;
                }
            }
        }

        return in;
    }

    private static void applyRNG(XMLModel rngInfo, Document doc, URL docURL, 
                                 boolean validate, Console console) 
        throws IOException {
        RNGSchema rngSchema = RNGSchema.get(rngInfo, console);

        if (validate) {
            // No efficient: docURL parsed a second time.
            rngSchema.validate(docURL, console);
        }

        rngSchema.attributeDefaultValues.addAll(doc.getDocumentElement());
    }
    
    // -----------------------------------------------------------------------

    private static Document load(InputStream in, URL url, 
                                 boolean validate, Console console) 
        throws IOException {
        Document doc;
        XMLReader parser;
        try {
            doc = DOMUtil.newDocument();

            parser = createSAXParser(validate);
        } catch (Exception shouldNotHappen) {
            throw new IOException(ThrowableUtil.reason(shouldNotHappen));
        }
        
        in = new BufferedInputStream(in);

        InputSource input = new InputSource(in);
        input.setSystemId(url.toExternalForm());

        SAXToDOM domBuilder = 
            getSAXToDOMFactory().createSAXToDOM(doc, isAddingElementPointer());
        parser.setContentHandler(domBuilder);

        ErrorHandler errorHandler;
        if (validate) {
            // Needed when setValidating(true) to report validation errors. 
            errorHandler = new LoadErrorHandler(console);
        } else {
            // Does not report anything.
            errorHandler = domBuilder;
        }
        parser.setErrorHandler(errorHandler);

        try {
            parser.parse(input);
        } catch (SAXParseException e) {
            throw new IOException(LoadErrorHandler.format(e, null));
        } catch (SAXException e) {
            throw new IOException(ThrowableUtil.reason(e));
        }

        int errorCount;
        if (validate && 
            (errorHandler instanceof LoadErrorHandler) && 
            (errorCount=((LoadErrorHandler)errorHandler).getErrorCount()) > 0) {
            throw new IOException(Msg.msg("hasValidationErrors", 
                                          URLUtil.toLabel(url), errorCount));
        }

        doc.setDocumentURI(url.toExternalForm());
        return doc;
    }

    private static XMLReader createSAXParser(boolean validate) {
        XMLReader parser = null;

        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();

            factory.setNamespaceAware(true);

            // We need attribute default values but it seems that we get
            // them even without turning validation on.
            factory.setValidating(validate);

            factory.setXIncludeAware(false);

            // We need the qNames.
            factory.setFeature(
                "http://xml.org/sax/features/namespace-prefixes", true);

            // Expand entities.
            factory.setFeature(
              "http://xml.org/sax/features/external-general-entities", true);
            factory.setFeature(
              "http://xml.org/sax/features/external-parameter-entities", true);

            factory.setFeature(
              "http://xml.org/sax/features/lexical-handler/parameter-entities",
              false);
            factory.setFeature(
              "http://xml.org/sax/features/resolve-dtd-uris", true);

            // For Xerces which otherwise, does not support "x-MacRoman".
            try {
                factory.setFeature(
                    "http://apache.org/xml/features/allow-java-encodings",
                    true);
            } catch (Exception ignored) {}

            // Without this feature, Xerces ignores
            // xsi:noNamespaceSchemaLocation.
            //
            // Now the question is: how Xerces resolves URIs such as
            // "urn:oasis:names:tc:dita:xsd:topicGrp.xsd:1.1"? 
            // Seems the EntityResolver specified below in order 
            // to resolve systemIds.
            try {
                factory.setFeature(
                    "http://apache.org/xml/features/validation/schema",
                    true);
            } catch (Exception ignored) {}

            parser = factory.newSAXParser().getXMLReader();
            parser.setEntityResolver(Resolve.createEntityResolver());
        } catch (Exception e) {
            throw new RuntimeException(Msg.msg("cannotCreateSAXParser", 
                                               ThrowableUtil.reason(e)));
        }

        return parser;
    }

    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.err.println(
                "usage: java com.xmlmind.ditac.util.LoadDocument" +
                " [-validate] in_xml_file out_xml_file");
            System.exit(1);
        }

        int i = 0;

        boolean validate = false;
        if ("-validate".equals(args[i])) {
            validate = true;
            ++i;
        }

        File inFile = new File(args[i++]);
        File outFile = new File(args[i++]);

        Document doc = LoadDocument.load(inFile, validate, null);
        SaveDocument.save(doc, outFile);
    }
}
