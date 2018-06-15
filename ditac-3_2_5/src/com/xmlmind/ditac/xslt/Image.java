/*
 * Copyright (c) 2017 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.xslt;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.util.zip.GZIPInputStream;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import javax.imageio.ImageReader;
import javax.imageio.ImageIO;
import javax.imageio.stream.FileCacheImageInputStream;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/*package*/ final class Image {
    private Image() {}

    // ------------------------------
    // API
    // ------------------------------

    public static int getWidth(String location) {
        int[] size = getSize(location);
        return (size == null)? -1 : size[0];
    }

    public static int getHeight(String location) {
        int[] size = getSize(location);
        return (size == null)? -1 : size[1];
    }

    public static int[] getSize(String location) {
        int[] size = null;

        try {
            URL url = new URL(location);

            String extension = extension(location);
            if (extension == null) {
                throw new RuntimeException("image filename has no extension");
            }
            extension = extension.toLowerCase();

            if ("svg".equals(extension) || "svgz".equals(extension)) {
                size = getSVGSize(url, "svgz".equals(extension));
            } else {
                size = getSize(url, extension);
            }
        } catch (Exception e) {
            System.err.println("Cannot determine the size of image '" + 
                               location + "': " + reason(e));
        }

        return size;
    }

    // ------------------------------
    // getSVGSize
    // ------------------------------

    private static final int[] getSVGSize(URL url, boolean gzipped) 
        throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setValidating(false);

        SAXParser parser = factory.newSAXParser();
        SVGHandler handler = new SVGHandler();

        InputStream in;
        if (gzipped) {
            in = new GZIPInputStream(url.openStream());
        } else {
            in = url.openStream();
        }

        try {
            parser.parse(in, handler, url.toExternalForm());
        } catch (HandledSVG done) {
        } finally {
            in.close();
        }

        if (handler.width == null || handler.height == null) {
            throw new RuntimeException("missing 'width' or 'height' attribute");
        }

        double width = parseAbsoluteSize(handler.width, "width");
        double height = parseAbsoluteSize(handler.height, "height");

        int[] size = new int[2];
        size[0] = (int) Math.rint(width);
        size[1] = (int) Math.rint(height);

        return size;
    }

    @SuppressWarnings("serial")
    private static final class HandledSVG extends SAXException {}

    private static final class SVGHandler extends DefaultHandler {
        public String width;
        public String height;

        private static final String DTD_MOCKUP =
            "<!ELEMENT svg ANY>\n" +
            "<!ATTLIST svg xmlns CDATA #FIXED 'http://www.w3.org/2000/svg'\n" +
            "xmlns:xlink CDATA #FIXED 'http://www.w3.org/1999/xlink'>";

        @Override
        public void startDocument() {
            width = height = null;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes atts)
            throws SAXException {
            if ("svg".equals(localName) && 
                "http://www.w3.org/2000/svg".equals(uri)) {
                final int attCount = atts.getLength();
                for (int i = 0; i < attCount; ++i) {
                    String attName = atts.getLocalName(i);

                    if ("width".equals(attName)) {
                        width = atts.getValue(i);
                        if ((width = width.trim()).length() == 0) {
                            width =  null;
                        }
                    } else if ("height".equals(attName)) {
                        height = atts.getValue(i);
                        if ((height = height.trim()).length() == 0) {
                            height =  null;
                        }
                    }
                }
            }

            throw new HandledSVG(); // Means: done.
        }

        @Override
        public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException, IOException {
            InputSource resolved = null;

            if (publicId != null && publicId.startsWith("-//W3C//DTD SVG")) {
                resolved = new InputSource(new StringReader(DTD_MOCKUP));
                resolved.setSystemId(systemId);
            }

            return resolved;
        }
    }

    private enum Unit {
        NUMBER,
        LENGTH_PX,
        LENGTH_IN,
        LENGTH_CM,
        LENGTH_MM,
        LENGTH_PT,
        LENGTH_PC,
        LENGTH_EM,
        LENGTH_EX,
        PERCENTAGE,
        UNSPECIFIED
    }

    private static double parseAbsoluteSize(String attrValue, String attrName) 
        throws RuntimeException {
        assert(attrValue != null);

        Unit unit = Unit.UNSPECIFIED;
        double size = -1;

        int unitLength = 2;
        if (attrValue.endsWith("px")) {
            unit = Unit.LENGTH_PX;
        } else if (attrValue.endsWith("in")) {
            unit = Unit.LENGTH_IN;
        } else if (attrValue.endsWith("cm")) {
            unit = Unit.LENGTH_CM;
        } else if (attrValue.endsWith("mm")) {
            unit = Unit.LENGTH_MM;
        } else if (attrValue.endsWith("pt")) {
            unit = Unit.LENGTH_PT;
        } else if (attrValue.endsWith("pc")) {
            unit = Unit.LENGTH_PC;
        } else if (attrValue.endsWith("em")) {
            unit = Unit.LENGTH_EM;
        } else if (attrValue.endsWith("ex")) {
            unit = Unit.LENGTH_EX;
        } else if (attrValue.endsWith("%")) {
            unit = Unit.PERCENTAGE;
            unitLength = 1;
        } else {
            unit = Unit.NUMBER;
            unitLength = 0;
        }

        String value = attrValue;
        if (unitLength > 0) {
            value = value.substring(0, value.length() - unitLength);
        }

        try {
            size = Double.parseDouble(value);
        } catch (NumberFormatException ignored) {}

        if (size <= 0) {
            unsupportedImageSize(attrName, attrValue);
        }

        // 96.0 = number of CSS pixels per inch.

        double pixels = -1;

        switch (unit) {
        case LENGTH_IN:
            pixels = size * 96.0;
            break;
        case LENGTH_CM:
            pixels = (size/2.54) * 96.0;
            break;
        case LENGTH_MM:
            pixels = (size/25.4) * 96.0;
            break;
        case LENGTH_PT:
            // 1in = 72pt.
            pixels = (size/72.0) * 96.0;
            break;
        case LENGTH_PC:
            // 1pc = 12pt ==> 1in = 6pc.
            pixels = (size/6.0) * 96.0;
            break;
        case LENGTH_PX:
        case NUMBER:
            pixels = size;
            break;
        default:
            unsupportedImageSize(attrName, attrValue);
        }

        return pixels;
    }

    private static void unsupportedImageSize(String attrName,
                                             String attrValue) 
        throws RuntimeException {
        throw new RuntimeException(attrName + "='" + attrValue + 
                                   "', attribute value not supported");
    }

    // ------------------------------
    // getSize
    // ------------------------------

    private static final int[] getSize(URL url, String extension) 
        throws IOException {
        Iterator<ImageReader> iter = 
            ImageIO.getImageReadersBySuffix(extension);
        if (!iter.hasNext()) {
            throw new RuntimeException("'" + extension + 
                                       "', unsupported image extension");
        }
        ImageReader reader = iter.next();

        InputStream in = url.openStream();
        try {
            FileCacheImageInputStream imageData = 
                new FileCacheImageInputStream(in, /*tempDir*/ null);
            try {
                reader.setInput(imageData);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return new int[] { width, height };
            } finally {
                imageData.close();
            }
        } finally {
            in.close();
        }
    }

    // ------------------------------
    // Helpers
    // ------------------------------

    private static final String extension(String path) {
        int pos = path.lastIndexOf('.');
        if (pos < 0 || pos == path.length()-1) {
            return null;
        }
        return path.substring(pos+1);
    }

    private static final String reason(Throwable e) {
        String reason = e.getMessage();
        if (reason == null) {
            reason = e.getClass().getName();
        }
        return reason;
    }

    // -----------------------------------------------------------------------
    // Tests
    //
    // java -cp ../lib/ditac.jar com.xmlmind.ditac.xslt.Image \
    // ~/icons/svg/vache_qui_rit/original.jpg
    // 320x451
    //
    // java -cp ../lib/ditac.jar com.xmlmind.ditac.xslt.Image \
    // ~/icons/svg/vache_qui_rit/vache_qui_rit.png
    // 600x575
    //
    // java -cp ../lib/ditac.jar com.xmlmind.ditac.xslt.Image \
    // ~/icons/svg/Wikipedia.svg
    // 103x94 (was px)
    //
    // java -cp ../lib/ditac.jar com.xmlmind.ditac.xslt.Image \
    // ~/icons/svg/komradeluxfer_8.svg
    //  794x1123 (was 210mmx297mm)
    //
    // java -cp ../lib/ditac.jar com.xmlmind.ditac.xslt.Image \
    // ~/icons/svg/gnome-serbia.svgz
    // 384x384 (was 288pt)
    // 
    // java -cp ../lib/ditac.jar com.xmlmind.ditac.xslt.Image \
    // ~/icons/svg/SVG_Logo.svg
    // width='100%'
    //
    // java -cp ../lib/ditac.jar com.xmlmind.ditac.xslt.Image \
    // ~/icons/svg/quick_fox.svg
    // 570x260 (no unit)
    //
    // java -cp ../lib/ditac.jar com.xmlmind.ditac.xslt.Image \
    // ~/icons/svg/test-ooo2.svg
    // missing 'width' or 'height' attribute
    //
    // java -cp ../lib/ditac.jar com.xmlmind.ditac.xslt.Image \
    // ~/icons/svg/vache_qui_rit/README.txt
    // 'txt', unsupported image extension
    //
    // java -cp ../lib/ditac.jar com.xmlmind.ditac.xslt.Image NOT_FOUND.jpeg
    // No such file or directory
    //
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println(
                "usage: java com.xmlmind.ditac.xslt.Image image_file");
            System.exit(1);
        }

        try {
            URI uri = (new File(args[0])).toURI().normalize();
            String location = uri.toASCIIString();

            int[] size = getSize(location);

            if (size == null) {
                System.out.println("The size of image '" + location + 
                                   "' cannot be determined."); 
            } else {
                System.out.println("The size of image '" + location + "' is " + 
                                   size[0] + "x" + size[1] + ".");
            }
        } catch (Exception e) {
            System.err.println(
                "*** Error *** Cannot determine the size of image '" + 
                args[0] + "':\n" + reason(e));
        }
    }
}
