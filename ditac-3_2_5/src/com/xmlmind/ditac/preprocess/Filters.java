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
import java.io.File;
import java.util.ArrayList;
import java.util.Stack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import org.w3c.dom.Node;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import com.xmlmind.util.ThrowableUtil;
import com.xmlmind.util.StringList;
import com.xmlmind.util.StringUtil;
import com.xmlmind.util.URLUtil;
import com.xmlmind.util.XMLText;
import com.xmlmind.util.KeyValuePair;
import com.xmlmind.util.LinearHashtable;
import com.xmlmind.util.Console;
import com.xmlmind.ditac.util.DOMUtil;
import com.xmlmind.ditac.util.DITAUtil;
import com.xmlmind.ditac.util.SimpleConsole;
import com.xmlmind.ditac.util.ConsoleHelper;

/*package*/ final class Filters implements Constants {
    /**
     * Name of the application-level attribute added to flagged elements.
     * 
     * @see #filterTopic
     */
    public static final String FLAGS_KEY = "DITAC_FLAGS";

    private Stack<Filter> filterStack;
    private HashMap<URL,Filter> loadedFilters;

    private ConsoleHelper console;
    private ResourceHandler resourceHandler;
    private File outDir;
    private boolean validate;
    private Filter externalFilter;

    // Used when filtering ---

    private String[] filterAttributes = StringList.EMPTY_LIST;

    // Updated when the Filter stack changes.
    private String[] referencedAttributes;

    // Scratch variables.
    private boolean[] hasFlags = new boolean[1];
    private Filter.Flags flags = new Filter.Flags();
    private LinearHashtable<String,String[]> keyToValues = 
        new LinearHashtable<String,String[]>();

    private static final String EXCLUDE = "EXCLUDE";

    // -----------------------------------------------------------------------

    public Filters() {
        filterStack = new Stack<Filter>();
        loadedFilters = new HashMap<URL,Filter>();
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

    public void setResourceHandler(ResourceHandler handler) {
        resourceHandler = handler;
    }

    public ResourceHandler getResourceHandler() {
        return resourceHandler;
    }

    public void setOutputDirectory(File dir) {
        outDir = dir;
    }

    public File getOutputDirectory() {
        return outDir;
    }

    public void setValidating(boolean validate) {
        this.validate = validate;
    }

    public boolean isValidating() {
        return validate;
    }

    public void setExternalFilter(Filter filter) {
        this.externalFilter = filter;
    }

    public Filter getExternalFilter() {
        return externalFilter;
    }

    // -----------------------------------------------------------------------

    private Filter pushFilter(Element ditavalref) {
        Filter filter = null;

        String href = DITAUtil.getNonEmptyAttribute(ditavalref, null, "href");
        if (href != null) {
            URL url = null;
            try {
                url = URLUtil.createURL(href);
            } catch (MalformedURLException ignored) {}

            if (url != null) {
                // Just in case.
                url = URLUtil.setRawFragment(url, null);

                filter = loadedFilters.get(url);
                if (filter == null) {
                    try {
                        filter = new Filter(url, validate, console);
                    } catch (Exception e) {
                        console.error(ditavalref,
                                      Msg.msg("cannotLoad", url, 
                                              ThrowableUtil.reason(e)));
                    }

                    if (filter != null) {
                        loadedFilters.put(url, filter);
                    }
                }
            }
        }

        if (filter != null) {
            pushFilter(filter);
        }

        return filter;
    }

    private void pushFilter(Filter filter) {
        filterStack.push(filter);
        updateStackState();
    }

    private void popFilter() {
        filterStack.pop();
        updateStackState();
    }

    private void updateStackState() {
        HashSet<String> allAttrs = new HashSet<String>();

        for (Filter f : filterStack) {
            for (Filter.Prop prop : f.getProps()) {
                if (prop.attribute != null) {
                    allAttrs.add(prop.attribute);
                }
            }
        }

        referencedAttributes = allAttrs.toArray(StringList.EMPTY_LIST);
    }

    // -----------------------------------------------------------------------

    public void filterMap(Element mapElement) {
        filterAttributes = DITAUtil.getFilterAttributes(mapElement);

        if (externalFilter != null) {
            pushFilter(externalFilter);
        }

        // ---

        Filter filter = null;
        Element[] ditavalrefs = DITAUtil.findDitavalrefs(mapElement);
        if (ditavalrefs != null) {
            filter = pushFilter(ditavalrefs[0]);
        }

        filterMap1(mapElement);
        filterMap2(mapElement);

        if (filter != null) {
            popFilter();
        }

        // ---

        if (externalFilter != null) {
            popFilter();
        }
    }

    private void filterMap1(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();

            Element childElement;
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                !DITAUtil.isDitavalref(childElement = (Element) child)) {

                Filter filter = null;
                boolean isTopicref =
                    DITAUtil.hasClass(childElement, "map/topicref");
                if (isTopicref) {
                    Element[] ditavalrefs =
                        DITAUtil.findDitavalrefs(childElement);
                    if (ditavalrefs != null) {
                        filter = pushFilter(ditavalrefs[0]);
                    }
                }

                // ---

                boolean filterContent = false;
                boolean removeHref = false;
                
                if (DITAUtil.hasClass(childElement, "map/topicmeta") ||
                    DITAUtil.hasClass(childElement, "topic/title")) {
                    filterContent = true;
                } else if (isTopicref) {
                    String href = childElement.getAttributeNS(null, "href");
                    if (href != null && href.length() > 0) {
                        removeHref = (computeAction(childElement) == EXCLUDE);
                    }
                }

                if (filterContent) {
                    filterTopicContent(childElement, /*allowFlagging*/ false);
                } else {
                    if (removeHref) {
                        childElement.removeAttributeNS(null, "href");
                    }

                    filterMap1(childElement);
                }

                // ---

                if (filter != null) {
                    popFilter();
                }
            }

            child = next;
        }
    }

    private void filterMap2(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();

            Element childElement;
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                !DITAUtil.isDitavalref(childElement = (Element) child)) {

                Filter filter = null;
                boolean isTopicref =
                    DITAUtil.hasClass(childElement, "map/topicref");
                if (isTopicref) {
                    Element[] ditavalrefs =
                        DITAUtil.findDitavalrefs(childElement);
                    if (ditavalrefs != null) {
                        filter = pushFilter(ditavalrefs[0]);
                    }
                }

                // ---

                filterMap2(childElement);

                if (isTopicref &&
                    computeAction(childElement) == EXCLUDE &&
                    DITAUtil.findChildByClass(childElement, 
                                              "map/topicref") == null) {
                    assert(DITAUtil.getNonEmptyAttribute(childElement, 
                                                         null, "href") == null);
                    childElement.getParentNode().removeChild(childElement);
                }

                // ---

                if (filter != null) {
                    popFilter();
                }
            }

            child = next;
        }
    }

    private void filterTopicContent(Element element, boolean allowFlagging) {
        Node child = element.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (DITAUtil.hasClass(childElement, "topic/topic")) {
                    // Do not process nested topics.
                    return;
                }

                Object action = computeAction(childElement);
                if (action == null) {
                    filterTopicContent(childElement, allowFlagging);
                } else {
                    Node parent = childElement.getParentNode();
                    assert(parent != null);

                    if (action == EXCLUDE) {
                        if (DITAUtil.hasClass(childElement, "map/relcell")) {
                            // Do not remove a relcell, just make it empty.
                            // This should be harmless for a "map/relcell" 
                            // which is not rendered. But this is not the case
                            // for "topic/entry", "topic/stentry". That is,
                            // may be the author wants to remove 
                            // the whole column.
                            DOMUtil.removeChildren(childElement);
                        } else {
                            parent.removeChild(childElement);
                        }
                    } else {
                        if (allowFlagging) {
                            parent.removeChild(childElement);

                            Filter.Flags flags = (Filter.Flags) action;

                            Document doc = parent.getOwnerDocument();
                            assert(doc != null);

                            Element wrapper = createFlagsElement(flags, doc);
                            wrapper.appendChild(childElement);

                            parent.insertBefore(wrapper, next);
                        }

                        filterTopicContent(childElement, allowFlagging);
                    }
                }
            }
            
            child = next;
        }
    }

    private Object computeAction(Element element) {
        Object action = null;

        hasFlags[0] = false;
        flags.clear();

        NamedNodeMap attrs = element.getAttributes();
        int attrCount = attrs.getLength();
        
        for (int i = 0; i < attrCount; ++i) {
            Attr attr = (Attr) attrs.item(i);
            
            String attrName = DOMUtil.formatName(attr);
            boolean isFilterAttribute = 
                StringList.contains(filterAttributes, attrName);
            if (!isFilterAttribute &&
                !StringList.contains(referencedAttributes, attrName)) {
                continue;
            }

            String attrValue = attr.getValue().trim();
            if (attrValue.length() == 0) {
                continue;
            }

            if (computeAction(attrName, attrValue, isFilterAttribute,
                              flags, hasFlags) == EXCLUDE) {
                action = EXCLUDE;
                break;
            }
            // Otherwise, no value; ignore attribute.
        }

        if (action == null && hasFlags[0]) {
            action = flags;
        }
        return action;
    }

    private Object computeAction(String attrName, String attrValue, 
                                 boolean isFilterAttribute,
                                 Filter.Flags flags, boolean[] hasFlags) {
        if (!parseAttributeValue(attrValue, attrName)) {
            return null;
        }

        Iterator<KeyValuePair<String,String[]>> iter = keyToValues.entries();
        while (iter.hasNext()) {
            KeyValuePair<String,String[]> entry = iter.next();

            int excludeCount = 0;

            String key = entry.key;
            String[] values = entry.value;

            for (String value : values) {
                boolean excluded = false;

                for (Filter filter : filterStack) {
                    Filter.PropValue propValue = 
                        findPropValue(filter.getProps(), key, value,
                                      attrName, isFilterAttribute);
                    if (propValue != null) {
                        switch (propValue.action) {
                        case EXCLUDE:
                            excluded = true;
                            break;
                        case FLAG:
                            flags.set(propValue.flags);
                            hasFlags[0] = true;
                            break;
                        }
                    }
                    // Otherwise, action is include.
                }

                if (excluded) {
                    ++excludeCount;
                }
            }

            if (excludeCount == values.length) {
                return EXCLUDE;
            }
        }

        return null;
    }

    private boolean parseAttributeValue(String attrValue, String attrName) {
        keyToValues.clear();

        if (attrValue.indexOf('(') >= 0 && 
            ("audience".equals(attrName) ||
             "product".equals(attrName) ||
             "platform".equals(attrName) ||
             "otherprops".equals(attrName))) {
            // May contain  grouped values and contains  grouped values ---

            attrValue = StringUtil.replaceAll(attrValue, "(", " ( ");
            attrValue = StringUtil.replaceAll(attrValue, ")", " ) ");

            final String[] tokens = XMLText.splitList(attrValue);
            final int tokenCount = tokens.length;

            String group = attrName;

            for (int i = 0; i < tokenCount; ++i) {
                String token = tokens[i];

                if ("(".equals(token)) {
                    // Nothing to do.
                } else if (")".equals(token)) {
                    group = attrName;
                } else {
                    if (i+1 < tokenCount && "(".equals(tokens[i+1])) {
                        group = token;
                    } else {
                        String[] values = keyToValues.get(group);
                        if (values == null) {
                            values = new String[] { token };
                        } else {
                            if (!StringList.contains(values, token)) {
                                values = StringList.append(values, token);
                            }
                        }
                        keyToValues.put(group, values);
                    }
                }
            }

            return (keyToValues.size() > 0);
        }

        // Otherwise, no grouped values or attribute not supporting grouped
        // values; the simplest, most common case.

        String[] tokens = XMLText.splitList(attrValue);
        if (tokens.length == 0) {
            return false;
        } else {
            keyToValues.put(attrName, tokens);
            return true;
        }
    }

    private static Filter.PropValue findPropValue(Filter.Prop[] props,
                                                  String key, String value, 
                                                  String attrName,
                                                  boolean isFilterAttribute) {
        if (!key.equals(attrName)) {
            // Key is a group name (e.g. database) found inside an attribute
            // (e.g. product) ---

            // For example, rule for database="myDB".
            for (Filter.Prop prop : props) {
                if (key.equals(prop.attribute)) {
                    Filter.PropValue propValue = prop.findValue(value);
                    if (propValue != null) {
                        return propValue;
                    }
                }
            }

            // For example, rule for product="myDB".
            for (Filter.Prop prop : props) {
                if (attrName.equals(prop.attribute)) {
                    Filter.PropValue propValue = prop.findValue(value);
                    if (propValue != null) {
                        return propValue;
                    }
                }
            }

            // For example, rule for product="database".
            for (Filter.Prop prop : props) {
                if (attrName.equals(prop.attribute)) {
                    Filter.PropValue propValue = prop.findValue(key);
                    if (propValue != null) {
                        return propValue;
                    }
                }
            }
        } else {
            // Key is either an attribute name (e.g. product) ---

            // For example, rule for product="myDB".
            for (Filter.Prop prop : props) {
                if (attrName.equals(prop.attribute)) {
                    Filter.PropValue propValue = prop.findValue(value);
                    if (propValue != null) {
                        return propValue;
                    }
                }
            }
        }

        // For example, rule for product whatever the value.
        for (Filter.Prop prop : props) {
            if (attrName.equals(prop.attribute)) {
                Filter.PropValue propValue = prop.getWildcardValue();
                if (propValue != null) {
                    return propValue;
                }
            }
        }

        // Rules which apply to all filter attributes ---

        if (isFilterAttribute) {
            for (Filter.Prop prop : props) {
                if (prop.attribute == null) {
                    Filter.PropValue propValue = prop.findValue(value);
                    if (propValue != null) {
                        return propValue;
                    }
                }
            }

            for (Filter.Prop prop : props) {
                if (prop.attribute == null) {
                    Filter.PropValue propValue = prop.getWildcardValue();
                    if (propValue != null) {
                        return propValue;
                    }
                }
            }
        }

        return null;
    }

    public Element createFlagsElement(Filter.Flags flags, Document doc) {
        Element element = doc.createElementNS(DITAC_NS_URI, "ditac:flags");

        if (flags.color != null) {
            element.setAttributeNS(null, "color", flags.color);
        }

        if (flags.backgroundColor != null) {
            element.setAttributeNS(null, "background-color", 
                                   flags.backgroundColor);
        }

        if (flags.fontWeight != null) {
            element.setAttributeNS(null, "font-weight", flags.fontWeight);
        }

        if (flags.fontStyle != null) {
            element.setAttributeNS(null, "font-style", flags.fontStyle);
        }

        if (flags.textDecoration != null) {
            element.setAttributeNS(null, "text-decoration",
                                   flags.textDecoration);
        }

        String[] changeBarProps = flags.changeBarProps;
        if (changeBarProps != null) {
            for (int i = 0; i < changeBarProps.length; i += 2) {
                element.setAttributeNS(null, changeBarProps[i], 
                                       changeBarProps[i+1]);
            }
        }

        if (flags.startImage != null) {
            element.setAttributeNS(null, "startImage", 
                                   flagImagePath(flags.startImage,
                                                flags.isAbsoluteStartImageURL));
        }

        if (flags.startText != null) {
            element.setAttributeNS(null, "startText", flags.startText);
        }

        if (flags.endImage != null) {
            element.setAttributeNS(null, "endImage", 
                                   flagImagePath(flags.endImage,
                                                 flags.isAbsoluteEndImageURL));
        }

        if (flags.endText != null) {
            element.setAttributeNS(null, "endText", flags.endText);
        }

        return element;
    }

    private String flagImagePath(URL url, boolean isAbsolute) {
        String path =  null;

        if (!isAbsolute && resourceHandler != null && outDir != null) {
            try {
                path = resourceHandler.handleResource(url, null, 
                                                      /*img*/ true,
                                                      outDir, console);
            } catch (Throwable t) {
                console.error(Msg.msg("cannotProcessResource",
                                      url, ThrowableUtil.reason(t)));
            }
        }

        if (path == null) {
            path = url.toExternalForm();
        }

        return path;
    }

    // -----------------------------------------------------------------------

    public void filterTopics(Element mapElement, LoadedDocuments loadedDocs) {
        if (externalFilter != null) {
            pushFilter(externalFilter);
        }

        // ---

        Filter filter = null;
        Element[] ditavalrefs = DITAUtil.findDitavalrefs(mapElement);
        if (ditavalrefs != null) {
            filter = pushFilter(ditavalrefs[0]);
        }

        doFilterTopics(mapElement, loadedDocs);

        if (filter != null) {
            popFilter();
        }

        // ---

        if (externalFilter != null) {
            popFilter();
        }
    }

    private void doFilterTopics(Element element, LoadedDocuments loadedDocs) {
        Node child = element.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();

            Element childElement;
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                !DITAUtil.isDitavalref(childElement = (Element) child)) {

                Filter filter = null;
                boolean isTopicref =
                    DITAUtil.hasClass(childElement, "map/topicref");
                if (isTopicref) {
                    Element[] ditavalrefs =
                        DITAUtil.findDitavalrefs(childElement);
                    if (ditavalrefs != null) {
                        filter = pushFilter(ditavalrefs[0]);
                    }
                }

                // ---

                if (isTopicref) {
                    filterReferencedTopics(childElement, loadedDocs);
                }

                doFilterTopics(childElement, loadedDocs);

                // ---

                if (filter != null) {
                    popFilter();
                }
            }

            child = next;
        }
    }

    private void filterReferencedTopics(Element topicref,
                                        LoadedDocuments loadedDocs) {
        String href = DITAUtil.getNonEmptyAttribute(topicref, null, "href");
        if (href != null) {
            URL url = null;
            try {
                url = URLUtil.createURL(href);
            } catch (MalformedURLException ignored) {}

            if (url != null) {
                String topicId = DITAUtil.getTopicId(url);
                url = URLUtil.setRawFragment(url, null);

                LoadedDocument loadedDoc = loadedDocs.get(url);
                // loadedDoc is null when for example, scope=external.

                if (loadedDoc != null && 
                    (loadedDoc.type == LoadedDocument.Type.MULTI_TOPIC || 
                     loadedDoc.type == LoadedDocument.Type.TOPIC)) {
                    Select select = 
                        (topicId == null)? Select.DOCUMENT : Select.TOPIC;

                    String value = topicref.getAttributeNS(null, "chunk");
                    if (value != null) {
                        if (value.indexOf("select-document") >= 0) {
                            select = Select.DOCUMENT;
                        } else if (value.indexOf("select-branch") >= 0) {
                            select = Select.BRANCH;
                        } else if (value.indexOf("select-topic") >= 0) {
                            select = Select.TOPIC;
                        }
                    }

                    LoadedTopic loadedTopic = null;
                    if (topicId != null) {
                        loadedTopic = loadedDoc.findTopicById(topicId);
                        if (loadedTopic == null) {
                            console.error(
                                topicref,
                                Msg.msg("topicNotFound", topicId, 
                                        URLUtil.toLabel(loadedDoc.url)));
                        }
                    } else {
                        loadedTopic = loadedDoc.getFirstTopic();
                    }

                    if (loadedTopic != null) {
                        if (loadedDoc.getSingleTopic() != null) {
                            // Most common case.
                            filterTopic(loadedTopic);
                        } else {
                            switch (select) {
                            case TOPIC:
                                // This will NOT process nested topics.
                                filterTopic(loadedTopic);
                                break;
                            case BRANCH:
                                filterBranch(loadedTopic);
                                break;
                            case DOCUMENT:
                                filterDocument(loadedDoc);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void filterTopic(LoadedTopic loadedTopic) {
        filterAttributes = DITAUtil.getFilterAttributes(loadedTopic.element);

        Object action = computeAction(loadedTopic.element);
        if (action == EXCLUDE) {
            loadedTopic.setExcluded(true);
        } else if (action != null) {
            Filter.Flags flags2 = ((Filter.Flags) action).copy();
            loadedTopic.element.setUserData(FLAGS_KEY, flags2, null);
        }

        // Do not assume that we will be able to actually exclude all topics
        // marked as excluded, so filter out and flag the contents of the
        // topic.
        filterTopicContent(loadedTopic.element, /*allowFlagging*/ true);
    }

    private void filterBranch(LoadedTopic loadedTopic) {
        filterTopic(loadedTopic);

        for (LoadedTopic nestedTopic : loadedTopic.getNestedTopics()) {
            filterBranch(nestedTopic);
        }
    }

    private void filterDocument(LoadedDocument loadedDoc) {
        for (LoadedTopic loadedTopic : loadedDoc.getTopics()) {
            filterBranch(loadedTopic);
        }
    }

    // -----------------------------------------------------------------------

    /**
     * Simple test driver.
     */
    public static void main(String[] args) {
        boolean validate = false;
        File filterFile = null;
        String filterOptions = null;
        File inFile = null;
        File outFile = null;
        boolean usage = false;

        if (args.length > 0 && "-validate".equals(args[0])) {
            validate = true;
            args = StringList.removeAt(args, 0);
        }

        switch (args.length) {
        case 2:
            filterFile = new File(args[0]);
            outFile = new File(args[1]);
            break;
        case 4:
            filterFile = new File(args[0]);
            filterOptions = args[1];
            inFile = new File(args[2]);
            outFile = new File(args[3]);
            break;
        default:
            usage = true;
            break;
        }

        if (usage) {
            System.err.println(
                "usage: java com.xmlmind.ditac.preprocess.Filters [-validate]" +
                " in_ditaval_file out_text_file" +
                " |\n    in_ditaval_file in_topic_or_map_file out_xml_file");
            System.exit(1);
        }

        try {
            Filter filter = new Filter(filterFile, validate, null);

            if (inFile != null) {
                LoadedDocument loadedDoc = 
                    (new LoadedDocuments()).load(inFile);

                if (filterOptions.indexOf("new") >= 0) {
                    filter = new Filter();
                }
                if (filterOptions.indexOf("prune") >= 0) {
                    filter.addExcludeProps("processing-role", "resource-only");
                }
                if (filterOptions.indexOf("screen") >= 0) {
                    filter.addExcludeProps("print", "printonly");
                }
                if (filterOptions.indexOf("print") >= 0) {
                    filter.addExcludeProps("print", "no");
                }

                Filters filters = new Filters();
                filters.setExternalFilter(filter);

                switch (loadedDoc.type) {
                case MAP:
                case BOOKMAP:
                    filters.filterMap(loadedDoc.document.getDocumentElement());
                    break;
                case MULTI_TOPIC:
                case TOPIC:
                    filters.pushFilter(filter);
                    filters.filterTopic(loadedDoc.getFirstTopic());
                    filters.popFilter();
                    break;
                }

                com.xmlmind.ditac.util.SaveDocument.save(loadedDoc.document,
                                                         outFile);
            } else {
                com.xmlmind.util.FileUtil.saveString(filter.toString(),
                                                     outFile);
            }
        } catch (Exception e) {
            System.out.println(ThrowableUtil.reason(e));
            System.exit(2);
        }
    }
}
