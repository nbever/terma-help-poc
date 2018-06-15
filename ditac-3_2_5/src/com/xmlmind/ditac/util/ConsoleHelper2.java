/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.util;

import org.w3c.dom.Element;
import com.xmlmind.util.Console;

/**
 * Same as {@link ConsoleHelper} except that it appends a 
 * <code>#element(element_pointer)</code> fragment to 
 * the system ID of the elements referenced in the messages.
 */
public class ConsoleHelper2 extends ConsoleHelper {
    public ConsoleHelper2(Console console) {
        super(console);
    }

    @Override
    protected String prependLocation(Element element, String message) {
        if (element != null) {
            NodeLocation location = (NodeLocation)
                element.getUserData(NodeLocation.USER_DATA_KEY);
            if (location == null) {
                location = NodeLocation.UNKNOWN_LOCATION;
            }

            StringBuilder buffer = new StringBuilder();

            if (location.systemId != null) {
                buffer.append(location.systemId);

                if (location.elementPointer != null) {
                    buffer.append("#element(");
                    buffer.append(location.elementPointer);
                    buffer.append(')');
                }
            }
            buffer.append('\uEEEE'); // Private Use Area: U+E000 - U+F8FF.
            if (location.lineNumber > 0) {
                buffer.append(Integer.toString(location.lineNumber));
            }
            buffer.append(':');
            if (location.columnNumber > 0) {
                buffer.append(Integer.toString(location.columnNumber));
            }

            buffer.append(": ");
            buffer.append(message);

            message = buffer.toString();
        }

        return message;
    }
}

