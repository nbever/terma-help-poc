/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.preprocess;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Map;
import java.util.HashMap;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import com.xmlmind.util.ThrowableUtil;
import com.xmlmind.util.URIComponent;
import com.xmlmind.util.URLUtil;
import com.xmlmind.ditac.util.DOMUtil;
import com.xmlmind.ditac.util.DITAUtil;

/**
 * Not part of the public, documented, API.
 */
public final class KeySpaces implements Constants {
    /*package*/ KeySpace rootKeySpace;
    /*package*/ HashMap<String,KeySpace> keySpaces;

    /*package*/ HashMap<String,KeySpace> topicURIToKeySpace;

    private ArrayList<KeySpace> keySpaceHierarchy;

    // -----------------------------------------------------------------------

    public KeySpaces() {
        rootKeySpace = new KeySpace("0", null);
        keySpaces = new HashMap<String,KeySpace>();
        keySpaces.put(rootKeySpace.id, rootKeySpace);

        topicURIToKeySpace = new HashMap<String,KeySpace>();

        keySpaceHierarchy = new ArrayList<KeySpace>();
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        toString(rootKeySpace, /*details*/ false, buffer);

        if (topicURIToKeySpace.size() > 0) {
            buffer.append("Topic URI to key space:");

            for (Map.Entry<String,KeySpace> e : topicURIToKeySpace.entrySet()) {
                buffer.append('\n');
                buffer.append(e.getKey());
                buffer.append('=');
                buffer.append(e.getValue().id);
            }
        }

        return buffer.toString();
    }

    private void toString(KeySpace keySpace, boolean details,
                          StringBuilder buffer) {
        buffer.append("---\n");
        keySpace.toString(details, buffer);
        buffer.append("\n---\n");

        for (KeySpace child : keySpace.getChildKeySpaces()) {
            toString(child, details, buffer);
        }
    }

    public KeySpace getRootKeySpace() {
        return rootKeySpace;
    }

    public KeyDefinition get(String key, Element context) {
        KeySpace keySpace = getKeySpace(context);
        if (keySpace == null) {
            return null;
        }

        return lookupKeyDefinition(keySpace, key);
    }

    private KeySpace getKeySpace(Element element) {
        Element topic = DITAUtil.findAncestorByClass(element, "topic/topic");
        if (topic != null) {
            String location = null;
            Document doc = topic.getOwnerDocument();
            if (doc != null) {
                location = doc.getDocumentURI();
            }

            if (location != null) {
                // Trim fragment just in case.
                int pos = location.lastIndexOf('#');
                if (pos >= 0) {
                    location = location.substring(0, pos);
                }

                String id = DITAUtil.getNonEmptyAttribute(topic, null, "id");
                if (id != null) {
                    StringBuilder buffer = new StringBuilder(location);
                    buffer.append('#');
                    buffer.append(URIComponent.quoteFragment(id));
                    location = buffer.toString();

                    return getTopicKeySpace(location);
                }
            }
        } else {
            // Inside a map ---

            String keySpaceId = null;

            loop: while (element != null) {
                keySpaceId = 
                    DITAUtil.getNonEmptyAttribute(element, 
                                                  DITAC_NS_URI, KEY_SPACE_NAME);
                if (keySpaceId != null) {
                    break;
                }

                int nesting = 0;

                Node prevNode = element.getPreviousSibling();
                while (prevNode != null) {
                    if (prevNode.getNodeType() == 
                            Node.PROCESSING_INSTRUCTION_NODE) {
                        ProcessingInstruction pi = 
                            (ProcessingInstruction) prevNode;
                        String piTarget = pi.getTarget();

                        if (END_GROUP_PI_TARGET.equals(piTarget)) {
                            ++nesting;
                        } else if (BEGIN_GROUP_PI_TARGET.equals(piTarget)) {
                            if (nesting > 0) {
                                --nesting;
                            } else {
                                keySpaceId = 
                                    parsePseudoAttribute(KEY_SPACE_START,
                                                         pi.getData());
                                break loop;
                            }
                        }
                    }

                    prevNode = prevNode.getPreviousSibling();
                }

                element = DOMUtil.getParentElement(element);
            }

            if (keySpaceId != null) {
                return getKeySpaceById(keySpaceId);
            }
        }

        return null;
    }

    /*package*/ static String parsePseudoAttribute(String attrStart,
                                                   String data) {
        int start = data.indexOf(attrStart);
        if (start >= 0) {
            start += attrStart.length();

            int end = data.indexOf('"', start+1);
            if (end >= 0) {
                return data.substring(start, end);
            }
        }

        // Should not happen.
        return null;
    }

    public KeySpace getKeySpaceById(String keySpaceId) {
        if (keySpaceId == null) {
            return null;
        } else {
            return keySpaces.get(keySpaceId);
        }
    }

    public KeySpace getTopicKeySpace(String topicLocation) {
        KeySpace keySpace = topicURIToKeySpace.get(topicLocation);
        if (keySpace == null) {
            // This often happens for topics which 
            // 1) contain content to be conref-ed;
            // 2) are not referenced as resources by the map.
            keySpace = rootKeySpace;
        }
        return keySpace;
    }

    public KeyDefinition lookupKeyDefinition(KeySpace keySpace, String key) {
        if (keySpace == rootKeySpace) {
            return keySpace.get(key);
        }

        // ---

        keySpaceHierarchy.clear();
        while (keySpace != null) {
            keySpaceHierarchy.add(keySpace);
            keySpace = keySpace.getParentKeySpace();
        }

        // Search from root to specified keySpace.
        for (int i = keySpaceHierarchy.size()-1; i >= 0; --i) {
            KeyDefinition kd = keySpaceHierarchy.get(i).get(key);
            if (kd != null) {
                return kd;
            }
        }

        return null;
    }

    public String getHref(String key, Element context) {
        KeyDefinition kd = get(key, context);
        return (kd == null)? null : kd.getAttribute(null, "href");
    }

    // -----------------------------------------------------------------------

    public boolean mapTopicsToKeySpaces(Element map,
                                        LoadedDocuments loadedDocs) {
        topicURIToKeySpace.clear();

        Stack<KeySpace> keySpaceStack = new Stack<KeySpace>();
        keySpaceStack.push(rootKeySpace);

        boolean done = mapTopicsToKeySpaces(map, loadedDocs, keySpaceStack);

        keySpaceStack.pop();

        return done;
    }

    private boolean mapTopicsToKeySpaces(Element element,
                                         LoadedDocuments loadedDocs,
                                         Stack<KeySpace> keySpaceStack) {
        Node child = element.getFirstChild();
        while (child != null) {
            switch (child.getNodeType()) {
            case Node.PROCESSING_INSTRUCTION_NODE:
                {
                    ProcessingInstruction pi = (ProcessingInstruction) child;
                    String piTarget = pi.getTarget();

                    if (BEGIN_GROUP_PI_TARGET.equals(piTarget)) {
                        String keySpaceId = 
                            parsePseudoAttribute(KEY_SPACE_START, pi.getData());

                        KeySpace keySpace = getKeySpaceById(keySpaceId);
                        if (keySpace == null) {
                            loadedDocs.getConsole().error(
                                element,
                                Msg.msg("unknownKeySpace", keySpaceId));
                            return false;
                        }

                        keySpaceStack.push(keySpace);
                    } else if (END_GROUP_PI_TARGET.equals(piTarget)) {
                        keySpaceStack.pop();
                    }
                }
                break;

            case Node.ELEMENT_NODE: 
                {
                    Element childElement = (Element) child;

                    KeySpace keySpace = null;
                    String keySpaceId =
                      DITAUtil.getNonEmptyAttribute(childElement, DITAC_NS_URI,
                                                    KEY_SPACE_NAME);
                    if (keySpaceId != null) {
                        keySpace = getKeySpaceById(keySpaceId);
                        if (keySpace == null) {
                            loadedDocs.getConsole().error(
                                childElement,
                                Msg.msg("unknownKeySpace", keySpaceId));
                            return false;
                        }

                        keySpaceStack.push(keySpace);
                    }

                    URL url;
                    if (DITAUtil.hasClass(childElement, "map/topicref") &&
                        (url=DITAUtil.getLocalTopicURL(childElement)) != null) {
                        String topicId = DITAUtil.getTopicId(url);
                        url = URLUtil.setRawFragment(url, null);

                        LoadedDocument loadedDoc = null;
                        URL originalURL = 
                            getOriginalTopicURL(childElement, url);
                        try {
                            loadedDoc = loadedDocs.load(originalURL, 
                                                        /*preprocess*/ false);
                        } catch (Exception e) {
                            loadedDocs.getConsole().error(
                              childElement, 
                              Msg.msg("cannotLoad", originalURL, 
                                      ThrowableUtil.reason(e)));
                            return false;
                        }

                        if (loadedDoc != null && 
                            (loadedDoc.type==LoadedDocument.Type.MULTI_TOPIC || 
                             loadedDoc.type==LoadedDocument.Type.TOPIC)) {
                            Select select = (topicId == null)? 
                                Select.DOCUMENT : Select.TOPIC;

                            String value = 
                                childElement.getAttributeNS(null, "chunk");
                            if (value != null) {
                                if (value.indexOf("select-document") >= 0) {
                                    select = Select.DOCUMENT;
                                } else if (value.indexOf("select-branch")>=0) {
                                    select = Select.BRANCH;
                                } else if (value.indexOf("select-topic") >= 0) {
                                    select = Select.TOPIC;
                                }
                            }

                            LoadedTopic loadedTopic = null;
                            if (topicId != null) {
                                loadedTopic = loadedDoc.findTopicById(topicId);
                                if (loadedTopic == null) {
                                    loadedDocs.getConsole().error(
                                        childElement,
                                        Msg.msg("topicNotFound", topicId, 
                                               URLUtil.toLabel(loadedDoc.url)));
                                    return false;
                                }
                            } else {
                                loadedTopic = loadedDoc.getFirstTopic();
                            }

                            if (loadedTopic != null) {
                                KeySpace ks = keySpaceStack.peek();

                                if (loadedDoc.getSingleTopic() != null) {
                                    // Most common case.
                                    mapTopicToKeySpace(url, loadedTopic, ks);
                                } else {
                                    switch (select) {
                                    case TOPIC:
                                        mapTopicToKeySpace(url, loadedTopic,
                                                           ks);
                                        break;
                                    case BRANCH:
                                        mapBranchToKeySpaces(url, loadedTopic,
                                                             ks);
                                        break;
                                    case DOCUMENT:
                                        mapDocumentToKeySpaces(url, loadedDoc,
                                                               ks);
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (!mapTopicsToKeySpaces(childElement, loadedDocs, 
                                              keySpaceStack)) {
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

    /*package*/ static URL getOriginalTopicURL(Element topicref, URL fallback) {
        // With ditavalref/ditavalmeta, topic copies are referenced but have
        // not been created yet, so get the ID of the original topic.

        URL url = fallback;

        String copyOf = topicref.getAttributeNS(DITAC_NS_URI, COPY_OF_NAME);
        if (copyOf != null && copyOf.length() > 0) {
            try {
                url = URLUtil.createURL(copyOf);
            } catch (MalformedURLException shouldNotHappen) {}
        }

        return url;
    }

    private void mapTopicToKeySpace(URL docURL, LoadedTopic loadedTopic,
                                    KeySpace keySpace) {
        topicURIToKeySpace.put(docURL.toExternalForm() + "#" + 
                               URIComponent.quoteFragment(loadedTopic.topicId),
                               keySpace);
    }

    private void mapBranchToKeySpaces(URL docURL, LoadedTopic loadedTopic,
                                      KeySpace keySpace) {
        mapTopicToKeySpace(docURL, loadedTopic, keySpace);

        for (LoadedTopic nestedTopic : loadedTopic.getNestedTopics()) {
            mapBranchToKeySpaces(docURL, nestedTopic, keySpace);
        }
    }

    private void mapDocumentToKeySpaces(URL docURL, LoadedDocument loadedDoc,
                                        KeySpace keySpace) {
        for (LoadedTopic loadedTopic : loadedDoc.getTopics()) {
            mapBranchToKeySpaces(docURL, loadedTopic, keySpace);
        }
    }
}
