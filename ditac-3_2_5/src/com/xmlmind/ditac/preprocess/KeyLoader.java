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
import java.util.Stack;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import com.xmlmind.util.ThrowableUtil;
import com.xmlmind.util.StringList;
import com.xmlmind.util.StringUtil;
import com.xmlmind.util.URIComponent;
import com.xmlmind.util.URLUtil;
import com.xmlmind.util.FileUtil;
import com.xmlmind.util.XMLText;
import com.xmlmind.util.Console;
import com.xmlmind.ditac.util.SimpleConsole;
import com.xmlmind.ditac.util.ConsoleHelper;
import com.xmlmind.ditac.util.DOMUtil;
import com.xmlmind.ditac.util.DITAUtil;
import com.xmlmind.ditac.util.LoadDocument;

/*package*/ final class KeyLoader implements Constants {
    private ConsoleHelper console;    
    private URL mapURL;
    private Document mapDoc;
    private Filters filters;

    private static final int LAST_PASS = 9;

    // -----------------------------------------------------------------------

    public KeyLoader() {
        this(null);
    }

    public KeyLoader(Console console) {
        setConsole(console);
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

    public void setFilters(Filters filters) {
        this.filters = filters;
    }

    public Filters getFilters() {
        return filters;
    }

    // ---------------------------------
    // prepareMap
    // ---------------------------------

    public boolean prepareMap(Document doc, URL url) 
        throws IOException {
        // Map document must NOT have been processed by LoadedDocuments.
        // This is done by MapSimplifier.

        mapDoc = (Document) doc.cloneNode(/*deep*/ true);
        mapURL = url;

        return (new MapSimplifier(/*keySpaces*/ null,
                                  console)).simplify(mapDoc, mapURL);
    }

    // ---------------------------------
    // createKeySpaces
    // ---------------------------------

    public KeySpaces createKeySpaces(LoadedDocuments loadedTopics) {
        if (loadedTopics == null) {
            loadedTopics = new LoadedDocuments(/*keySpaces*/ null, console);
        }

        if (mapDoc == null) {
            throw new IllegalStateException("no map");
        }

        Element map = mapDoc.getDocumentElement();
        if (filters != null) {
            filters.filterMap(map);
        }

        KeySpaces keySpaces = new KeySpaces();

        // Add keys ---

        int[] keyrefCount = new int[1];
        int prevKeyrefCount = -1;
        long now = System.currentTimeMillis();
        int iteration = 0;

        Stack<KeySpace> keySpaceStack = new Stack<KeySpace>();
        String keyscope = DITAUtil.getNonEmptyAttribute(map, null, "keyscope");
        if (keyscope != null) {
            keySpaces.rootKeySpace.initKeyscopeNames(keyscope);
        }
        keySpaceStack.push(keySpaces.rootKeySpace);

        for (int pass = 0; pass <= LAST_PASS; ++pass) {
            console.debug(Msg.msg("iteration", iteration++));

            keyrefCount[0] = 0;
            if (!collectKeys(map, loadedTopics, keySpaceStack,
                             keySpaces, keyrefCount, (pass == LAST_PASS))) {
                return null;
            }

            if (keyrefCount[0] == 0) {
                // Done.
                break;
            }

            if (keyrefCount[0] == prevKeyrefCount) {
                // No change. Next pass will be the last one.
                pass = LAST_PASS-1;
            }
            prevKeyrefCount = keyrefCount[0];
        }

        keySpaceStack.pop();

        console.debug(Msg.msg("keysCollected", 
                              iteration, System.currentTimeMillis()-now));

        return keySpaces;
    }

    // ---------------------------------
    // collectKeys
    // ---------------------------------

    private boolean collectKeys(Element element, 
                                LoadedDocuments loadedTopics, 
                                Stack<KeySpace> keySpaceStack,
                                KeySpaces keySpaces,
                                int[] keyrefCount, boolean lastPass) {

        Node child = element.getFirstChild();
        while (child != null) {
            switch (child.getNodeType()) {
            case Node.PROCESSING_INSTRUCTION_NODE:
                {
                    ProcessingInstruction pi = (ProcessingInstruction) child;
                    String piTarget = pi.getTarget();

                    if (BEGIN_GROUP_PI_TARGET.equals(piTarget)) {
                        KeySpace keySpace =
                            pushKeySpace(pi, keySpaceStack, keySpaces);
                        if (keySpace == null) {
                            console.error(element, 
                                          Msg.msg("invalidPI", 
                                                  piTarget, pi.getData()));
                            return false;
                        }
                    } else if (END_GROUP_PI_TARGET.equals(piTarget)) {
                        keySpaceStack.pop();
                    }
                }
                break;

            case Node.ELEMENT_NODE: 
                {
                    Element childElement = (Element) child;

                    KeySpace keySpace;
                    try {
                        keySpace =
                          pushKeySpace(childElement, keySpaceStack, keySpaces);
                    } catch (Exception e) {
                        console.error(childElement, ThrowableUtil.reason(e));
                        return false;
                    }

                    if (!doCollectKeys(childElement, loadedTopics,keySpaceStack,
                                       keySpaces, keyrefCount, lastPass)) {
                        return false;
                    }

                    if (!collectKeys(childElement, loadedTopics, keySpaceStack,
                                     keySpaces, keyrefCount, lastPass)) {
                        return false;
                    }

                    if (keySpace != null) {
                        keySpaceStack.pop();
                    }
                }
                break;
            }

            child = child.getNextSibling();
        }

        return true;
    }

    private KeySpace pushKeySpace(ProcessingInstruction pi,
                                  Stack<KeySpace> keySpaceStack,
                                  KeySpaces keySpaces) {
        KeySpace keySpace = null;

        String data = pi.getData();
        String keySpaceId =
            KeySpaces.parsePseudoAttribute(KEY_SPACE_START, data);
        if (keySpaceId != null) {
            String keyscope = 
                KeySpaces.parsePseudoAttribute("keyscope=\"", data);
            if (keyscope != null) {
                keySpace = addKeySpace(keySpaces, keySpaceId, keyscope, 
                                       keySpaceStack.peek());
            }
        }

        if (keySpace != null) {
            keySpaceStack.push(keySpace);
        }

        return keySpace;
    }

    private static KeySpace pushKeySpace(Element element,
                                         Stack<KeySpace> keySpaceStack,
                                         KeySpaces keySpaces) 
        throws RuntimeException {
        KeySpace keySpace = null;

        String keySpaceId = 
          DITAUtil.getNonEmptyAttribute(element, DITAC_NS_URI, KEY_SPACE_NAME);
        if (keySpaceId != null) {
            String keyscope =
                DITAUtil.getNonEmptyAttribute(element, null, "keyscope");
            if (keyscope == null) {
                throw new RuntimeException(Msg.msg("missingKeyscope",
                                                   keySpaceId));
            }

            keySpace = addKeySpace(keySpaces, keySpaceId, keyscope, 
                                   keySpaceStack.peek());
        }

        if (keySpace != null) {
            keySpaceStack.push(keySpace);
        }

        return keySpace;
    }

    private static KeySpace addKeySpace(KeySpaces keySpaces, 
                                        String keySpaceId, String keyscope, 
                                        KeySpace parent) {
        KeySpace keySpace = keySpaces.keySpaces.get(keySpaceId);
        if (keySpace == null) {
            keySpace = new KeySpace(keySpaceId, keyscope);
            keySpace.initParentKeySpace(parent);

            parent.addChildKeySpace(keySpace);

            keySpaces.keySpaces.put(keySpaceId, keySpace);
        }

        return keySpace;
    }

    private boolean doCollectKeys(Element element, 
                                  LoadedDocuments loadedTopics, 
                                  Stack<KeySpace> keySpaceStack,
                                  KeySpaces keySpaces,
                                  int[] keyrefCount, boolean lastPass) {
        String keyList;
        if (DITAUtil.hasClass(element, "map/topicref") &&
            (keyList = 
             DITAUtil.getNonEmptyAttribute(element, null, "keys")) != null) {
            boolean add = true;
            boolean skip = false;

            if (DITAUtil.getNonEmptyAttribute(element,
                                              null, "conkeyref") != null) {
                console.warning(element, 
                                Msg.msg("ignoringAttrInKeydef", 
                                        "conkeyref", keyList));
                skip = true;
            }

            String keyref = DITAUtil.getNonEmptyAttribute(element, 
                                                          null, "keyref");
            if (keyref != null) {
                if (keyref.lastIndexOf('/') < 0) {
                    String href = null; 

                    KeyDefinition kd = keySpaces.get(keyref, element);
                    if (kd != null) {
                        // DON'T UNDERSTAND:
                        // ---
                        // If, in addition to the @keys attribute, a
                        // key definition specifies a @keyref
                        // attribute that can be resolved after the
                        // key resolution context for the key
                        // definition has been determined, the
                        // resources bound to the referenced key
                        // definition take precedence.
                        // ---
                        // This implementation is based on the example:
                        // http://docs.oasis-open.org/dita/dita/v1.3/
                        //   os/part2-tech-content/archSpec/base/
                        //   example-keydef-with-keyref.html

                        /*
                        loadedTopics.processKeyref(kd, null,
                                                   element);
                        */

                        setHref(kd.element, element);

                        loadedTopics.addMetadata(kd, element);
                        // add==true.
                    } else {
                        if (lastPass) {
                            console.warning(element, 
                                            Msg.msg("ignoringAttrInKeydef", 
                                                    "keyref", keyList));

                            if (DITAUtil.getNonEmptyAttribute(
                                    element, null, "href") == null && 
                                !DOMUtil.hasContent(element)) {
                                skip = true;
                            }
                            // Otherwise, add==true.
                        } else {
                            // Do not add during this pass. 
                            // Retry later.
                            add = false;
                            ++keyrefCount[0];
                        }
                    }
                } else {
                    // Something like keys="foo" keyref="bar/gee" is
                    // not a usable key definition (because the keyref
                    // must point to a keydef, not to a non-topic
                    // element).
                            
                    console.warning(element, 
                                    Msg.msg("ignoringAttrInKeydef", 
                                            "keyref", keyList));

                    if (DITAUtil.getNonEmptyAttribute(element,
                                                      null, "href") == null && 
                        !DOMUtil.hasContent(element)) {
                        skip = true;
                    }
                    // Otherwise, add==true.
                }
            }

            if (skip) {
                // Mark as processed.
                element.removeAttributeNS(null, "keys");
                element.removeAttributeNS(null, "keyref");

                console.warning(element, 
                                Msg.msg("skippingKeydef", keyList));
            } else if (add) {
                // Mark as processed.
                element.removeAttributeNS(null, "keys");
                element.removeAttributeNS(null, "keyref");

                if (!addKeys(keyList, element, loadedTopics,
                             keySpaceStack.peek())) {
                    return false;
                }
            }
            // Otherwise, may be during next pass.
        }

        return true;
    }

    private static void setHref(Element from, Element to) {
        if (DITAUtil.getNonEmptyAttribute(to, null, "href") == null && 
            DITAUtil.getNonEmptyAttribute(from, null, "href") != null) {
            final int count = LoadedDocuments.LINKING_ATTRIBUTES.length;
            for (int i = 0; i < count; i += 3) {
                String ns = LoadedDocuments.LINKING_ATTRIBUTES[i];
                String qName = LoadedDocuments.LINKING_ATTRIBUTES[i+1];
                String localName = LoadedDocuments.LINKING_ATTRIBUTES[i+2];

                String value = 
                    DITAUtil.getNonEmptyAttribute(from, ns, localName);
                if (value == null) {
                    to.removeAttributeNS(ns, localName);
                } else {
                    to.setAttributeNS(ns, qName, value);
                }
            }
        }
    }

    // ---------------------------------
    // addKeys
    // ---------------------------------

    private static String[] SKIPPED_ATTRIBUTES = {
        "keys",
        "processing-role",
        "id",
        "class",
        "keyref",
        "keyscope"
    };

    private boolean addKeys(String keyList, Element element, 
                            LoadedDocuments loadedTopics, KeySpace keySpace) {
        // Quick check ---

        boolean add = false;

        String[] keyItems = XMLText.splitList(keyList);
        for (String key : keyItems) {
            if (!keySpace.contains(key)) {
                add = true;
                break;
            }
        }

        if (!add) {
            return true;
        }

        // Create the topicref which is bound to the key ---

        String href = null;
        String scope = null;
        String format = null;

        Element topicref = mapDoc.createElementNS(null, "topicref");
        topicref.setAttributeNS(null, "class", "- map/topicref ");

        NamedNodeMap attrs = element.getAttributes();
        int attrCount = attrs.getLength();
        
        for (int i = 0; i < attrCount; ++i) {
            Attr attr = (Attr) attrs.item(i);

            String attrName = attr.getName(); // A QName.
            String attrValue = attr.getValue();

            if (!StringList.contains(SKIPPED_ATTRIBUTES, attrName) &&
                (attrValue = attrValue.trim()).length() > 0) {
                topicref.setAttributeNS(attr.getNamespaceURI(), attrName, 
                                        attrValue);

                if ("href".equals(attrName)) {
                    href = attrValue;
                } else if ("scope".equals(attrName)) {
                    scope = attrValue;
                } else if ("format".equals(attrName)) {
                    format = attrValue.toLowerCase();
                }
            }
        }

        Element meta = DITAUtil.findChildByClass(element, "map/topicmeta");
        if (meta != null) {
            topicref.appendChild(meta.cloneNode(/*deep*/ true));
        }

        // Normalize the href of a topic ---

        if (href != null) {
            URL url = null;
            String ext = null;
            try {
                // Href has been made absolute by LoadedDocuments.
                url = URLUtil.createURL(href);

                ext = URLUtil.getExtension(url);
                if (ext != null) {
                    ext = ext.toLowerCase();
                }
            } catch (MalformedURLException ignored) {}

            String ref;
            if (url != null &&
                ((ref = url.getRef()) == null || ".".equals(ref)) && 
                (scope == null || "local".equals(scope.trim())) &&
                ((format != null && "dita".equals(format.trim())) ||
                 (format == null && 
                  ("dita".equals(ext) || "xml".equals(ext))))) {
                // We have a local topic whose URL has no #topic_id fragment.

                url = KeySpaces.getOriginalTopicURL(element, url);

                LoadedDocument loadedDoc;
                try {
                    loadedDoc = loadedTopics.load(url, /*process*/ false);
                } catch (Exception e) {
                    console.error(element, Msg.msg("cannotLoad", url, 
                                                  ThrowableUtil.reason(e)));
                    return false;
                }

                LoadedTopic firstTopic = loadedDoc.getFirstTopic();
                if (firstTopic != null) {
                    href = URIComponent.setFragment(href, firstTopic.topicId);
                    topicref.setAttributeNS(null, "href", href);
                }
            }
        }

        // Add keys ---

        for (String key : keyItems) {
            if (!keySpace.contains(key)) {
                KeyDefinition kd = 
                    new KeyDefinition(key, topicref, /*fromChildKeySpace*/ 0);
                keySpace.set(kd);
                
                addKeyDefinitionToAncestors(kd, keySpace);
            }
        }

        return true;
    }

    private static void addKeyDefinitionToAncestors(KeyDefinition kd,
                                                    KeySpace keySpace) {
        KeySpace parent = keySpace.getParentKeySpace();
        if (parent != null) {
            for (String keyscopeName : keySpace.getKeyscopeNames()) {
                String key2 = keyscopeName + "." + kd.key;
                int fromChildKeySpace2 = kd.fromChildKeySpace + 1;

                KeyDefinition kd2 = 
                    new KeyDefinition(key2, kd.element, fromChildKeySpace2);

                if (!parent.contains(key2)) {
                    parent.set(kd2);
                }

                addKeyDefinitionToAncestors(kd2, parent);
            }
        }
    }
}
