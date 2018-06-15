/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of several XMLmind projects.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.util;

import java.util.HashMap;
import java.util.Locale;

/**
 * A collection of utility functions (static methods) operating on Locales.
 */
public final class LocaleUtil {
    private LocaleUtil() {}

    private static HashMap<String,Locale> langToLocale = 
        new HashMap<String,Locale>();

    /**
     * Returns the Locale corresponding to specified language.
     *
     * @param lang specifies the language: 
     * a two-letter ISO language code as defined by ISO-639, 
     * possibly followed by a two-letter ISO Country Code
     * as defined by ISO-3166. Examples: "en", "en-US", "fr", "fr-CA".
     * @return the Locale corresponding to specified language.
     * Note that this Locale is not necessarily supported by Java methods
     * such as <code>NumberFormat.getInstance()</code>.
     */
    public static Locale getLocale(String lang) {
        lang = LocaleUtil.normalizeLang(lang);

        synchronized (langToLocale) {
            Locale locale = langToLocale.get(lang);
            if (locale == null) {
                locale = LocaleUtil.createLocale(lang);
                langToLocale.put(lang, locale);
            }
            return locale;
        }
    }

    /**
     * Equivalent to {@link #normalizeLang(String, String[]) 
     * normalizeLang(lang, null)}.
     */
    public static String normalizeLang(String lang) {
        return normalizeLang(lang, null);
    }

    /**
     * Returns a normalized, possibly simplified, version of
     * specified language. For example, returns "en-US" for "en_us-win".
     *
     * @param lang a language code preferably matching pattern
     * <tt>[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*</tt>
     * @param parts returns <i>lower_case_language</i> in the first element
     * of this array and <i>upper_case_country</i> or <code>null</code> in 
     * its second element. May be <code>null</code>.
     * @return <tt>lang</tt> normalized as <i>lower_case_language</i>,
     * optionally followed by '<b>-</b>' <i>upper_case_country</i>.
     */
    public static String normalizeLang(String lang, String[] parts) {
        // Using '_' instead of '-' is a common mistake.
        String[] split = lang.split("[-_]");
        if (split.length >= 2) {
            String l = split[0].toLowerCase(Locale.US);
            String c = split[1].toUpperCase(Locale.US);
            if (parts != null) {
                parts[0] = l;
                parts[1] = c;
            }

            StringBuilder buffer = new StringBuilder();
            buffer.append(l);
            buffer.append('-');
            buffer.append(c);
            return buffer.toString();
        } else {
            String l = lang.toLowerCase(Locale.US);
            if (parts != null) {
                parts[0] = l;
                parts[1] = null;
            }

            return l;
        }
    }

    private static Locale createLocale(String lang) {
        int pos = lang.indexOf('-');
        if (pos < 0) {
            return new Locale(lang);
        } else {
            return new Locale(lang.substring(0, pos), lang.substring(pos+1));
        }
    }

    // -----------------------------------------------------------------------

    private static final String[] INDIVIDUAL_LANGUAGES = {
        "no", "NO", "", "nb",  // Norwegian Bokmal
        "no", "NO", "NY", "nn" // Norwegian Nynorsk
    };

    /**
     * Returns the lowercase ISO 639 code corresponding to 
     * the <em>individual language</em> of the specified locale.
     * <p>For example, returns <tt>nn</tt> for <tt>no-NO-NY</tt>
     * because <tt>no</tt> is just a macrolanguage and not 
     * an actual individual language.
     */
    public static String getIndividualLanguageCode(Locale locale) {
        String lang = locale.getLanguage();

        String country = locale.getCountry();
        if (country == null) {
            country = "";
        } else {
            country = country.toUpperCase();
        }

        String variant = locale.getVariant();
        if (variant == null) {
            variant = "";
        } else {
            variant = variant.toUpperCase();
        }

        final int count = INDIVIDUAL_LANGUAGES.length;
        for (int i = 0; i < count; i += 4) {
            if (lang.equals(INDIVIDUAL_LANGUAGES[i]) &&
                country.equals(INDIVIDUAL_LANGUAGES[i+1]) &&
                variant.equals(INDIVIDUAL_LANGUAGES[i+2])) {
                return INDIVIDUAL_LANGUAGES[i+3];
            }
        }

        return lang;
    }
}
