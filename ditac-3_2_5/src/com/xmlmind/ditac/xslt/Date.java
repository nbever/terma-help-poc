/*
 * Copyright (c) 2017-2018 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.xslt;

import java.util.Locale;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;

/*package*/ final class Date {
    private Date() {}

    // -----------------------------------------------------------------------

    public static String format(String value, String lang) {
        try {
            java.util.Date date;
            value = value.trim();
            if (value.length() == 0) {
                date = new java.util.Date();
            } else {
                // Date formats are not synchronized. It is recommended to
                // create separate format instances for each thread.

                SimpleDateFormat inFormat = 
                    new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                inFormat.setLenient(false);

                date = inFormat.parse(value);
            }

            Locale locale;

            // Using '_' instead of '-' is a common mistake.
            String[] split = lang.split("[-_]");
            if (split.length >= 3) {
                locale = new Locale(split[0].toLowerCase(Locale.US), 
                                    split[1].toUpperCase(Locale.US),
                                    split[2]);
            } else if (split.length >= 2) {
                locale = new Locale(split[0].toLowerCase(Locale.US), 
                                    split[1].toUpperCase(Locale.US));
            } else if (split.length >= 1) {
                locale = new Locale(split[0].toLowerCase(Locale.US));
            } else {
                locale = Locale.US;
            }

            DateFormat outFormat = 
                DateFormat.getDateInstance(DateFormat.LONG, locale);

            return outFormat.format(date);
        } catch (ParseException ignored) {
            return value;
        }
    }
}
