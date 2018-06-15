/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.preprocess;

/*package*/ interface Constants extends com.xmlmind.ditac.util.Constants {
    public static final String SEARCH_NAME = "search";
    public static final String SEARCH_QNAME = DITAC_PREFIX + SEARCH_NAME;

    // Used to generate a hover tooltip when generating XHTML.
    public static final String TITLE_NAME = "title";
    public static final String TITLE_QNAME = DITAC_PREFIX + TITLE_NAME;

    public static final String FILLED_NAME = "filled";
    public static final String FILLED_QNAME = DITAC_PREFIX + FILLED_NAME;

    public static final String COPY_OF_NAME = "copyOf";
    public static final String COPY_OF_QNAME = DITAC_PREFIX + COPY_OF_NAME;

    public static final String KEY_SPACE_NAME = "keySpace";
    public static final String KEY_SPACE_QNAME = DITAC_PREFIX + KEY_SPACE_NAME;
    public static final String KEY_SPACE_START = KEY_SPACE_QNAME + "=\"";

    public static final String BEGIN_GROUP_PI_TARGET = "ditac-begin-group";
    public static final String END_GROUP_PI_TARGET = "ditac-end-group";

    public static final String ID_SEPARATOR = "__";

    public static final String[] ROOT_ROLES = {
        "bookmap/toc",
        "bookmap/figurelist",
        "bookmap/tablelist",
        "bookmap/examplelist",
        "bookmap/equationlist",
        "bookmap/abbrevlist",
        "bookmap/trademarklist",
        "bookmap/bibliolist",
        "bookmap/glossarylist",
        "bookmap/indexlist",
        "bookmap/booklist", // Must be after the other *lists.
        "bookmap/notices",
        "bookmap/dedication",
        "bookmap/colophon",
        "bookmap/bookabstract",
        "bookmap/draftintro",
        "bookmap/preface",
        "bookmap/part",
        "bookmap/chapter",
        "bookmap/appendices",
        "bookmap/appendix",
        "bookmap/amendments"
    };
    public static final int ROOT_ROLE_COUNT = ROOT_ROLES.length;
}
