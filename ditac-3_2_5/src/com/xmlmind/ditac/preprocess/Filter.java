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
import java.util.ArrayList;
import java.util.HashSet;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import com.xmlmind.util.ArrayUtil;
import com.xmlmind.util.StringList;
import com.xmlmind.util.FileUtil;
import com.xmlmind.util.URLUtil;
import com.xmlmind.util.XMLText;
import com.xmlmind.util.Console;
import com.xmlmind.ditac.util.DOMUtil;
import com.xmlmind.ditac.util.NodeLocation;
import com.xmlmind.ditac.util.LoadDocument;
import com.xmlmind.ditac.util.Resolve;

/**
 * Representation of a conditional processing profile (contents of a
 * <tt>.ditaval</tt> file).
 */
public final class Filter {
    public static final class Prop {
        /**
         * May be null: means any.
         * If not null, uses Clark's notation. 
         * Example: {http://www.w3.org/XML/1998/namespace}lang.
         */
        public final String attribute;
        private PropValue[] values;

        public Prop(String attribute, PropValue value) {
            this.attribute = attribute;
            values = new PropValue[] { value };
        }

        public void addValue(PropValue value) {
            int index = -1;
            String v = value.value;

            for (int i = 0; i < values.length; ++i) {
                String v2 = values[i].value;

                if ((v2 == null && v == null) ||
                    (v2 != null && v2.equals(v))) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                values = ArrayUtil.append(values, value);
            } else {
                values[index] = value;
            }
        }

        public PropValue findValue(String searched) {
            for (PropValue value : values) {
                if (searched.equals(value.value)) {
                    return value;
                }
            }

            return null;
        }

        public PropValue getWildcardValue() {
            for (PropValue value : values) {
                if (value.value == null) {
                    return value;
                }
            }

            return null;
        }

        public int getValueCount() {
            return values.length;
        }

        public PropValue[] getValues() {
            return values;
        }

        @Override
        public String toString() {
            StringBuilder buffer = new StringBuilder();
            toString(buffer);
            return buffer.toString();
        }

        public void toString(StringBuilder buffer) {
            buffer.append("attribute=");
            if (attribute != null) {
                buffer.append(attribute);
            }
            buffer.append('\n');

            for (int i = 0; i < values.length; ++i) {
                buffer.append("  ");
                values[i].toString(buffer);
                buffer.append('\n');
            }
        }
    }

    public static final Prop[] NO_PROPS = new Prop[0];

    // -----------------------------------------------------------------------

    public static enum Action {
        EXCLUDE,
        FLAG,
        INCLUDE,
        PASSTHROUGH;

        @Override
        public String toString() {
            switch (this) {
            case EXCLUDE:
                return "exclude";
            case FLAG:
                return "flag";
            case INCLUDE:
                return "include";
            case PASSTHROUGH:
                return "passthrough";
            default:
                return "???";
            }
        }
    }

    public static final class PropValue {
        public final String value;
        public final Action action;
        public final Flags flags;

        public PropValue(String value, Action action, Flags flags) {
            this.value = value;
            this.action = action;
            this.flags = flags;
        }

        @Override
        public String toString() {
            StringBuilder buffer = new StringBuilder();
            toString(buffer);
            return buffer.toString();
        }

        public void toString(StringBuilder buffer) {
            buffer.append("value=");
            if (value != null) {
                buffer.append(value);
            }

            buffer.append(" action=");
            buffer.append(action);
            if (flags != null) {
                flags.toString(buffer);
            }
        }
    }

    // -----------------------------------------------------------------------

    /**
     * Representation of the styles used to flag an element.
     */
    public static final class Flags {
        public String color;
        public String backgroundColor;
        public String fontWeight;
        public String fontStyle;
        public String textDecoration;
        public String[] changeBarProps;
        public URL startImage;
        public boolean isAbsoluteStartImageURL;
        public String startText;
        public URL endImage;
        public boolean isAbsoluteEndImageURL;
        public String endText;

        private String conflictColor;
        private String conflictBackgroundColor;

        public void set(Flags other) {
            if (other.color != null) {
                if (color != null && 
                    !color.equals(other.color) &&
                    other.conflictColor != null) {
                    color = other.conflictColor;
                } else {
                    color = other.color;
                }
            }

            if (other.backgroundColor != null) {
                if (backgroundColor != null && 
                    !backgroundColor.equals(other.backgroundColor) &&
                    other.conflictBackgroundColor != null) {
                    backgroundColor = other.conflictBackgroundColor;
                } else {
                    backgroundColor = other.backgroundColor;
                }
            }

            if (other.fontWeight != null) {
                fontWeight = other.fontWeight;
            }

            if (other.fontStyle != null) {
                fontStyle = other.fontStyle;
            }

            if (other.textDecoration != null) {
                textDecoration = other.textDecoration;
            }

            if (other.changeBarProps != null) {
                changeBarProps = other.changeBarProps;
            }

            if (other.startImage != null) {
                startImage = other.startImage;
                isAbsoluteStartImageURL = other.isAbsoluteStartImageURL;
            }

            if (other.startText != null) {
                startText = other.startText;
            }

            if (other.endImage != null) {
                endImage = other.endImage;
                isAbsoluteEndImageURL = other.isAbsoluteEndImageURL;
            }

            if (other.endText != null) {
                endText = other.endText;
            }
        }

        public void clear() {
            color = null;
            backgroundColor = null;
            fontWeight = null;
            fontStyle = null;
            textDecoration = null;
            changeBarProps = null;
            startImage = null;
            isAbsoluteStartImageURL = false;
            startText = null;
            endImage = null;
            isAbsoluteEndImageURL = false;
            endText = null;
        }

        public boolean isEmpty() {
            return (color == null &&
                    backgroundColor == null &&
                    fontWeight == null &&
                    fontStyle == null &&
                    textDecoration == null &&
                    changeBarProps == null &&
                    startImage == null &&
                    startText == null &&
                    endImage == null &&
                    endText == null);
        }

        public Flags copy() {
            Flags copy = new Flags();

            copy.color = color;
            copy.backgroundColor = backgroundColor;
            copy.fontWeight = fontWeight;
            copy.fontStyle = fontStyle;
            copy.textDecoration = textDecoration;
            copy.changeBarProps = changeBarProps;
            copy.startImage = startImage;
            copy.isAbsoluteStartImageURL = isAbsoluteStartImageURL;
            copy.startText = startText;
            copy.endImage = endImage;
            copy.isAbsoluteEndImageURL = isAbsoluteEndImageURL;
            copy.endText = endText;

            return copy;
        }

        @Override
        public String toString() {
            StringBuilder buffer = new StringBuilder();
            toString(buffer);
            return buffer.toString();
        }

        public void toString(StringBuilder buffer) {
            if (color != null) {
                buffer.append(" color=");
                buffer.append(color);
            }

            if (backgroundColor != null) {
                buffer.append(" backgroundColor=");
                buffer.append(backgroundColor);
            }

            if (fontWeight != null) {
                buffer.append(" fontWeight=");
                buffer.append(fontWeight);
            }

            if (fontStyle != null) {
                buffer.append(" fontStyle=");
                buffer.append(fontStyle);
            }

            if (textDecoration != null) {
                buffer.append(" textDecoration=");
                buffer.append(textDecoration);
            }

            if (changeBarProps != null) {
                for (int i = 0; i < changeBarProps.length; i += 2) {
                    buffer.append(' ');
                    buffer.append(changeBarProps[i]);
                    buffer.append('=');
                    buffer.append(changeBarProps[i+1]);
                }
            }

            if (startImage != null) {
                buffer.append(" startImage=");
                buffer.append(startImage.toExternalForm());
                if (isAbsoluteStartImageURL) {
                    buffer.append(" (ABSOLUTE URL)");
                }
            }

            if (startText != null) {
                buffer.append(" startText=\"");
                buffer.append(startText);
                buffer.append('"');
            }

            if (endImage != null) {
                buffer.append(" endImage=");
                buffer.append(endImage.toExternalForm());
                if (isAbsoluteEndImageURL) {
                    buffer.append(" (ABSOLUTE URL)");
                }
            }

            if (endText != null) {
                buffer.append(" endText=\"");
                buffer.append(endText);
                buffer.append('"');
            }
        }
    }

    // -----------------------------------------------------------------------

    private URL url;
    private Prop[] props;
    private String conflictColor;
    private String conflictBackgroundColor;

    private static final String[] NAMED_COLORS = {
        "aqua", "black", "blue", "fuchsia", "gray", "green", "lime", 
        "maroon", "navy", "olive", "purple", "red", "silver", "teal", 
        "white", "yellow", "orange"
    };

    // -----------------------------------------------------------------------

    /**
     * Constructs a Filter initialized using the contents of specified DITAVAL
     * file.
     */
    public Filter(File file, boolean validate, Console console) 
        throws IOException {
        this(FileUtil.fileToURL(file), validate, console);
    }

    /**
     * Constructs a Filter initialized using the contents of the DITAVAL file
     * having specified URL.
     */
    public Filter(URL url, boolean validate, Console console) 
        throws IOException {
        this(LoadDocument.load(url, validate, console));
        setURL(url);
    }

    /**
     * Constructs a Filter initialized using the contents of specified DITAVAL
     * document.
     */
    public Filter(Document doc) 
        throws IOException {
        props = new Prop[0];

        Element val = doc.getDocumentElement();
        if (val.getNamespaceURI() != null || 
            !"val".equals(val.getLocalName())) {
            reportError(val, Msg.msg("notADitaval", DOMUtil.formatName(val)));
            /*NOTREACHED*/
        }

        Node child = val.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                String childName = childElement.getLocalName();
                if ("prop".equals(childName)) {
                    parseProp(childElement);
                } else if ("revprop".equals(childName)) {
                    parseRevprop(childElement);
                } else if ("style-conflict".equals(childName)) {
                    parseStyleConflict(childElement);
                } else {
                    reportError(childElement, 
                                Msg.msg("unknownElement", 
                                        DOMUtil.formatName(childElement)));
                    /*NOTREACHED*/
                }
            }

            child = child.getNextSibling();
        }

        // Let all flags capture the conflict colors. ---

        if (conflictColor != null || conflictBackgroundColor != null) {
            for (Prop prop : props) {
                for (PropValue value : prop.values) {
                    Flags flags = value.flags;
                    if (flags != null) {
                        flags.conflictColor = conflictColor;
                        flags.conflictBackgroundColor = conflictBackgroundColor;
                    }
                }
            }
        }
    }

    public Filter(Filter other) {
        url = other.url;
        props = other.props;
        conflictColor = other.conflictColor;
        conflictBackgroundColor = other.conflictBackgroundColor;
    }

    public Filter() {
        setProps(null);
    }

    public void setProps(Prop[] props) {
        if (props == null) {
            props = new Prop[0];
        }
        this.props = props;
    }

    public void addExcludeProps(String... pairs) {
        Prop[] props = getProps();

        for (int i = 0; i < pairs.length; i += 2) {
            props = ArrayUtil.append(props,
                                     newExcludeProp(pairs[i], pairs[i+1]));
        }

        setProps(props);
    }

    public static Prop newExcludeProp(String attrName, String attrValue) {
        return new Prop(attrName, 
                        new PropValue(attrValue, Action.EXCLUDE,
                                      /*flags*/ null));
    }

    public Prop[] getProps() {
        return props;
    }

    public void setConflictColor(String conflictColor) {
        this.conflictColor = conflictColor;
    }

    public String getConflictColor() {
        return conflictColor;
    }

    public void setConflictBackgroundColor(String conflictBackgroundColor) {
        this.conflictBackgroundColor = conflictBackgroundColor;
    }

    public String getConflictBackgroundColor() {
        return conflictBackgroundColor;
    }

    public void setURL(URL url) {
        this.url = url;
    }

    /**
     * Returns the URL of the DITAVAL file used to initialize this Filter.
     * Returns <code>null</code> if unknown.
     */
    public URL getURL() {
        return url;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        toString(buffer);
        return buffer.toString();
    }

    public void toString(StringBuilder buffer) {
        if (conflictColor != null) {
            buffer.append("conflictColor=");
            buffer.append(conflictColor);
            buffer.append('\n');
        }

        if (conflictBackgroundColor != null) {
            buffer.append("conflictBackgroundColor=");
            buffer.append(conflictBackgroundColor);
            buffer.append('\n');
        }

        for (int i = 0; i < props.length; ++i) {
            props[i].toString(buffer);
        }
    }

    // -----------------------------------------------------------------------
    // Parse ".ditaval" files
    // -----------------------------------------------------------------------

    private void parseProp(Element element) 
        throws IOException {
        // EXTENSION: Any attribute may be use to filter or flag elements.

        String attrName = element.getAttributeNS(null, "att");
        if (attrName == null || (attrName = attrName.trim()).length() == 0) {
            // Wildcard.
            attrName = null;
        } else {
            // attrName uses Clark's notation.
            attrName = expandQName(attrName, element);
        }

        doParseProp(element, attrName);
    }

    private static final String expandQName(String qName, Node context) {
        if (qName == null) {
            return null;
        }

        int pos = qName.indexOf(':');
        if (pos <= 0 || pos == qName.length()-1) {
            return qName;
        }

        String prefix = qName.substring(0, pos);
        String localPart = qName.substring(pos+1);

        String ns = null;
        if ("xml".equals(prefix)) {
            ns = "http://www.w3.org/XML/1998/namespace";
        } else {
            ns = context.lookupNamespaceURI(prefix);
        }
        if (ns == null) {
            // Unknown prefix. An XML 1.0 name?
            return qName;
        }
        
        StringBuilder buffer = new StringBuilder();
        buffer.append('{');
        buffer.append(ns);
        buffer.append('}');
        buffer.append(localPart);
        return buffer.toString();
    }

    private void parseRevprop(Element element) 
        throws IOException {
        doParseProp(element, "rev");
    }

    private void doParseProp(Element element, String attrName) 
        throws IOException {
        String attrValue = element.getAttributeNS(null, "val");
        if (attrValue == null || (attrValue=attrValue.trim()).length() == 0) {
            // Wildcard.
            attrValue = null;
        }

        String action = element.getAttributeNS(null, "action");
        if (action == null || (action = action.trim()).length() == 0) {
            reportError(element, Msg.msg("missingAttribute", "action"));
            /*NOTREACHED*/
            return;
        }

        Action propAction = null;
        Flags flags = null;

        if ("exclude".equals(action)) {
            propAction = Action.EXCLUDE;
        } else if ("include".equals(action)) {
            propAction = Action.INCLUDE;
        } else if ("passthrough".equals(action)) {
            propAction = Action.PASSTHROUGH;
        } else if ("flag".equals(action)) {
            flags = new Flags();

            flags.color = parseColor(element, "color");
            flags.backgroundColor = parseColor(element, "backcolor");

            String value = element.getAttributeNS(null, "style");
            if (value != null && (value = value.trim()).length() > 0) {
                if ("underline".equals(value) ||
                    "double-underline".equals(value)) {
                    // LIMITATION: "double-underline" treated like "underline".
                    flags.textDecoration = "underline";
                } else if ("overline".equals(value)) {
                    flags.textDecoration = "overline";
                } else if ("line-through".equals(value)) {
                    // EXTENSION: "line-through".
                    flags.textDecoration = "line-through";
                } else if ("italics".equals(value)) {
                    flags.fontStyle = "italic";
                } else if ("bold".equals(value)) {
                    flags.fontWeight = "bold";
                } else {
                    reportError(element, Msg.msg("invalidStyle", value));
                    /*NOTREACHED*/
                }
            }

            value = element.getAttributeNS(null, "changebar");
            if (value != null && (value = value.trim()).length() > 0) {
                flags.changeBarProps = parseChangeBarProps(value);
            }

            Element startflag =
                DOMUtil.getChildElementByName(element, null, "startflag");
            if (startflag != null) {
                parseStartEndFlag(startflag, /*start*/ true, flags);
            }

            Element endflag =
                DOMUtil.getChildElementByName(element, null, "endflag");
            if (endflag != null) {
                parseStartEndFlag(endflag, /*start*/ false, flags);
            }
 
            if (flags.isEmpty()) {
                flags = null;
            }

            if (flags != null) {
                propAction = Action.FLAG;
            }
        } else {
            reportError(element, Msg.msg("invalidAttribute", action, "action"));
            /*NOTREACHED*/
            return;
        }
        
        // Add to the list ---

        if (propAction == null) {
            // Nothing to do.
            return;
        }

        Prop prop = null;

        for (int i = 0; i < props.length; ++i) {
            String propAttribute = props[i].attribute;
            if ((propAttribute == null && attrName == null) ||
                (propAttribute != null && propAttribute.equals(attrName))) {
                prop = props[i];
                break;
            }
        }

        PropValue propValue = new PropValue(attrValue, propAction, flags);
        if (prop == null) {
            prop = new Prop(attrName, propValue);
            props = ArrayUtil.append(props, prop);
        } else {
            prop.addValue(propValue);
        }
    }

    private static String[] parseChangeBarProps(String styles) {
        String[] props = splitStyleProps(styles);

        int propCount = props.length;
        if (propCount == 0) {
            props = null;
        } else {
            String[] props2 = new String[2*propCount];
            int j = 0;
            
            for (int i = 0; i < propCount; ++i) {
                String prop = props[i];

                String name = null;
                String value = null;

                int pos = prop.indexOf(':');
                if (pos > 0 && pos < prop.length()-1) {
                    name = prop.substring(0, pos).trim();
                    value = prop.substring(pos+1).trim();
                }

                if (name == null || name.length() == 0 ||
                    value == null || value.length() == 0) {
                    continue;
                }

                if (!name.startsWith("change-bar-")) {
                    name = "change-bar-" + name;
                }

                if ("change-bar-color".equals(name) ||
                    "change-bar-offset".equals(name) ||
                    "change-bar-placement".equals(name) ||
                    "change-bar-style".equals(name) ||
                    "change-bar-width".equals(name)) {
                    props2[j++] = name;
                    props2[j++] = value;
                }
            }

            if (j == 0) {
                props = null;
            } else {
                if (j != props2.length) {
                    props2 = ArrayUtil.trimToSize(props2, j);
                }
                props = props2;
            }
        }

        return props;
    }

    @SuppressWarnings("fallthrough")
    private static String[] splitStyleProps(String styles) {
        ArrayList<String> list = new ArrayList<String>();
        char quote = 0;
        StringBuilder buffer = null;

        final int length = styles.length();
        loop: for (int i = 0; i < length; ++i) {
            char c = styles.charAt(i);

            switch (c) {
            case ';':
                if (quote != 0) {
                    // ';' inside quoted string.
                    buffer.append(c);
                } else {
                    // End of style.
                    if (buffer != null) {
                        String style = buffer.toString().trim();
                        if (style.length() > 0) {
                            list.add(style);
                        }
                        buffer = null;
                    }
                }
                break;

            case '\"': case '\'':
                if (quote != 0) {
                    if (c == quote) {
                        int last = buffer.length()-1;
                        if (last < 0 || buffer.charAt(last) != '\\') {
                            // End of quoted string.
                            quote = 0;
                        }
                        // Otherwise, escaped quote.
                    }
                    // Otherwise, the other quote char (e.g. '\'' inside
                    // a '"' quoted string).
                } else {
                    // Begin quoted string.
                    quote = c;
                }
                /*FALLTHROUGH*/

            default:
                if (buffer == null) {
                    buffer = new StringBuilder();
                }
                buffer.append(c);
                break;
            }
        }

        if (buffer != null) {
            String style = buffer.toString().trim();
            if (style.length() > 0) {
                list.add(style);
            }
        }

        String[] styleList = new String[list.size()];
        return list.toArray(styleList);
    }

    private static void parseStartEndFlag(Element element, boolean isStartflag,
                                          Flags flags) 
        throws IOException {
        URL url = null;
        boolean isAbsoluteURL = false;

        String imageref = element.getAttributeNS(null, "imageref");
        if (imageref != null && (imageref = imageref.trim()).length() > 0) {
            try {
                url = Resolve.resolveURI(imageref, null);
                isAbsoluteURL = true;
            } catch (MalformedURLException ignored) {}

            if (url == null) {
                URL baseURL = null;

                String baseLocation = element.getBaseURI();
                if (baseLocation != null) {
                    try {
                        baseURL = URLUtil.createURL(baseLocation);
                    } catch (MalformedURLException ignored) {}
                }

                try {
                    url = URLUtil.createURL(baseURL, imageref);
                } catch (MalformedURLException ignored) {
                    reportError(element, Msg.msg("invalidAttribute", imageref,
                                                 "imageref"));
                    /*NOTREACHED*/
                    return;
                }
            }
        }

        // alt-text child ---

        String text = null;

        Element altText =
            DOMUtil.getChildElementByName(element, null, "alt-text");
        if (altText != null) {
            text = altText.getTextContent();
            if (text != null) {
                text = XMLText.collapseWhiteSpace(text);
                if (text.length() == 0) {
                    text = null;
                }
            }
        }

        // Update flags ---

        if (url != null || text != null) {
            if (isStartflag) {
                flags.startImage = url;
                flags.isAbsoluteStartImageURL = isAbsoluteURL;
                flags.startText = text;
            } else {
                flags.endImage = url;
                flags.isAbsoluteEndImageURL = isAbsoluteURL;
                flags.endText = text;
            }
        }
    }

    private void parseStyleConflict(Element element) 
        throws IOException {
        conflictColor = parseColor(element, "foreground-conflict-color");

        conflictBackgroundColor = 
            parseColor(element, "background-conflict-color");
    }

    private String parseColor(Element element, String attrName) 
        throws IOException {
        String value = element.getAttributeNS(null, attrName);
        if (value != null && (value = value.trim()).length() > 0) {
            if (StringList.contains(NAMED_COLORS, value) ||
                value.matches("#[0-9A-Fa-f]{6}")) {
                return value;
            }

            reportError(element, Msg.msg("invalidColor", value));
            /*NOTREACHED*/
        }

        // Not specified.
        return null;
    }

    private static void reportError(Element element, String message) 
        throws IOException {
        if (element != null) {
            NodeLocation location = 
                (NodeLocation) element.getUserData(NodeLocation.USER_DATA_KEY);
            if (location == null) {
                location = NodeLocation.UNKNOWN_LOCATION;
            }

            StringBuilder buffer = new StringBuilder();
            location.toString(buffer);
            buffer.append(": ");
            buffer.append(message);

            message = buffer.toString();
        }
        throw new IOException(message);
    }
}
