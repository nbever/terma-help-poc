/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.util;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Arrays;
import static javax.xml.XMLConstants.XML_NS_URI;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import com.xmlmind.util.ObjectUtil;
import com.xmlmind.util.XMLText;
import com.thaiopensource.xml.util.Name;
import com.thaiopensource.validate.Schema;
import com.thaiopensource.relaxng.impl.NameClass;
import com.thaiopensource.relaxng.impl.NullNameClass;
import com.thaiopensource.relaxng.impl.SimpleNameClass;
import com.thaiopensource.relaxng.impl.ChoiceNameClass;
import com.thaiopensource.relaxng.impl.Pattern;
import com.thaiopensource.relaxng.impl.PatternSchema;
import com.thaiopensource.relaxng.impl.CombineSchema;
import com.thaiopensource.relaxng.impl.AttributePattern;
import com.thaiopensource.relaxng.impl.ElementPattern;
import com.thaiopensource.relaxng.impl.GroupPattern;
import com.thaiopensource.relaxng.impl.ChoicePattern;
import com.thaiopensource.relaxng.impl.OneOrMorePattern;
import com.thaiopensource.relaxng.impl.RefPattern;
import com.thaiopensource.relaxng.impl.InterleavePattern;

/*TEST*/
import java.io.IOException;
import java.io.File;
import java.net.URL;
import com.xmlmind.util.FileUtil;

public final class RNGAttributeDefaultValues {
    private static final class QualifiedName
                        implements Comparable<QualifiedName> {
        public final String namespaceURI; // null if none.
        public final String localName;

        public QualifiedName(String namespaceURI, String localName) {
            this.namespaceURI = namespaceURI;
            this.localName = localName;
        }

        public QualifiedName(Name name) {
            String ns = name.getNamespaceUri();
            if (ns != null && ns.length() == 0) {
                ns = null;
            }
            namespaceURI = ns;
            localName = name.getLocalName();
        }

        @Override
        public int hashCode() {
            int code;
            if (namespaceURI == null) {
                code = 0;
            } else {
                code = namespaceURI.hashCode();
            }

            return (code ^ localName.hashCode());
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof QualifiedName)) {
                return false;
            }

            QualifiedName o = (QualifiedName) other;
            return (ObjectUtil.equals(namespaceURI, o.namespaceURI) && 
                    localName.equals(o.localName));
        }

        @Override
        public String toString() {
            if (namespaceURI == null) {
                return localName;
            }

            if (XML_NS_URI.equals(namespaceURI)) {
                return "xml:" + localName;
            }

            StringBuilder buffer = new StringBuilder();
            buffer.append('{');
            buffer.append(namespaceURI);
            buffer.append('}');
            buffer.append(localName);
            return buffer.toString();
        }

        public int compareTo(QualifiedName other) {
            if (ObjectUtil.equals(namespaceURI, other.namespaceURI)) {
                return localName.compareTo(other.localName);
            } else {
                String ns1 = (namespaceURI == null)? "" : namespaceURI;
                String ns2 =
                    (other.namespaceURI == null)? "" : other.namespaceURI;
                return ns1.compareTo(ns2);
            }
        }
    }

    @SuppressWarnings("overrides")
    private static final class AttributeEntry
                         implements Comparable<AttributeEntry> {
        public final QualifiedName name;
        public final String defaultValue;

        public AttributeEntry(QualifiedName name, String defaultValue) {
            this.name = name;
            this.defaultValue = XMLText.compressWhiteSpace(defaultValue);
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof AttributeEntry)) {
                return false;
            }

            AttributeEntry o = (AttributeEntry) other;
            return (name.equals(o.name) &&
                    defaultValue.equals(o.defaultValue));
        }

        @Override
        public String toString() {
            StringBuilder buffer = new StringBuilder();
            buffer.append(name);
            buffer.append('=');
            char quote = (defaultValue.indexOf('"') < 0)? '"' : '\'';
            buffer.append(quote);
            buffer.append(defaultValue);
            buffer.append(quote);
            return buffer.toString();
        }

        public int compareTo(AttributeEntry other) {
            return name.compareTo(other.name);
        }
    }

    private HashMap<QualifiedName,AttributeEntry[]> map;

    // -----------------------------------------------------------------------

    public RNGAttributeDefaultValues(Schema schema) 
        throws Exception {
        Pattern startPattern = findStartPattern(schema);
        if (startPattern == null) {
            throw new Exception(Msg.msg("noStartPattern"));
        }

        IdentityHashMap<ElementPattern,List<AttributePattern>> elementPatterns =
            new IdentityHashMap<ElementPattern,List<AttributePattern>>();
        collectElements(startPattern, elementPatterns);

        List<Name> nameList = new ArrayList<Name>();
        map = new HashMap<QualifiedName,AttributeEntry[]>();

        Iterator<Map.Entry<ElementPattern,List<AttributePattern>>> 
            iter = elementPatterns.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<ElementPattern,List<AttributePattern>> e = iter.next();

            ElementPattern elementPattern = e.getKey();
            List<AttributePattern> attributePatterns = e.getValue();

            collectAttributes(elementPattern.getContent(), attributePatterns);

            if (attributePatterns.size() > 0) {
                updateMap(elementPattern, attributePatterns, nameList, map);
            }
        }
    }

    public void addAll(Element tree) {
        QualifiedName name = new QualifiedName(tree.getNamespaceURI(),
                                               tree.getLocalName());
        AttributeEntry[] attrs = map.get(name);
        if (attrs != null) {
            for (AttributeEntry attr : attrs) {
                QualifiedName attrName = attr.name;

                if (!tree.hasAttributeNS(attrName.namespaceURI,
                                         attrName.localName)) {
                    String attrQName = attrName.localName;
                    if ("http://www.w3.org/XML/1998/namespace".equals(
                            attrName.namespaceURI)) {
                        // Otherwise Java 9 uses "NS1: which is a bug.
                        attrQName = "xml:" + attrName.localName;
                    } else {
                        attrQName = attrName.localName;
                    }

                    tree.setAttributeNS(attrName.namespaceURI,
                                        attrQName, attr.defaultValue);
                }
            }
        }

        Node child = tree.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                addAll((Element) child);
            }

            child = child.getNextSibling();
        }
    }

    // -----------------------------------------------------------------------
    // Collect attribute default values
    // -----------------------------------------------------------------------

    private static Pattern findStartPattern(Schema schema) {
        if (schema instanceof PatternSchema) {
            PatternSchema ps = (PatternSchema) schema;

            return ps.getStartPattern();
        } else if (schema instanceof CombineSchema) {
            CombineSchema cs = (CombineSchema) schema;

            Pattern p = findStartPattern(cs.getSchema1());
            if (p != null) {
                return p;
            } else {
                return findStartPattern(cs.getSchema2());
            }
        } else {
            return null;
        }
    }

    private static
    void collectElements(Pattern p,
                         Map<ElementPattern,List<AttributePattern>> collected) {
        if (p instanceof GroupPattern) {
            GroupPattern gp = (GroupPattern) p;

            collectElements(gp.getOperand1(), collected);
            collectElements(gp.getOperand2(), collected);
        } else if (p instanceof ChoicePattern) {
            ChoicePattern cp = (ChoicePattern) p;

            collectElements(cp.getOperand1(), collected);
            collectElements(cp.getOperand2(), collected);
        } else if (p instanceof OneOrMorePattern) {
            OneOrMorePattern oomp = (OneOrMorePattern) p;

            collectElements(oomp.getOperand(), collected);
        } else if (p instanceof RefPattern) {
            RefPattern rp = (RefPattern) p;

            collectElements(rp.getPattern(), collected);
        } else if (p instanceof ElementPattern) {
            ElementPattern ep = (ElementPattern) p;

            if (!collected.containsKey(ep)) {
                collected.put(ep, new ArrayList<AttributePattern>());

                collectElements(ep.getContent(), collected);
            }
        } else if (p instanceof InterleavePattern) {
            InterleavePattern ip = (InterleavePattern) p;

            collectElements(ip.getOperand1(), collected);
            collectElements(ip.getOperand2(), collected);
        }
    }

    private static
    void collectAttributes(Pattern p, List<AttributePattern> collected) {
        if (p instanceof GroupPattern) {
            GroupPattern gp = (GroupPattern) p;

            collectAttributes(gp.getOperand1(), collected);
            collectAttributes(gp.getOperand2(), collected);
        } else if (p instanceof ChoicePattern) {
            ChoicePattern cp = (ChoicePattern) p;

            collectAttributes(cp.getOperand1(), collected);
            collectAttributes(cp.getOperand2(), collected);
        } else if (p instanceof OneOrMorePattern) {
            OneOrMorePattern oomp = (OneOrMorePattern) p;

            collectAttributes(oomp.getOperand(), collected);
        } else if (p instanceof RefPattern) {
            RefPattern rp = (RefPattern) p;

            collectAttributes(rp.getPattern(), collected);
        } else if (p instanceof AttributePattern) {
            AttributePattern ap = (AttributePattern) p;

            if (ap.getDefaultValue() != null) {
                if (!contains(collected, ap)) {
                    collected.add(ap);
                }
            }
        } else if (p instanceof InterleavePattern) {
            InterleavePattern ip = (InterleavePattern) p;

            collectAttributes(ip.getOperand1(), collected);
            collectAttributes(ip.getOperand2(), collected);
        }
    }

    private static <T> boolean contains(List<T> list, T searched) {
        for (T item : list) {
            if (item == searched) {
                return true;
            }
        }
        return false;
    }

    private static void updateMap(ElementPattern elementPattern, 
                                  List<AttributePattern> attributePatterns, 
                                  List<Name> nameList,
                                  Map<QualifiedName,AttributeEntry[]> map) 
        throws Exception {
        nameList.clear();
        if (!getSimpleNames(elementPattern.getNameClass(), nameList)) {
            throw new Exception(Msg.msg("wildcardHasAttributeDefaultValues"));
        }

        int elementNameCount = nameList.size();
        if (elementNameCount == 0) {
            // Unusable. Ignore.
            return;
        }

        Name[] elementNames = new Name[elementNameCount];
        nameList.toArray(elementNames);

        // ---

        HashMap<QualifiedName,AttributeEntry> attributeMap = 
            new HashMap<QualifiedName,AttributeEntry>();

        for (AttributePattern ap : attributePatterns) {
            nameList.clear();
            if (!getSimpleNames(ap.getNameClass(), nameList)) {
                throw new Exception(Msg.msg("defaultValueForWildcard"));
            }

            String attrDefaultValue = ap.getDefaultValue();

            for (Name name : nameList) {
                QualifiedName attrName = new QualifiedName(name);

                AttributeEntry newEntry = 
                    new AttributeEntry(attrName, attrDefaultValue);

                AttributeEntry oldEntry = attributeMap.get(attrName);
                if (oldEntry != null && !oldEntry.equals(newEntry)) {
                    throw new Exception(Msg.msg("ambiguousAttribute", 
                                                elementNames[0], attrName));
                }

                attributeMap.put(attrName, newEntry);
            }
        }

        int attrCount = attributeMap.size();
        if (attrCount == 0) {
            // Unusable. Ignore.
            return;
        }

        AttributeEntry[] newEntries = new AttributeEntry[attrCount];
        attributeMap.values().toArray(newEntries);

        if (newEntries.length > 1) {
            Arrays.sort(newEntries);
        }

        // ---

        for (Name name : elementNames) {
            QualifiedName elementName = new QualifiedName(name);

            AttributeEntry[] oldEntries = map.get(elementName);
            if (oldEntries != null && !Arrays.equals(oldEntries, newEntries)) {
                throw new Exception(Msg.msg("ambiguousElement", elementName));
            }

            map.put(elementName, newEntries);
        }
    }

    private static boolean getSimpleNames(NameClass nc, List<Name> nameList) {
        if (nc instanceof SimpleNameClass) {
            SimpleNameClass snc = (SimpleNameClass) nc;

            Name n = snc.getName();
            if (!nameList.contains(n)) { // Name implements equals().
                nameList.add(n);
            }

            return true;
        } else if (nc instanceof ChoiceNameClass) {
            ChoiceNameClass cnc = (ChoiceNameClass) nc;

            if (!getSimpleNames(cnc.getOperand1(), nameList) ||
                !getSimpleNames(cnc.getOperand2(), nameList)) {
                return false;
            }

            return true;
        } else if (nc instanceof NullNameClass) {
            // Ignore.
            return true;
        }

        // Else NsNameClass, AnyNameClass, etc.
        return false;
    }

    // -----------------------------------------------------------------------
    // Test
    // -----------------------------------------------------------------------

    public static void main(String[] args) 
        throws IOException {
        if (args.length != 2) {
            System.err.println(
                "Usage: java com.xmlmind.ditac.util.RNGAttributeDefaultValues" +
                " in_rng_file out_default_values_file");
            System.exit(1);
        }
        File inFile = new File(args[0]);
        File outFile = new File(args[1]);

        URL inURL = FileUtil.fileToURL(inFile);
        XMLModel rngInfo = new XMLModel(inURL.toExternalForm(), inURL, 
                                        null, null, null, null, null, null);
        RNGSchema rngSchema = RNGSchema.get(rngInfo, /*console*/ null);
        
        StringBuilder buffer = new StringBuilder();
        
        Map<QualifiedName,AttributeEntry[]> map =
            rngSchema.attributeDefaultValues.map;

        QualifiedName[] elementNames = new QualifiedName[map.size()];
        map.keySet().toArray(elementNames);
        Arrays.sort(elementNames);

        for (QualifiedName elementName : elementNames) {
            buffer.append(elementName);

            AttributeEntry[] entries = map.get(elementName);
            for (AttributeEntry entry : entries) {
                buffer.append(' ');
                buffer.append(entry);
            }

            buffer.append('\n');
        }

        FileUtil.saveString(buffer.toString(), outFile, "UTF-8");
    }
}
