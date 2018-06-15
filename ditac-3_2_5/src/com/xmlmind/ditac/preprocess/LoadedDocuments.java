/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.preprocess;

import java.io.IOException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.HashMap;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import com.xmlmind.util.ObjectUtil;
import com.xmlmind.util.StringList;
import com.xmlmind.util.StringUtil;
import com.xmlmind.util.FileUtil;
import com.xmlmind.util.URIComponent;
import com.xmlmind.util.URLUtil;
import com.xmlmind.util.XMLText;
import com.xmlmind.util.Console;
import com.xmlmind.ditac.util.SimpleConsole;
import com.xmlmind.ditac.util.ConsoleHelper;
import com.xmlmind.ditac.util.LoadDocument;
import com.xmlmind.ditac.util.DOMUtil;
import com.xmlmind.ditac.util.DITAUtil;
import com.xmlmind.ditac.util.Resolve;
import static com.xmlmind.ditac.preprocess.CascadeMeta.TOPICMETA_ELEMENTS;
import static com.xmlmind.ditac.preprocess.CascadeMeta.CASCADED_ELEMENTS;
import static com.xmlmind.ditac.preprocess.CascadeMeta.CASCADED_ELEMENT_SINGLE;

/*package*/ class LoadedDocuments implements Constants {
    private KeySpaces keySpaces;
    private ConsoleHelper console;
    private boolean validate;
    private HashMap<URL,LoadedDocument> docs;
    private HashMap<URL,LoadedDocument> preloadedDocs;
    private String[] cascadingAttributes;

    // -----------------------------------------------------------------------

    public LoadedDocuments() {
        this(null, null);
    }

    public LoadedDocuments(KeySpaces keySpaces, Console console) {
        docs = new HashMap<URL,LoadedDocument>();
        preloadedDocs = new HashMap<URL,LoadedDocument>();
        setKeySpaces(keySpaces);
        setConsole(console);
    }

    /**
     * Specifies the key spaces object which is to be used to resolve conkeyrefs
     * and keyrefs. May be <code>null</code> in which case conkeyrefs and 
     * keyrefs are not processed at all.
     */
    public void setKeySpaces(KeySpaces keySpaces) {
        this.keySpaces = keySpaces;
    }

    /**
     * Returns the key spaces object which is to be used to resolve conkeyrefs
     * and keyrefs. May return <code>null</code>.
     */
    public KeySpaces getKeySpaces() {
        return keySpaces;
    }

    public void setConsole(Console c) {
        if (c == null) {
            c = new SimpleConsole();
        }
        this.console = ((c instanceof ConsoleHelper)? 
                        (ConsoleHelper) c : new ConsoleHelper(c));
    }

    public ConsoleHelper getConsole() {
        return console;
    }

    public void setValidating(boolean validate) {
        this.validate = validate;
    }

    public boolean isValidating() {
        return validate;
    }

    public  LoadedDocument load(File file) 
        throws IOException {
        return load(file, /*process*/ true);
    }

    public  LoadedDocument load(File file, boolean process) 
        throws IOException {
        return load(FileUtil.fileToURL(file), process);
    }

    public LoadedDocument load(URL url)
        throws IOException {
        return load(url, true);
    }

    public LoadedDocument load(URL url, boolean process)
        throws IOException {
        if (url.getRef() != null) {
            url = URLUtil.setRawFragment(url, null);
        }

        LoadedDocument doc = preload(url);

        if (preloadedDocs.containsKey(url)) {
            preloadedDocs.remove(url);

            docs.put(url, doc);
        
            if (process) {
                process(doc.document, url);
            }

            // Not a fatal error.
            checkDITAVersion(doc);
        }

        return doc;
    }

    private LoadedDocument preload(URL url) 
        throws IOException {
        // Needed to normalize "#." and "#./foo".

        if (url.getRef() != null) {
            url = URLUtil.setRawFragment(url, null);
        }

        LoadedDocument doc = docs.get(url);
        if (doc == null) {
            doc = preloadedDocs.get(url);
            if (doc == null) {
                console.info(Msg.msg("loadingDoc", URLUtil.toLabel(url)));
                Document loaded = LoadDocument.load(url, validate, console);

                doc = createLoadedDocument(url, loaded);
                // Preload all topics.
                doc.getTopics(console);

                preloadedDocs.put(url, doc);
            }
        }

        return doc;
    }

    public LoadedDocument put(URL url, Document loaded, boolean process) {
        if (url.getRef() != null) {
            url = URLUtil.setRawFragment(url, null);
        }

        LoadedDocument doc = createLoadedDocument(url, loaded);
        // Preload all topics.
        doc.getTopics(console);

        docs.put(url, doc);
        preloadedDocs.remove(url);
        
        if (process) {
            process(loaded, url);
        }

        return doc;
    }

    protected LoadedDocument createLoadedDocument(URL url, Document doc) {
        return new LoadedDocument(url, doc);
    }

    public LoadedDocument remove(URL url) {
        LoadedDocument removed = docs.remove(url);
        preloadedDocs.remove(url);
        return removed;
    }

    public LoadedDocument get(URL url) {
        if (url.getRef() != null) {
            url = URLUtil.setRawFragment(url, null);
        }

        return docs.get(url);
    }

    public int size() {
        return docs.size();
    }

    public Iterator<LoadedDocument> iterator() {
        return docs.values().iterator();
    }

    // -----------------------------------------------------------------------

    private final boolean checkDITAVersion(LoadedDocument loadedDoc) {
        boolean checked = true;

        if (loadedDoc.type == LoadedDocument.Type.MULTI_TOPIC) {
            Element root = loadedDoc.document.getDocumentElement();

            Node child = root.getFirstChild();
            while (child != null) {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    if (!checkDITAVersion((Element) child)) {
                        checked = false;
                    }
                }

                child = child.getNextSibling();
            }
        } else if (loadedDoc.type != LoadedDocument.Type.DITAVAL) {
            checked = checkDITAVersion(loadedDoc.document.getDocumentElement());
        }

        return checked;
    }

    private static final String[] SUPPORTED_VERSIONS = {
        "1.0", "1.1", "1.2", "1.3"
    };

    private final boolean checkDITAVersion(Element element) {
        String version = element.getAttributeNS(
            "http://dita.oasis-open.org/architecture/2005/", 
            "DITAArchVersion");
        if (version == null || (version = version.trim()).length() == 0) {
            console.error(element, Msg.msg("missingAttribute",
                                           "ditaarch:DITAArchVersion"));
            return false;
        } else {
            boolean supported = false;
            for (int i = 0; i < SUPPORTED_VERSIONS.length; ++i) {
                if (SUPPORTED_VERSIONS[i].equals(version)) {
                    supported = true;
                    break;
                }
            }
            if (!supported) {
                console.warning(element, 
                                Msg.msg("unsupportedDITAVersion",
                                        version,
                                        StringUtil.join(", ",
                                                        SUPPORTED_VERSIONS)));
            }
            return true;
        }
    }

    private final void process(Node tree, URL baseURL) {
        Node child = tree.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();

            switch (child.getNodeType()) {
            case Node.ELEMENT_NODE:
                if (DITAUtil.hasDITANamespace(child)) { // Skip SVG and MathML.
                    Element childElement = (Element) child;

                    // Make sure that certain elements have an ID ---

                    ensureHasValidID(childElement);

                    // Use absolute URLs everywhere --- 
                    // This avoids adding xml:base attributes during the
                    // transclusion step
                    // Ignore navref/@mapref.

                    String value = 
                        DITAUtil.getNonEmptyAttribute(childElement,
                                                      null, "conref");
                    if (value != null) {
                        resolveHref(childElement, "conref", value, baseURL);
                    }

                    value = DITAUtil.getNonEmptyAttribute(childElement,
                                                         null, "conrefend");
                    if (value != null) {
                        resolveHref(childElement, "conrefend", value,
                                    baseURL);
                    }

                    value = DITAUtil.getNonEmptyAttribute(childElement,
                                                          null, "href");
                    if (value != null) {
                        resolveHref(childElement, "href", value, baseURL);
                    }

                    // Substitute keyref ---

                    if (keySpaces != null) {
                        value = 
                            DITAUtil.getNonEmptyAttribute(childElement,
                                                         null, "conkeyref");
                        if (value != null) {
                            resolveConkeyref(childElement, value);
                        }

                        value = 
                            DITAUtil.getNonEmptyAttribute(childElement,
                                                          null, "keyref");
                        if (value != null) {
                            processKeyref(childElement, value);
                        }
                    }

                    if (DITAUtil.hasClass(childElement, "topic/object")) {
                        // This processes hrefs and keyrefs in the object and
                        // its params.
                        resolveObjectURLs(childElement, baseURL);
                    }

                    process(childElement, baseURL);
                }
                break;

            case Node.PROCESSING_INSTRUCTION_NODE:
                if ("onclick".equals(
                        ((ProcessingInstruction) child).getTarget())) {
                    Element parent = DOMUtil.getParentElement(child);
                    if (parent != null) {
                        DITAUtil.ensureHasValidID(parent, console);
                    }
                }
                break;
            }

            child = next;
        }
    }

    // ----------------------------------
    // ensureHasValidID
    // ----------------------------------

    // "equation-d/equation-figure" (for ditac:equationList) is also a
    // "topic/fig".
    private static final String[] ANCHOR_ELEMENTS = {
        "topic/section", 	         // For toc
        "topic/table", 		         // For tablelist
        "topic/fig", 		         // For figurelist
        "topic/example", 	         // For ditac:exampleList
        "topic/indexterm" 	         // For indexlist
    };
    private static final int ANCHOR_ELEMENT_COUNT = ANCHOR_ELEMENTS.length;

    private final void ensureHasValidID(Element element) {
        if (DITAUtil.hasClass(element, "topic/topic")) {
            DITAUtil.ensureHasValidID(element, console);
        } else { // Not a topic
            for (int i = 0; i < ANCHOR_ELEMENT_COUNT; ++i) {
                if (DITAUtil.hasClass(element, ANCHOR_ELEMENTS[i])) {
                    DITAUtil.ensureHasValidID(element, console);
                    break;
                }
            }
        }
    }

    // ----------------------------------
    // resolveObjectURLs
    // ----------------------------------

    private final void resolveObjectURLs(Element element, URL baseURL) {
        // codebase ---

        URL codebase = null;

        String location =
            DITAUtil.getNonEmptyAttribute(element, null, "codebase");
        if (location != null) {
            // No longer useful.
            element.removeAttributeNS(null, "codebase");

            try {
                codebase = Resolve.resolveURI(location, baseURL);
            } catch (MalformedURLException ignored) {}
        }

        if (keySpaces != null) {
            String keyref =
                DITAUtil.getNonEmptyAttribute(element, null, "codebasekeyref");
            if (keyref != null) {
                // No longer useful.
                element.removeAttributeNS(null, "codebasekeyref");

                Object[] resolved = 
                    resolveObjectKeyref(element,
                                        "codebasekeyref", keyref, "codebase",
                                        /*warn*/ true);
                if (resolved != null) {
                    codebase = (URL) resolved[0];
                }
            }
        }

        // classid ---

        location = DITAUtil.getNonEmptyAttribute(element, null, "classid");
        if (location != null) {
            if (location.startsWith("clsid:")) {
                // Mark as absolute URL.
                element.setAttributeNS(DITAC_NS_URI, 
                                       DITAC_PREFIX + "absoluteClassid",
                                       "true");
            } else {
                resolveObjectURL(element, "classid", location,
                                 codebase, baseURL);
            }
        }
        
        if (keySpaces != null) {
            resolveObjectKeyref(element, "classidkeyref", "classid");
        }

        // data ---

        location = DITAUtil.getNonEmptyAttribute(element, null, "data");
        if (location != null) {
            resolveObjectURL(element, "data", location, codebase, baseURL);
        }
        
        if (keySpaces != null) {
            resolveObjectType(element, "datakeyref");
            resolveObjectKeyref(element, "datakeyref", "data");
        }

        // archive ---

        location = DITAUtil.getNonEmptyAttribute(element, null, "archive");
        if (location != null) {
            resolveObjectURLs(element, "archive", location, codebase, baseURL);
        }
        
        if (keySpaces != null) {
            resolveObjectKeyrefs(element, "archivekeyrefs", "archive");
        }

        Element[] params = DITAUtil.findChildrenByClass(element, "topic/param");
        for (Element param : params) {
            String valuetype = 
                DITAUtil.getNonEmptyAttribute(param, null, "valuetype");

            if (DITAUtil.getNonEmptyAttribute(param, null, "keyref") != null) {
                if (valuetype == null) {
                    valuetype = "ref";
                } else if (!"ref".equals(valuetype)) {
                    console.warning(param,
                                    Msg.msg("ignoringAttribute", "keyref"));
                    param.removeAttributeNS(null, "keyref");
                }
            }

            String name = DITAUtil.getNonEmptyAttribute(param, null, "name");

            if ("ref".equals(valuetype) ||
                (("source".equals(name) || "poster".equals(name)) &&
                 valuetype == null)) { // Implicit valuetype=ref.
                param.setAttributeNS(null, "valuetype", "ref");
                       
                location = DITAUtil.getNonEmptyAttribute(param, null, "value");
                if (location != null) {
                    resolveObjectURL(param, "value", location, 
                                     /*codebase*/ null, baseURL);
                }

                if (keySpaces != null) {
                    resolveObjectType(param, "keyref");
                    resolveObjectKeyref(param, "keyref", "value");
                }
            } else if ("movie".equals(name) && valuetype == null) { 
                // Implicit valuetype=ref depending on object type.

                String type = 
                    DITAUtil.getNonEmptyAttribute(element, null, "type");

                location = DITAUtil.getNonEmptyAttribute(param, null, "value");

                boolean isRef = 
                    ("application/x-shockwave-flash".equalsIgnoreCase(type) ||
                     (location != null && 
                      location.toLowerCase().endsWith(".swf")));

                if (isRef) {
                    param.setAttributeNS(null, "valuetype", "ref");
                    param.setAttributeNS(null, "type", 
                                         "application/x-shockwave-flash");

                    if (location != null) {
                        resolveObjectURL(param, "value", location, 
                                         /*codebase*/ null, baseURL);
                    }

                    if (keySpaces != null) {
                        resolveObjectKeyref(param, "keyref", "value");
                    }
                }
            }
        }
    }

    private final void resolveObjectKeyrefs(Element element,
                                            String attrName,
                                            String fallbackAttrName) {
        String attrValue = 
            DITAUtil.getNonEmptyAttribute(element, null, attrName);
        if (attrValue == null) {
            return;
        }
        // No longer useful.
        element.removeAttributeNS(null, attrName);


        String[] keyrefs = XMLText.splitList(attrValue);
        int count = keyrefs.length;

        URL[] urls = new URL[count];
        Boolean[] absoluteURLs = new Boolean[count];
        count = 0;

        for (String keyref : keyrefs) {
            Object[] resolved =
                resolveObjectKeyref(element, attrName, keyref, fallbackAttrName,
                                    /*warn*/ false);
            if (resolved == null) {
                continue;
            }

            urls[count] = (URL) resolved[0];
            absoluteURLs[count] = (Boolean) resolved[1];
            ++count;
        }

        if (count == 0) {
            keyrefWarning(element, attrName, attrValue, fallbackAttrName);
            return;
        }

        StringBuilder hrefs = new StringBuilder();
        StringBuilder absoluteFlags = new StringBuilder();

        for (int i = 0; i < count; ++i) {
            if (i > 0) {
                hrefs.append(' ');
                absoluteFlags.append(' ');
            }

            hrefs.append(urls[i].toExternalForm());
            absoluteFlags.append(absoluteURLs[i].toString());
        }
        
        element.setAttributeNS(null, fallbackAttrName, hrefs.toString());
        element.setAttributeNS(
            DITAC_NS_URI,
            DITAC_PREFIX + "absolute" + StringUtil.capitalize(fallbackAttrName),
            absoluteFlags.toString());
    }

    private final void resolveObjectType(Element element, String attrName) {
        String type = DITAUtil.getNonEmptyAttribute(element, null, "type");
        if (type != null) {
            // Nothing to do.
            return;
        }

        String keyref = DITAUtil.getNonEmptyAttribute(element, null, attrName);
        if (keyref == null) {
            return;
        }

        KeyDefinition kd = keySpaces.get(keyref, element);
        if (kd != null) {
            type = kd.getAttribute(null, "type");
            if (type != null) {
                element.setAttributeNS(null, "type", type);
            }
        }
    }

    private final void resolveObjectKeyref(Element element,
                                           String attrName,
                                           String fallbackAttrName) {
        String keyref = DITAUtil.getNonEmptyAttribute(element, null, attrName);
        if (keyref == null) {
            return;
        }
        // No longer useful.
        element.removeAttributeNS(null, attrName);

        Object[] resolved =
            resolveObjectKeyref(element, attrName, keyref, fallbackAttrName,
                                /*warn*/ true);
        if (resolved == null) {
            return;
        }
        URL url = (URL) resolved[0];
        Boolean absoluteURL = (Boolean) resolved[1];

        element.setAttributeNS(null, fallbackAttrName, url.toExternalForm());

        String localName = "absolute" + StringUtil.capitalize(fallbackAttrName);
        String qName = DITAC_PREFIX + localName;
        if (absoluteURL == Boolean.TRUE) {
            element.setAttributeNS(DITAC_NS_URI, qName, "true");
        } else {
            element.removeAttributeNS(DITAC_NS_URI, localName);
        }
    }

    private final Object[] resolveObjectKeyref(Element element,
                                               String attrName,
                                               String keyref,
                                               String fallbackAttrName,
                                               boolean warn) {
        URL url = null;
        Boolean absoluteURL = null;

        KeyDefinition kd = keySpaces.get(keyref, element);
        if (kd != null) {
            String href = kd.getAttribute(null, "href");
            if (href != null) {
                try {
                    url = Resolve.resolveURI(href, null);

                    absoluteURL = 
                        "true".equals(kd.getAttribute(DITAC_NS_URI,
                                                      ABSOLUTE_HREF_NAME));
                } catch (MalformedURLException ignored) {}
            }
        }

        if (url == null) {
            if (warn) {
                keyrefWarning(element, attrName, keyref, fallbackAttrName);
            }
            return null;
        } else {
            return new Object[] { url, absoluteURL };
        }
    }

    private final void keyrefWarning(Element element,
                                     String attrName,
                                     String keyref,
                                     String fallbackAttrName) {
        if (DITAUtil.getNonEmptyAttribute(element, null, fallbackAttrName) != 
            null) {
            console.warning(element, 
                            Msg.msg("cannotResolveKeyref", 
                                    attrName, keyref, fallbackAttrName));
        } else {
            console.warning(element, 
                            Msg.msg("cannotResolveKeyref2",
                                    attrName, keyref, fallbackAttrName));
        }
    }

    private static final void resolveObjectURLs(Element element, 
                                                String attrName, 
                                                String attrValue, 
                                                URL codebase, URL baseURL) {
        String[] hrefs = XMLText.splitList(attrValue);
        int hrefCount = hrefs.length;
        String[] absoluteFlags = new String[hrefCount];

        for (int i = 0; i < hrefCount; ++i) {
            String href = hrefs[i];
            href = joinObjectURL(codebase, href);

            absoluteFlags[i] = "false";

            if (href.startsWith("#")) {
                href = baseURL.toExternalForm() + href;
            } else {
                boolean resolve = true;

                // Absolute URL?
                try {
                    URL url = Resolve.resolveURI(href, null);
                    href = url.toExternalForm();
                    resolve = false;

                    absoluteFlags[i] = "true";
                } catch (MalformedURLException ignored) {}

                if (resolve) {
                    // Relative URL?
                    try {
                        URL url = URLUtil.createURL(baseURL, href);
                        href = url.toExternalForm();
                    } catch (MalformedURLException ignored) {}
                }
            }

            hrefs[i] = href;
        }

        element.setAttributeNS(null, attrName, StringUtil.join(' ', hrefs));
        // Mark absolute URLs.
        element.setAttributeNS(
            DITAC_NS_URI,
            DITAC_PREFIX + "absolute" + StringUtil.capitalize(attrName), 
            StringUtil.join(' ', absoluteFlags));
    }

    private static final void resolveObjectURL(Element element, 
                                               String attrName, 
                                               String attrValue, 
                                               URL codebase, URL baseURL) {
        attrValue = joinObjectURL(codebase, attrValue);
        String href = attrValue;

        if (attrValue.startsWith("#")) {
            href = baseURL.toExternalForm() + attrValue;
        } else {
            boolean resolve = true;

            // Absolute URL?
            try {
                URL url = Resolve.resolveURI(attrValue, null);
                href = url.toExternalForm();
                resolve = false;

                // Mark absolute URLs.
                element.setAttributeNS(
                    DITAC_NS_URI, 
                    DITAC_PREFIX + "absolute" + StringUtil.capitalize(attrName),
                    "true");
            } catch (MalformedURLException ignored) {}

            if (resolve) {
                // Relative URL?
                try {
                    URL url = URLUtil.createURL(baseURL, attrValue);
                    href = url.toExternalForm();
                } catch (MalformedURLException ignored) {}
            }
        }

        element.setAttributeNS(null, attrName, href);
    }

    private static final String joinObjectURL(URL codebase, String path) {
        if (codebase != null) {
            try {
                URL url = URLUtil.createURL(codebase, path);
                path = url.toExternalForm();
            } catch (MalformedURLException ignored) {}
        }
        return path;
    }

    // ----------------------------------
    // resolveHref
    // ----------------------------------

    private final void resolveHref(Element element, 
                                   String attrName, String attrValue, 
                                   URL baseURL) {
        String href = attrValue;

        if (attrValue.startsWith("#")) {
            href = baseURL.toExternalForm() +
                resolveFragment(attrValue, element);
        } else {
            boolean resolve = true;

            // Absolute URL?
            try {
                URL url = Resolve.resolveURI(attrValue, null);
                // This allows to cope with URIs making using of advanced
                // features of XML catalogs.
                href = url.toExternalForm();
                resolve = false;

                if (attrName.equals("href")) {
                    // Mark absolute hrefs.
                    element.setAttributeNS(DITAC_NS_URI, ABSOLUTE_HREF_QNAME,
                                           "true");
                }
            } catch (MalformedURLException ignored) {}

            String scope;
            boolean isExternalResource = 
                attrName.equals("href") &&
                (scope = 
                 DITAUtil.inheritAttribute(element, null, "scope")) != null &&
                !"local".equals(scope);
            boolean isImage = DITAUtil.hasClass(element, "topic/image");

            if (resolve && isExternalResource && !isImage) {
                // Unless the element is an image, do not resolve an href
                // pointing to a peer or external resource.
                href = attrValue;
                resolve = false;
            }

            if (resolve) {
                // Relative URL?
                
                // Also performed on @scope other than "local" and
                // on @format other than "dita" and "ditamap",
                // but we do not care.

                try {
                    URL url = URLUtil.createURL(baseURL, attrValue);
                    href = url.toExternalForm();
                } catch (MalformedURLException ignored) {}
            }

            if (!isExternalResource && !isImage) {
                href = resolveHrefFragment(href);
            }
        }

        element.setAttributeNS(null, attrName, href);
    }

    private final String resolveFragment(String fragment, Element link) {
        if ("#.".equals(fragment) || fragment.startsWith("#./")) {
            Element topic = DITAUtil.findAncestorByClass(link, "topic/topic");
            if (topic != null) {
                String topicId = DITAUtil.ensureHasValidID(topic, console);
                fragment = "#" + URIComponent.quoteFragment(topicId) + 
                    fragment.substring(2);
            }
        }

        return fragment;
    }

    private final String resolveHrefFragment(String href) {
        // href is already a resolved, absolute, location.

        int pos = href.lastIndexOf('#');
        if (pos > 0) { 
            String ref = href.substring(pos+1);
            if (".".equals(ref) || ref.startsWith("./")) {
                String location = href.substring(0, pos);

                try {
                    URL url = new URL(location);

                    LoadedDocument doc = preload(url);

                    LoadedTopic topic = doc.getFirstTopic();
                    if (topic != null) {
                        // Topmost, non-nested, LoadedTopics are guaranteed to
                        // have a valid ID.

                        href = location + "#" + 
                            URIComponent.quoteFragment(topic.topicId) + 
                            ref.substring(1);
                    }
                } catch (Exception ignored) {}
            }
        }

        return href;
    }

    // ----------------------------------
    // resolveConkeyref
    // ----------------------------------

    private final void resolveConkeyref(Element element, String attrValue) {
        element.removeAttributeNS(null, "conkeyref");

        String[] split = splitKeyref(element, "conkeyref", attrValue);
        if (split == null) {
            return;
        }
        String key = split[0];
        String id = split[1];

        String resolved = keySpaces.getHref(key, element);
        if (resolved != null) {
            // If resolved points to a topic, then it is guaranteed to end
            // with "#actual_topic_id" (never "#.").
            resolved = addIdToHref(resolved, id);

            // Add or replace conref.
            element.setAttributeNS(null, "conref", resolved);
        } else {
            keyrefWarning(element, "conkeyref", attrValue, "conref");
        }
    }

    private final String[] splitKeyref(Element element,
                                       String attrName, String attrValue) {
        String key, id;
        int pos = attrValue.indexOf('/');
        if (pos < 0) {
            key = attrValue;
            id = null;
        } else {
            key = attrValue.substring(0, pos);
            id = attrValue.substring(pos+1);
        }

        if (!DITAUtil.isValidKey(key) ||
            (id != null && !XMLText.isNmtoken(id))) {
            console.error(element,
                          Msg.msg("invalidAttribute", attrValue, attrName));
            return null;
        }
        
        return new String[] { key, id };
    }

    private static final String addIdToHref(String href, String id) {
        if (id != null) {
            if (URIComponent.getRawFragment(href) != null) {
                href += "/" + URIComponent.quoteFragment(id);
            } else {
                href += "#" + URIComponent.quoteFragment(id);
            }
        }

        return href;
    }

    // ----------------------------------
    // processKeyref
    // ----------------------------------

    private final void processKeyref(Element element, String attrValue) {
        element.removeAttributeNS(null, "keyref");

        String[] split = splitKeyref(element, "keyref", attrValue);
        if (split == null) {
            return;
        }
        String key = split[0];
        String id = split[1];

        KeyDefinition kd = keySpaces.get(key, element);
        if (kd == null) {
            keyrefWarning(element, "keyref", attrValue, "href");
            return;
        }

        String href = processKeyref(kd, id, element);

        // Remove void elements ---

        if (href == null && !DOMUtil.hasContent(element)) {
            element.getParentNode().removeChild(element);
        }
    }
    
    /*package*/ static final String[] LINKING_ATTRIBUTES = {
        null, "href", "href", // Must be first item
        null, "scope", "scope",
        null, "format", "format",
        DITAC_NS_URI, ABSOLUTE_HREF_QNAME, ABSOLUTE_HREF_NAME, 
        DITAC_NS_URI, COPY_OF_QNAME, COPY_OF_NAME 
    };

    /*package*/ final String processKeyref(KeyDefinition kd, String id,
                                           Element element) {
        // Replace @keyref by @href and its companion linking attributes ---

        String href = kd.getAttribute(null, "href");
        if (href != null &&
            "none".equals(kd.getAttribute(null, "linking"))) {
            if (!DITAUtil.hasClass(element, "abbrev-d/abbreviated-form")) {
                // NOT CONFORMING: however abbreviated-form being empty,
                // we really need to add some contents taken from the
                // referenced glossentry.
                href = null;
            }
        }

        if (href != null) {
            // Replace @href and its companion linking attributes ---

            // If href points to a topic, then it is guaranteed to end
            // with "#actual_topic_id" (never "#.").
            href = addIdToHref(href, id);

            for (int i = 0; i < LINKING_ATTRIBUTES.length; i += 3) {
                String ns = LINKING_ATTRIBUTES[i];
                String qName = LINKING_ATTRIBUTES[i+1];
                String localName = LINKING_ATTRIBUTES[i+2];

                String value;
                if (i == 0) {
                    value = href;
                } else {
                    value = kd.getAttribute(ns, localName);
                }
                if (value == null) {
                    element.removeAttributeNS(ns, localName);
                } else {
                    element.setAttributeNS(ns, qName, value);
                }
            }
        } else {
            // Remove @href and its companion linking attributes ---

            for (int i = 0; i < LINKING_ATTRIBUTES.length; i += 3) {
                String ns = LINKING_ATTRIBUTES[i];
                String localName = LINKING_ATTRIBUTES[i+2];

                element.removeAttributeNS(ns, localName);
            }
        }

        // Inject content coming from the key definition ---

        if (DITAUtil.hasClass(element, "map/topicref")) {
            addMetadata(kd, element);
        } else {
            addContent(kd, element);
        }

        return href;
    }

    // "glossentry/glossAlternateFor" is a "topic/xref".
    // "abbrev-d/abbreviated-form" is a "topic/term".
    private static final String[] VARIABLE_ELEMENTS = {
        "topic/dt",
        "topic/cite",
        "topic/term",
        "topic/keyword",
        "topic/ph"
    };

    private static final void addContent(KeyDefinition kd, Element element) {
        // XXE creates link element containing an empty linktext child.
        // Get rid of these empty child elements.
        boolean isLink = DITAUtil.hasClass(element, "topic/link");
        if (isLink) {
            Element child = 
                DITAUtil.findChildByClass(element, "topic/linktext");
            if (child != null && !DOMUtil.hasContent(child)) {
                element.removeChild(child);
            }
            
            child = DITAUtil.findChildByClass(element, "topic/desc");
            if (child != null && !DOMUtil.hasContent(child)) {
                element.removeChild(child);
            }
        }

        if (!DOMUtil.hasContent(element)) {
            Element meta = kd.getMeta();
            if (meta != null) {
                // LIMITATION: matching element content taken from a key
                // definition is limited to the following cases:

                if (isLink) {
                    Element linktext = 
                        DITAUtil.findChildByClass(meta, "map/linktext");
                    if (linktext != null) {
                        Element copy = copyElement(linktext, 
                                                  "linktext", "topic/linktext",
                                                  element.getOwnerDocument());
                        element.appendChild(copy);
                    }

                    Element shortdesc = 
                        DITAUtil.findChildByClass(meta, "map/shortdesc");
                    if (shortdesc != null) {
                        Element copy = copyElement(shortdesc, 
                                                   "desc", "topic/desc", 
                                                   element.getOwnerDocument());
                        element.appendChild(copy);
                    }
                } else if (DITAUtil.hasClass(element, "topic/xref")) {
                    Element linktext = 
                        DITAUtil.findChildByClass(meta, "map/linktext");
                    if (linktext != null) {
                        DOMUtil.copyChildren(linktext, element, 
                                             element.getOwnerDocument());
                    }
                } else if (DITAUtil.hasClass(element, "topic/image") &&
                           DITAUtil.getNonEmptyAttribute(element,
                                                         null, "alt") == null) {
                    Element linktext = 
                        DITAUtil.findChildByClass(meta, "map/linktext");
                    if (linktext != null) {
                        Element copy = copyElement(linktext, 
                                                   "alt", "topic/alt",
                                                   element.getOwnerDocument());
                        element.appendChild(copy);
                    }
                } else {
                    if (DITAUtil.hasClass(element, VARIABLE_ELEMENTS)) {
                        Element container = null;

                        Element keywords = 
                            DITAUtil.findChildByClass(meta, "topic/keywords");
                        if (keywords != null) {
                            container = 
                                DITAUtil.findChildByClass(keywords, 
                                                          "topic/keyword");
                        }

                        if (container == null) {
                            container =
                              DITAUtil.findChildByClass(meta, "map/linktext");
                        }

                        if (container != null) {
                            DOMUtil.copyChildren(container, element, 
                                                 element.getOwnerDocument());
                        }
                    }
                }
            }
        }
    }

    private static final Element copyElement(Element from, 
                                             String toQName, String toClass, 
                                             Document doc) {
        Element to = doc.createElementNS(null, toQName);

        NamedNodeMap attrs = from.getAttributes();
        int attrCount = attrs.getLength();

        for (int i = 0; i < attrCount; ++i) {
            Attr attr = (Attr) attrs.item(i);

            to.setAttributeNS(attr.getNamespaceURI(), /*qName*/ attr.getName(),
                              attr.getValue());
        }

        to.setAttributeNS(null, "class", "- " + toClass + " ");

        DOMUtil.copyChildren(from, to, doc);

        return to;
    }

    // Local names = qualified names because here we assume that the cascading
    // attribute name have no namespace (e.g. no xml:lang).
    private static final String[] CASCADING_ATTRIBUTES = {
        "linking", 
        "toc", 
        "print",
        "search",
        "type",
        "translate",
        "processing-role",
        "cascade", 
        "rev",
        // All filtering attributes but rev are space-delimited set of values.
        "audience",
        "platform",
        "product",
        "otherprops",
        "props",
        "deliveryTarget" // specializes "props".
    };

    private static final int FIRST_ADDITIVE_ATTRIBUTE = 9; // audience

    private final String[] getCascadingAttributes(Element topicref) {
        if (cascadingAttributes == null) {
            cascadingAttributes = CASCADING_ATTRIBUTES;

            Element map = DITAUtil.findAncestorByClass(topicref, "map/map");
            if (map != null) {
                String[] filterAttributes = DITAUtil.getFilterAttributes(map);

                // Attribute names having a namespace not supported.
                for (String name : filterAttributes) {
                    if (!StringList.contains(cascadingAttributes, name)) {
                        cascadingAttributes = 
                            StringList.append(cascadingAttributes, name);
                    }
                }
            }
        }

        return cascadingAttributes;
    }

    /*package*/ final void addMetadata(KeyDefinition kd, Element topicref) {
        // Content from a key-defining element cascades to the key-referencing
        // element following the rules for combining metadata between maps and
        // other maps and between maps and topics. The @lockmeta attribute is
        // honored when metadata content is combined.

        boolean lockmeta = true;
        Element srcMeta = kd.getMeta();
        if (srcMeta != null &&
            "no".equals(DITAUtil.getNonEmptyAttribute(srcMeta,
                                                      null, "lockmeta"))) {
            lockmeta = false;
        }
        if (!lockmeta) {
            return;
        }

        // ---
        
        String cascade = 
            DITAUtil.getNonEmptyAttribute(topicref, null, "cascade");
        if (cascade == null) {
            cascade = kd.getAttribute(null, "cascade");
        }
        boolean nomerge = "nomerge".equals(cascade);

        final String[] cascadingAttributes = getCascadingAttributes(topicref);
        final int cascadingAttributeCount = cascadingAttributes.length;

        for (int i = 0; i < cascadingAttributeCount; ++i) {
            String attrName = cascadingAttributes[i];

            String cascadedValue = kd.getAttribute(null, attrName);
            if (cascadedValue == null) {
                continue;
            }

            String newValue;
            String localValue = DITAUtil.getNonEmptyAttribute(topicref, 
                                                              null, attrName);
            if (localValue == null) {
                newValue = cascadedValue;
            } else {
                if (i >= FIRST_ADDITIVE_ATTRIBUTE) {
                    // Additive.
                    if (nomerge) {
                        newValue = localValue;
                    } else {
                        newValue = 
                            DOMUtil.mergeTokens(localValue, cascadedValue);
                    }
                } else {
                    // Not additive.
                    newValue = localValue;
                }
            }

            if (!newValue.equals(localValue)) {
                topicref.setAttributeNS(null, attrName, newValue);
            }
        }

        // ---

        if (srcMeta == null) {
            return;
        }

        Document doc = topicref.getOwnerDocument();

        Element dstMeta = DITAUtil.findChildByClass(topicref, "map/topicmeta");
        if (dstMeta == null) {
            dstMeta = doc.createElementNS(null, "topicmeta");
            dstMeta.setAttributeNS(null, "class", "- map/topicmeta ");

            topicref.insertBefore(dstMeta, topicref.getFirstChild());
        }

        Node child = srcMeta.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                String cls = null;
                boolean single = false;

                for (int k = 0; k < CASCADED_ELEMENTS.length; ++k) {
                    String c = CASCADED_ELEMENTS[k];

                    if (DITAUtil.hasClass(childElement, c)) {
                        cls = c;
                        single = CASCADED_ELEMENT_SINGLE[k];
                        break;
                    }
                }

                if (cls != null) {
                    Element existing = DITAUtil.findChildByClass(dstMeta, cls);
                    if (!single || existing == null) {
                        Element before = DITAUtil.findChildByClass(
                            dstMeta,
                            StringList.indexOf(TOPICMETA_ELEMENTS, 
                                               cls), 
                            TOPICMETA_ELEMENTS);
                        insertBefore(dstMeta, childElement, before, doc);
                    }
                    // Otherwise, single && existing != null.
                }
            }

            child = child.getNextSibling();
        }
    }

    private static final void insertBefore(Element parent, Element newChild, 
                                           Element before, Document doc) {
        Node copy = doc.importNode(newChild, /*deep*/ true);
        parent.insertBefore(copy, before);
    }

    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        KeySpaces keySpaces = null;
        boolean usage = false;

        int i = 0;
        for (; i < args.length; ++i) {
            String arg = args[i];

            if ("-k".equals(arg)) {
                if (i+1 >= args.length) {
                    usage = true;
                    break;
                }

                File mapFile = new File(args[++i]);
                
                KeyLoader keyLoader = new KeyLoader();
                Document mapDoc = loadMap(keyLoader, mapFile);
                if (mapDoc == null) {
                    System.exit(2);
                }

                LoadedDocuments loadedTopics = new LoadedDocuments();
                keySpaces = keyLoader.createKeySpaces(loadedTopics);
            } else {
                if (arg.startsWith("-")) {
                    usage = true;
                }
                break;
            }
        }

        // ---

        if (!usage && keySpaces != null && i == args.length) {
            System.out.println(keySpaces);
            return;
        }

        // ---

        if (!usage && i+2 != args.length) {
            usage = true;
        }

        if (usage) {
            System.err.println(
                "usage: java com.xmlmind.ditac.preprocess.LoadedDocuments" +
                " [ -k map_file ] [ in_xml_file out_xml_file ]");
            System.exit(1);
        }


        File inFile = new File(args[i]);
        URL inURL = inFile.toURI().toURL();
        File outFile = new File(args[i+1]);

        LoadedDocuments loadedDocs = new LoadedDocuments(keySpaces, null);
        LoadedDocument loadedDoc = loadedDocs.load(inURL, /*process*/ false);

        switch (loadedDoc.type) {
        case MAP:
        case BOOKMAP:
            if (keySpaces != null) {
                if (!(new MapSimplifier(keySpaces,
                                        null)).simplify(loadedDoc.document,
                                                        inURL)) {
                    System.exit(3);
                }
            } else {
                loadedDoc = loadedDocs.put(inURL, loadedDoc.document,
                                           /*process*/ true);
            }
            break;
        case MULTI_TOPIC:
        case TOPIC:
            {
                // Input topic file may be unrelated to -k map_file, so in
                // this case use the root key space.

                KeySpace rootkeySpace = null;
                HashMap<String,KeySpace> map = null;
                if (keySpaces != null) {
                    rootkeySpace = keySpaces.getRootKeySpace();
                    map = keySpaces.topicURIToKeySpace;
                }

                System.out.println("Topics:");
                listTopics(loadedDoc.getTopics(), rootkeySpace, map, 0);

                loadedDoc = loadedDocs.put(inURL, loadedDoc.document,
                                           /*process*/ true);
            }
            break;
        }

        com.xmlmind.ditac.util.SaveDocument.save(loadedDoc.document, outFile);
    }

    private static final Document loadMap(KeyLoader keyLoader, File file) 
        throws IOException {
        return loadMap(keyLoader, FileUtil.fileToURL(file));
    }

    private static final Document loadMap(KeyLoader keyLoader, URL url) 
        throws IOException {
        Document doc =
            LoadDocument.load(url, /*validate*/ false, /*console*/ null);
        if (!keyLoader.prepareMap(doc, url)) {
            return null;
        } else {
            return doc;
        }
    }

    private static final void listTopics(LoadedTopic[] topics,
                                         KeySpace rootkeySpace,
                                         HashMap<String,KeySpace> map, 
                                         int indent) {
        for (LoadedTopic topic : topics) {
            if (map != null) {
                String href = topic.getHref();
                if (!map.containsKey(href)) {
                    map.put(href, rootkeySpace);
                }
            }

            // ---

            int count = indent;
            while (count > 0) {
                System.out.print(' ');
                --count;
            }
            System.out.println(topic.topicId);

            LoadedTopic[] subTopics = topic.getNestedTopics();
            if (subTopics != null) {
                listTopics(subTopics, rootkeySpace, map, indent+4);
            }
        }
    }
}
