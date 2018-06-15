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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import com.xmlmind.util.ThrowableUtil;
import com.xmlmind.util.ArrayUtil;
import com.xmlmind.util.URLUtil;
import com.xmlmind.util.Console;
import com.xmlmind.ditac.util.SimpleConsole;
import com.xmlmind.ditac.util.ConsoleHelper;
import com.xmlmind.ditac.util.DOMUtil;
import com.xmlmind.ditac.util.DITAUtil;

/*package*/ final class MapSimplifier implements Constants {
    private KeySpaces keySpaces;
    private ConsoleHelper console;

    // -----------------------------------------------------------------------

    public MapSimplifier(KeySpaces keySpaces, Console console) {
        setKeySpaces(keySpaces);
        setConsole(console);
    }

    public void setKeySpaces(KeySpaces keySpaces) {
        this.keySpaces = keySpaces;
    }

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

    public boolean simplify(Document mapDoc, URL mapURL) 
        throws IOException {
        console.info(Msg.msg("simplifyingMap", URLUtil.toLabel(mapURL)));

        Element map = mapDoc.getDocumentElement();
        assert(DITAUtil.hasClass(map, "map/map"));

        int[] keySpaceCounter = new int[1];
        addKeySpaces(map, keySpaceCounter);

        // Load all maps ---

        LoadedDocuments loadedDocs = new LoadedDocuments(keySpaces, console);
        loadedDocs.put(mapURL, mapDoc, /*process*/ true);

        if (!loadAllMaps(map, keySpaceCounter, loadedDocs)) {
            return false;
        }

        LoadedDocument[] loadedMaps = new LoadedDocument[loadedDocs.size()];
        int j = 0;

        Iterator<LoadedDocument> iter = loadedDocs.iterator();
        while (iter.hasNext()) {
            LoadedDocument loadedDoc = iter.next();

            switch (loadedDoc.type) {
            case MAP:
            case BOOKMAP:
                loadedMaps[j++] = loadedDoc;
                break;
            }
        }

        if (j != loadedMaps.length) {
            loadedMaps = ArrayUtil.trimToSize(loadedMaps, j);
        }

        // Conref-push all maps ---

        console.verbose(Msg.msg("pushingMapContent"));

        (new ConrefPusher(console)).process(loadedMaps);

        // Conref-transclude all maps ---

        console.verbose(Msg.msg("pullingMapContent"));

        if (!(new ConrefIncluder(keySpaces, console)).process(loadedMaps)) {
            return false;
        }

        // Cascade attributes and metadata in all maps ---

        console.verbose(Msg.msg("cascadingMapMeta"));

        for (LoadedDocument loadedMap : loadedMaps) {
            CascadeMeta.processMap(loadedMap.document.getDocumentElement());
        }

        // Mapref-tranclude all maps ---

        console.verbose(Msg.msg("includingSubmaps"));

        if (!(new MaprefIncluder(keySpaces, console)).process(loadedMaps)) {
            return false;
        }

        // Simplify ditavalrefs ---

        console.verbose(Msg.msg("processingDitavalrefs"));

        SimplifyTopicrefs.processMap(map, mapURL, console);
        
        return true;
    }

    // -----------------------------------
    // addKeySpaces
    // -----------------------------------

    private void addKeySpaces(Element map, int[] keySpaceCounter) {
        // The root element of a root map always defines a key scope,
        // regardless of whether a @keyscope attribute is present.
        map.setAttributeNS(DITAC_NS_URI, KEY_SPACE_QNAME, "0");

        Node child = map.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                doAddKeySpaces((Element) child, keySpaceCounter);
            }

            child = child.getNextSibling();
        }
    }

    private void doAddKeySpaces(Element element, int[] keySpaceCounter) {
        if (DITAUtil.getNonEmptyAttribute(element, null, "keyscope") != null) {
            element.setAttributeNS(DITAC_NS_URI, KEY_SPACE_QNAME, 
                                   nextKeySpaceId(keySpaceCounter));
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                doAddKeySpaces((Element) child, keySpaceCounter);
            }

            child = child.getNextSibling();
        }
    }

    /*package*/ static String nextKeySpaceId(int[] keySpaceCounter) {
        return Integer.toString(++keySpaceCounter[0], Character.MAX_RADIX);
    }

    // -----------------------------------
    // loadAllMaps
    // -----------------------------------

    private boolean loadAllMaps(Element element, int[] keySpaceCounter,
                                LoadedDocuments loadedDocs) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (DITAUtil.hasClass(childElement, "map/topicref")) {
                    URL url = getLocalMapURL(childElement);
                    if (url != null) {
                        if (url.getRef() != null) {
                            url = URLUtil.setFragment(url, null);
                        }

                        LoadedDocument loadedDoc = loadedDocs.get(url);
                        if (loadedDoc == null) {
                            try {
                                loadedDoc =
                                    loadedDocs.load(url, /*process*/ false);
                            } catch (Exception e) {
                                console.error(childElement, 
                                              Msg.msg("cannotLoad", url, 
                                                      ThrowableUtil.reason(e)));
                                return false;
                            }

                            switch (loadedDoc.type) {
                            case MAP:
                            case BOOKMAP:
                                {
                                    Document submapDoc = loadedDoc.document;
                                    Element submap =
                                        submapDoc.getDocumentElement();

                                    if (DITAUtil.hasClass(
                                            submap, 
                                            "subjectScheme/subjectScheme")) {
                                        console.warning(
                                            childElement,
                                            Msg.msg("ignoringSubjectSchemeMap",
                                                    url));

                                        loadedDocs.remove(url);
                                    } else {
                                        doAddKeySpaces(submap, keySpaceCounter);

                                        // Consistent with createKeyscopeGroup
                                        // in MaprefIncluder.

                                        String keySpace = 
                                            DITAUtil.getNonEmptyAttribute(
                                              submap,
                                              DITAC_NS_URI, KEY_SPACE_NAME);
                                        if (keySpace == null) {
                                            keySpace = 
                                                DITAUtil.getNonEmptyAttribute(
                                                  childElement,
                                                  DITAC_NS_URI, KEY_SPACE_NAME);
                                            if (keySpace != null) {
                                                submap.setAttributeNS(
                                                  DITAC_NS_URI, KEY_SPACE_QNAME,
                                                  keySpace);
                                            }
                                        }

                                        loadedDocs.put(url, submapDoc, 
                                                       /*process*/ true);

                                        loadAllMaps(submap, keySpaceCounter,
                                                    loadedDocs);
                                    }
                                }
                                break;
                            }
                        }
                    }
                }

                if (!loadAllMaps(childElement, keySpaceCounter, loadedDocs)) {
                    return false;
                }
            }

            child = child.getNextSibling();
        }

        return true;
    }

    private URL getLocalMapURL(Element topicref) {
        String href = DITAUtil.getNonEmptyAttribute(topicref, null, "href");
        if (href == null) {
            return null;
        }

        String scope = DITAUtil.inheritAttribute(topicref, null, "scope");
        if (scope != null && !"local".equals(scope)) {
            return null;
        }

        String format = DITAUtil.inheritFormat(topicref, href);
        if (format == null) {
            console.warning(topicref, Msg.msg("missingAttribute", "format"));
            return null;
        }
        if (!"ditamap".equals(format)) {
            // Example: ditavalref which has has format="ditaval" by default.
            return null;
        }

        // Remember that LoadedDocuments automatically resolves relative URLs.
        URL url = null;
        try {
            url = URLUtil.createURL(href);
        } catch (MalformedURLException ignored) {
            console.warning(topicref,
                            Msg.msg("invalidAttribute", href, "href"));
            return null;
        }

        return url;
    }

    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                "usage: java com.xmlmind.ditac.preprocess.MapSimplifier" +
                " in_ditamap_file out_flat_ditamap_file");
            System.exit(1);
        }
        java.io.File inFile = new java.io.File(args[0]);
        java.io.File outFile = new java.io.File(args[1]);

        SimpleConsole console = new SimpleConsole(null, false, 
                                                  Console.MessageType.ERROR);

        URL url = inFile.toURI().toURL();
        Document doc = 
            com.xmlmind.ditac.util.LoadDocument.load(url, /*validate*/ false,
                                                     console);

        MapSimplifier simplifier = new MapSimplifier(null, console);
        boolean done = simplifier.simplify(doc, url);

        com.xmlmind.ditac.util.SaveDocument.save(doc, outFile);
        System.exit(done? 0 : 2);
    }
}
