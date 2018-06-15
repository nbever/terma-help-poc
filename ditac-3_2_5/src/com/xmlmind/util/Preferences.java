/*
 * Copyright (c) 2017 XMLmind Software. All right reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of several XMLmind projects.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.util;

import java.io.IOException;
import java.io.File;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.awt.Rectangle;
import java.awt.Color;
import java.awt.Font;
import java.awt.print.Paper;

/**
 * Class used by an application to store the preferences of its user.
 * <p>This class is basically a wrapper around a {@link Properties} object, 
 * implementing a number of convenience methods such as {@link #getBoolean}.
 * <p>This class is thread-safe.
 */
public class Preferences {
    /**
     * The <code>Properties</code> object being wrapped by 
     * this <code>Preferences</code> object.
     */
    public final Properties properties;

    // -----------------------------------------------------------------------

    /**
     * Constructs an empty <code>Preferences</code> object.
     */
    public Preferences() {
        this(null);
    }

    /**
     * Constructs a <code>Preferences</code> object acting a a wrapper for 
     * specified <code>Properties</code> object.
     * 
     * @param properties wrapped <code>Properties</code> object. 
     * May be <code>null</code> in which case a <code>Properties</code> object
     * is created.
     */
    public Preferences(Properties properties) {
        if (properties == null) {
            properties = new Properties();
        }
        this.properties = properties;
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param value the value of the preference
     */
    public synchronized void putString(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found
     * @return specified preference if found; returns <code>fallback</code>
     * otherwise
     */
    public synchronized String getString(String key, String fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        return value;
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param value the value of the preference
     */
    public synchronized void putBoolean(String key, boolean value) {
        properties.setProperty(key, value? "true" : "false");
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as a <code>boolean</code>
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized boolean getBoolean(String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        if ("true".equals(value)) {
            return true;
        } else if ("false".equals(value)) {
            return false;
        } else {
            return fallback;
        }
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param value the value of the preference
     */
    public synchronized void putInt(String key, int value) {
        properties.setProperty(key, Integer.toString(value));
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param min minimum allowed value for the preference
     * @param max maximum allowed value for the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as an <code>int</code> or is less than
     * <code>min</code> or is greater than <code>max</code>
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized int getInt(String key, int min, int max,
                                   int fallback) {
        int i = getInt(key, fallback);
        if (i < min || i > max) {
            return fallback;
        } else {
            return i;
        }
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as an <code>int</code>
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized int getInt(String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param value the value of the preference
     */
    public synchronized void putLong(String key, long value) {
        properties.setProperty(key, Long.toString(value));
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param min minimum allowed value for the preference
     * @param max maximum allowed value for the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as an <code>long</code> or is less than
     * <code>min</code> or is greater than <code>max</code>
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized long getLong(String key, long min, long max,
                                     long fallback) {
        long i = getLong(key, fallback);
        if (i < min || i > max) {
            return fallback;
        } else {
            return i;
        }
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as an <code>long</code>
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized long getLong(String key, long fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param value the value of the preference
     */
    public synchronized void putDouble(String key, double value) {
        properties.setProperty(key, Double.toString(value));
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param min minimum allowed value for the preference
     * @param max maximum allowed value for the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as a <code>double</code> or is less than
     * <code>min</code> or is greater than <code>max</code>
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized double getDouble(String key, double min, double max, 
                                         double fallback) {
        double i = getDouble(key, fallback);
        if (i < min || i > max) {
            return fallback;
        } else {
            return i;
        }
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as a <code>double</code>
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized double getDouble(String key, double fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param value the value of the preference
     */
    public synchronized void putStrings(String key, String[] value) {
        StringBuilder buffer = new StringBuilder();

        final int count = value.length;
        for (int i = 0; i < count; ++i) {
            String s = value[i];

            if (s.indexOf('&') >= 0) {
                s = StringUtil.replaceAll(s, "&", "&amp;");
            }

            if (s.indexOf('\n') >= 0) {
                s = StringUtil.replaceAll(s, "\n", "&#xA;");
            }

            if (i > 0) {
                buffer.append('\n');
            }
            buffer.append(s);
        }

        properties.setProperty(key, buffer.toString());
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found
     * @return specified preference if found; returns <code>fallback</code>
     * otherwise
     */
    public synchronized String[] getStrings(String key, String[] fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        if (value.length() == 0) {
            return new String[0];
        }

        String[] list = StringUtil.split(value, '\n');
        final int count = list.length;
        for (int i = 0; i < count; ++i) {
            String s = list[i];

            if (s.indexOf("&#xA;") >= 0) {
                s = StringUtil.replaceAll(s, "&#xA;", "\n");
            }

            if (s.indexOf("&amp;") >= 0) {
                s = StringUtil.replaceAll(s, "&amp;", "&");
            }

            list[i] = s;
        }

        return list;
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param value the value of the preference
     */
    public synchronized void putInts(String key, int[] value) {
        StringBuilder buffer = new StringBuilder();

        final int count = value.length;
        for (int i = 0; i < count; ++i) {
            if (i > 0) {
                buffer.append(' ');
            }
            buffer.append(value[i]);
        }

        properties.setProperty(key, buffer.toString());
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as an array of <code>int</code>s
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized int[] getInts(String key, int[] fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        StringTokenizer tokens = new StringTokenizer(value);
        final int count = tokens.countTokens();
        int[] list = new int[count];

        for (int i = 0; i < count; ++i) {
            try {
                list[i] = Integer.parseInt(tokens.nextToken());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        return list;
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param value the value of the preference
     */
    public synchronized void putDoubles(String key, double[] value) {
        StringBuilder buffer = new StringBuilder();

        final int count = value.length;
        for (int i = 0; i < count; ++i) {
            if (i > 0) {
                buffer.append(' ');
            }
            buffer.append(value[i]);
        }

        properties.setProperty(key, buffer.toString());
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as an array of <code>double</code>s
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized double[] getDoubles(String key, double[] fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }

        StringTokenizer tokens = new StringTokenizer(value);
        final int count = tokens.countTokens();
        double[] list = new double[count];

        for (int i = 0; i < count; ++i) {
            try {
                list[i] = Double.parseDouble(tokens.nextToken());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        return list;
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param value the value of the preference
     */
    public synchronized void putURL(String key, URL value) {
        putString(key, value.toExternalForm());
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as an <code>URL</code>
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized URL getURL(String key, URL fallback) {
        String value = getString(key, null);
        if (value == null) {
            return fallback;
        }

        URL url = null;
        try {
            url = new URL(value);
        } catch (MalformedURLException ignored) {}

        return (url == null)? fallback : url;
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param urls the value of the preference
     */
    public synchronized void putURLs(String key, URL[] urls) {
        final int count = urls.length;
        String[] locations = new String[count];
        for (int i = 0; i < count; ++i) {
            locations[i] = urls[i].toExternalForm();
        }
        putStrings(key, locations);
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as an array of <code>URL</code>s
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized URL[] getURLs(String key, URL[] fallback) {
        String[] locations = getStrings(key, null);
        if (locations == null) {
            return fallback;
        }

        final int count = locations.length;
        URL[] urls = new URL[count];
        for (int i = 0; i < count; ++i) {
            URL url = null;
            try {
                url = new URL(locations[i]);
            } catch (MalformedURLException ignored) {}
            if (url == null) {
                return fallback;
            }

            urls[i] = url;
        }

        return urls;
    }

    /**
     * Considers that the specified preference contains a map 
     * which maps an URL to another URL; adds or replaces in this map 
     * the value corresponding to specified key.
     * 
     * @param key the name of the preference
     * @param urlKey the key for which an entry is to be added or replaced 
     * in the map
     * @param urlValue the value of <tt>urlKey</tt>.
     * May be <code>null</code> in case the map entry corresponding 
     * to <tt>urlKey</tt> is removed.
     * @param maxEntries capacity of the map (in terms of entries, not in
     * terms of URLs).
     * If this capacity is exceeded, the oldest entries (that is,
     * the first added ones) are automatically removed.
     * @see #getURLEntry
     * @see #putURLs
     * @see #getURLs
     */
    public synchronized void putURLEntry(String key, URL urlKey, URL urlValue, 
                                         int maxEntries) {
        String[] urlMap = getStrings(key, null);
        int urlMapLength = (urlMap == null)? 0 : urlMap.length;

        if (maxEntries < 1) {
            maxEntries = 1;
        }
        int maxLength = 2*maxEntries;

        String[] entries = new String[Math.max(maxLength, urlMapLength) + 2];
        int length = 0;

        if (urlMap != null) {
            int count = 2*(urlMapLength/2);
            for (int i = 0; i < count; i += 2) {
                try {
                    URL url = new URL(urlMap[i]);
                    
                    if (!url.equals(urlKey)) {
                        entries[length] = urlMap[i];
                        entries[length+1] = urlMap[i+1];
                        length += 2;
                    }
                } catch (MalformedURLException ignored) {}
            }
        }

        if (urlValue != null) {
            entries[length] = urlKey.toExternalForm();
            entries[length+1] = urlValue.toExternalForm();
            length += 2;
        }

        if (length == 0) {
            remove(key);
        } else {
            if (length != entries.length) {
                String[] entries2 = new String[length];
                System.arraycopy(entries, 0, entries2, 0, length);
                entries = entries2;
            }

            if (length > maxLength) {
                String[] entries2 = new String[maxLength];
                System.arraycopy(entries, length - maxLength,
                                 entries2, 0, maxLength);
                entries = entries2;
            } 

            putStrings(key, entries);
        }
    }

    /**
     * Considers that the specified preference contains a map 
     * which maps an URL to another URL; returns the value 
     * corresponding to specified key.
     *
     * @param key the name of the preference
     * @param urlKey the searched key
     * @param fallback returned value when <tt>urlKey</tt> is not found 
     * in the map
     * @return value corresponding to <tt>urlKey</tt> if any; 
     * <tt>fallback</tt> otherwise
     * @see #putURLEntry
     * @see #putURLs
     * @see #getURLs
     */
    public synchronized URL getURLEntry(String key, URL urlKey, URL fallback) {
        String[] urlMap = getStrings(key, null);
        if (urlMap == null) {
            return fallback;
        }

        final int count = urlMap.length;
        for (int i = 0; i < count; i += 2) {
            try {
                URL url = new URL(urlMap[i]);

                if (url.equals(urlKey)) {
                    return new URL(urlMap[i+1]);
                }
            } catch (MalformedURLException ignored) {}
        }
        return fallback;
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param value the value of the preference
     */
    public synchronized void putFont(String key, Font value) {
        StringBuilder buffer = new StringBuilder(value.getFamily());
        if (!value.isPlain()) {
            buffer.append('-');
            if (value.isBold()) {
                buffer.append("BOLD");
            }
            if (value.isItalic()) {
                buffer.append("ITALIC");
            }
        }
        buffer.append('-');
        buffer.append(Integer.toString(value.getSize()));

        putString(key, buffer.toString());
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as a <code>Font</code>
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized Font getFont(String key, Font fallback) {
        String value = getString(key, null);
        if (value == null) {
            return fallback;
        }

        Font font = Font.decode(value);
        if (font == null) {
            return fallback;
        }

        return font;
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param value the value of the preference
     */
    public synchronized void putColor(String key, Color value) {
        putInts(key, 
                new int[] {
                    value.getRed(), value.getGreen(), value.getBlue()
                });
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as a <code>Color</code>
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized Color getColor(String key, Color fallback) {
        int[] rgb = 
            getInts(key, 
                    new int[] {
                       fallback.getRed(),fallback.getGreen(),fallback.getBlue()
                    });

        if (rgb.length != 3 || 
            rgb[0] < 0 || rgb[0] > 255 || 
            rgb[1] < 0 || rgb[1] > 255 || 
            rgb[2] < 0 || rgb[2] > 255) {
            return fallback;
        } else {
            return new Color(rgb[0], rgb[1], rgb[2]);
        }
    }

    /**
     * Adds or replaces preference.
     * 
     * @param key the name of the preference
     * @param value the value of the preference
     */
    public synchronized void putRectangle(String key, Rectangle value) {
        putInts(key, new int[] {value.x, value.y, value.width, value.height});
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as a <code>Rectangle</code>
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized Rectangle getRectangle(String key,
                                               Rectangle fallback) {
        int[] r = getInts(
            key, 
            new int[] {fallback.x,fallback.y,fallback.width,fallback.height});
        if (r.length != 4 || r[2] < 0 || r[3] < 0) {
            return fallback;
        } else {
            return new Rectangle(r[0], r[1], r[2], r[3]);
        }
    }

    /**
     * Adds or replaces preference for the format of the paper 
     * used when printing documents.
     * 
     * @param key the name of the preference
     * @param paper the value of the preference
     */
    public synchronized void putPaper(String key, Paper paper) {
        putDoubles(key, new double[] {
            paper.getWidth(), paper.getHeight(), 
            paper.getImageableX(), paper.getImageableY(), 
            paper.getImageableWidth(), paper.getImageableHeight()
        });
    }

    /**
     * Returns specified preference.
     * 
     * @param key the name of the preference
     * @param fallback value returned if specified preference is not found or
     * cannot be parsed as a <code>Paper</code>
     * @return specified preference if found and valid; returns
     * <code>fallback</code> otherwise
     */
    public synchronized Paper getPaper(String key, Paper fallback) {
        double[] dim = getDoubles(key, null);
        if (dim == null) {
            return fallback;
        }

        Paper paper = newPaper(dim[0], dim[1], dim[2], dim[3], dim[4], dim[5]);
        if (paper == null) {
            return fallback;
        }

        return paper;
    }

    /**
     * Helper function: returns a new Paper initialized using specified
     * paper size and specified imageable area.
     */
    public static final Paper newPaper(double paperW, double paperH,
                                       double x, double y, 
                                       double width, double height) {
        if (paperW < 0 || paperH < 0 ||
            x < 0 || x >= paperW ||
            y < 0 || y >= paperH ||
            width <= 0 || width > paperW-x ||
            height <= 0 || height > paperH-y) {
            return null;
        }

        Paper paper = new Paper();
        paper.setSize(paperW, paperH);
        paper.setImageableArea(x, y, width, height);

        return paper;
    }

    /**
     * Removes specified preference.
     * 
     * @param key the name of the preference
     */
    public synchronized void remove(String key) {
        properties.remove(key);
    }

    /**
     * Removes all preferences.
     */
    public synchronized void removeAll() {
        properties.clear();
    }

    /**
     * Returns all preferences in the form of a list of key/value pairs. This
     * list may be empty but cannot be <code>null</code>.
     */
    public synchronized String[] getAll() {
        String[] all = new String[properties.size()*2];
        int i = 0;

        Iterator<Map.Entry<Object,Object>> iter =  
            properties.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Object,Object> e = iter.next();

            all[i++] = (String) e.getKey();
            all[i++] = (String) e.getValue();
        }

        return all;
    }

    /**
     * Adds or replaces preferences with preferences read from another
     * <code>Preferences</code> object.
     * 
     * @param other the source <code>Preferences</code> object
     */
    public synchronized void putAll(Preferences other) {
        final String[] all = other.getAll();
        final int count = all.length;
        for (int i = 0; i < count; i += 2) {
            properties.setProperty(all[i], all[i+1]);
        }
    }

    // -----------------------------------------------------------------------

    /**
     * <em>Merges</em> the contents of specified Java properties file to this
     * <code>Preferences</code> object.
     * <p>Invoke {@link #removeAll} if you need to <em>reset</em> this 
     * <code>Preferences</code> object to the contents of specified file.
     *
     * @param file the file to be loaded
     * @return <code>true</code> if the file has been sucessfully loaded;
     * <code>false</code> otherwise
     * 
     * @see #load(URL)
     * @see #save
     */
    public boolean load(File file) {
        return load(FileUtil.fileToURL(file));
    }

    /**
     * Merges the contents of specified Java properties file to this
     * <code>Preferences</code> object.
     *
     * @param url the location of the file to be loaded
     * @return <code>true</code> if the file has been sucessfully loaded;
     * <code>false</code> otherwise
     * 
     * @see #load(File)
     * @see #save
     */
    public synchronized boolean load(URL url) {
        boolean done = false;

        try {
            load(url, properties);
            done = true;
        } catch (IOException ignored) {
            //ignored.printStackTrace();
        }

        return done;
    }

    /**
     * Helper method: adds to specified properties the 
     * contents of specified URL.
     *
     * @param url the location of the file to be loaded
     * @param props the properties to be updated
     * @exception IOException if for any reason, the properties 
     * have not been sucessfully updated
     */
    public static void load(URL url, Properties props) 
        throws IOException {
        BufferedInputStream in = 
            new BufferedInputStream(URLUtil.openStreamNoCache(url), 65536);
        try {
            props.load(in);
        } finally {
            in.close();
        }
    }

    /**
     * Saves this <code>Preferences</code> object to specified 
     * Java properties file.
     *
     * @param file the save file
     * @param header optional header for the save file; may be <code>null</code>
     * @return <code>true</code> if the properties have been sucessfully 
     * updated; <code>false</code> otherwise
     * @see #load(File)
     * @see #load(URL)
     */
    public synchronized boolean save(File file, String header) {
        boolean done = false;

        try {
            File tmpFile = File.createTempFile("prefs", ".tmp", 
                                               file.getParentFile());
            save(properties, tmpFile, header);

            if (file.exists()) {
                file.delete();
            }
            // renameTo is assumed to be atomic.
            tmpFile.renameTo(file);

            done = true;
        } catch (IOException ignored) {
            //ignored.printStackTrace();
        }

        return done;
    }

    /**
     * Helper method: saves specified properties to specified file.
     *
     * @param props the properties to be saved
     * @param file the save file
     * @param header optional header for the save file; may be <code>null</code>
     * @exception IOException if for any reason, the properties 
     * have not been sucessfully saved
     */
    public static void save(Properties props, File file, String header) 
        throws IOException {
        BufferedOutputStream out = 
            new BufferedOutputStream(new FileOutputStream(file), 65536);
        try {
            props.store(out, ((header == null)? "" : header));
            out.flush();
        } finally {
            out.close();
        }
    }

    // -----------------------------------------------------------------------

    private static final Preferences[] appPreferences = new Preferences[1];

    /**
     * Specifies the Preferences object containing application-wide 
     * user preferences.
     * 
     * @param prefs application-wide user preferences.
     * May be <code>null</code> in which case a new, non-persistent, 
     * Preferences object will be used.
     * @see #getPreferences
     */
    public static final void setPreferences(Preferences prefs) {
        synchronized (appPreferences) {
            appPreferences[0] = prefs;
        }
    }

    /**
     * Returns the Preferences object containing application-wide 
     * user preferences.
     *
     * @return a non-null Preferences object
     * @see #setPreferences
     */
    public static final Preferences getPreferences() {
        synchronized (appPreferences) {
            if (appPreferences[0] == null) {
                appPreferences[0] = new Preferences();
            }
            return appPreferences[0];
        }
    }
}
