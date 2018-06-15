/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.preprocess;

import java.util.HashMap;
import java.util.Arrays;
import com.xmlmind.util.ArrayUtil;
import com.xmlmind.util.StringUtil;
import com.xmlmind.util.XMLText;

/**
 * Not part of the public, documented, API.
 */
public final class KeySpace {
    public final String id;

    private String[] keyscopeNames;

    private KeySpace parent;
    private KeySpace[] children;

    private HashMap<String,KeyDefinition> keyDefinitions;
    private KeyDefinition[] allKeyDefinitions;

    private static final KeySpace[] EMPTY_LIST = new KeySpace[0];

    // -----------------------------------------------------------------------

    public KeySpace(String id, String keyscope) {
        this.id = id;

        initKeyscopeNames(keyscope);

        children = EMPTY_LIST;

        keyDefinitions = new HashMap<String,KeyDefinition>();
    }

    public void initKeyscopeNames(String keyscope) {
        if (keyscope == null) {
            keyscopeNames = StringUtil.EMPTY_LIST;
        } else {
            keyscopeNames = XMLText.splitList(keyscope);
        }
    }

    public String[] getKeyscopeNames() {
        return keyscopeNames;
    }

    public void initParentKeySpace(KeySpace parent) {
        this.parent = parent;
    }

    public KeySpace getParentKeySpace() {
        return parent;
    }

    public void addChildKeySpace(KeySpace child) {
        if (ArrayUtil.find(children, child) < 0) {
            children = ArrayUtil.append(children, child);
        }
    }

    public KeySpace[] getChildKeySpaces() {
        return children;
    }

    public void set(KeyDefinition kd) {
        keyDefinitions.put(kd.key, kd);
        allKeyDefinitions = null;
    }

    public KeyDefinition get(String key) {
        return keyDefinitions.get(key);
    }

    public boolean contains(String key) {
        return keyDefinitions.containsKey(key);
    }

    public KeyDefinition[] getAll() {
        if (allKeyDefinitions == null) {
            int count = keyDefinitions.size();
            allKeyDefinitions = new KeyDefinition[count];
            keyDefinitions.values().toArray(allKeyDefinitions);
        }
        return allKeyDefinitions;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        toString(/*details*/ false, buffer);
        return buffer.toString();
    }

    public void toString(boolean details, StringBuilder buffer) {
        buffer.append("KeySpace[id=");
        buffer.append(id);

        buffer.append(", keyscope=");
        buffer.append(StringUtil.join(' ', keyscopeNames));

        buffer.append(", parent=");
        if (parent != null) {
            buffer.append(parent.id);
        }

        buffer.append(", children=");
        for (int k = 0; k < children.length; ++k) {
            KeySpace child = children[k];

            if (k > 0) {
                buffer.append(' ');
            }
            buffer.append(child.id);
        }

        buffer.append(", definitions=");
        buffer.append('\n');
        KeyDefinition[] all = getAll();
        int count = all.length;
        if (count > 0) {
            if (count > 1) {
                Arrays.sort(all);
            }

            if (details) {
                for (KeyDefinition kd : all) {
                    buffer.append(kd);
                }
            } else {
                for (KeyDefinition kd : all) {
                    buffer.append("  ");

                    if (kd.fromChildKeySpace > 0) {
                        buffer.append('(');
                    }
                    buffer.append(kd.key);
                    if (kd.fromChildKeySpace > 0) {
                        buffer.append(')');
                    }

                    String href = kd.getHref();
                    String text = kd.getText();
                    if (href != null || text != null) {
                        buffer.append('=');
                        if (href != null) {
                            buffer.append(href);
                        }
                        if (text != null) {
                            buffer.append('"');
                            int textLength = text.length();
                            if (textLength > 40) {
                                buffer.append(text.substring(0, 19));
                                buffer.append("...");
                                buffer.append(text.substring(textLength-18));
                            } else {
                                buffer.append(text);
                            }
                            buffer.append('"');
                        }
                    }

                    buffer.append('\n');
                }
            }
        }

        buffer.append(']');
    }
}
