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
import java.util.Stack;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import com.xmlmind.util.ThrowableUtil;
import com.xmlmind.util.URIComponent;
import com.xmlmind.util.URLUtil;
import com.xmlmind.ditac.util.DOMUtil;
import com.xmlmind.ditac.util.DITAUtil;
import com.xmlmind.ditac.util.ConsoleHelper;

/*package*/ final class SimplifyTopicrefs implements Constants {
    private static final class Ditavalmeta {
        public String resourcePrefix;
        public String resourceSuffix;
        public String keyscopePrefix;
        public String keyscopeSuffix;

        public static Ditavalmeta fromDitavalref(Element ditavalref) {
            Ditavalmeta ditavalmeta = null;

            Element container = 
                DITAUtil.findChildByClass(ditavalref,
                                          "ditavalref-d/ditavalmeta");
            if (container != null) {
                ditavalmeta = new Ditavalmeta();
                boolean empty = true;

                String text = getNonEmptyText(container,
                                              "ditavalref-d/dvrResourcePrefix");
                if (text != null) {
                    ditavalmeta.resourcePrefix = text;
                    empty = false;
                }

                text = getNonEmptyText(container,
                                       "ditavalref-d/dvrResourceSuffix");
                if (text != null) {
                    ditavalmeta.resourceSuffix = text;
                    empty = false;
                }

                text = getNonEmptyText(container,
                                       "ditavalref-d/dvrKeyscopePrefix");
                if (text != null) {
                    ditavalmeta.keyscopePrefix = text;
                    empty = false;
                }

                text = getNonEmptyText(container,
                                       "ditavalref-d/dvrKeyscopeSuffix");
                if (text != null) {
                    ditavalmeta.keyscopeSuffix = text;
                    empty = false;
                }

                if (empty) {
                    ditavalmeta = null;
                }
            }

            return ditavalmeta;
        }

        private static String getNonEmptyText(Element element, String cls) {
            Element childElement = DITAUtil.findChildByClass(element, cls);
            if (childElement != null) {
                String text = childElement.getTextContent();
                if (text != null) {
                    text = text.trim();
                    if (text.length() == 0) {
                        text = null;
                    }
                    return text;
                }
            }

            return null;
        }
    }

    // -----------------------------------------------------------------------

    public static void processMap(Element map, URL mapURL, 
                                  ConsoleHelper console) {
        Element[] ditavalrefs = DITAUtil.findDitavalrefs(map);
        if (ditavalrefs != null) {
            if (ditavalrefs.length > 1) {
                console.warning(Msg.msg("severalDitavalrefsInMap", 
                                        URLUtil.toLabel(mapURL)));

                for (int i = 1; i < ditavalrefs.length; ++i) {
                    map.removeChild(ditavalrefs[i]);
                }
            }

            // Make sure that the ditavalref is found before any other
            // topicref .

            Element firstTopicref =
                DITAUtil.findChildByClass(map, "map/topicref");
            if (firstTopicref != null && firstTopicref != ditavalrefs[0]) {
                map.removeChild(ditavalrefs[0]);
                map.insertBefore(ditavalrefs[0], firstTopicref);
            }
        }

        // ---

        processMap1(map);

        // Starting from here a map branch (including the map itself) directly
        // contains at most a single ditavalref ---

        Stack<Ditavalmeta> stack = new Stack<Ditavalmeta>();
        if (ditavalrefs != null) {
            Ditavalmeta pushed = Ditavalmeta.fromDitavalref(ditavalrefs[0]);
            if (pushed != null) {
                stack.push(pushed);
            }
        }

        processMap2(map, stack);
    }

    private static void processMap1(Element tree) {
        if (DITAUtil.hasClass(tree, "map/topicref")) {
            Element[] ditavalrefs = DITAUtil.findDitavalrefs(tree);
            int count;
            if (ditavalrefs != null && (count = ditavalrefs.length) > 1) {
                Node parentNode = tree.getParentNode();
                Node nextNode = tree.getNextSibling();

                for (int i = 1; i < count; ++i) {
                    Element copy = (Element) tree.cloneNode(/*deep*/ true);

                    Element[] ditavalrefs2 = DITAUtil.findDitavalrefs(copy);
                    for (int j = 0; j < count; ++j) {
                        if (j != i) {
                            copy.removeChild(ditavalrefs2[j]);
                        }
                    }

                    updateKeySpace(copy, "." + Integer.toString(1+i));

                    parentNode.insertBefore(copy, nextNode);
                }

                for (int i = 1; i < count; ++i) {
                    tree.removeChild(ditavalrefs[i]);
                }
            }
        }

        // ---
        
        Node child = tree.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                processMap1((Element) child);
            }

            child = child.getNextSibling();
        }
    }

    private static void updateKeySpace(Element tree, String suffix) {
        String keySpace = 
            DITAUtil.getNonEmptyAttribute(tree, DITAC_NS_URI, KEY_SPACE_NAME);
        if (keySpace != null) {
            tree.setAttributeNS(DITAC_NS_URI, KEY_SPACE_QNAME,
                                keySpace + suffix);
        }

        // ---
        
        Node child = tree.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                updateKeySpace((Element) child, suffix);
            }

            child = child.getNextSibling();
        }
    }

    private static void processMap2(Element tree, Stack<Ditavalmeta> stack) {
        Ditavalmeta pushed = null;

        if (DITAUtil.hasClass(tree, "map/topicref")) {
            Element[] ditavalrefs = DITAUtil.findDitavalrefs(tree);
            if (ditavalrefs != null) {
                pushed = Ditavalmeta.fromDitavalref(ditavalrefs[0]);
                if (pushed != null) {
                    stack.push(pushed);
                }
            }

            if (stack.size() > 0) {
                URL url = DITAUtil.getLocalTopicURL(tree);
                if (url != null) {
                    updateHref(tree, url.toExternalForm(), stack);
                }

                String keyscope = 
                    DITAUtil.getNonEmptyAttribute(tree, null, "keyscope");
                if (keyscope != null) {
                    updateKeyscope(tree, keyscope, stack);
                }
            }
        }

        // ---

        Node child = tree.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                processMap2((Element) child, stack);
            }

            child = child.getNextSibling();
        }

        if (pushed != null) {
            stack.pop();
        }
    }

    private static void updateHref(Element topicref, String href,
                                   Stack<Ditavalmeta> stack) {
        String[] split = splitHref(href);
        if (split == null) {
            return;
        }
        String head = split[0]; // Raw
        String name = split[1]; // Decoded.
        String tail = split[2]; // Raw. Possibly has a fragment.

        // ---

        StringBuilder buffer = new StringBuilder(name);
        for (int i = stack.size()-1; i >= 0; --i) {
            Ditavalmeta item = stack.get(i);

            if (item.resourcePrefix != null) {
                buffer.insert(0, item.resourcePrefix);
            }
            if (item.resourceSuffix != null) {
                buffer.append(item.resourceSuffix);
            }
        }

        String newName = buffer.toString();
        if (!newName.equals(name)) {
            buffer.setLength(0);
            buffer.append(head);
            buffer.append(DITAUtil.quotePathSegment(newName));
            buffer.append(tail);

            topicref.setAttributeNS(null, "href", buffer.toString());
            topicref.setAttributeNS(DITAC_NS_URI, COPY_OF_QNAME, href);
        }
    }

    private static String[] splitHref(String href) {
        String head = null;
        String name = null;
        String tail = null;

        int pos = href.lastIndexOf('#');
        if (pos < 0) {
            pos = href.length()-1;
        }

        pos = href.lastIndexOf('.', pos);
        if (pos >= 0) {
            tail = href.substring(pos); // Starts with '.'.
            head = href.substring(0, pos);

            pos = head.lastIndexOf('/');
            if (pos >= 0) {
                name = head.substring(pos+1);
                head = head.substring(0, pos+1); // Ends with '/'.
            }
        }

        if (name == null) {
            return null;
        }
        name = URIComponent.decode(name);

        return new String[] { head, name, tail };
    }

    private static void updateKeyscope(Element topicref, String keyscope,
                                       Stack<Ditavalmeta> stack) {
        StringBuilder buffer = new StringBuilder(keyscope);
        for (int i = stack.size()-1; i >= 0; --i) {
            Ditavalmeta item = stack.get(i);

            if (item.keyscopePrefix != null) {
                buffer.insert(0, item.keyscopePrefix);
            }
            if (item.keyscopeSuffix != null) {
                buffer.append(item.keyscopeSuffix);
            }
        }

        String newKeyscope = buffer.toString();
        if (!newKeyscope.equals(keyscope)) {
            topicref.setAttributeNS(null, "keyscope", newKeyscope);
        }
    }

    // -----------------------------------------------------------------------

    public static void duplicateTopics(Element map) {
        HashSet<URL> hrefs = new HashSet<URL>();
        HashMap<URL,int[]> serialNums = new HashMap<URL,int[]>();
        duplicateTopics(map, /*insideReltable*/ false, hrefs, serialNums);
    }

    private static void duplicateTopics(Element tree, boolean insideReltable,
                                        Set<URL> hrefs,
                                        Map<URL,int[]> serialNums) {
        Node child = tree.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (DITAUtil.hasClass(childElement, "map/topicref")) {
                    URL url = DITAUtil.getLocalTopicURL(childElement);
                    if (url != null && 
                        !insideReltable &&
                        !"resource-only".equals(
                            childElement.getAttributeNS(null,
                                                        "processing-role"))) {
                        checkHref(childElement, url, hrefs, serialNums);
                    }
                    // Otherwise, attribute href absent or does not point to
                    // a local topic.
                }

                boolean insideReltable2 = insideReltable;
                if (!insideReltable2) {
                    insideReltable2 =
                        DITAUtil.hasClass(childElement, "map/reltable");
                }
                duplicateTopics(childElement, insideReltable2,
                                hrefs, serialNums);
            }

            child = child.getNextSibling();
        }
    }

    private static void checkHref(Element topicref, URL url, 
                                  Set<URL> hrefs, Map<URL,int[]> serialNums) {
        boolean hasFragment = (url.getRef() != null);

        if (hrefs.contains(url) || 
            (hasFragment && hrefs.contains(URLUtil.setFragment(url, "*")))) {
            String href = url.toExternalForm();
            String[] split = splitHref(href);
            if (split == null) {
                return;
            }
            String head = split[0]; // Raw
            String name = split[1]; // Decoded.
            String tail = split[2]; // Raw. Possibly has a fragment.

            // ---

            String copyTo = 
                DITAUtil.getNonEmptyAttribute(topicref, null, "copy-to");
            if (copyTo != null) {
                // Just keep the ``root name'' specified in @copy-to.
                int pos = copyTo.lastIndexOf('/');
                if (pos >= 0) {
                    copyTo = copyTo.substring(pos+1);
                }

                pos = copyTo.lastIndexOf('\\');
                if (pos >= 0) {
                    copyTo = copyTo.substring(pos+1);
                }

                copyTo = URIComponent.setExtension(copyTo, null);

                copyTo = copyTo.trim();
                if (copyTo.length() == 0) {
                    copyTo = null;
                }
            }

            // ---
            
            String newName;
            if (copyTo != null) {
                newName = copyTo;
            } else {
                URL urlNoFrag = url;
                if (hasFragment) {
                    urlNoFrag = URLUtil.setRawFragment(url, null);
                }

                int[] value = serialNums.get(urlNoFrag);
                if (value == null) {
                    value = new int[] { 2 };
                    serialNums.put(urlNoFrag, value);
                }
                int serialNum = value[0]++;

                StringBuilder buffer = new StringBuilder();
                buffer.append("__");
                buffer.append(name);
                buffer.append('-');
                buffer.append(Integer.toString(serialNum));
                newName = buffer.toString();
            }

            if (!newName.equals(name)) {
                StringBuilder buffer = new StringBuilder();
                buffer.append(head);
                buffer.append(DITAUtil.quotePathSegment(newName));
                buffer.append(tail);

                topicref.setAttributeNS(null, "href", buffer.toString());
                topicref.setAttributeNS(DITAC_NS_URI, COPY_OF_QNAME, href);
            }
        } else {
            hrefs.add(url);

            if (hasFragment) {
                // Seen <topicref href="foo.dita#foo"> ==> after that,
                // <topicref href="foo.dita"> must be a copy.
                // (But not <topicref href="foo.dita#bar">!)

                hrefs.add(URLUtil.setRawFragment(url, null));
            } else {
                // Seen <topicref href="foo.dita> ==> after that,
                // <topicref href="foo.dita#WHATEVER"> must be a copy.

                hrefs.add(URLUtil.setFragment(url, "*"));
            }
        }
    }

    // -----------------------------------------------------------------------

    public static boolean createTopicCopies(Element tree,
                                            LoadedDocuments loadedDocs) {
        if (DITAUtil.hasClass(tree, "map/topicref")) {
            URL originalURL = null;

            String copyOf = tree.getAttributeNS(DITAC_NS_URI, COPY_OF_NAME);
            if (copyOf != null && copyOf.length() > 0) {
                tree.removeAttributeNS(DITAC_NS_URI, COPY_OF_NAME);

                try {
                    originalURL = URLUtil.createURL(copyOf);
                } catch (MalformedURLException shouldNotHappen) {
                    loadedDocs.getConsole().error(tree, 
                                                 Msg.msg("invalidAttribute",
                                                        copyOf, COPY_OF_QNAME));
                    return false;
                }

                if (originalURL.getRef() != null) {
                    originalURL = URLUtil.setRawFragment(originalURL, null);
                }
            }

            if (originalURL != null) {
                URL copyURL = null;

                String href = tree.getAttributeNS(null, "href");
                if (href != null && href.length() > 0) {
                    try {
                        copyURL = URLUtil.createURL(href);
                    } catch (MalformedURLException shouldNotHappen) {}
                }

                if (copyURL == null) {
                    loadedDocs.getConsole().error(tree, 
                                                  Msg.msg("invalidAttribute",
                                                          href, "href"));
                    return false;
                }

                if (copyURL.getRef() != null) {
                    copyURL = URLUtil.setRawFragment(copyURL, null);
                }

                // ---

                LoadedDocument originalDoc;
                try {
                    originalDoc = loadedDocs.load(originalURL);
                } catch (Exception e) {
                    loadedDocs.getConsole().error(
                        tree, 
                        Msg.msg("cannotLoad", originalURL,
                                ThrowableUtil.reason(e)));
                    return false;
                }

                // ---

                Document copy = DOMUtil.newDocument();
                copy.appendChild(
                    copy.importNode(originalDoc.document.getDocumentElement(), 
                                    /*deep*/ true));
                copy.setDocumentURI(copyURL.toExternalForm());

                // ---

                LoadedDocument copyDoc = loadedDocs.put(copyURL, copy, 
                                                        /*process*/ false);
                copyDoc.setSynthetic(originalURL);
            }
        }

        // ---
        
        Node child = tree.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (!createTopicCopies((Element) child, loadedDocs)) {
                    return false;
                }
            }

            child = child.getNextSibling();
        }

        return true;
    }
}
