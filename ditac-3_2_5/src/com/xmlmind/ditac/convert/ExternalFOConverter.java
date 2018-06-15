/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.convert;

import java.io.File;
import java.net.URL;
import com.xmlmind.util.SystemUtil;
import com.xmlmind.util.FileUtil;
import com.xmlmind.util.URIComponent;
import com.xmlmind.util.URLUtil;
import com.xmlmind.util.Console;

/**
 * An implementation of {@link FOConverter} based on 
 * an external command-line utility such as <tt>/opt/xep/xep</tt>, 
 * <tt>D:\opt\fop\fop.bat</tt>, etc.
 */
public final class ExternalFOConverter implements FOConverter {
    /**
     * The name of this XSL-FO processor.
     */
    public final String processorName;

    /**
     * The format generated by this XSL-FO processor.
     */
    public final Format targetFormat;

    /**
     * Command to be executed by a <tt>/bin/sh</tt> on Unix and 
     * <tt>cmd.exe</tt> on Windows.
     *
     * <p>This command may contain the following variables which are 
     * substituted with their values prior to executing the command:
     * <table border="1" summary="variables">
     * <tr style="background-color: #E0E0FF;">
     * <th>Variable</th>
     * <th>Description</th>
     * <tr>
     * <td>%I</td>
     * <td>The XSL-FO input file.</td>
     * </tr>
     * <tr>
     * <td>%i</td>
     * <td>Same as %I but an URL rather a filename.</td>
     * </tr>
     * <tr>
     * <td>%O</td>
     * <td>The output file.</td>
     * </tr>
     * <tr>
     * <td>%o</td>
     * <td>Same as %O but an URL rather a filename.</td>
     * </tr>
     * <tr>
     * <td>%S</td>
     * <td>Portable filename separator: '\' on Windows, '/' 
     * on all the other platforms.</td>
     * </tr>
     * </table>
     *
     * <p>A variable name may be preceded by one of these modifiers:
     * <table border="1" summary="modifiers">
     * <tr style="background-color: #E0E0FF;">
     * <th>Modifier</th>
     * <th>Description</th>
     * <th>Example</th>
     * <tr>
     * <td>~p</td>
     * <td>Parent directory.</td>
     * <td>
     * <p>%~pI = C:\Users\john\Documents\doc&nbsp;src
     * <p>%~pi = file:/C:/Users/john/Documents/doc%20src/ 
     * (note the trailing '/')
     * </td>
     * </tr>
     * <tr>
     * <td>~n</td>
     * <td>Basename of the file, including the extension.</td>
     * <td>
     * <p>%~nI = manual.xml
     * <p>%~ni = manual.xml
     * </td>
     * </tr>
     * <tr>
     * <td>~r</td>
     * <td>Basename of the file, without the extension.</td>
     * <td>
     * <p>%~rI = manual
     * <p>%~ri = manual
     * </td>
     * </tr>
     * <tr>
     * <td>~e</td>
     * <td>File extension, if any.</td>
     * <td>
     * <p>%~eI = xml
     * <p>%~ei = xml
     * </td>
     * </tr>
     * </table>
     */
    public final String command;

    /**
     * Constructs an ExternalFOConverter fully initialized using specified
     * arguments.
     *
     * @param processorName the name of the XSL-FO processor
     * @param targetFormat the format generated by the XSL-FO processor
     * @param command command to be executed to convert 
     * the XSL-FO input file to the output format
     */
    public ExternalFOConverter(String processorName,
                               Format targetFormat,
                               String command) {
        this.processorName = processorName;
        this.targetFormat = targetFormat;
        this.command = command;
    }

    public String getProcessorName() {
        return processorName;
    }

    public Format getTargetFormat() {
        return targetFormat;
    }

    public void convertFO(File inFile, File outFile, Console console) 
        throws Exception {
        inFile = inFile.getCanonicalFile();
        outFile = outFile.getCanonicalFile();
        String cmd = substituteVars(command, inFile, outFile);

        console.showMessage(Msg.msg("runningFOConverter", 
                                    processorName, targetFormat, cmd), 
                            Console.MessageType.VERBOSE);

        File outDir = outFile.getParentFile();
        if (!outDir.isDirectory()) {
            outDir = null;
        }

        int exitCode = SystemUtil.shellExec(cmd, null, outDir, console);
        if (exitCode != 0) {
            throw new RuntimeException(Msg.msg("commandHasFailed",
                                               cmd, exitCode));
        }
    }

    /**
     * Returns a string representation of this ExternalFOConverter
     * which is useful when debugging.
     */
    public String toString() {
        StringBuilder buffer = new StringBuilder(processorName);
        buffer.append('[');
        buffer.append(targetFormat);
        buffer.append("]: \"");
        buffer.append(command);
        buffer.append('"');
        return buffer.toString();
    }

    // -----------------------------------
    // substituteVars
    // -----------------------------------

    private static final int IN_FILE  = 0x01;
    private static final int OUT_FILE = 0x02;
    
    private static final int URL_FORM = 0x0100;

    private static final int MOD_PARENT    = 0x010000;
    private static final int MOD_NAME      = 0x020000;
    private static final int MOD_ROOT_NAME = 0x040000;
    private static final int MOD_EXTENSION = 0x080000;

    private static String substituteVars(String text,
                                         File inFile, File outFile) {
        if (text.indexOf('%') < 0) {
            return text;
        }

        StringBuilder buffer = new StringBuilder();
        
        int length = text.length();
        for (int i = 0; i < length; ++i) {
            char c = text.charAt(i);

            if (c == '%') {
                switch (charAt(text, length, i+1)) {
                case '%':
                    buffer.append('%');
                    ++i;
                    break;
                case 'S':
                    buffer.append(File.separatorChar);
                    ++i;
                    break;
                default:
                    {
                        int parsed = parseVar(text, length, i+1);
                        if ((parsed & IN_FILE) != 0 && 
                            inFile != null) {
                            buffer.append(varValue(inFile, null, parsed));
                            if (hasModifier(parsed)) {
                                i += 3;
                            } else {
                                ++i;
                            }
                        } else if ((parsed & OUT_FILE) != 0 && 
                                   outFile != null) {
                            buffer.append(varValue(outFile, null, parsed));
                            if (hasModifier(parsed)) {
                                i += 3;
                            } else {
                                ++i;
                            }
                        } else {
                            buffer.append(c);
                        }
                    }
                    break;
                }
            } else {
                buffer.append(c);
            }
        }

        return buffer.toString();
    }

    private static char charAt(String text, int length, int i) {
        return (i < length)? text.charAt(i) : '\0';
    }

    private static int parseVar(String text, int length, int i) {
        switch (charAt(text, length, i)) {
        case '~':
            {
                int modifier;
                switch (charAt(text, length, i+1)) {
                case 'p':
                    modifier = MOD_PARENT;
                    break;
                case 'n':
                    modifier = MOD_NAME;
                    break;
                case 'r':
                    modifier = MOD_ROOT_NAME;
                    break;
                case 'e':
                    modifier = MOD_EXTENSION;
                    break;
                default:
                    return 0x0;
                }

                switch (charAt(text, length, i+2)) {
                case 'I':
                    return modifier|IN_FILE;
                case 'O':
                    return modifier|OUT_FILE;

                case 'i':
                    return modifier|IN_FILE|URL_FORM;
                case 'o':
                    return modifier|OUT_FILE|URL_FORM;

                default:
                    return 0x0;
                }
            }

        case 'I':
            return IN_FILE;
        case 'O':
            return OUT_FILE;

        case 'i':
            return IN_FILE|URL_FORM;
        case 'o':
            return OUT_FILE|URL_FORM;

        default:
            return 0x0;
        }
    }

    private static String varValue(File file, URL url, int spec) {
        String value = null;

        if ((spec & URL_FORM) != 0) {
            if (url == null) {
                assert(file != null);
                url = FileUtil.fileToURL(file);
                if (url == null) {
                    return "";
                }
            }

            if ((spec & MOD_PARENT) != 0) {
                // Always ends with a '/'.
                URL parent = URLUtil.getParent(url);
                if (parent != null) {
                    value = parent.toExternalForm();
                }
            } else if ((spec & MOD_NAME) != 0) {
                // Not null when url ends with a '/'.
                value = URLUtil.getRawBaseName(url);
            } else if ((spec & MOD_ROOT_NAME) != 0) {
                value = URLUtil.getRawBaseName(url);
                if (value != null) {
                    value = URIComponent.setRawExtension(value, null);
                }
            } else if ((spec & MOD_EXTENSION) != 0) {
                value = URLUtil.getRawExtension(url);
            } else {
                value = url.toExternalForm();
            }
        } else {
            if (file == null) {
                assert(url != null);
                file = URLUtil.urlToFile(url);
                if (file == null) {
                    return "";
                }
            }
                
            // Assumes that file is canonical.

            if ((spec & MOD_PARENT) != 0) {
                value = file.getParent();
            } else if ((spec & MOD_NAME) != 0) {
                value = file.getName();
            } else if ((spec & MOD_ROOT_NAME) != 0) {
                value = FileUtil.setExtension(file.getName(), null);
            } else if ((spec & MOD_EXTENSION) != 0) {
                value = FileUtil.getExtension(file.getName());
            } else {
                value = file.getPath();
            }
        }

        return (value == null)? "" : value;
    }

    private static boolean hasModifier(int parsed) {
        return 
          ((parsed & (MOD_PARENT|MOD_NAME|MOD_ROOT_NAME|MOD_EXTENSION)) != 0);
    }
}
