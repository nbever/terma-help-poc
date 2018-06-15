/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved. 
 *
 * Author: Hussein Shafie
 *
 * This file is part of several XMLmind projects.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.util;

import java.io.IOException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import java.util.jar.Manifest;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * A collection of utility functions (static methods) operating on JARs.
 */
public final class JarUtil {
    private JarUtil() {}

    /**
     * Returns the URLs of the JARs listed in the <code>Class-Path</code>
     * attribute of the manifest of a JAR.
     * 
     * @param jarURL the URL of the JAR
     * @return the URLs of the JARs listed in the <code>Class-Path</code>
     * attribute of the manifest or an empty array if the manifest or the
     * <code>Class-Path</code> attribute were not found
     * @exception IOException if an I/O error occurs while reading the JAR
     */
    public static URL[] getClassPath(URL jarURL) 
        throws IOException {
        String classPath = getClassPathAttribute(jarURL);
        if (classPath == null) {
            return URLUtil.EMPTY_LIST;
        }
            
        String[] names = StringUtil.split(classPath);
        URL[] urls = new URL[names.length];
        int j = 0;

        for (String name : names) {
            try {
                urls[j++] = URLUtil.createURL(jarURL, name);
            } catch (MalformedURLException ignored) {}
        }

        if (j != urls.length) {
            urls = ArrayUtil.trimToSize(urls, j);
        }

        return urls;
    }

    /**
     * Returns the <code>Class-Path</code> attribute of the manifest 
     * of a JAR.
     * 
     * @param jarURL the URL of the JAR
     * @return the <code>Class-Path</code> attribute of the manifest if any;
     * <code>null</code> otherwise
     * @exception IOException if an I/O error occurs while reading the JAR
     */
    public static String getClassPathAttribute(URL jarURL) 
        throws IOException {
        Manifest manifest = null;

        JarInputStream in = 
            new JarInputStream(URLUtil.openStreamUseCache(jarURL), false);
        try {
            manifest = in.getManifest();
        } finally {
            in.close();
        }

        if (manifest == null) {
            return null;
        }

        return manifest.getMainAttributes().getValue("Class-Path");
    }

    // -----------------------------------------------------------------------

    /**
     * Returns the service providers found in a JAR.
     * 
     * @param jarURL the URL of the JAR
     * @return a possibly empty map where a key is the name of a service
     * and the value is an array of names of classes implementing this service
     * @exception IOException if an I/O error occurs while reading the JAR
     */
    public static Map<String,String[]> getServiceProviders(URL jarURL) 
        throws IOException {
        JarInputStream in = 
            new JarInputStream(URLUtil.openStreamUseCache(jarURL), false);

        HashMap<String,String[]> map = new HashMap<String,String[]>();

        try {
            JarEntry entry;
            while ((entry = in.getNextJarEntry()) != null) {
                String entryName = entry.getName();

                if (entryName.startsWith("META-INF/services/") &&
                    !entry.isDirectory()) {
                    String text = FileUtil.loadString(in, "UTF-8");
                    in.closeEntry();

                    String[] lines = StringUtil.split(text.trim(), '\n');
                    String[] services = new String[lines.length];
                    int j = 0; 

                    for (String line : lines) {
                        line = line.trim();
                        if (line.length() > 0 && line.charAt(0) != '#') {
                            services[j++] = line;
                        }
                    }

                    if (j != services.length) {
                        services = ArrayUtil.trimToSize(services, j);
                    }

                    String serviceName = URIComponent.getRawBaseName(entryName);
                    map.put(serviceName, services);
                }
            }
        } finally {
            in.close();
        }

        return map;
    }

    /*TEST_JAR
    public static void main(String[] args) throws IOException {
        URL jarURL = null;
        if (args.length != 1 ||
            (jarURL = URLUtil.urlOrFile(args[0])) == null) {
            System.err.println(
                "usage: java com.xmlmind.util.JarUtil URL_or_file");
            System.exit(1);
        }

        System.out.println("Class-Path");
        URL[] urls = JarUtil.getClassPath(jarURL);
        for (URL url : urls) {
            System.out.println("    " + url);
        }

        System.out.println("META-INF/services/");
        Map<String,String[]> map = JarUtil.getServiceProviders(jarURL);
        java.util.Iterator<Map.Entry<String,String[]>> iter = 
            map.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String,String[]> e = iter.next();

            System.out.println("    " + e.getKey());
            for (String className : e.getValue()) {
                System.out.println("        " + className);
            }
        }
    }
    TEST_JAR*/
}
