/*
 * Copyright (c) 2017-2018 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind DITA Converter project.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.ditac.preprocess;

import java.io.IOException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Stack;
import java.util.Comparator;
import java.util.Arrays;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import com.xmlmind.util.ThrowableUtil;
import com.xmlmind.util.ArrayUtil;
import com.xmlmind.util.StringUtil;
import com.xmlmind.util.FileUtil;
import com.xmlmind.util.URIComponent;
import com.xmlmind.util.URLUtil;
import com.xmlmind.util.XMLText;
import com.xmlmind.util.Console;
import com.xmlmind.ditac.util.NodeLocation;
import com.xmlmind.ditac.util.DOMUtil;
import com.xmlmind.ditac.util.DITAUtil;
import com.xmlmind.ditac.util.SaveDocument;
import com.xmlmind.ditac.util.SimpleConsole;
import com.xmlmind.ditac.util.ConsoleHelper;

/**
 * Converts a DITA map file (or a list of topic files, in which case 
 * a DITA map is generated on the fly) to one or more <tt>.ditac</tt> files 
 * and a single <tt>ditac_lists.ditac_lists</tt> file.
 *
 * <p>The <tt>.ditac</tt> files and the <tt>ditac_lists.ditac_lists</tt> file
 * are generated in the same directory.
 * 
 * <p>The formats of <tt>.ditac</tt> and <tt>ditac_lists.ditac_lists</tt> files
 * have been designed in order to be transformed by the XSL stylesheets found
 * in <tt><i>ditac_install_dir</i>/xsl/</tt>.
 * The format of these files is documented in 
 * <i>XMLmind DITA Converter Manual</i>.
 *
 * <p>Using this class involves:
 * <ol>
 * <li>Creating an instance using the {@link #PreProcessor} Constructor.
 * <li>Configuring the newly created instance using methods such as 
 * {@link #setConsole}, {@link #setMedia}, {@link #setChunking}, 
 * {@link #setResourceHandler}, etc.
 * <li>Invoking {@link #process} to convert the input DITA map or 
 * topic files to <tt>.ditac</tt> and <tt>ditac_lists.ditac_lists</tt> 
 * <em>fully preprocessed</em> files.
 * </ol>
 * 
 * <p><b>Note:</b> This class is not thread-safe. Each thread must use 
 * its own instance. However the same instance may be run several times.
 */
public class PreProcessor implements Constants {
    /**
     * The version number of this PreProcessor.
     */
    public static final String VERSION = "3.2.5";

    /**
     * Specifies that a <tt>&lt;toc&gt;</tt> is to be added 
     * to frontmatter or backmatter.
     *
     * @see #setFrontMatter
     * @see #setBackMatter
     */
    public static final int TOC = 0x01;

    /**
     * Specifies that a <tt>&lt;figurelist&gt;</tt> is to be added 
     * to frontmatter or backmatter.
     *
     * @see #setFrontMatter
     * @see #setBackMatter
     */
    public static final int FIGURE_LIST = 0x02;

    /**
     * Specifies that a <tt>&lt;tablelist&gt;</tt> is to be added 
     * to frontmatter or backmatter.
     *
     * @see #setFrontMatter
     * @see #setBackMatter
     */
    public static final int TABLE_LIST = 0x04;

    /**
     * Specifies that a <tt>&lt;examplelist&gt;</tt> is to be added 
     * to frontmatter or backmatter.
     *
     * @see #setFrontMatter
     * @see #setBackMatter
     */
    public static final int EXAMPLE_LIST = 0x08;

    /**
     * Specifies that a <tt>&lt;equationlist&gt;</tt> is to be added 
     * to frontmatter or backmatter.
     *
     * @see #setFrontMatter
     * @see #setBackMatter
     */
    public static final int EQUATION_LIST = 0x10;

    /**
     * Specifies that a <tt>&lt;indexlist&gt;</tt> is to be added 
     * to frontmatter or backmatter.
     *
     * @see #setFrontMatter
     * @see #setBackMatter
     */
    public static final int INDEX_LIST = 0x20;

    protected ConsoleHelper console;
    protected Chunking chunking;
    protected Media media;
    protected File autoMapSaveFile;
    protected ResourceHandler resourceHandler;
    protected int[] frontMatter;
    protected int[] backMatter;
    protected String lang;
    protected Filter externalFilter;
    protected boolean forceTitlePage;
    protected boolean partRestartsChapterNumber;
    protected boolean validate;
    protected boolean dryRun;

    protected Filters filters;
    protected String rootName;
    protected String extension;
    protected LoadedDocuments loadedDocs;
    protected LoadedDocument mainMap;
    protected boolean isAutoMap;
    protected KeySpaces keySpaces;

    // -----------------------------------------------------------------------

    /**
     * Equivalent to {@link #PreProcessor PreProcessor(null)}.
     */
    public PreProcessor() {
        this(null);
    }

    /**
     * Constructs a PreProcessor using specified console to display 
     * its progress, warning, error, etc, messages.
     *
     * @param c the console. May be <code>null</code>.
     * @see #setConsole
     */
    public PreProcessor(Console c) {
        setConsole(c);
        media = Media.SCREEN;
        chunking = Chunking.AUTO;
    }

    /**
     * Specifies the console on which messages issued during preprocessing
     * are to be displayed. 
     * 
     * @param c the console; may be <code>null</code>, in which case messages
     * are displayed on <code>System.err</code> and <code>System.out</code>
     *
     * @see #getConsole
     */
    public void setConsole(Console c) {
        if (c == null) {
            c = new SimpleConsole();
        }
        this.console = ((c instanceof ConsoleHelper)? 
                        (ConsoleHelper) c : new ConsoleHelper(c));
    }

    /**
     * Returns the console on which messages issued during preprocessing
     * are to be displayed.
     * 
     * @see #setConsole
     */
    public ConsoleHelper getConsole() {
        return console;
    }

    /**
     * Specifies the media, print or screen, associated to the output format.
     *
     * @see #getMedia
     */
    public void setMedia(Media media) {
        if (media == null) {
            media = Media.SCREEN;
        }
        this.media = media;
    }

    /**
     * Returns the media, print or screen, associated to the output format.
     * Initial value: {@link Media#SCREEN}.
     *
     * @see #setMedia
     */
    public Media getMedia() {
        return media;
    }

    /**
     * Specifies the chunking mode of this PreProcessor.
     * Initial value: {@link Chunking#AUTO}.
     *
     * @see #getChunking
     */
    public void setChunking(Chunking chunking) {
        if (chunking == null) {
            chunking = Chunking.AUTO;
        }
        this.chunking = chunking;
    }

    /**
     * Returns the chunking mode of this PreProcessor. 
     *
     * @see #setChunking
     */
    public Chunking getChunking() {
        return chunking;
    }

    /**
     * Specifies where to save the DITA map file generated on the fly 
     * when {@link #process} is passed one or more topic files instead 
     * of a single map file.
     * <p>Ignored when {@link #process} is passed a map file.
     *
     * @param saveFile the save file
     * @see #getAutoMapSaveFile
     */
    public void setAutoMapSaveFile(File saveFile) {
        autoMapSaveFile = saveFile;
    }

    /**
     * Returns the save location of the DITA map file generated on the fly 
     * when {@link #process} is passed one or more topic files instead 
     * of a single map file. May return <code>null</code>.
     *
     * @see #setAutoMapSaveFile
     */
    public File getAutoMapSaveFile() {
        return autoMapSaveFile;
    }

    /**
     * Specifies the ResourceHandler used by this PreProcessor.
     *
     * @param handler the ResourceHandler
     * Specify <code>null</code> to revert to default image handling.
     * @see #getResourceHandler
     */
    public void setResourceHandler(ResourceHandler handler) {
        resourceHandler = handler;
    }

    /**
     * Returns the ResourceHandler used by this PreProcessor. 
     * May return <code>null</code>.
     * 
     * @see #setResourceHandler
     */
    public ResourceHandler getResourceHandler() {
        return resourceHandler;
    }

    /**
     * Allows to add one or more frontmatter items to the DITA map 
     * (what ever its type: map or bookmap) being preprocessed.
     * <p>Ignored if the items are already found in the frontmatter 
     * or backmatter of the DITA map being preprocessed.
     *
     * @param items which items to add. OR-ed items are output 
     * in the same <tt>.ditac</tt> file. May be <code>null</code> or empty.
     * <p>HTML example: <code>{TOC, FIGURE_LIST|TABLE_LIST}</code> 
     * will cause a TOC to be generated in its own page followed 
     * by another page containing a List of Figures and a List of Tables.
     * @see #getFrontMatter
     */
    public void setFrontMatter(int[] items) {
        if (items != null && items.length == 0) {
            items = null;
        }
        frontMatter = items;
    }

    /**
     * Returns the frontmatter items added to the DITA map 
     * being preprocessed. May return <code>null</code>.
     *
     * @see #setFrontMatter
     */
    public int[] getFrontMatter() {
        return frontMatter;
    }

    /**
     * Allows to add one or more backmatter items to the DITA map 
     * (what ever its type: map or bookmap) being preprocessed.
     * <p>Ignored if the items are already found in the frontmatter 
     * or backmatter of the DITA map being preprocessed.
     *
     * @param items which items to add. OR-ed items are output 
     * in the same <tt>.ditac</tt> file. May be <code>null</code> or empty.
     * @see #getBackMatter
     */
    public void setBackMatter(int[] items) {
        if (items != null && items.length == 0) {
            items = null;
        }
        backMatter = items;
    }

    /**
     * Returns the backmatter items added to the DITA map 
     * being preprocessed. May return <code>null</code>.
     *
     * @see #setBackMatter
     */
    public int[] getBackMatter() {
        return backMatter;
    }

    /**
     * Specifies the default language of the DITA maps processed by this
     * PreProcessor. This language is needed in order to sort the index
     * entries.
     * <p>This specification is ignored if the root element of the DITA map 
     * being preprocessed has an <tt>xml:lang</tt> attribute.
     *
     * @param lang a language specification following the standard. Examples:
     * <tt>fr</tt>, <tt>fr-CA</tt>.
     * @see #getLang
     */
    public void setLang(String lang) {
        this.lang = lang;
    }

    /**
     * Returns the default language of the DITA maps processed by this
     * PreProcessor. May return <code>null</code>.
     *
     * @see #setLang
     */
    public String getLang() {
        return lang;
    }

    /**
     * Specifies the conditional processing profile applied to 
     * the DITA files processed by this PreProcessor. 
     * 
     * @param filter a conditional processing profile typically loaded 
     * from the contents of a <tt>.ditaval</tt> file. 
     * Specify <code>null</code> to suppress conditional processing.
     * @see #setFilter
     */
    public void setFilter(Filter filter) {
        externalFilter = filter;
    }

    /**
     * Returns the conditional processing profile applied to 
     * the DITA files processed by this PreProcessor. 
     * May return <code>null</code>.
     *
     * @see #setFilter
     */
    public Filter getFilter() {
        return externalFilter;
    }

    /**
     * If <code>true</code>, force the generation of a title page, 
     * and this, even when the map contains no title info.
     *
     * @see #getForceTitlePage
     */
    public void setForceTitlePage(boolean force) {
        forceTitlePage = force;
    }

    /**
     * Returns <code>true</code> if the title page is to be generated
     * and this, even when the map contains no title info.
     *
     * @see #setForceTitlePage
     */
    public boolean getForceTitlePage() {
        return forceTitlePage;
    }

    /**
     * If <code>true</code>, give the first chapter of each part number 1.
     * <p>Initial value is <code>false</code>, which means that chapters
     * are continuously numbered across parts.
     *
     * @see #getPartRestartsChapterNumber
     */
    public void setPartRestartsChapterNumber(boolean restarts) {
        partRestartsChapterNumber = restarts;
    }

    /**
     * Returns <code>true</code> if the first chapter of each part 
     * is given number 1.
     *
     * @see #setPartRestartsChapterNumber
     */
    public boolean getPartRestartsChapterNumber() {
        return partRestartsChapterNumber;
    }

    /**
     * If <code>true</code>, do not generate any file; 
     * just report error messages (if any.)
     *
     * @see #getDryRun
     */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Returns <code>true</code> if this preprocessor is used just to 
     * report error messages (if any).
     *
     * @see #setDryRun
     */
    public boolean getDryRun() {
        return dryRun;
    }

    /**
     * If <code>true</code>, validate all loaded DITA documents against
     * the DTDs specified in their <tt>DOCTYPE</tt>s.
     * <p>Initial value is <code>false</code>.
     *
     * @see #isValidating
     */
    public void setValidating(boolean validate) {
        this.validate = validate;
    }

    /**
     * Returns <code>true</code> if this preprocessor validates 
     * all loaded DITA documents against the DTDs specified 
     * in their <tt>DOCTYPE</tt>s.
     *
     * @see #setValidating
     */
    public boolean isValidating() {
        return validate;
    }

    protected void saveInfo(String msg) {
        if (!dryRun) {
            console.info(msg);
        }
    }

    protected void saveDocument(Document doc, File file) 
        throws IOException {
        if (!dryRun) {
            SaveDocument.save(doc, file);
        }
    }

    /**
     * Converts specified DITA files to one or more <tt>.ditac</tt> files 
     * and a single <tt>ditac_lists.ditac_lists</tt> file.
     *
     * @param inFiles the URL of a single map file or
     * the URLs of one or more topic files (in which case 
     * a DITA map is generated on the fly)
     * @param outFile the directory where the <tt>.ditac</tt> files 
     * and the <tt>ditac_lists.ditac_lists</tt> file are to be generated
     * @return the filenames of the generated <tt>.ditac</tt> and
     * <tt>ditac_lists.ditac_lists</tt> files.
     * <p>Returns <code>null</code> if a preprocessing error other 
     * than an <code>IOException</code> has occurred, in which case, 
     * some error messages should have been displayed on the console.
     * @exception IOException if an I/O error occurs during this operation
     * @see #setConsole
     */
    public File[] process(URL[] inFiles, File outFile) 
        throws IOException {
        return process(inFiles, outFile, null);
    }
    
    /**
     * Same as {@link #process(URL[], File)} except that it returns in 
     * <tt>tocFile</tt> (an array having at least one element) the path 
     * of the  "<tt>.ditac</tt>" file containing the TOC.
     * <p>If there is no such file, then <code>tocFile[0]</code> is set 
     * to <code>null</code>.
     */
    public File[] process(URL[] inFiles, File outFile, File[] tocFile) 
        throws IOException {
        outFile = outFile.getAbsoluteFile();
        File outDir = outFile.getParentFile();
        String baseName = outFile.getName();

        // Splitting process into process1+process2 is needed to implement
        // XXE's TopicSetCompiler.

        if (!process1(inFiles, outDir, baseName)) {
            return null;
        }

        return process2(outDir, tocFile);
    }

    protected boolean process1(URL[] inFiles, File outDir, String baseName) 
        throws IOException {
        // Check the output directory. Determine the rootname and extension of
        // the deliverable ---

        if (outDir == null || !outDir.isDirectory()) {
            console.error(Msg.msg("noOutputDirectory", 
                                  (outDir == null)? "" : outDir.getPath()));
            return false;
        }

        if (baseName == null || (baseName = baseName.trim()).length() == 0) {
            rootName = extension = null;
        } else {
            rootName = FileUtil.setExtension(baseName, null);
            if (rootName != null && 
                ((rootName = rootName.trim()).length() == 0 ||
                 "_".equals(rootName) || "*".equals(rootName))) {
                rootName = null;
            }

            extension = FileUtil.getExtension(baseName);
            if (extension != null && 
                (extension = extension.trim()).length() == 0) {
                extension = null;
            }
        }

        // Reset image handler ---

        if (resourceHandler != null && 
            !dryRun) {
            resourceHandler.setMedia(media);

            try {
                resourceHandler.reset();
            } catch (Throwable t) {
                console.error(Msg.msg("cannotResetResourceHandler",
                                      resourceHandler.getClass().getName(),
                                      ThrowableUtil.reason(t)));
                return false;
            }
        }

        // Initialize filtering ---

        filters = new Filters();
        filters.setConsole(console);
        filters.setResourceHandler(dryRun? null : resourceHandler);
        filters.setOutputDirectory(outDir);
        filters.setValidating(validate);

        // Preload all input files ---

        mainMap = null;
        loadedDocs = new LoadedDocuments(/*keySpaces*/ null, console);
        loadedDocs.setValidating(validate);

        for (int i = 0; i < inFiles.length; ++i) {
            // May throw an IOException.
            LoadedDocument loadedDoc = 
                loadedDocs.load(inFiles[i], /*process*/ false);

            if (mainMap == null) {
                switch (loadedDoc.type) {
                case BOOKMAP:
                case MAP:
                    mainMap = loadedDoc;
                    break;
                }
            }
        }

        // Load keys from main map ---

        Document mapDoc;
        Element mapElement;
        if (mainMap == null) {
            console.info(Msg.msg("usingAutoMap"));

            mainMap = createAutoMap(inFiles);

            isAutoMap = true;
            mapDoc = mainMap.document;
            mapElement = mapDoc.getDocumentElement();

            mainMap = loadedDocs.put(mainMap.url, mapDoc, /*process*/ false);

            if (autoMapSaveFile != null) {
                saveInfo(Msg.msg("savingAutoMap", autoMapSaveFile));

                saveDocument(mapDoc, autoMapSaveFile);
            }

            mapElement.setAttributeNS(DITAC_NS_URI, KEY_SPACE_QNAME, "0");

            keySpaces = new KeySpaces();
        } else {
            isAutoMap = false;
            mapDoc = mainMap.document;
            mapElement = mapDoc.getDocumentElement();

            console.info(Msg.msg("loadingKeys", URLUtil.toLabel(mainMap.url)));

            KeyLoader keyLoader = new KeyLoader(console);
            // KeyLoader works on a private copy of mapDoc.
            if (!keyLoader.prepareMap(mapDoc, mainMap.url)) {
                return false;
            }

            Filter filter2 = copyExternalFilter();
            filter2.addExcludeProps("print", 
                                    (media==Media.PRINT)? "no" : "printonly");
            filters.setExternalFilter(filter2);
            keyLoader.setFilters(filters);

            // Non-processed topics may be added to loadedDocs.
            keySpaces = keyLoader.createKeySpaces(loadedDocs);
            if (keySpaces == null) {
                return false;
            }

            // Simplify main map.
            // This also apply LoadedDocuments' process=true to mapDoc.

            if (!(new MapSimplifier(keySpaces,
                                    console)).simplify(mapDoc, mainMap.url)) {
                return false;
            }

            // If a topic is *pulled* (that is, skip topicrefs found
            // reltables, skip keydefs) several times from the topicref
            // hierarchy, duplicate this topic as many times as needed
            // to. Take into account @copy-to, if any, to determine the
            // basename of the copy. Otherwise, automatically generate a
            // basename.

            SimplifyTopicrefs.duplicateTopics(mapElement);
        }

        // ---

        if (!addFrontBackMatter()) {
            return false;
        }

        prepareChunking();

        if (!keySpaces.mapTopicsToKeySpaces(mapElement, loadedDocs)) {
            return false;
        }

        // With above SimplifyTopicrefs.duplicateTopics and also with
        // ditavalref/ditavalmeta, topic copies are referenced but have not
        // been created yet, so create them now.
        // 
        // (We need copies of UNPROCESSED topics, so we do that here.)

        if (!SimplifyTopicrefs.createTopicCopies(mapElement, loadedDocs)) {
            return false;
        }

        // ---

        // Process all preloaded input files ---

        LoadedDocuments loadedDocs2 = new LoadedDocuments(keySpaces, console);
        loadedDocs2.setValidating(validate);

        Iterator<LoadedDocument> iter = loadedDocs.iterator();
        while (iter.hasNext()) {
            LoadedDocument loadedDoc = iter.next();

            if (loadedDoc.document == mapDoc) {
                if (!isAutoMap) {
                    // Already processed by MapSimplifier.
                    mainMap = loadedDocs2.put(loadedDoc.url, loadedDoc.document,
                                              /*process*/ false);
                } else {
                    mainMap = loadedDocs2.put(loadedDoc.url, loadedDoc.document,
                                              /*process*/ true);
                }
            } else {
                loadedDocs2.put(loadedDoc.url, loadedDoc.document,
                                /*process*/ true);
            }
        }

        loadedDocs = loadedDocs2;

        // Load all topics ---

        if (!isAutoMap && !loadTopics(mapElement)) {
            return false;
        }

        return true;
    }

    protected Filter copyExternalFilter() {
        if (externalFilter != null) {
            return new Filter(externalFilter);
        } else {
            return new Filter();
        }
    }

    protected File[] process2(File outDir, File[] tocFile) 
        throws IOException {
        console.info(Msg.msg("transcludingTopics"));

        LoadedDocument[] loadedTopicDocs = getAllTopicDocuments();
        if (!transcludeTopics1(loadedTopicDocs)) {
            return null;
        }

        LoadedTopic[] loadedTopics = getAllTopics();
        processRelatedLinks(loadedTopics);

        Element mapElement = mainMap.document.getDocumentElement();
        if (!isAutoMap) {
            filterMap(mapElement, /*excludeResourceOnly*/ true);

            addMetadata(mapElement);
        }

        filterTopics(mapElement);

        if (!transcludeTopics2(loadedTopicDocs)) {
            return null;
        }

        if (!isAutoMap) {
            wrapTopicrefTitles(mapElement);

            addRelatedLinks(mapElement);
        }

        Chunk[] chunks = chunkTopics();
        if (chunks == null) {
            return null;
        }

        if (tocFile != null) {
            tocFile[0] = getTOCFile(outDir, chunks);
        }

        if (!processLinks(chunks)) {
            return null;
        }

        // *** From this point, all id attributes are unique and flat. ***

        if (resourceHandler != null && 
            !dryRun &&
            !processResources(chunks, outDir)) {
            return null;
        }

        sortColspecs(chunks);

        numberEquations(chunks);

        File listsFile = writeLists(chunks, outDir);
        if (listsFile == null) {
            return null;
        }

        File[] chunkFiles = writeChunks(chunks, outDir);
        if (chunkFiles == null) {
            return null;
        }

        File[] preprocessedFiles = new File[1+chunkFiles.length];
        preprocessedFiles[0] = listsFile;
        System.arraycopy(chunkFiles, 0, preprocessedFiles, 1,
                         chunkFiles.length);

        return preprocessedFiles;
    }

    private static File getTOCFile(File outDir, Chunk[] chunks) {
        String rootName = null;

        loop: for (Chunk chunk : chunks) {
            for (ChunkEntry chunkEntry : chunk.getEntries()) {
                if (chunkEntry.type == ChunkEntry.Type.TOC) {
                    rootName = chunkEntry.chunk.getRootName();
                    break loop;
                }
            }
        }

        if (rootName == null) {
            return null;
        } else {
            return new File(outDir, rootName + ".ditac");
        }
    }

    // -----------------------------------------------------------------------
    // Implementation
    // -----------------------------------------------------------------------

    // ----------------------------------
    // createAutoMap
    // ----------------------------------

    protected LoadedDocument createAutoMap(URL[] inFiles) {
        URL mapURL;
        if (autoMapSaveFile != null) {
            mapURL = FileUtil.fileToURL(autoMapSaveFile);
        } else {
            mapURL = FileUtil.fileToURL(new File("__MAP.ditamap"));
        }

        Document mapDoc = DOMUtil.newDocument();
        mapDoc.setDocumentURI(mapURL.toExternalForm());

        Element map = mapDoc.createElementNS(null, "map");
        map.setAttributeNS(null, "class", "- map/map ");

        int inFileCount = inFiles.length;
        if (inFileCount == 1) {
            map.setAttributeNS(null, "chunk", "to-content");
        }

        // Note that an automatically generated map has no title.

        mapDoc.appendChild(map);

        String language = lang; // User-specified lang if any.

        for (int i = 0; i < inFileCount; ++i) {
            URL url = inFiles[i];

            LoadedDocument loadedDoc = loadedDocs.get(url);
            assert(loadedDoc != null);

            Element topicref = mapDoc.createElementNS(null, "topicref");
            topicref.setAttributeNS(null, "class", "- map/topicref ");
            topicref.setAttributeNS(null, "href", 
                                    loadedDoc.url.toExternalForm());
            map.appendChild(topicref);

            // Determine the main language of the deliverable.

            if (language == null) {
                switch (loadedDoc.type) {
                case MULTI_TOPIC:
                    {
                        LoadedTopic[] loadedTopics = loadedDoc.getTopics();
                        if (loadedTopics != null && loadedTopics.length > 0) {
                            language = findLanguage(loadedTopics);
                        }
                    }
                    break;
                case TOPIC:
                    {
                        LoadedTopic loadedTopic = loadedDoc.getFirstTopic();

                        language = DOMUtil.getXMLLang(loadedTopic.element);
                        if (language == null) {
                            LoadedTopic[] nestedTopics = 
                                loadedTopic.nestedTopics(null);
                            if (nestedTopics.length > 0) {
                                language = findLanguage(nestedTopics);
                            }
                        }
                    }
                    break;
                }
            }
        }

        if (language != null) {
            DOMUtil.setXMLLang(map, language);
        }

        return new LoadedDocument(mapURL, mapDoc);
    }
    
    protected static String findLanguage(LoadedTopic[] loadedTopics) {
        // Breadth first search.

        int count = loadedTopics.length;
        for (int i = 0; i < count; ++i) {
            String language = DOMUtil.getXMLLang(loadedTopics[i].element);
            if (language != null) {
                return language;
            }
        }

        for (int i = 0; i < count; ++i) {
            LoadedTopic[] nestedTopics = loadedTopics[i].nestedTopics(null);
            if (nestedTopics.length > 0) {
                String language = findLanguage(nestedTopics);
                if (language != null) {
                    return language;
                }
            }
        }

        return null;
    }

    // ----------------------------------
    // addFrontBackMatter
    // ----------------------------------

    protected boolean addFrontBackMatter() {
        Document doc = mainMap.document;
        Element map = doc.getDocumentElement();

        // Do not add a TOC if the (generally automatically generated) map
        // contains a single topicref having no topicref descendants ---

        int[] frontMatter2 = frontMatter;
        int[] backMatter2 = backMatter;
        if (frontMatter2 != null || backMatter2 != null) {
            int[] topicrefCount = new int[1];
            countTOCEntries(map, topicrefCount);

            if (topicrefCount[0] <= 1) {
                frontMatter2 = clearTOC(frontMatter2);
                backMatter2 = clearTOC(backMatter2);
            }
        }

        Element[] containers = new Element[2];
        frontMatter2 = checkFrontBackMatter(map, frontMatter2, containers);
        backMatter2 = checkFrontBackMatter(map, backMatter2, containers);
        if (frontMatter2 == null && backMatter2 == null) {
            // Nothing to do.
            return true;
        }
        console.info(Msg.msg("addingFrontBackMatter"));

        if (frontMatter2 != null) {
            // We'll add frontmatter/booklists/toc, etc, even to a map,
            // though this is not allowed by the grammar.

            Element element = createFrontBackMatter(frontMatter2, false, doc);
            if (element != null) {
                insertFrontMatter(element, containers[0], map);
            }
        }

        if (backMatter2 != null) {
            Element element = createFrontBackMatter(backMatter2, true, doc);
            if (element != null) {
                insertBackMatter(element, containers[1], map);
            }
        }

        return true;
    }

    /**
     * Count the topicref elements contained in the topicref hierarchy.
     * Ignore those found inside frontmatter and backmatter.
     * Ignore those found inside a reltable.
     */
    protected static void countTOCEntries(Element element, int[] count) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                // A frontmatter/backmatter is also a topicref.
                if (DITAUtil.hasClass(childElement, "map/topicref") &&
                    !DITAUtil.hasClass(childElement, "bookmap/frontmatter") &&
                    !DITAUtil.hasClass(childElement, "bookmap/backmatter")) {
                    ++count[0];
                    countTOCEntries(childElement, count);
                }
            }
            child = child.getNextSibling();
        }
    }

    protected static int[] clearTOC(int[] items) {
        if (items != null) {
            items = items.clone();
            for (int i = 0; i < items.length; ++i) {
                items[i] &= ~TOC;
            }
        }
        return items;
    }

    protected static int[] checkFrontBackMatter(Element map, int[] items, 
                                                Element[] containers) {
        if (items == null || items.length == 0) {
            // But do not clear containers which may already have been
            // initialized.
            return null;
        }

        Element booklists = null;
        Element container = 
            DITAUtil.findChildByClass(map, "bookmap/frontmatter");
        if (container != null) {
            containers[0] = container;

            booklists = 
                DITAUtil.findChildByClass(container, "bookmap/booklists");
            if (booklists != null) {
                items = doCheckFrontBackMatter(booklists, items);
                if (items == null) {
                    return null;
                }
            }
        }

        booklists = null;
        container = DITAUtil.findChildByClass(map, "bookmap/backmatter");
        if (container != null) {
            containers[1] = container;

            booklists = 
                DITAUtil.findChildByClass(container, "bookmap/booklists");
            if (booklists != null) {
                items = doCheckFrontBackMatter(booklists, items);
                if (items == null) {
                    return null;
                }
            }
        }

        return items;
    }

    protected static int[] doCheckFrontBackMatter(Element booklists, 
                                                  int[] items) {
        int[] checkedItems = new int[items.length];
        int j = 0;

        for (int i = 0; i < items.length; ++i) {
            int item = items[i];

            int checkedItem = 0x0;

            if ((item & TOC) != 0 &&
                DITAUtil.findChildByClass(booklists, "bookmap/toc") == null) {
                checkedItem |= TOC;
            }

            if ((item & FIGURE_LIST) != 0 &&
                DITAUtil.findChildByClass(booklists,
                                          "bookmap/figurelist") == null) {
                checkedItem |= FIGURE_LIST;
            }

            if ((item & TABLE_LIST) != 0 &&
                DITAUtil.findChildByClass(booklists,
                                          "bookmap/tablelist") == null) {
                checkedItem |= TABLE_LIST;
            }

            if ((item & EXAMPLE_LIST) != 0 &&
                DITAUtil.findChildByClass(booklists,
                                          "bookmap/examplelist") == null) {
                checkedItem |= EXAMPLE_LIST;
            }

            if ((item & EQUATION_LIST) != 0 &&
                DITAUtil.findChildByClass(booklists,
                                          "bookmap/equationlist") == null) {
                checkedItem |= EQUATION_LIST;
            }

            if ((item & INDEX_LIST) != 0 &&
                DITAUtil.findChildByClass(booklists,
                                          "bookmap/indexlist") == null) {
                checkedItem |= INDEX_LIST;
            }

            if (checkedItem != 0x0) {
                checkedItems[j++] = checkedItem;
            }
        }

        if (j == 0) {
            return null;
        }
        if (j != checkedItems.length) {
            int[] items2 = new int[j];
            System.arraycopy(checkedItems, 0, items2, 0, j);
            checkedItems = items2;
        }

        return checkedItems;
    }

    protected static Element createFrontBackMatter(int[] items, 
                                                   boolean isBackMatter,
                                                   Document doc) {
        Element top;
        if (isBackMatter) {
            top = doc.createElementNS(null, "backmatter");
            top.setAttributeNS(null, "class", 
                               "- map/topicref bookmap/backmatter ");
        } else {
            top = doc.createElementNS(null, "frontmatter");
            top.setAttributeNS(null, "class", 
                               "- map/topicref bookmap/frontmatter ");
        }

        boolean done = false;

        for (int i = 0; i < items.length; ++i) {
            int item = items[i];

            Element parent = doc.createElementNS(null, "booklists");
            top.appendChild(parent);

            parent.setAttributeNS(null, "class", 
                                  "- map/topicref bookmap/booklists ");

            parent.setAttributeNS(null, "chunk", "to-content");

            String copyTo = null;
            if (item == TOC) {
                copyTo = "toc";
            } else if (item == FIGURE_LIST) {
                copyTo = "figurelist";
            } else if (item == TABLE_LIST) {
                copyTo = "tablelist";
            } else if (item == EXAMPLE_LIST) {
                copyTo = "examplelist";
            } else if (item == EQUATION_LIST) {
                copyTo = "equationlist";
            } else if (item == INDEX_LIST) {
                copyTo = "indexlist";
            }
            if (copyTo != null) {
                parent.setAttributeNS(null, "copy-to", copyTo);
            }

            if ((item & TOC) != 0) {
                Element element = doc.createElementNS(null, "toc");
                element.setAttributeNS(null, "class", 
                                       "- map/topicref bookmap/toc ");
                parent.appendChild(element);
                done = true;
            } 

            if ((item & FIGURE_LIST) != 0) {
                Element element = doc.createElementNS(null, "figurelist");
                element.setAttributeNS(null, "class", 
                                       "- map/topicref bookmap/figurelist ");
                parent.appendChild(element);
                done = true;
            } 

            if ((item & TABLE_LIST) != 0) {
                Element element = doc.createElementNS(null, "tablelist");
                element.setAttributeNS(null, "class", 
                                       "- map/topicref bookmap/tablelist ");
                parent.appendChild(element);
                done = true;
            } 

            if ((item & EXAMPLE_LIST) != 0) {
                Element element = doc.createElementNS(null, "examplelist");
                element.setAttributeNS(
                    null, "class", 
                    "- map/topicref bookmap/booklist bookmap/examplelist ");
                parent.appendChild(element);
                done = true;
            } 

            if ((item & EQUATION_LIST) != 0) {
                Element element = doc.createElementNS(null, "equationlist");
                element.setAttributeNS(
                    null, "class", 
                    "- map/topicref bookmap/booklist bookmap/equationlist ");
                parent.appendChild(element);
                done = true;
            } 

            if ((item & INDEX_LIST) != 0) {
                Element element = doc.createElementNS(null, "indexlist");
                element.setAttributeNS(null, "class", 
                                       "- map/topicref bookmap/indexlist ");
                parent.appendChild(element);
                done = true;
            }
        }

        return done? top : null;
    }

    protected static void insertFrontMatter(Element element, 
                                            Element frontmatter,
                                            Element map) {
        if (frontmatter != null) {
            // Insert automatically generated booklists after any
            // user-specified booklist (e.g. toc).
            Element[] booklists = 
                DITAUtil.findChildrenByClass(frontmatter, "bookmap/booklists");
            Node before =
                (booklists.length > 0)? booklists[booklists.length-1] : null;
            if (before == null) {
                // If before is null, prepend.
                before = DOMUtil.getFirstChildElement(frontmatter);
            } else {
                before = before.getNextSibling();
            }

            moveChildren(element, frontmatter, before);
        } else {
            Element[] ditavalrefs = DITAUtil.findDitavalrefs(map);
            if (ditavalrefs != null) {
                // After first ditavalref.
                map.insertBefore(element, ditavalrefs[0].getNextSibling());
            } else {
                // Before first topicref.
                Element before = DITAUtil.findChildByClass(map, "map/topicref");
                if (before == null) {
                    before = 
                        DITAUtil.findChildByClass(map, "bookmap/backmatter");
                    if (before == null) {
                        before = 
                            DITAUtil.findChildByClass(map, "map/reltable");
                    }
                }

                map.insertBefore(element, before);
            }
        }
    }

    protected static void insertBackMatter(Element element, 
                                           Element backmatter,
                                           Element map) {
        if (backmatter != null) {
            // Insert automatically generated booklists before any
            // user-specified booklist (e.g. indexlist).
            Element[] booklists = 
                DITAUtil.findChildrenByClass(backmatter, "bookmap/booklists");
            Element before = (booklists.length > 0)? booklists[0] : null;
            // If before is null, append.

            moveChildren(element, backmatter, before);
        } else {
            Element before = DITAUtil.findChildByClass(map, "map/reltable");

            map.insertBefore(element, before);
        }
    }

    protected static void moveChildren(Element from, Element to, Node before) {
        Node child = from.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();

            from.removeChild(child);
            to.insertBefore(child, before);

            child = next;
        }
    }

    // ----------------------------------
    // prepareChunking
    // ----------------------------------

    protected void prepareChunking() {
        Chunking chunkMode = chunking;
        if (chunkMode == Chunking.AUTO && media == Media.PRINT) {
            chunkMode = Chunking.NONE;
        }

        Element mapElement = mainMap.document.getDocumentElement();
        if (chunkMode != Chunking.AUTO) { // NONE or SINGLE
            discardChunk(mainMap.document, (chunkMode == Chunking.SINGLE));

            mapElement.setAttributeNS(null, "chunk", "to-content");
        } 
        // Otherwise AUTO (SCREEN).
    }

    protected static void discardChunk(Node node, boolean keepSelect) {
        Node child = node.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (keepSelect) {
                    String value = childElement.getAttributeNS(null, "chunk");
                    if (value != null && value.length() > 0) {
                        String select = null;

                        String[] split = XMLText.splitList(value);
                        for (int k = 0; k < split.length; ++k) {
                            String spec = split[k];

                            if (spec.startsWith("select-")) {
                                select = spec;
                                break;
                            }
                        }

                        if (select == null) {
                            childElement.removeAttributeNS(null, "chunk");
                        } else {
                            childElement.setAttributeNS(null, "chunk", select);
                        }
                    }
                } else {
                    childElement.removeAttributeNS(null, "chunk");
                }

                discardChunk(childElement, keepSelect);
            }

            child = child.getNextSibling();
        }
    }

    // ----------------------------------
    // loadTopics
    // ----------------------------------

    protected boolean loadTopics(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (DITAUtil.hasClass(childElement, "map/topicref")) {
                    URL url = null;
                    try {
                        url = DITAUtil.doGetLocalTopicURL(childElement);
                    } catch (IllegalArgumentException e) {
                        console.error(childElement, e.getMessage());
                        return false;
                    }

                    if (url != null) {
                        LoadedDocument loadedDoc;
                        try {
                            loadedDoc = loadedDocs.load(url);
                        } catch (Exception e) {
                            console.error(childElement, 
                                          Msg.msg("cannotLoad", url, 
                                                  ThrowableUtil.reason(e)));
                            return false;
                        }

                        switch (loadedDoc.type) {
                        case MULTI_TOPIC:
                        case TOPIC:
                            break;
                        default:
                            // Should not happen otherwise url would have been
                            // null.
                            console.error(childElement,
                                          Msg.msg("notATopic", url));
                            return false;
                        }
                    }
                    // Otherwise, attribute href absent or does not point to
                    // a local topic.
                }

                if (!loadTopics(childElement)) {
                    return false;
                }
            }

            child = child.getNextSibling();
        }

        return true;
    }

    // ----------------------------------
    // transcludeTopics
    // ----------------------------------

    protected LoadedDocument[] getAllTopicDocuments() {
        int count = loadedDocs.size();
        LoadedDocument[] docs = new LoadedDocument[count];
        count = 0;

        Iterator<LoadedDocument> iter = loadedDocs.iterator();
        while (iter.hasNext()) {
            LoadedDocument loadedDoc = iter.next();

            switch (loadedDoc.type) {
            case TOPIC:
            case MULTI_TOPIC:
                docs[count++] = loadedDoc;
                break;
            }
        }

        if (count != docs.length) {
            docs = ArrayUtil.trimToSize(docs, count);
        }

        return docs;
    }

    protected boolean transcludeTopics1(LoadedDocument[] loadedTopicDocs) 
        throws IOException {
        console.verbose(Msg.msg("pullingTopicContent"));

        return (new ConrefIncluder(keySpaces,
                                   console)).process(loadedTopicDocs);
    }

    protected boolean transcludeTopics2(LoadedDocument[] loadedTopicDocs) 
        throws IOException { 
        console.verbose(Msg.msg("pushingTopicContent"));

        (new ConrefPusher(console)).process(loadedTopicDocs);
        return true;
    }

    // ----------------------------------
    // getAllTopics
    // ----------------------------------

    protected LoadedTopic[] getAllTopics() {
        ArrayList<LoadedTopic> topicList = new ArrayList<LoadedTopic>();

        Iterator<LoadedDocument> iter = loadedDocs.iterator();
        while (iter.hasNext()) {
            LoadedDocument loadedDoc = iter.next();

            switch (loadedDoc.type) {
            case TOPIC:
            case MULTI_TOPIC:
                {
                    LoadedTopic[] loadedTopics = loadedDoc.getTopics();
                    if (loadedTopics != null) {
                        for (int i = 0; i < loadedTopics.length; ++i) {
                            LoadedTopic loadedTopic = loadedTopics[i];

                            topicList.add(loadedTopic);
                            addNestedTopics(loadedTopic, topicList);
                        }
                    }
                }
                break;
            }
        }

        LoadedTopic[] topics = new LoadedTopic[topicList.size()];
        topicList.toArray(topics);
        return topics;
    }

    protected void addNestedTopics(LoadedTopic loadedTopic, 
                                   List<LoadedTopic> topicList) {
        LoadedTopic[] nestedTopics = loadedTopic.getNestedTopics(console);
        for (int i = 0; i < nestedTopics.length; ++i) {
            LoadedTopic nestedTopic = nestedTopics[i];

            topicList.add(nestedTopic);
            addNestedTopics(nestedTopic, topicList);
        }
    }

    // ----------------------------------
    // processRelatedLinks
    // ----------------------------------

    protected void processRelatedLinks(LoadedTopic[] loadedTopics) {
        console.info(Msg.msg("processingRelatedLinks"));

        for (LoadedTopic loadedTopic : loadedTopics) {
            CascadeMeta.processTopic(loadedTopic.element);
        }
    }

    // ----------------------------------
    // filterMap
    // ----------------------------------

    protected void filterMap(Element mapElement, boolean excludeResourceOnly) {
        console.info(Msg.msg("filteringMap"));

        Filter filter2 = copyExternalFilter();
        if (excludeResourceOnly) {
            filter2.addExcludeProps("processing-role", "resource-only");
        }
        filter2.addExcludeProps("print",
                                ((media == Media.PRINT)? "no" : "printonly"));
        filters.setExternalFilter(filter2);
        filters.filterMap(mapElement);
    }

    // ----------------------------------
    // addMetadata
    // ----------------------------------

    protected void addMetadata(Element mapElement) {
        console.info(Msg.msg("addingMetadata"));

        CopyMeta.processMap(mapElement, loadedDocs);
    }

    // ----------------------------------
    // wrapTopicrefTitles
    // ----------------------------------

    protected void wrapTopicrefTitles(Element mapElement) {
        console.info(Msg.msg("wrappingTopicrefTitles"));

        WrapTopicrefTitle.processMap(mapElement, mainMap.url, loadedDocs);
    }

    // ----------------------------------
    // addRelatedLinks
    // ----------------------------------

    protected void addRelatedLinks(Element mapElement) {
        console.info(Msg.msg("addingRelatedLinks"));

        (new LinkGenerator()).processMap(mapElement, mainMap.url, 
                                         loadedDocs);
    }

    // ----------------------------------
    // filterTopics
    // ----------------------------------

    protected void filterTopics(Element mapElement) {
        if (externalFilter == null &&
            !DITAUtil.containsDitavalrefs(mapElement)) {
            // Nothing to do.
            return;
        }

        console.info(Msg.msg("filteringTopics"));

        filters.setExternalFilter(externalFilter);
        filters.filterTopics(mapElement, loadedDocs);
    }

    // ----------------------------------
    // chunkTopics
    // ----------------------------------

    protected Chunk[] chunkTopics() {
        console.info(Msg.msg("chunkingTopics"));

        boolean byTopic = false;
        Element mapElement = mainMap.document.getDocumentElement();
        String value = mapElement.getAttributeNS(null, "chunk");
        if (value != null && value.indexOf("by-topic") >= 0) {
            byTopic = true;
        }

        Chunker chunker = new Chunker(byTopic, rootName, console);
        Chunk[] chunks = chunker.processMap(mainMap, loadedDocs);

        if (chunks != null &&
            !partRestartsChapterNumber && 
            mainMap.type == LoadedDocument.Type.BOOKMAP) {
            numberChapters(chunks);
        }

        return chunks;
    }

    protected static void numberChapters(Chunk[] chunks) {
        int chapterCount = 0;
        int appendixCount = 0;
        long lastChapterSerial = 0;
        long lastAppendixSerial = 0;

        for (Chunk chunk : chunks) {
            for (ChunkEntry entry : chunk.getEntries()) {
                int partIndex = -1;
                int appendixIndex = -1;
                int chapterIndex = -1;
                int appendicesIndex = -1;
                
                final String[] segments = entry.number;
                final int segmentCount = segments.length;
                for (int i = 0; i < segmentCount; ++i) {
                    String segment = segments[i];

                    if (segment.startsWith("chapter.")) {
                        chapterIndex = i;
                        break;
                    } else if (segment.startsWith("appendix.")) {
                        appendixIndex = i;
                        break;
                    } else if (segment.startsWith("part.")) {
                        partIndex = i;
                    } else if (segment.startsWith("appendices.")) {
                        appendicesIndex = i;
                    }
                }

                if (chapterIndex >= 0) {
                    long chapterNumber =
                        TOCInfo.parseNumberSegment(segments[chapterIndex]);
                    if (chapterNumber > 0) {
                        // No part is interpreted as part #0.
                        long partNumber = 0;
                        if (partIndex >= 0) {
                            partNumber = 
                                TOCInfo.parseNumberSegment(segments[partIndex]);
                        }

                        if (partNumber >= 0) {
                            long chapterSerial = 
                                ((partNumber << 32) | chapterNumber);
                            if (chapterSerial != lastChapterSerial) {
                                lastChapterSerial = chapterSerial;
                                ++chapterCount;
                            }

                            segments[chapterIndex] = 
                                TOCInfo.formatNumberSegment("chapter", 
                                                            chapterCount);
                        }
                    }
                } else if (appendixIndex >= 0) {
                    long appendixNumber =
                        TOCInfo.parseNumberSegment(segments[appendixIndex]);
                    if (appendixNumber > 0) {
                        // No appendices interpreted as appendices #0.
                        long appendicesNumber = 0;
                        if (appendicesIndex >= 0) {
                            appendicesNumber = TOCInfo.parseNumberSegment(
                                segments[appendicesIndex]);
                        }

                        if (appendicesNumber >= 0) {
                            long appendixSerial = 
                                ((appendicesNumber << 32) | appendixNumber);
                            if (appendixSerial != lastAppendixSerial) {
                                lastAppendixSerial = appendixSerial;
                                ++appendixCount;
                            }

                            segments[appendixIndex] = 
                                TOCInfo.formatNumberSegment("appendix", 
                                                            appendixCount);
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------
    // processLinks
    // ----------------------------------

    protected boolean processLinks(Chunk[] chunks) {
        console.info(Msg.msg("processingLinks"));

        // After next step, loadedTopic.topicId and topic/@id will be out of
        // sync. That's why we need to keep a targetURLToChunkEntry map. ---

        HashMap<URL,ChunkEntry> targetURLToChunkEntry = 
            new HashMap<URL,ChunkEntry>();

        // The same topic may be referenced in several ChunkEntries.
        IdentityHashMap<Element,Element> processed = 
            new IdentityHashMap<Element,Element>();

        for (Chunk chunk : chunks) {
            ChunkEntry[] entries = chunk.getEntries();
            for (ChunkEntry entry : entries) {
                Element element = entry.getElement();
                if (element != null) { // That is, a topic.
                    if (!processed.containsKey(element)) {

                        URL url = targetURL(entry);
                        if (url != null) {
                            targetURLToChunkEntry.put(url, entry);
                        }

                        processed.put(element, element);
                    }
                }
            }
        }

        // Ensure that all preprocessed topics have a unique ID and that 
        // all non-topic element IDs are flat and unique ---

        HashMap<String,Element> idToElement = new HashMap<String,Element>();

        processed.clear();

        for (Chunk chunk : chunks) {
            ChunkEntry[] entries = chunk.getEntries();
            for (ChunkEntry entry : entries) {
                Element element = entry.getElement();
                if (element != null) { // That is, a topic.
                    if (!processed.containsKey(element)) {

                        String topicId =
                            DITAUtil.getNonEmptyAttribute(element, null, "id");
                        assert(topicId != null);

                        topicId = setUniqueId(element, topicId, idToElement);

                        if (!processIds(element, topicId, idToElement)) {
                            return false;
                        }

                        processed.put(element, element);
                    }
                }
            }
        }

        // From now, all IDs are flat and unique. The problem is that links
        // are out of sync with these flat and unique IDs. 
        // So massage the links too. ---

        processed.clear();

        IdentityHashMap<Element,Element> glossEntries = 
            new IdentityHashMap<Element,Element>();

        for (Chunk chunk : chunks) {
            String chunkName = chunk.getRootName();

            ChunkEntry[] entries = chunk.getEntries();
            for (ChunkEntry entry : entries) {
                Element element = entry.getElement();
                if (element != null) { // That is, a topic.
                    if (!processed.containsKey(element)) {

                        if (!processLinks(element, chunkName, 
                                          targetURLToChunkEntry, idToElement,
                                          glossEntries)) {
                            return false;
                        }

                        processed.put(element, element);
                    }
                }
            }
        }

        return true;
    }

    protected static URL targetURL(ChunkEntry entry) {
        URL url = null;

        LoadedTopic loadedTopic = entry.loadedTopic;
        assert(loadedTopic != null);

        LoadedDocument loadedDoc = loadedTopic.getAncestorDocument();
        if (loadedDoc != null) {
            url = URLUtil.setFragment(loadedDoc.url, loadedTopic.topicId);
        }

        return url;
    }

    // -----------
    // setUniqueId
    // -----------

    protected static String setUniqueId(Element element, String id,
                                        Map<String,Element> idToElement) {
        String id2 = id;

        int counter = 2;
        Element e;
        while ((e = idToElement.get(id2)) != null && e != element) {
            if (counter >= 100) {
                // Too much conflicts. Use a more radical approach
                // (which gives rather long and unreadable IDs).
                id2 = id + "-" + 
                    Integer.toString(System.identityHashCode(element), 
                                     Character.MAX_RADIX);
                break;
            }

            StringBuilder buffer = new StringBuilder(id);
            buffer.append('-');
            buffer.append(Integer.toString(counter++));
            id2 = buffer.toString();
        }

        element.setAttributeNS(null, "id", id2);

        idToElement.put(id2, element);
        return id2;
    }

    // ----------
    // processIds
    // ----------

    protected boolean processIds(Element element, String topicId, 
                                 Map<String,Element> idToElement) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                DITAUtil.hasDITANamespace(child)) { // Skip SVG and MathML.
                Element childElement = (Element) child;

                if (DITAUtil.hasClass(childElement, "topic/topic")) {
                    // Do not process nested topics.
                    return true;
                }

                // When we have an id, make it flat and unique.

                String id = 
                    DITAUtil.getNonEmptyAttribute(childElement, null, "id");
                if (id != null) {
                    if (DITAUtil.hasClass(childElement, "topic/resourceid")) {
                        // Trust the author. Keep this id unchanged. 
                        idToElement.put(id, childElement);
                    } else {
                        String flatId = topicId + ID_SEPARATOR + id;

                        // Option -validate only checks the ID of topics.
                        // The "id" attribute of non-topic elements is not
                        // an actual ID.

                        Element anchor = idToElement.get(flatId);
                        if (anchor != null) {
                            // flatId starts with the unique ID of the
                            // topic, hence anchor and childElement are
                            // found in the same topic.

                            String where = "???";
                            NodeLocation location = 
                                (NodeLocation) anchor.getUserData(
                                    NodeLocation.USER_DATA_KEY);
                            if (location != null) {
                                where = location.toString();
                            }
                            console.warning(childElement,
                                            Msg.msg("duplicateId", id, where));
                        }

                        setUniqueId(childElement, flatId, idToElement);
                    }
                }

                if (!processIds(childElement, topicId, idToElement)) {
                    return false;
                }
            }

            child = child.getNextSibling();
        }

        return true;
    }
    
    // ------------
    // processLinks
    // ------------

    protected static String[] XREF_ELEMENTS = {
        "topic/ph",
        "topic/term",
        "topic/keyword",
        "topic/cite",
        "topic/dt",
        "glossentry/glossAlternateFor",
        "topic/xref"
    };

    protected static String[] IMAGE_ELEMENTS = {
        "topic/image",
        "svg-d/svgref",
        "mathml-d/mathmlref"
    };

    protected boolean processLinks(Element element, String chunkName,
                                   Map<URL,ChunkEntry> targetURLToChunkEntry,
                                   Map<String,Element> idToElement,
                                   Map<Element,Element> glossEntries) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (DITAUtil.hasClass(childElement, "topic/topic")) {
                    // Do not process nested topics.
                    return true;
                }

                // Process href when it points inside a topic ---

                boolean deeper = true;
                URL targetURL = null;
                if (DITAUtil.hasClass(childElement, IMAGE_ELEMENTS)) {
                    // Part of the validation service of ditac.
                    checkImageHref(childElement);
                } else {
                    targetURL = getLocalTopicURL(childElement);
                }

                if (targetURL != null) {
                    String chunkRef = 
                        targetURLToChunkRef(targetURL, targetURLToChunkEntry,
                                            chunkName, childElement);
                    if (chunkRef != null) {
                        childElement.setAttributeNS(null, "href", chunkRef);
                        
                        Element target = findTarget(chunkRef, idToElement);
                        if (target == null) {
                            // toDisplayForm preserves the fragment.
                            console.warning(childElement,
                                            Msg.msg("noHrefTarget", 
                                             URLUtil.toDisplayForm(targetURL)));
                        } else {
                            // When possible and when needed, add a text to
                            // link and xref elements.

                            boolean done = true;

                            if (DITAUtil.hasClass(childElement, "topic/link")) {
                                done = addLinkText(childElement, target);
                            } else if (DITAUtil.hasClass(
                                                 childElement,
                                                 "abbrev-d/abbreviated-form")) {
                                done = addAbbrev(childElement, target,
                                                 glossEntries);
                            } else if (DITAUtil.hasClass(childElement,
                                                         XREF_ELEMENTS)) {
                                done = addXrefText(childElement, target);
                            }

                            if (done) {
                                // If automatically filled with link text, do
                                // not process links inside this child.
                                deeper = 
                                    !childElement.hasAttributeNS(DITAC_NS_URI, 
                                                                 FILLED_NAME);
                            } else {
                                console.warning(
                                    childElement,
                                    Msg.msg("noLinkText", 
                                            childElement.getLocalName(), 
                                            target.getLocalName(), 
                                            target.getAttributeNS(null, "id")));
                            }
                        }
                    }
                }

                if (deeper && 
                    !processLinks(childElement, chunkName, 
                                  targetURLToChunkEntry, idToElement,
                                  glossEntries)) {
                    return false;
                }
            }

            child = child.getNextSibling();
        }

        return true;
    }

    protected void checkImageHref(Element element) {
        String href = DITAUtil.getNonEmptyAttribute(element, null, "href");
        if (href == null) {
            console.warning(element, Msg.msg("missingAttribute", "href"));
        } else {
            URL url = null;
            try {
                url = URLUtil.createURL(href);
            } catch (MalformedURLException ignored) {}

            if (url == null) {
                console.warning(element,
                                Msg.msg("invalidAttribute", href, "href"));
            } else {
                if (!URLUtil.exists(url)) {
                    console.warning(element,
                                   Msg.msg("noSuchFile", URLUtil.toLabel(url)));
                }
            }
        }
    }

    protected URL getLocalTopicURL(Element element) {
        URL url = null;
        try {
            url = DITAUtil.doGetLocalTopicURL(element);
        } catch (IllegalArgumentException e) {
            console.warning(element, e.getMessage());
        }
        return url;
    }

    protected
    String targetURLToChunkRef(URL targetURL, 
                               Map<URL,ChunkEntry> targetURLToChunkEntry,
                               String chunkName,
                               Element pointerElement) {
        String warnMessage = null;

        String ref = URLUtil.getFragment(targetURL);
        if (ref == null) {
            LoadedDocument loadedDoc = loadedDocs.get(targetURL);
            if (loadedDoc != null) {
                LoadedTopic loadedTopic = loadedDoc.getFirstTopic();
                if (loadedTopic != null) {
                    // Do not use @id here! We need the author-specified topic
                    // ID.
                    ref = loadedTopic.topicId;
                }
            }

            if (ref == null) {
                warnMessage = Msg.msg("pointsOutsidePreprocessedTopics", 
                                      targetURL);
            }
        }

        if (ref != null) {
            String targetTopicId = null;
            String targetElementId = null;

            int slash = ref.lastIndexOf('/');
            if (slash >= 0) {
                targetTopicId = ref.substring(0, slash).trim();

                targetElementId = ref.substring(slash+1).trim();
                if (targetElementId.length() == 0) {
                    targetElementId = null;
                }
            } else {
                targetTopicId = ref.trim();
            }
            if (targetTopicId.length() == 0) {
                targetTopicId = null;
            }

            if (targetTopicId != null) {
                targetURL = URLUtil.setFragment(targetURL, targetTopicId);
                ChunkEntry targetChunkEntry =
                    targetURLToChunkEntry.get(targetURL);

                if (targetChunkEntry != null) {
                    StringBuilder buffer = new StringBuilder();

                    String targetChunkName = 
                        targetChunkEntry.chunk.getRootName();
                    if (chunkName == null ||
                        !targetChunkName.equals(chunkName)) {
                        buffer.append(chunkBaseName(targetChunkName));
                    }

                    // Append the corresponding flat and unique ID.

                    String topicId = DITAUtil.getNonEmptyAttribute(
                        targetChunkEntry.loadedTopic.element, null, "id");
                    assert(topicId != null);

                    buffer.append('#');
                    buffer.append(URIComponent.quoteFragment(topicId));

                    if (targetElementId != null) {
                        buffer.append(ID_SEPARATOR);
                        buffer.append(
                            URIComponent.quoteFragment(targetElementId));
                    }

                    return buffer.toString();
                } else {
                    warnMessage = Msg.msg("pointsOutsidePreprocessedTopics", 
                                          targetURL);
                }
            } else {
                console.error(pointerElement, 
                              Msg.msg("invalidAttribute", targetURL, "href"));
            }
        }

        if (warnMessage != null) {
            console.warning(pointerElement, warnMessage);
        }
        return null;
    }

    protected String chunkBaseName(Chunk chunk) {
        return chunkBaseName(chunk.getRootName());
    }

    protected String chunkBaseName(String rootName) {
        StringBuilder buffer = new StringBuilder();
        buffer.append(DITAUtil.quotePathSegment(rootName));
        if (extension != null) {
            buffer.append('.');
            buffer.append(DITAUtil.quotePathSegment(extension));
        }
        return buffer.toString();
    }

    protected static Element findTarget(String chunkRef, 
                                        Map<String,Element> idToElement) {
        int pos = chunkRef.lastIndexOf('#');
        if (pos < 0) {
            // Should not happen.
            return null;
        }

        // Something like "foo" or "foo__bar".
        String targetId = chunkRef.substring(pos+1);
        return idToElement.get(targetId);
    }

    protected static boolean addLinkText(Element link, Element target) {
        Element linktext = DITAUtil.findChildByClass(link, "topic/linktext");
        if (linktext != null) {
            String text = linktext.getTextContent();
            if (text == null || text.length() == 0) {
                // Remove empty linktext.
                link.removeChild(linktext);
                linktext = null;
            }
        }

        Element desc = DITAUtil.findChildByClass(link, "topic/desc");
        if (desc != null) {
            String text = desc.getTextContent();
            if (text == null || text.length() == 0) {
                // Remove empty desc.
                link.removeChild(desc);
                desc = null;
            }
        }

        Document doc = link.getOwnerDocument();
        assert(doc != null);

        boolean done = false;

        if (linktext != null) {
            // Nothing to do.
            done = true;
        } else {
            Element title = null;
            if (DITAUtil.hasClass(target, "topic/title")) {
                title = target;
            } else {
                // Examples: topic, section, table, figure, glossentry
                // (glossterm is a "topic/title").
                title = DITAUtil.findChildByClass(target, "topic/title");
            }

            if (title != null) {
                linktext = doc.createElementNS(null, "linktext");
                linktext.setAttributeNS(null, "class", "- topic/linktext ");
                link.insertBefore(linktext, desc);

                DOMUtil.copyChildren(title, linktext, doc);
                done = true;

                // Mark this link as having been filled with text by the
                // preprocessor.
                link.setAttributeNS(DITAC_NS_URI, FILLED_QNAME, "true");
            }
        }

        if (desc == null) {
            Element shortdesc = 
                DITAUtil.findChildByClass(target, "topic/shortdesc");
            if (shortdesc == null && 
                DITAUtil.hasClass(target, "topic/topic")) {
                // May be the shortdesc is contained in an abstract?
                Element container = 
                    DITAUtil.findChildByClass(target, "topic/abstract");
                if (container != null) {
                    shortdesc = 
                        DITAUtil.findChildByClass(container, "topic/shortdesc");
                }
            }

            if (shortdesc != null) {
                desc = doc.createElementNS(null, "desc");
                desc.setAttributeNS(null, "class", "- topic/desc ");
                link.appendChild(desc);

                DOMUtil.copyChildren(shortdesc, desc, doc);
            }
        }

        return done;
    }

    protected boolean addAbbrev(Element abbrev, Element target,
                                Map<Element,Element> glossEntries) {
        boolean done = false;

        boolean useLongForm = !glossEntries.containsKey(target); // First occur.
        if (useLongForm) {
            glossEntries.put(target, target);
        }

        Element longForm = 
            DITAUtil.findDescendantByClass(target,
                                           0, "glossentry/glossSurfaceForm");
        Element shortForm =
            DITAUtil.findDescendantByClass(target, 0, 
                                           "glossentry/glossAcronym", 
                                           "glossentry/glossAbbreviation",
                                           "glossentry/glossShortForm");

        if (useLongForm) {
            if (longForm == null) {
                // Use glossterm which is a "topic/title".
                done = addXrefText(abbrev, target);
            } else {
                fillXref(abbrev, longForm, null);
                done = true;
            }
        } else {
            if (shortForm == null) {
                done = addXrefText(abbrev, target);
            } else {
                fillXref(abbrev, shortForm, null);
                done = true;

                String title;
                if (longForm != null &&
                    (title = XMLText.collapseWhiteSpace(
                        longForm.getTextContent())).length() > 0) {
                    abbrev.setAttributeNS(DITAC_NS_URI, TITLE_QNAME, title);
                }
            }
        }

        return done;
    }

    protected boolean addXrefText(Element xref, Element target) {
        if (DOMUtil.hasContent(xref)) {
            // Nothing to do.
            return true;
        }

        boolean isXref = DITAUtil.hasClass(xref, "topic/xref");
        String xrefType = DITAUtil.getNonEmptyAttribute(xref, null, "type");
        if (isXref && "fn".equals(xrefType)) {
            // Nothing to do.
            return true;
        }

        // Special cases ---

        if (DITAUtil.hasClass(target, "topic/fn") &&
            isXref && xrefType == null) {
            // The author has almost certainly forgot to specify type="fn".
            // Add it now and proceed.
            console.warning(xref, Msg.msg("missingAttribute2", "type", "fn"));

            xref.setAttributeNS(null, "type", "fn");
            return true;
        }

        Document doc = xref.getOwnerDocument();
        assert(doc != null);

        Element targetParent;
        if (DITAUtil.hasClass(target, "topic/li") &&
            (targetParent = DOMUtil.getParentElement(target)) != null &&
            DITAUtil.hasClass(targetParent, "topic/ol")) {
            int num = getListItemNumber(target);
            if (num > 0) {
                // For the first substep of second step, this gives us "1"
                // where the author expects "2.a". But this is still useful
                // for many other cases.
                xref.appendChild(doc.createTextNode(Integer.toString(num)));
                xref.setAttributeNS(DITAC_NS_URI, FILLED_QNAME, "true");
                return true;
            }
        }

        if (DITAUtil.hasClass(target, "topic/dlentry")) {
            Element term = DITAUtil.findChildByClass(target, "topic/dt");
            if (term != null) {
                fillXref(xref, term, doc);
                return true;
            }
        }

        // Normal cases ---

        Element title = null;
        if (DITAUtil.hasClass(target, "topic/title")) {
            title = target;
        } else {
            // Examples: topic, section, table, figure, glossentry
            // (glossterm is a "topic/title").
            title = DITAUtil.findChildByClass(target, "topic/title");
        }

        if (title == null) {
            return false;
        }
        fillXref(xref, title, doc);

        return true;
    }

    protected static void fillXref(Element xref, Element title, Document doc) {
        if (doc == null) {
            doc = xref.getOwnerDocument();
            assert(doc != null);
        }

        DOMUtil.copyChildren(title, xref, doc);

        // Mark this xref-like element as having been filled with text by
        // the preprocessor.
        xref.setAttributeNS(DITAC_NS_URI, FILLED_QNAME, "true");

        // No need to add a desc child to an xref-like element.
    }

    protected static int getListItemNumber(Element element) {
        Node parent = element.getParentNode();
        if (parent != null) {
            int index = 1;

            Node child = parent.getFirstChild();
            while (child != null) {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    if (child == element) {
                        return index;
                    }

                    if (DOMUtil.sameName(element, (Element) child)) {
                        ++index;
                    }
                }

                child = child.getNextSibling();
            }
        }

        return -1;
    }

    // ----------------------------------
    // processResources
    // ----------------------------------

    protected boolean processResources(Chunk[] chunks, File outDir) {
        console.info(Msg.msg("processingResources"));

        Element map = mainMap.document.getDocumentElement();

        // bookmap/booktitle (which specializes topic/title) may contain
        // images.
        Element title = DITAUtil.findChildByClass(map, "topic/title");
        if (title != null && ! processResources(title, outDir)) {
            return false;
        }

        // topic/data may contain images too.
        Element metadata = DITAUtil.findChildByClass(map, "map/topicmeta");
        if (metadata != null && ! processResources(metadata, outDir)) {
            return false;
        }

        // ---

        // The same topic may be referenced in several ChunkEntries.
        IdentityHashMap<Element, Element> processed = 
            new IdentityHashMap<Element, Element>();

        for (Chunk chunk : chunks) {
            ChunkEntry[] entries = chunk.getEntries();
            for (ChunkEntry entry : entries) {
                Element element = entry.getElement();
                if (element != null) { // That is, a topic.
                    if (!processed.containsKey(element)) {

                        if (!processResources(element, outDir)) {
                            return false;
                        }

                        processed.put(element, element);
                    }
                }
            }
        }

        return true;
    }

    protected boolean processResources(Element element, File outDir) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (DITAUtil.hasClass(childElement, "topic/topic")) {
                    // Do not process nested topics.
                    return true;
                }

                if (DITAUtil.hasClass(childElement, "topic/image")) {
                    if (!processImage(childElement, outDir)) {
                        return false;
                    }
                } else if (DITAUtil.hasClass(childElement, "topic/object")) {
                    if (!processObject(childElement, outDir)) {
                        return false;
                    }
                } else {
                    if (!processResources(childElement, outDir)) {
                        return false;
                    }
                }
            }
            
            child = child.getNextSibling();
        }

        return true;
    }

    protected boolean processImage(Element element, File outDir) {
        return processResource(element, "href", null, true, outDir);
    }

    protected boolean processResource(Element element, String attrName,
                                      String mimeType, boolean isImage, 
                                      File outDir) {
        String absAttrName = "absolute" + StringUtil.capitalize(attrName);
        if (element.hasAttributeNS(DITAC_NS_URI, absAttrName)) {
            // Resources having absolute URLs are not ``handled''.
            element.removeAttributeNS(DITAC_NS_URI, absAttrName);
        } else {
            String href = 
                DITAUtil.getNonEmptyAttribute(element, null, attrName);
            if (href != null) {
                URL url = null;
                try {
                    url = URLUtil.createURL(href);
                } catch (MalformedURLException ignored) {}
                if (url != null) {
                    String href2 = href;

                    try {
                        href2 = resourceHandler.handleResource(url, mimeType, 
                                                               isImage,
                                                               outDir, console);
                        if (href2 == null) {
                            href2 = href;
                        }
                    } catch (Throwable t) {
                        console.error(element,
                                      Msg.msg("cannotProcessResource",
                                              url, ThrowableUtil.reason(t)));
                        return false;
                    }

                    if (!href2.equals(href)) {
                        element.setAttributeNS(null, attrName, href2);
                    }
                }
            }
        }

        return true;
    }

    protected boolean processObject(Element element, File outDir) {
        String mimeType = 
            DITAUtil.getNonEmptyAttribute(element, null, "codetype");
        if (!processResource(element, "classid", mimeType, false, outDir)) {
            return false;
        }

        mimeType = DITAUtil.getNonEmptyAttribute(element, null, "type");
        if (!processResource(element, "data", mimeType, false, outDir)) {
            return false;
        }

        if (!processResources(element, "archive", null, false, outDir)) {
            return false;
        }

        Element[] params = DITAUtil.findChildrenByClass(element, "topic/param");
        int paramCount = params.length;
        for (int i = 0; i < paramCount; ++i) {
            Element param = params[i];

            String name = DITAUtil.getNonEmptyAttribute(param, null, "name");
            if ("source".equals(name)) {
                mimeType = DITAUtil.getNonEmptyAttribute(param, null, "type");
                if (!processResource(param, "value", mimeType, false, outDir)) {
                    return false;
                }
            } else if ("poster".equals(name)) {
                if (!processResource(param, "value", null, true, outDir)) {
                    return false;
                }
            } else if ("movie".equals(name)) {
                String location = 
                    DITAUtil.getNonEmptyAttribute(param, null, "value");
                if (location != null &&
                    ((location.toLowerCase().endsWith(".swf")) || 
                     (mimeType != null && mimeType.equalsIgnoreCase(
                         "application/x-shockwave-flash")))) {
                    if (!processResource(param, "value", 
                                         "application/x-shockwave-flash", false,
                                         outDir)) {
                        return false;
                    }
                }
            }
        }
        
        // Process resources in desc child.
        if (!processResources(element, outDir)) {
            return false;
        }

        return true;
    }

    protected boolean processResources(Element element, String attrName,
                                       String mimeType, boolean isImage, 
                                       File outDir) {
        String absAttrName = "absolute" + StringUtil.capitalize(attrName);
        String absAttrValue = 
            DITAUtil.getNonEmptyAttribute(element, DITAC_NS_URI, absAttrName);
        element.removeAttributeNS(DITAC_NS_URI, absAttrName);

        String attrValue = 
            DITAUtil.getNonEmptyAttribute(element, null, attrName);
        if (attrValue == null || absAttrValue == null) {
            return true;
        }

        String[] absFlags = XMLText.splitList(absAttrValue);
        String[] hrefs = XMLText.splitList(attrValue);
        int hrefCount = hrefs.length;
        assert(hrefCount == absFlags.length);

        for (int i = 0; i < hrefCount; ++i) {
            if ("true".equals(absFlags[i])) {
                // Resources having absolute URLs are not ``handled''.
                continue;
            }
            String href = hrefs[i];

            URL url = null;
            try {
                url = URLUtil.createURL(href);
            } catch (MalformedURLException ignored) {}
            if (url != null) {
                String href2 = href;

                try {
                    href2 = resourceHandler.handleResource(url, null, false,
                                                           outDir, console);
                    if (href2 == null) {
                        href2 = href;
                    }
                } catch (Throwable t) {
                    console.error(element,
                                  Msg.msg("cannotProcessResource",
                                          url, ThrowableUtil.reason(t)));
                    return false;
                }

                hrefs[i] = href2;
            }
        }

        element.setAttributeNS(null, attrName, StringUtil.join(' ', hrefs));
        return true;
    }

    // ----------------------------------
    // sortColspecs
    // ----------------------------------

    protected void sortColspecs(Chunk[] chunks) {
        console.verbose(Msg.msg("sortingColspecs"));

        for (int i = 0; i < chunks.length; ++i) {
            Chunk chunk = chunks[i];

            ChunkEntry[] entries = chunk.getEntries();
            for (int j = 0; j < entries.length; ++j) {
                ChunkEntry entry = entries[j];

                Element element = entry.getElement();
                if (element != null) { // That is, a topic.
                    sortColspecs(element);
                }
            }
        }
    }

    protected void sortColspecs(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (DITAUtil.hasClass(childElement, "topic/topic")) {
                    // Do not process nested topics.
                    return;
                }

                if (DITAUtil.hasClass(childElement, "topic/tgroup")) {
                    Element[] colspecs = 
                      DITAUtil.findChildrenByClass(childElement,
                                                   "topic/colspec");
                    if (colspecs.length > 1) { // No need to sort 1 colspec.
                        boolean hasColnums = false;
                        for (Element colspec : colspecs) {
                            int colnum = 
                                getColnum(colspec, /*reportError*/ false);
                            if (colnum >= 1) {
                                hasColnums = true;
                                break;
                            }
                        }

                        if (hasColnums) {
                            int prevColnum = 0;
                            for (Element colspec : colspecs) {
                                int colnum = 
                                    getColnum(colspec, /*reportError*/ true);
                                if (colnum >= 1) {
                                    if (colnum <= prevColnum) {
                                        // Do not attempt to fix by setting it
                                        // to prevColnum+1.
                                        console.error(
                                            colspec,
                                            Msg.msg("unsortedColnum", 
                                                    colnum, prevColnum));
                                    }
                                } else {
                                    colnum = prevColnum+1;
                                    colspec.setAttributeNS(
                                        null, "colnum",
                                        Integer.toString(colnum));
                                }

                                prevColnum = colnum;
                            }

                            Element[] colspecs2 = colspecs.clone();
                            Arrays.sort(colspecs2, new Comparator<Element>() {
                                public int compare(Element cs1, Element cs2) {
                                    int cn1 = 
                                        getColnum(cs1, /*reportError*/ false);
                                    int cn2 = 
                                        getColnum(cs2, /*reportError*/ false);
                                    return cn1 - cn2;
                                }
                            });

                            Node before = 
                                colspecs[colspecs.length-1].getNextSibling();
                            for (Element colspec : colspecs) {
                                childElement.removeChild(colspec);
                            }
                            for (Element colspec : colspecs2) {
                                childElement.insertBefore(colspec, before);
                            }
                        }
                    }
                } else {
                    sortColspecs(childElement);
                }
            }
            
            child = child.getNextSibling();
        }
    }

    protected int getColnum(Element colspec, boolean reportError) {
        int colnum = -1;

        String colnumValue = colspec.getAttributeNS(null, "colnum");
        if (colnumValue != null && colnumValue.length() > 0) {
            try {
                colnum = (int) Double.parseDouble(colnumValue);
            } catch (NumberFormatException ignored) {
                if (reportError) {
                    console.error(colspec, 
                                  Msg.msg("invalidColnum", colnumValue));
                }
            }
        }

        return (colnum >= 1)? colnum : -1;
    }

    // ----------------------------------
    // numberEquations
    // ----------------------------------

    /**
     * Give a number to all empty <tt>equation-number</tt>s.
     * <p><tt>equation-block</tt>s not containing <tt>equation-number</tt>s
     * are not counted. 
     * <p>This gives a numbering which is different from
     * the numbering of <tt>equation-figure</tt>s.
     */
    protected void numberEquations(Chunk[] chunks) {
        console.verbose(Msg.msg("numberingEquations"));

        FormalElementCounter equationCounter = 
            new FormalElementCounter("equation");

        // The same topic may be referenced in several ChunkEntries.
        IdentityHashMap<Element,Element> processed = 
            new IdentityHashMap<Element,Element>();

        for (int i = 0; i < chunks.length; ++i) {
            Chunk chunk = chunks[i];

            ChunkEntry[] entries = chunk.getEntries();
            for (int j = 0; j < entries.length; ++j) {
                ChunkEntry entry = entries[j];

                Element element = entry.getElement();
                if (element != null) { // That is, a topic.
                    if (!processed.containsKey(element)) {
                        equationCounter.traversing(entry);

                        Document doc = element.getOwnerDocument();
                        numberEquations(element, doc, equationCounter);

                        processed.put(element, element);
                    }
                }
            }
        }
    }

    protected static void numberEquations(Element element, Document doc,
                                          FormalElementCounter counter) {

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (DITAUtil.hasClass(childElement, "topic/topic")) {
                    // Do not process nested topics.
                    return;
                }

                if (DITAUtil.hasClass(childElement,
                                      "equation-d/equation-block")) {
                    Element[] numbers = 
                      DITAUtil.findChildrenByClass(childElement,
                                                  "equation-d/equation-number");
                    if (numbers.length > 0) {
                        counter.increment();
                        String value = counter.format();

                        for (Element number : numbers) {
                            if (!DOMUtil.hasContent(number)) {
                                number.appendChild(doc.createTextNode(value));
                                number.setAttributeNS(DITAC_NS_URI, 
                                                      FILLED_QNAME, "true");
                            }
                        }
                    }
                } else {
                    numberEquations(childElement, doc, counter);
                }
            }
            
            child = child.getNextSibling();
        }
    }

    // ----------------------------------
    // writeLists
    // ----------------------------------

    protected File writeLists(Chunk[] chunks, File outDir) 
        throws IOException {
        File outFile = new File(outDir, "ditac_lists.ditac_lists");
        saveInfo(Msg.msg("writingLists", outFile));

        Document doc = DOMUtil.newDocument();

        Element root = doc.createElementNS(DITAC_NS_URI, "ditac:lists");
        doc.appendChild(root);

        String language = getDocLang();
        if (language != null) {
            DOMUtil.setXMLLang(root, language);
        }

        // chunkList ---

        Element chunkList =
            doc.createElementNS(DITAC_NS_URI, "ditac:chunkList");
        root.appendChild(chunkList);

        if (hasTitlePage() && chunks.length > 0) {
            chunks[0].prependEntry(
                new ChunkEntry(chunks[0], ChunkEntry.Type.TITLE_PAGE, 
                               StringUtil.EMPTY_LIST, null, null, TOCType.NONE, 
                               null));
        }

        addChunks(chunks, doc, chunkList);

        // titlePage ---

        Element titlePage = 
            doc.createElementNS(DITAC_NS_URI, "ditac:titlePage");
        root.appendChild(titlePage);

        addTitlePage(doc, titlePage);

        // frontmatterTOC ---

        Element frontmatterTOC = 
            doc.createElementNS(DITAC_NS_URI, "ditac:frontmatterTOC");
        root.appendChild(frontmatterTOC);

        addTOC(chunks, doc, TOCType.FRONTMATTER, frontmatterTOC);

        // toc ---

        Element toc = doc.createElementNS(DITAC_NS_URI, "ditac:toc");
        root.appendChild(toc);

        addTOC(chunks, doc, TOCType.BODY, toc);

        // backmatterTOC ---

        Element backmatterTOC = 
            doc.createElementNS(DITAC_NS_URI, "ditac:backmatterTOC");
        root.appendChild(backmatterTOC);

        addTOC(chunks, doc, TOCType.BACKMATTER, backmatterTOC);

        // tableList, figureList, exampleList, equationList, indexList ---

        Element figureList =
            doc.createElementNS(DITAC_NS_URI, "ditac:figureList");
        root.appendChild(figureList);

        FormalElementCounter figureCounter = 
            new FormalElementCounter("figure");

        Element tableList =
            doc.createElementNS(DITAC_NS_URI, "ditac:tableList");
        root.appendChild(tableList);

        FormalElementCounter tableCounter = 
            new FormalElementCounter("table");

        Element exampleList =
            doc.createElementNS(DITAC_NS_URI, "ditac:exampleList");
        root.appendChild(exampleList);

        FormalElementCounter exampleCounter = 
            new FormalElementCounter("example");

        Element equationList =
            doc.createElementNS(DITAC_NS_URI, "ditac:equationList");
        root.appendChild(equationList);

        FormalElementCounter equationCounter = 
            new FormalElementCounter("equation");

        IndexTerms indexTerms = new IndexTerms(console);

        Element indexList =
            doc.createElementNS(DITAC_NS_URI, "ditac:indexList");
        root.appendChild(indexList);

        // The same topic may be referenced in several ChunkEntries.
        IdentityHashMap<Element,Element> processed = 
            new IdentityHashMap<Element,Element>();

        for (int i = 0; i < chunks.length; ++i) {
            Chunk chunk = chunks[i];

            String chunkBaseName = chunkBaseName(chunk);

            ChunkEntry[] entries = chunk.getEntries();
            for (int j = 0; j < entries.length; ++j) {
                ChunkEntry entry = entries[j];

                Element element = entry.getElement();
                if (element != null) { // That is, a topic.
                    if (!processed.containsKey(element)) {
                        tableCounter.traversing(entry);
                        figureCounter.traversing(entry);
                        exampleCounter.traversing(entry);
                        equationCounter.traversing(entry);

                        // ditac:search=false means: ignore indexterm elements
                        // in this topic.

                        IndexTerms indexTerms2 = indexTerms;
                        if ("false".equals(DITAUtil.getNonEmptyAttribute(
                               element, DITAC_NS_URI, SEARCH_NAME))) {
                            element.removeAttributeNS(DITAC_NS_URI,
                                                      SEARCH_NAME);
                            indexTerms2 = null;
                        }

                        addLists(element, chunkBaseName, doc, 
                                 tableCounter, tableList, 
                                 figureCounter, figureList,
                                 exampleCounter, exampleList,
                                 equationCounter, equationList,
                                 indexTerms2);

                        processed.put(element, element);
                    }
                }
            }
        }

        // Here we really need a language in order to sort the index entries.
        indexTerms.addEntries((language == null)? "en" : language, 
                              doc, indexList);

        saveDocument(doc, outFile);
        return outFile;
    }

    /**
     * Returns the main language of the document if it can be determined
     * returns <code>null</code> otherwise.
     * <p>The main language of the document is the value of 
     * the <tt>xml:lang</tt> attribute of the map, if any. 
     * It's the language specified using {@link #setLang} otherwise.
     */
    public String getDocLang() {
        String language = 
            DOMUtil.getXMLLang(mainMap.document.getDocumentElement());
        if (language == null) {
            language = lang;
        }
        return language;
    }

    // ---------
    // addChunks
    // ---------

    protected void addChunks(Chunk[] chunks, Document doc, Element chunkList) {
        for (int i = 0; i < chunks.length; ++i) {
            Chunk chunk = chunks[i];

            Element chunkItem =
                doc.createElementNS(DITAC_NS_URI, "ditac:chunk");
            chunkList.appendChild(chunkItem);

            chunkItem.setAttributeNS(null, "file", chunkBaseName(chunk));

            ChunkEntry[] entries = chunk.getEntries();
            for (int j = 0; j < entries.length; ++j) {
                ChunkEntry entry = entries[j];

                String qName = null;

                switch (entry.type) {
                case TITLE_PAGE:
                    qName = "ditac:titlePage";
                    break;
                case TOC:
                    qName = "ditac:toc";
                    break;
                case FIGURE_LIST:
                    qName = "ditac:figureList";
                    break;
                case TABLE_LIST:
                    qName = "ditac:tableList";
                    break;
                case EXAMPLE_LIST:
                    qName = "ditac:exampleList";
                    break;
                case EQUATION_LIST:
                    qName = "ditac:equationList";
                    break;
                case INDEX_LIST:
                    qName = "ditac:indexList";
                    break;
                case TOPIC:
                    qName = "ditac:topic";
                    break;
                }

                Element entryItem = doc.createElementNS(DITAC_NS_URI, qName);

                if (entry.type == ChunkEntry.Type.TOPIC) {
                    entryItem.setAttributeNS(
                        null, "number", StringUtil.join(' ', entry.number));

                    entryItem.setAttributeNS(null, "role", entry.role);

                    String title = entry.title;
                    if (title == null) {
                        title = getTitleFromChild(entry.getElement());
                    }
                    entryItem.setAttributeNS(null, "title", title);

                    String id = entry.getElement().getAttributeNS(null, "id");
                    assert(id != null && id.length() > 0);
                    entryItem.setAttributeNS(null, "id", id);
                }

                chunkItem.appendChild(entryItem);
            }
        }
    }

    protected static String getTitleFromChild(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (DITAUtil.hasClass(childElement, "topic/title")) {
                    String title = childElement.getTextContent();
                    if (title != null && 
                        (title = 
                         XMLText.collapseWhiteSpace(title)).length() == 0) {
                        title = null;
                    }
                    return title;
                }
            }
            
            child = child.getNextSibling();
        }

        return null;
    }

    protected boolean hasTitlePage() {
        Element map = mainMap.document.getDocumentElement();

        // Note that bookmap/booktitle specializes topic/title.
        Element title = DITAUtil.findChildByClass(map, "topic/title");
        if (title != null) {
            return true;
        } else {
            // Use title attribute if any (found on old maps).
            String titleText = map.getAttributeNS(null, "title");
            if (titleText != null && titleText.length() > 0) {
                return true;
            }
        }
        return forceTitlePage;
    }

    // ------------
    // addTitlePage
    // ------------
    
    protected void addTitlePage(Document doc, Element titlePage) {
        Element map = mainMap.document.getDocumentElement();

        Element title = DITAUtil.findChildByClass(map, "topic/title");
        if (title != null) {
            titlePage.appendChild(doc.importNode(title, /*deep*/ true));
        } else {
            String titleText = map.getAttributeNS(null, "title");
            if (titleText != null && 
                (titleText = 
                 XMLText.collapseWhiteSpace(titleText)).length() == 0) {
                titleText = null;
            }

            if (forceTitlePage || titleText != null) {
                title = doc.createElementNS(null, "title");
                title.setAttributeNS(null, "class", "- topic/title ");

                titlePage.appendChild(title);

                if (titleText != null) {
                    title.appendChild(doc.createTextNode(titleText));
                }
                // Note that when forceTitlePage=true, titlePage/title may be
                // empty.
            }
        }

        Element metadata = DITAUtil.findChildByClass(map, "map/topicmeta");
        if (metadata != null) {
            titlePage.appendChild(doc.importNode(metadata, /*deep*/ true));
        }
    }

    // ------
    // addTOC
    // ------

    protected static class TOCItem {
        public final ChunkEntry chunkEntry;
        public final Element tocElement;

        public TOCItem(ChunkEntry chunkEntry, Element tocElement) {
            this.chunkEntry = chunkEntry;
            this.tocElement = tocElement;
        }

        public boolean isParent(ChunkEntry entry) {
            if (chunkEntry == null) {
                // Topmost TOCItem.
                return true;
            }

            if (chunkEntry.type != ChunkEntry.Type.TOPIC) {
                // TITLE_PAGE, TOC, etc, cannot be the parent of entry.
                return false;
            }

            String subRole = DITAUtil.getSubRole(chunkEntry.role);
            if (!subRole.equals(entry.role)) {
                return false;
            }

            String[] num1 = chunkEntry.number;
            int num1Length = num1.length;

            String[] num2 = entry.number;
            if (num2.length != num1Length + 1) {
                return false;
            }

            for (int i = 0; i < num1Length; ++i) {
                if (!num2[i].equals(num1[i])) {
                    return false;
                }
            }

            return true;
        }
    }

    protected void addTOC(Chunk[] chunks, Document doc, TOCType tocType,
                          Element toc) {
        Stack<TOCItem> stack = new Stack<TOCItem>();
        stack.push(new TOCItem(null, toc));

        for (Chunk chunk : chunks) {
            for (ChunkEntry entry : chunk.getEntries()) {
                TOCItem top = stack.peek();
                while (!top.isParent(entry)) {
                    stack.pop();
                    top = stack.peek();
                }

                Element tocEntry = doc.createElementNS(DITAC_NS_URI,
                                                       "ditac:tocEntry");

                if (entry.tocType == tocType) {
                    Element topicElement = entry.getElement();
                    if (topicElement == null ||
                        // Do not pollute the TOC with glossary entries.
                        !DITAUtil.hasClass(topicElement, 
                                           "glossentry/glossentry")) {
                        top.tocElement.appendChild(tocEntry);

                        String chunkBaseName = chunkBaseName(entry.chunk);
                        tocEntry.setAttributeNS(null, "file", chunkBaseName);

                        tocEntry.setAttributeNS(
                          null, "number", StringUtil.join(' ', entry.number));

                        tocEntry.setAttributeNS(null, "role", entry.role);

                        String title = entry.title;
                        if (title == null) {
                            if (topicElement != null) {
                                title = getTitleFromChild(topicElement);
                            } else {
                                // A nonEmptyToken.
                                title = "__AUTO__" + entry.role + "__";  
                            }
                        }
                        tocEntry.setAttributeNS(null, "title", title);

                        String id;
                        if (topicElement != null) {
                            id = topicElement.getAttributeNS(null, "id");
                            assert(id != null);
                        } else {
                            // An xsd:Name.
                            id = "__AUTO__" + entry.role + "__"; 
                        }

                        tocEntry.setAttributeNS(null, "id", id);
                    }
                }

                stack.push(new TOCItem(entry, tocEntry));
            }
        }
    }
    
    // --------
    // addLists
    // --------

    protected static void addLists(Element element, 
                                   String chunkBaseName, Document doc, 
                                   FormalElementCounter tableCounter, 
                                   Element tableList,
                                   FormalElementCounter figureCounter, 
                                   Element figureList,
                                   FormalElementCounter exampleCounter, 
                                   Element exampleList,
                                   FormalElementCounter equationCounter, 
                                   Element equationList,
                                   IndexTerms indexTerms) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;

                if (DITAUtil.hasClass(childElement, "topic/topic")) {
                    // Do not process nested topics.
                    return;
                }

                boolean isTable = 
                    DITAUtil.hasClass(childElement, "topic/table");
                boolean isFig = 
                    DITAUtil.hasClass(childElement, "topic/fig");
                boolean isExample = 
                    DITAUtil.hasClass(childElement, "topic/example");
                // "equation-d/equation-figure" is also a "topic/fig".
                boolean isEquation = 
                  DITAUtil.hasClass(childElement, "equation-d/equation-figure");

                if (isTable || isFig || isExample || isEquation) {
                    String title = getTitleFromChild(childElement);
                    if (title != null) {
                        FormalElementCounter counter;
                        String qName;
                        Element list;

                        if (isTable) {
                            counter = tableCounter;
                            qName = "ditac:table";
                            list = tableList;
                        } else if (isEquation) { // Before testing isFig.
                            counter = equationCounter;
                            qName = "ditac:equation";
                            list = equationList;
                        } else if (isFig) {
                            counter = figureCounter;
                            qName = "ditac:figure";
                            list = figureList;
                        } else {
                            counter = exampleCounter;
                            qName = "ditac:example";
                            list = exampleList;
                        }
                        counter.increment();

                        Element formalElement = 
                            doc.createElementNS(DITAC_NS_URI, qName);
                        list.appendChild(formalElement);

                        formalElement.setAttributeNS(null, "number", 
                                                     counter.format());

                        formalElement.setAttributeNS(null, "title", title);

                        formalElement.setAttributeNS(null, "file", 
                                                     chunkBaseName);

                        String id = childElement.getAttributeNS(null, "id");
                        assert(id != null && id.length() > 0);
                        formalElement.setAttributeNS(null, "id", id);

                        copyDesc(childElement, formalElement, doc);
                    }
                    // Otherwise, ignore tables and figs not having a title
                    // child.

                    // A table/fig/example/equation-figure can contain
                    // indexterms.
                    addLists(childElement, chunkBaseName, doc, 
                             tableCounter, tableList, 
                             figureCounter, figureList,
                             exampleCounter, exampleList,
                             equationCounter, equationList,
                             indexTerms);
                } else if (DITAUtil.hasClass(childElement,"topic/indexterm")) {
                    if (indexTerms != null) {
                        indexTerms.collect(childElement, chunkBaseName);
                    }
                } else {
                    addLists(childElement, chunkBaseName, doc, 
                             tableCounter, tableList, 
                             figureCounter, figureList,
                             exampleCounter, exampleList,
                             equationCounter, equationList,
                             indexTerms);
                }
            }
            
            child = child.getNextSibling();
        }
    }

    protected static void copyDesc(Element from, Element to, Document doc) {
        Element desc = DITAUtil.findChildByClass(from, "topic/desc");
        if (desc != null) {
            Element description = 
                doc.createElementNS(DITAC_NS_URI, "ditac:description");
            to.appendChild(description);

            DOMUtil.copyChildren(desc, description, doc);
        }
    }

    // ----------------------------------
    // writeChunks
    // ----------------------------------

    protected File[] writeChunks(Chunk[] chunks, File outDir) 
        throws IOException {
        String language = getDocLang();

        int chunkCount = chunks.length;
        File[] outFiles = new File[chunkCount];
        int outFileCount = 0;

        for (int i = 0; i < chunkCount; ++i) {
            Chunk chunk = chunks[i];

            File outFile = new File(outDir, chunk.getRootName() + ".ditac");
            saveInfo(Msg.msg("writingChunk", outFile));

            Document doc = DOMUtil.newDocument();

            Element root = doc.createElementNS(DITAC_NS_URI, "ditac:chunk");
            doc.appendChild(root);

            if (language != null) {
                // Possibly overriden by another xml:lang specified in a topic.
                DOMUtil.setXMLLang(root, language);
            }

            ChunkEntry[] entries = chunk.getEntries();
            for (int j = 0; j < entries.length; ++j) {
                ChunkEntry entry = entries[j];

                Element child = null;
                String qName = null;

                switch (entry.type) {
                case TITLE_PAGE:
                    qName = "ditac:titlePage";
                    break;
                case TOC:
                    qName = "ditac:toc";
                    break;
                case FIGURE_LIST:
                    qName = "ditac:figureList";
                    break;
                case TABLE_LIST:
                    qName = "ditac:tableList";
                    break;
                case EXAMPLE_LIST:
                    qName = "ditac:exampleList";
                    break;
                case EQUATION_LIST:
                    qName = "ditac:equationList";
                    break;
                case INDEX_LIST:
                    qName = "ditac:indexList";
                    break;
                case TOPIC:
                    {
                        Element element = entry.getElement();

                        Filter.Flags flags = (Filter.Flags)
                            element.getUserData(Filters.FLAGS_KEY);
                        if (flags != null) {
                            element.setUserData(Filters.FLAGS_KEY, null, null);
                        }

                        child = (Element) doc.importNode(element,/*deep*/ true);
                        if (hasNestedTopics(element)) {
                            removeNestedTopics(child);
                        }

                        if (flags != null) {
                            // Wrap the topic into a ditac:flags element.
                            Element wrapper = 
                                filters.createFlagsElement(flags, doc);
                            wrapper.appendChild(child);
                            child = wrapper;
                        }
                    }
                    break;
                }

                if (child == null) {
                    assert(qName != null);
                    child = doc.createElementNS(DITAC_NS_URI, qName);
                }

                root.appendChild(child);
            }

            saveDocument(doc, outFile);
            outFiles[outFileCount++] = outFile;
        }

        return outFiles;
    }

    protected static boolean hasNestedTopics(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (DITAUtil.hasClass(childElement, "topic/topic")) {
                    return true;
                }
            }

            child = child.getNextSibling();
        }
        
        return false;
    }

    protected static void removeNestedTopics(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            Node nextChild = child.getNextSibling();

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (DITAUtil.hasClass(childElement, "topic/topic")) {
                    element.removeChild(childElement);
                }
            }

            child = nextChild;
        }
    }
}
