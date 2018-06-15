/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.util;

public interface Constants {
    public static final String DITAC_NS_URI =
        "http://www.xmlmind.com/ditac/schema/ditac";

    public static final String DITAC_PREFIX = "ditac:";

    public static final String ABSOLUTE_HREF_NAME = "absoluteHref";
    public static final String ABSOLUTE_HREF_QNAME = 
        DITAC_PREFIX + ABSOLUTE_HREF_NAME;
}
