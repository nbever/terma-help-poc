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
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ServiceLoader;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.InputSource;
import org.xml.sax.EntityResolver;
import org.xml.sax.XMLReader;
import org.relaxng.datatype.DatatypeLibraryFactory;
import com.thaiopensource.util.PropertyMap;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.xml.sax.XMLReaderCreator;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.IncorrectSchemaException;
import com.thaiopensource.validate.Schema;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.Validator;
import com.thaiopensource.relaxng.impl.RngProperty;
import com.thaiopensource.relaxng.impl.CompactSchemaReader;
import com.thaiopensource.relaxng.impl.SAXSchemaReader;
import com.xmlmind.util.ThrowableUtil;
import com.xmlmind.util.URIComponent;
import com.xmlmind.util.URLUtil;
import com.xmlmind.util.XMLUtil;
import com.xmlmind.util.Console;

/*package*/ final class RNGSchema {
    private static final HashMap<URL,RNGSchema> cache = 
        new HashMap<URL,RNGSchema>();

    public static RNGSchema get(XMLModel rngInfo, Console console)
        throws IOException {
        synchronized (cache) {
            RNGSchema rngSchema = cache.get(rngInfo.url);
            if (rngSchema == null) {
                Schema schema = loadSchema(rngInfo, console);

                RNGAttributeDefaultValues attributeDefaultValues;
                try {
                    attributeDefaultValues = 
                        new RNGAttributeDefaultValues(schema);
                } catch (Exception e) {
                    throw new IOException(Msg.msg("incorrectRNGSchema2",
                                                  URLUtil.toLabel(rngInfo.url),
                                                  ThrowableUtil.reason(e)));
                }

                rngSchema = new RNGSchema(schema, attributeDefaultValues);
                cache.put(rngInfo.url, rngSchema);
            }

            return rngSchema;
        }
    }

    // -----------------------------------------------------------------------

    public final Schema schema;
    public final RNGAttributeDefaultValues attributeDefaultValues;

    private RNGSchema(Schema schema,
                      RNGAttributeDefaultValues attributeDefaultValues) {
        this.schema = schema;
        this.attributeDefaultValues = attributeDefaultValues;
    }

    public void validate(URL docURL, Console console) 
        throws IOException {
        PropertyMapBuilder props = new PropertyMapBuilder();
        LoadErrorHandler errorHandler = new LoadErrorHandler(console);
        ValidateProperty.ERROR_HANDLER.put(props, errorHandler);
        // At least check duplicate topic IDs.
        RngProperty.CHECK_ID_IDREF.add(props);
        Validator validator = schema.createValidator(props.toPropertyMap());
        
        try {
            XMLReader xmlReader = XMLUtil.newSAXParser().getXMLReader();
            xmlReader.setContentHandler(validator.getContentHandler());

            xmlReader.parse(docURL.toExternalForm());
        } catch (ParserConfigurationException e) {
            throw new IOException(ThrowableUtil.reason(e));
        } catch (SAXParseException e) {
            throw new IOException(LoadErrorHandler.format(e, null));
        } catch (SAXException e) {
            throw new IOException(ThrowableUtil.reason(e));
        }

        int errorCount = errorHandler.getErrorCount();
        if (errorCount > 0) {
            throw new IOException(Msg.msg("hasValidationErrors", 
                                          URLUtil.toLabel(docURL), errorCount));
        }
    }

    // -----------------------------------------------------------------------
    // loadSchema
    // -----------------------------------------------------------------------

    private static final class EntityResolverImpl implements EntityResolver {
        public InputSource resolveEntity(String publicId, String systemId) {
            String resolved = Resolve.resolveURI(systemId);
            if (resolved == null) {
                resolved = systemId;
            }
            resolved = URIComponent.encode(resolved);

            return new InputSource(resolved); 
        }
    }
    private static final EntityResolverImpl RESOLVER_INSTANCE = 
        new EntityResolverImpl();

    private static final class XMLReaderCreatorImpl
                         implements XMLReaderCreator {
        public XMLReader createXMLReader() 
            throws SAXException {
            try {
                XMLReader xmlReader = XMLUtil.newSAXParser().getXMLReader();
                xmlReader.setEntityResolver(RESOLVER_INSTANCE);

                return xmlReader;
            } catch (ParserConfigurationException e) {
                throw new SAXException(e);
            }
        }
    }
    private static final XMLReaderCreatorImpl READER_CREATOR_INSTANCE =
        new XMLReaderCreatorImpl();

    private static final DatatypeLibraryFactory[] DATATYPE_LIBRARY_FACTORY =
        new DatatypeLibraryFactory[1];

    private static final DatatypeLibraryFactory getDatatypeLibraryFactory() {
        synchronized (DATATYPE_LIBRARY_FACTORY) {
            if (DATATYPE_LIBRARY_FACTORY[0] == null) {
                try {
                    ServiceLoader<DatatypeLibraryFactory> serviceLoader = 
                        ServiceLoader.load(DatatypeLibraryFactory.class);
                    Iterator<DatatypeLibraryFactory> iter = 
                        serviceLoader.iterator();
                    if (iter.hasNext()) {
                        DATATYPE_LIBRARY_FACTORY[0] = iter.next();

                        /*
                        System.err.println(
                            "Found implementation of" +
                            " org.relaxng.datatype.DatatypeLibraryFactory: " + 
                            DATATYPE_LIBRARY_FACTORY[0].getClass().getName());
                        */
                    } else {
                        // Fallback ---

                        String className =
                            "com.thaiopensource.datatype.xsd" +
                            ".DatatypeLibraryFactoryImpl";

                        /*
                        System.err.println(
                            "Using fallback implementation of" +
                            " org.relaxng.datatype.DatatypeLibraryFactory: " + 
                            className);
                        */

                        DATATYPE_LIBRARY_FACTORY[0] = (DatatypeLibraryFactory)
                            Class.forName(className)
                            .getDeclaredConstructor().newInstance();
                    }
                } catch (Throwable shouldNotHappen) {
                    System.err.println(
                        "*** Internal error: missing relaxng.jar?" +
                        " don't find an implementation of" +
                        " org.relaxng.datatype.DatatypeLibraryFactory: " + 
                        ThrowableUtil.reason(shouldNotHappen));
                }
            }

            return DATATYPE_LIBRARY_FACTORY[0];
        }
    }

    private static Schema loadSchema(XMLModel rngInfo, Console console) 
        throws IOException {
        PropertyMapBuilder propsBuilder = new PropertyMapBuilder();
        RngProperty.DATATYPE_LIBRARY_FACTORY.put(propsBuilder,
                                                 getDatatypeLibraryFactory());
        LoadErrorHandler errorHandler = new LoadErrorHandler(console);
        ValidateProperty.ERROR_HANDLER.put(propsBuilder, errorHandler);
        ValidateProperty.XML_READER_CREATOR.put(propsBuilder, 
                                                READER_CREATOR_INSTANCE);
        // Check ID/IDREFs in the schema? At least check duplicate IDs.
        RngProperty.CHECK_ID_IDREF.add(propsBuilder);
        PropertyMap props = propsBuilder.toPropertyMap();

        SchemaReader schemaReader;
        boolean isRNC = rngInfo.isRNC();
        if (isRNC) {
            schemaReader = CompactSchemaReader.getInstance();
        } else {
            schemaReader = SAXSchemaReader.getInstance();
        }

        InputSource in = new InputSource(rngInfo.url.toExternalForm());
        if (isRNC &&  rngInfo.charset != null) {
            in.setEncoding(rngInfo.charset);
        }

        Schema schema = null;
        try {
            schema = schemaReader.createSchema(in, props);
        } catch (SAXParseException e) {
            throw new IOException(LoadErrorHandler.format(e, null));
        } catch (SAXException e) {
            throw new IOException(ThrowableUtil.reason(e));
        } catch (IncorrectSchemaException e) {
            throw new IOException(Msg.msg("incorrectRNGSchema",
                                          URLUtil.toLabel(rngInfo.url)));
        }

        // Use schema even it has (non fatal) errors.

        return schema;
    }
}
