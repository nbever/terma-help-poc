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
import java.util.ArrayList;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import com.xmlmind.util.ArrayUtil;
import com.xmlmind.util.URIComponent;
import com.xmlmind.util.URLUtil;
import com.xmlmind.util.Console;
import com.xmlmind.ditac.util.DOMUtil;
import com.xmlmind.ditac.util.DITAUtil;

/*package*/ final class MaprefIncluder extends Includer implements Constants {
    private static final class MaprefIncl extends Incl {
        public final URL targetURL;
        public final String targetId;
        public final Element[] ditavalrefs;

        public MaprefIncl(Element directiveElement, URL url,
                          Element[] ditavalrefs) {
            super(directiveElement);
            
            targetId = URLUtil.getFragment(url);
            if (targetId != null) {
                targetURL = URLUtil.setFragment(url, null);
            } else {
                targetURL = url;
            }

            this.ditavalrefs = ditavalrefs;
        }

        public String getMaprefHref() {
            if (targetId != null) {
                StringBuilder buffer =
                    new StringBuilder(targetURL.toExternalForm());
                buffer.append('#');
                buffer.append(URIComponent.quoteFragment(targetId));
                return buffer.toString();
            } else {
                return targetURL.toExternalForm();
            }
        }
    }

    // -----------------------------------------------------------------------

    public MaprefIncluder(KeySpaces keySpaces, Console c) {
        super(keySpaces, c);
    }

    protected Incl detectInclusion(Element element) {
        // The map containing the referencing topicref is assumed to have been
        // processed using CascadeMeta.processMap. No need to lookup
        // attributes.

        if (!DITAUtil.hasClass(element, "map/topicref")) {
            return null;
        }

        String href = DITAUtil.getNonEmptyAttribute(element, null, "href");
        if (href == null) {
            return null;
        }

        String scope = DITAUtil.getScope(element, href);
        if (!"local".equals(scope)) {
            return null;
        }

        String format = DITAUtil.getFormat(element, href);
        if (format == null) {
            console.warning(element,
                            Msg.msg(Msg.msg("missingAttribute", "format")));
            return null;
        }
        if (!"ditamap".equals(format)) {
            return null;
        }

        String type = DITAUtil.getNonEmptyAttribute(element, null, "type");
        if ("subjectScheme".equals(type)) {
            return null;
        }

        // Note that maprefs having processing-role="resource-only" are
        // included too.

        URL url = null;
        try {
            url = URLUtil.createURL(href);
        } catch (MalformedURLException ignored) {
            console.warning(element,
                            Msg.msg("invalidAttribute", href, "href"));
        }
        if (url == null) {
            return null;
        }

        return new MaprefIncl(element, url, DITAUtil.findDitavalrefs(element));
    }
    
    protected void fetchIncluded(Incl incl)
        throws IOException, InclusionException {
        MaprefIncl maprefIncl = (MaprefIncl) incl;

        Doc targetDoc = fetchDoc(maprefIncl.targetURL);

        Element rootElement = targetDoc.document.getDocumentElement();
        if (!DITAUtil.hasClass(rootElement, "map/map")) {
            throw new InclusionException(Msg.msg("notAMap", 
                                                 maprefIncl.targetURL));
        }

        if (DITAUtil.hasClass(rootElement, "subjectScheme/subjectScheme")) {
            // In case, attribute type was not set.
            console.warning(incl.directiveElement,
                            Msg.msg("ignoringSubjectSchemeMap",
                                    maprefIncl.targetURL));

            maprefIncl.replacementNodes = maprefIncl.appendedNodes = null;
            return;
        }

        Element target = null;
        if (maprefIncl.targetId != null) {
            // Branch or the whole map (e.g. href="foo.ditamap#foo").
            target = DITAUtil.findElementById(targetDoc.document, 
                                              maprefIncl.targetId);
        } else {
            // Whole map.
            target = rootElement;
        }

        if (target == null) {
            throw new InclusionException(
                Msg.msg("targetNotFound", maprefIncl.getMaprefHref(), "href"));
        }

        copyTarget(target, maprefIncl);

        if (maprefIncl.ditavalrefs != null) {
            insertDitavalrefs(maprefIncl);
        }

        createKeyscopeGroup(target, maprefIncl);
    }

    private static void copyTarget(Element mapOrTopicref, MaprefIncl incl) {
        // The referenced map or branch is assumed to have been processed
        // using CascadeMeta.processMap. Therefore the referenced topicrefs
        // have their proper, local, xml:lang, dir, etc.

        Element directiveElement = incl.directiveElement;
        Document doc = directiveElement.getOwnerDocument();

        if (DITAUtil.hasClass(mapOrTopicref, "map/map")) {
            // Copy map contents ---

            ArrayList<Node> replacement = new ArrayList<Node>();
            ArrayList<Node> appended = new ArrayList<Node>();

            Node child = mapOrTopicref.getFirstChild();
            while (child != null) {
                switch (child.getNodeType()) {
                case Node.PROCESSING_INSTRUCTION_NODE:
                    {
                        ProcessingInstruction pi =
                            (ProcessingInstruction) child;
                        String piTarget = pi.getTarget();
                        boolean isBeginGroup = 
                            BEGIN_GROUP_PI_TARGET.equals(piTarget);
                        boolean isEndGroup = 
                            END_GROUP_PI_TARGET.equals(piTarget);

                        if (isBeginGroup || isEndGroup) {
                            ArrayList<Node> list;

                            Element siblingElement = null;
                            if (isBeginGroup && 
                                (siblingElement =
                                 getNextElement(child)) != null &&
                                DITAUtil.hasClass(siblingElement, 
                                                  "map/reltable")) {
                                list = appended;
                            } else if (isEndGroup && 
                                       (siblingElement =
                                        getPreviousElement(child)) != null &&
                                       DITAUtil.hasClass(siblingElement, 
                                                         "map/reltable")) {
                                list = appended;
                            } else {
                                list = replacement;
                            }

                            list.add(doc.importNode(child, /*deep*/ true));
                        }
                    }
                    break;
                case Node.ELEMENT_NODE:
                    {
                        Element childElement = (Element) child;

                        if (DITAUtil.hasClass(childElement, "map/topicref") &&
                            // ditavalref, frontmatter, backmatter are
                            // topicrefs too.
                            !DITAUtil.hasClass(childElement,
                                               "ditavalref-d/ditavalref",
                                               "bookmap/frontmatter",
                                               "bookmap/backmatter")) {
                            replacement.add(copyTopicrefAs(childElement,
                                                           directiveElement,
                                                           doc));
                        } else if (DITAUtil.hasClass(childElement, 
                                                     "map/reltable")) {
                            appended.add((Element) doc.importNode(childElement, 
                                                                /*deep*/ true));
                        }
                    }
                    break;
                }

                child = child.getNextSibling();
            }

            int count = replacement.size();
            if (count > 0) {
                incl.replacementNodes = new Node[count];
                replacement.toArray(incl.replacementNodes);
            }

            count = appended.size();
            if (count > 0) {
                incl.appendedNodes = new Node[count];
                appended.toArray(incl.appendedNodes);
            }
        } else {
            // Copy branch ---

            incl.replacementNodes = new Node[] {
                copyTopicrefAs(mapOrTopicref, directiveElement, doc)
            };
        }

        if (incl.replacementNodes != null) {
            CascadeMeta.processMapref(directiveElement, incl.replacementNodes);
        }

        if (incl.appendedNodes != null) {
            CascadeMeta.processMapref(directiveElement, incl.appendedNodes);
        }
    }

    private static Element getNextElement(Node node) {
        Node sibling = node.getNextSibling();
        while (sibling != null) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) sibling;
            }

            sibling = sibling.getNextSibling();
        }

        return null;
    }

    private static Element getPreviousElement(Node node) {
        Node sibling = node.getPreviousSibling();
        while (sibling != null) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) sibling;
            }

            sibling = sibling.getPreviousSibling();
        }

        return null;
    }

    private static Element copyTopicrefAs(Element source, Element template, 
                                          Document doc) {
        String cls = template.getAttributeNS(null, "class");
        if (cls != null && cls.length() > 0 && 
            cls.indexOf("mapgroup-d/") < 0) {
            // Example:
            // <chapter href="foo.ditamap#bar"/>
            // where:
            // <topicref id="bar" href="bar.dita"/>
            // is copied as a chapter and not as a topicref.

            Element copy = doc.createElementNS(template.getNamespaceURI(), 
                                               template.getLocalName());

            copyUserData(source, copy);

            DOMUtil.copyAllAttributes(source, copy);
            copy.setAttributeNS(null, "class", cls);

            DOMUtil.copyChildren(source, copy, doc);

            return copy;
        } else {
            // Example:
            // <mapref href="foo.ditamap"/>
            // (The class of a mapref is "+ map/topicref mapgroup-d/mapref ".)

            return (Element) doc.importNode(source, /*deep*/ true);
        }
    }

    private static void insertDitavalrefs(MaprefIncl incl) {
        Node[] replacementNodes = incl.replacementNodes;
        if (replacementNodes == null || replacementNodes.length == 0) {
            return;
        }

        ArrayList<Node> nodeList = new ArrayList<Node>();

        for (Element ditavalref : incl.ditavalrefs) {
            for (Node replacementNode : replacementNodes) {
                replacementNode = replacementNode.cloneNode(/*deep*/ true);

                if (replacementNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element replacementElement = (Element) replacementNode;

                    if (DITAUtil.hasClass(replacementElement, "map/topicref")) {
                        insertDitavalref(
                            (Element) ditavalref.cloneNode(/*deep*/ true), 
                            replacementElement);
                    }
                }

                nodeList.add(replacementNode);
            }
        }

        incl.replacementNodes = new Node[nodeList.size()];
        nodeList.toArray(incl.replacementNodes);
    }

    private static void insertDitavalref(Element ditavalref,
                                         Element topicref) {
        // Insert ditavalref before any other topic reference.
        Node before = null;

        Node child = topicref.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (DITAUtil.hasClass(childElement, "map/topicref")) {
                    before = childElement;
                    break;
                }
            }

            child = child.getNextSibling();
        }

        topicref.insertBefore(ditavalref, before);
    }

    private void createKeyscopeGroup(Element mapOrTopicref, MaprefIncl incl) {
        Element directiveElement = incl.directiveElement;
        String keySpace1 = 
            DITAUtil.getNonEmptyAttribute(directiveElement,
                                          DITAC_NS_URI, KEY_SPACE_NAME);

        String keySpace2 = null;
        if (DITAUtil.hasClass(mapOrTopicref, "map/map")) {
            keySpace2 = 
                DITAUtil.getNonEmptyAttribute(mapOrTopicref,
                                              DITAC_NS_URI, KEY_SPACE_NAME);
        }

        if (keySpace1 == null && keySpace2 == null) {
            // Nothing to do.
            return;
        }

        // ---

        String keyscope1 = DITAUtil.getNonEmptyAttribute(directiveElement,
                                                         null, "keyscope");
        String keyscope2 = DITAUtil.getNonEmptyAttribute(mapOrTopicref,
                                                         null, "keyscope");
        String keyscope = DOMUtil.mergeTokens(keyscope1, keyscope2);

        String keySpace = (keySpace2 != null)? keySpace2 : keySpace1;

        Document doc = directiveElement.getOwnerDocument();

        incl.replacementNodes = 
            addKeyscopeGroup(incl.replacementNodes, keyscope, keySpace, doc);
        incl.appendedNodes = 
            addKeyscopeGroup(incl.appendedNodes, keyscope, keySpace, doc);
    }

    private static Node[] addKeyscopeGroup(Node[] nodes, 
                                           String keyscope, String keySpace,
                                           Document doc) {
        if (nodes == null || nodes.length == 0) {
            return nodes;
        }

        StringBuilder buffer = new StringBuilder();
        buffer.append("keyscope=\"");
        buffer.append(keyscope);
        buffer.append("\" ");
        buffer.append(KEY_SPACE_START);
        buffer.append(keySpace);
        buffer.append('"');

        ProcessingInstruction beginPI =
            doc.createProcessingInstruction(BEGIN_GROUP_PI_TARGET,
                                            buffer.toString()); 
        ProcessingInstruction endPI =
            doc.createProcessingInstruction(END_GROUP_PI_TARGET, ""); 

        nodes = ArrayUtil.prepend(nodes, beginPI);
        nodes = ArrayUtil.append(nodes, endPI);
        return nodes;
    }

    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                "usage: java com.xmlmind.ditac.preprocess.MaprefIncluder" +
                " in_ditamap_file out_flat_ditamap_file");
            System.exit(1);
        }
        java.io.File inFile = new java.io.File(args[0]);
        java.io.File outFile = new java.io.File(args[1]);

        LoadedDocument loadedDoc = 
            (new LoadedDocuments()).load(inFile.toURI().toURL());

        com.xmlmind.ditac.util.SimpleConsole console = 
            new com.xmlmind.ditac.util.SimpleConsole(null, false, 
                                                     Console.MessageType.ERROR);
        MaprefIncluder includer = new MaprefIncluder(null, console);
        boolean done = includer.process(loadedDoc);

        com.xmlmind.ditac.util.SaveDocument.save(loadedDoc.document, outFile);
        System.exit(done? 0 : 2);
    }
}

