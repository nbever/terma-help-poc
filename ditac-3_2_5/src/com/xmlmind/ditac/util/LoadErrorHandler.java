/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.util;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ErrorHandler;
import com.xmlmind.util.Console;

/*package*/ final class LoadErrorHandler implements ErrorHandler {
    public final Console console; // May be null.
    private int errorCount;

    // -----------------------------------------------------------------------

    public LoadErrorHandler(Console console) {
        this.console = console;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void warning(SAXParseException e)
        throws SAXException {
        String message = format(e, "warning");
        if (console != null) {
            console.showMessage(message, Console.MessageType.WARNING);
        } else {
            System.err.println(message);
        }
    }

    public void error(SAXParseException e)
        throws SAXException {
        ++errorCount;
        String message = format(e, "error");
        if (console != null) {
            console.showMessage(message, Console.MessageType.ERROR);
        } else {
            System.err.println(message);
        }
    }

    public void fatalError(SAXParseException e)
        throws SAXException {
        // To be reported by loader.
        throw e;
    }

    public static String format(SAXParseException e, String severity) {
        StringBuilder buffer = new StringBuilder();

        if (e.getSystemId() != null) {
            buffer.append(e.getSystemId());
        }

        buffer.append(':');
        if (e.getLineNumber() > 0) {
            buffer.append(e.getLineNumber());
        }

        buffer.append(':');
        if (e.getColumnNumber() > 0) {
            buffer.append(e.getColumnNumber());
        }

        buffer.append(':');
        if (severity == null) {
            severity = "parse error";
        }
        buffer.append(severity);
        buffer.append(": ");
        buffer.append(e.getMessage());

        return buffer.toString();
    }
}
