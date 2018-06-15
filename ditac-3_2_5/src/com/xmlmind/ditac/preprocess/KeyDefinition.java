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
import com.xmlmind.util.StringUtil;
import com.xmlmind.util.URLUtil;
import com.xmlmind.util.XMLText;
import org.w3c.dom.Element;
import com.xmlmind.ditac.util.DOMUtil;
import com.xmlmind.ditac.util.DITAUtil;

/**
 * Not part of the public, documented, API.
 */
public final class KeyDefinition 
             implements Constants, Comparable<KeyDefinition> {
    /**
     * The key.
     */
    public final String key;

    /**
     * A <em>stand-alone</em> topicref which is the definition of the key.
     * May be completely empty (no content, no attributes).
     * <p>The href attribute, if any, contains an absolute URI.
     * If it points to a topic, this URI ends with 
     * <tt>#<i>topic_id</i></tt>. (That is, the href attribute is 
     * ready-to-use.)
     * <p>This element has a ditac:absoluteHref="true" if the href 
     * attribute was specified by the DITA author as an absolute URI.
     */
    public final Element element;

    /**
     * Strictly positive if this key definition has been copied from
     * a child key space. Value is 1 if it comes for a child, 
     * 2 if it comes from a grand-child, etc.
     */
    public final int fromChildKeySpace;

    private boolean initQuickFields;
    private String href;
    private boolean isAbsoluteHref;
    private URL url;
    private String text;

    // -------------------------------------------------------------------

    public KeyDefinition(String key, Element element, int fromChildKeySpace) {
        this.key = key;
        this.element = element;
        this.fromChildKeySpace = fromChildKeySpace;
    }

    public String getAttribute(String ns, String localName) {
        return DITAUtil.getNonEmptyAttribute(element, ns, localName);
    }

    /**
     * returns the absolute URI corresponding to <tt>key</tt>. 
     * May return <code>null</code>.
     * <p>This URI, if any, ends with <tt>#<i>topic_id</i></tt> 
     * if specified key designates a topic.
     */
    public String getHref() {
        if (!initQuickFields) {
            initQuickFields = true;
            initQuickFields();
        }
        return href;
    }

    private void initQuickFields() {
        href = getAttribute(null, "href");

        if (href != null) {
            isAbsoluteHref =
                "true".equals(getAttribute(DITAC_NS_URI, ABSOLUTE_HREF_NAME));

            try {
                url = URLUtil.createURL(href);
            } catch (MalformedURLException ignored) {}
        }

        Element meta = getMeta();
        if (meta != null) {
            Element container = null;

            Element keywords = 
                DITAUtil.findChildByClass(meta, "topic/keywords");
            if (keywords != null) {
                container = DITAUtil.findChildByClass(keywords, 
                                                      "topic/keyword");
            }

            if (container == null) {
                container = DITAUtil.findChildByClass(meta, "map/linktext");
            }

            if (container == null) {
                container = meta;
            }

            text = container.getTextContent();
            if (text != null && 
                (text = XMLText.collapseWhiteSpace(text)).length() == 0) {
                text = null;
            }
        }
    }
    
    /**
     * Returns <code>true</code> if the <tt>href</tt> attribute contained 
     * an absolute URL as its value; <code>false</code> otherwise.
     * <p>This method is needed because {@link #getHref} always 
     * returns an absolute location.
     */
    public boolean isAbsoluteHref() {
        if (!initQuickFields) {
            initQuickFields = true;
            initQuickFields();
        }
        return isAbsoluteHref;
    }

    /**
     * Returns the URL corresponding to {@link #href}.
     * Returns <code>null</code> if <tt>href</tt> is absent or malformed.
     */
    public URL getURL() {
        if (!initQuickFields) {
            initQuickFields = true;
            initQuickFields();
        }
        return url;
    }

    public Element getMeta() {
        return DOMUtil.getFirstChildElement(element);
    }
    
    /**
     * Returns the textual content corresponding to <tt>key</tt>. 
     * May return <code>null</code>.
     */
    public String getText() {
        if (!initQuickFields) {
            initQuickFields = true;
            initQuickFields();
        }
        return text;
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof KeyDefinition)) {
            return false;
        }
        return key.equals(((KeyDefinition) other).key);
    }

    @Override
    public String toString() {
        Element copy = (Element) element.cloneNode(/*deep*/ true);
        copy.setAttributeNS(null, "keys", key);

        if (fromChildKeySpace > 0) {
            copy.setAttributeNS(DITAC_NS_URI, "fromChildKeySpace", 
                                Integer.toString(fromChildKeySpace));
        }

        return DOMUtil.toString(copy);
    }

    public int compareTo(KeyDefinition other) {
        return key.compareTo(other.key);
    }
}

