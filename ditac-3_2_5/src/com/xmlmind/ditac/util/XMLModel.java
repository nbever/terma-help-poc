/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import com.xmlmind.util.SystemUtil;
import com.xmlmind.util.StringList;
import com.xmlmind.util.XMLText;

/*package*/ final class XMLModel {
    public static final String PI_TARGET = "xml-model";
    public static final String PI_START = "<?" + PI_TARGET;
    public static final String XML_TYPE = "application/xml";
    public static final String RNC_TYPE = "application/relax-ng-compact-syntax";
    public static final String RNG_NS = "http://relaxng.org/ns/structure/1.0";

    public final String href;
    public final URL url;
    public final String type;
    public final String schematypens;
    public final String charset;
    public final String title;
    public final String group;
    public final String phase;

    // -----------------------------------------------------------------------

    public XMLModel(String href, URL url,
                    String type, String schematypens, String charset,
                    String title, String group, String phase) {
        this.href = href;
        this.url = url;
        this.type = type;
        this.schematypens = schematypens;
        this.charset = charset;
        this.title = title;
        this.group = group;
        this.phase = phase;
    }

    public boolean isRNC() {
        return (RNC_TYPE.equals(type) || href.endsWith(".rnc"));
    }

    public boolean isRNG() {
        return (RNG_NS.equals(schematypens) || href.endsWith(".rng"));
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(PI_START);

        if (href != null) {
            buffer.append(" href=");
            XMLText.quoteXML(href, buffer);
        }

        if (type != null) {
            buffer.append(" type=");
            XMLText.quoteXML(type, buffer);
        }

        if (schematypens != null) {
            buffer.append(" schematypens=");
            XMLText.quoteXML(schematypens, buffer);
        }

        if (charset != null) {
            buffer.append(" charset=");
            XMLText.quoteXML(charset, buffer);
        }

        if (title != null) {
            buffer.append(" title=");
            XMLText.quoteXML(title, buffer);
        }

        if (group != null) {
            buffer.append(" group=");
            XMLText.quoteXML(group, buffer);
        }

        if (phase != null) {
            buffer.append(" phase=");
            XMLText.quoteXML(phase, buffer);
        }

        buffer.append("?>");
        return buffer.toString();
    }

    // -----------------------------------------------------------------------
    // parse
    // -----------------------------------------------------------------------

    public static XMLModel parse(String piData, URL baseURL) 
        throws IllegalArgumentException {
        String href = null;
        URL url = null;
        String type = XML_TYPE;
        String schematypens = null;
        String charset = null;
        String title = null;
        String group = null;
        String phase = null;

        final String[] pairs = parsePseudoAttributes(piData);
        final int pairCount = pairs.length;
        for (int i = 0; i < pairCount; i += 2) {
            String key = pairs[i];
            String value = pairs[i+1].trim();
            if (value.length() == 0) {
                continue;
            }
            value = XMLText.unescapeXML(value);

            if ("href".equals(key)) {
                href = value;

                try {
                    url = Resolve.resolveURI(href, baseURL);
                } catch (MalformedURLException ignored) {
                    throw new IllegalArgumentException(
                        Msg.msg("invalidPseudoAttribute", value, "href"));
                }
            } else if ("type".equals(key)) {
                type = value;
            } else if ("schematypens".equals(key)) {
                schematypens = value;
            } else if ("charset".equals(key)) {
                charset = value;

                if (!StringList.containsIgnoreCase(SystemUtil.listEncodings(), 
                                                   charset)) {
                    throw new IllegalArgumentException(
                        Msg.msg("invalidPseudoAttribute", value, "charset"));
                }
            } else if ("title".equals(key)) {
                title = value;
            } else if ("group".equals(key)) {
                group = value;
            } else if ("phase".equals(key)) {
                phase = value;
            }
        }

        if (url == null) {
            throw new IllegalArgumentException(
                Msg.msg("missingPseudoAttribute", "href"));
        }

        return new XMLModel(href, url, type, schematypens, charset, 
                            title, group, phase);
    }

    private static String[] parsePseudoAttributes(String text) {
        String[] split = splitPseudoAttributes(text);
        if ((split.length % 3) != 0) {
            return null;
        }

        String[] parsed = new String[2*(split.length/3)];
        int j = 0;

        for (int i = 0; i < split.length; i += 3) {
            String name = split[i];
            String op = split[i+1];
            String value = split[i+2];

            if (!op.equals("=")) {
                return null;
            }

            // Unquote value.
            if (value.length() < 2) {
                return null;
            }
            char c1 = value.charAt(0);
            if (c1 != '\'' && c1 != '\"') {
                return null;
            }
            int last = value.length()-1;
            char c2 = value.charAt(last);
            if (c2 != c1) {
                return null;
            }
            value = value.substring(1, last);

            parsed[j++] = name;
            parsed[j++] = value;
        }

        return parsed;
    }

    private static String[] splitPseudoAttributes(String text) {
        ArrayList<String> split = new ArrayList<String>();
        StringBuilder part = new StringBuilder();
        int charCount = text.length();
        char quote = 0;

        for (int i = 0; i < charCount; ++i) {
            char c = text.charAt(i);

            switch (c) {
            case ' ': case '\n': case '\r': case '\t':
                if (quote != 0) {
                    part.append(c);
                } else {
                    if (part.length() > 0) {
                        split.add(part.toString());
                        part = new StringBuilder();
                    }
                }
                break;

            case '=':
                if (quote != 0) {
                    part.append(c);
                } else {
                    if (part.length() > 0) {
                        split.add(part.toString());
                        part = new StringBuilder();
                    }
                    split.add("=");
                }
                break;

            case '\'': case '\"':
                if (quote == 0) {
                    part.append(c);
                    quote = c;
                } else if (quote == c) {
                    part.append(c);
                    split.add(part.toString());
                    part = new StringBuilder();
                    quote = 0;
                } else {
                    // The ``other'' quote char.
                    part.append(c);
                }
                break;

            default:
                part.append(c);
            }
        }
        if (part.length() > 0) {
            split.add(part.toString());
        }

        String[] list = new String[split.size()];
        split.toArray(list);
        return list;
    }
}
