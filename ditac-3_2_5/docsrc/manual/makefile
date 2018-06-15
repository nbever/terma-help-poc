# ----------------------------------------------------------------------------
# Copyright (c) 2017-2018 XMLmind Software. All rights reserved.
#
# Author: Hussein Shafie
#
# This file is part of the XMLmind DITA Converter project.
# For conditions of distribution and use, see the accompanying LEGAL.txt file.
# ----------------------------------------------------------------------------

DITAC=../../bin/ditac -errout -ignoreoptionsfile

AHF=-ahf /bin/false
XEP=-xep /opt/xep/xep
FOP=-fop /opt/fop/fop
XFC=-xfc /home/hussein/src/xfc/java/bin/fo2rtf
JHI=-jhindexer /opt/javahelp/javahelp/bin/jhindexer
HHC=-hhc /bin/false

#DITAC=..\..\bin\ditac.bat -errout -ignoreoptionsfile
 
#AHF=-ahf "C:\Program Files\Antenna House\AHFormatterV64\AHFCmd.exe"
#XEP=-xep C:\opt\xep\xep.bat
#FOP=-fop C:\opt\fop-2.2\fop\fop.bat
#XFC=-xfc C:\src\xfc\java\bin\fo2rtf.bat
#JHI=-jhindexer C:\opt\javahelp\javahelp\bin\jhindexer.bat
#HHC=-hhc "C:\Program Files\HTML Help Workshop\hhc.exe"

DOC_DIR=../../doc/manual
WH_DIR=../../doc/manual/webhelp
WH2_DIR=../../doc/manual/webhelp2

#WATERMARK_OPTIONS=-p watermark-image watermark.svg -p watermark all
WATERMARK_OPTIONS=

COMMON_OPTIONS=-p extended-toc both -p number all -p use-note-icon yes \
	-p mark-important-steps no $(WATERMARK_OPTIONS)

JUSTIFIED_OPTIONS=-p justified yes -p hyphenate yes

# -r media is needed because the manual contains audio and video.
PRINT_OPTIONS=-filter svg.ditaval -r media \
	-p title-color "rgb(57,73,134)" \
	-p pdf-outline yes \
	$(COMMON_OPTIONS)

SCREEN_OPTIONS=-filter no_svg.ditaval -images images \
	-p xsl-resources-directory resources \
	-p default-table-width "100%" \
	-p mark-external-links yes \
	$(COMMON_OPTIONS)

HELP_OPTIONS=$(SCREEN_OPTIONS) \
	-p number none

EPUBCHECK=epubcheck -f

ANT=ant

SHELL=/bin/sh

# ----------------------------------------------------------------------------
all: realclean \
	xhtml cp_samples webhelp webhelp2 \
	javahelp htmlhelp eclipsehelp epub epub3 \
	pdf pdf-fop rtf wml docx odt

xhtml:
	$(DITAC) -validate $(SCREEN_OPTIONS) \
		-p mathjax auto \
		-p chain-pages both -p chain-topics no \
		$(DOC_DIR)/_.html manual.ditamap

cp_samples:
	rm -rf $(DOC_DIR)/schema \
		$(DOC_DIR)/customize \
		$(DOC_DIR)/sample_plugin \
		$(DOC_DIR)/embed \
		$(DOC_DIR)/samples \
		$(DOC_DIR)/wh_resources
	cp -r schema customize sample_plugin embed samples wh_resources \
		$(DOC_DIR)

webhelp:
	rm -rf $(WH_DIR)/
	$(DITAC) -format webhelp5 $(SCREEN_OPTIONS) \
		-p mathjax auto -filter svg.ditaval \
		-p external-resource-base "../" \
		-p chain-topics yes \
		-p wh-collapse-toc yes \
		-p wh-user-header wh_resources/header.html \
		-p wh-user-footer wh_resources/footer.html \
		-p wh-user-css wh_resources/header_footer.css \
		-p wh-user-resources wh_resources/header_footer_files \
		-p wh-jquery-custom-theme \
			../../../whc/jquery-ui-1.12.1.custom.zip \
		-p wh-id-map-href "" \
		$(WH_DIR)/_.html manual.ditamap

webhelp2:
	rm -rf $(WH2_DIR)/
	$(DITAC) -format webhelp5 $(SCREEN_OPTIONS) \
		-p mathjax auto -filter svg.ditaval \
		-p external-resource-base "../" \
		-p chain-topics yes \
		-p wh-layout simple \
		-p wh-collapse-toc yes \
		-p wh-user-header wh_resources/header.html \
		-p wh-user-footer wh_resources/footer.html \
		-p wh-user-css wh_resources/header_footer.css \
		-p wh-user-resources wh_resources/header_footer_files \
		-p wh-id-map-href "" \
		$(WH2_DIR)/_.html manual.ditamap

javahelp:
	$(DITAC) $(JHI) $(HELP_OPTIONS) \
		$(DOC_DIR)/manual.jar manual.ditamap

htmlhelp:
	$(DITAC) $(HHC) $(HELP_OPTIONS) \
		$(DOC_DIR)/manual.chm manual.ditamap

eclipsehelp:
	$(DITAC) -format eclipsehelp $(HELP_OPTIONS) \
		-p plugin-name "XMLmind DITA Converter Manual" \
		-p plugin-id com.xmlmind.ditac.manual \
		-p plugin-provider XMLmind \
		-p plugin-version 3.2.5 \
		$(DOC_DIR)/com.xmlmind.ditac.manual/_.html manual.ditamap

# Test on iPad iBooks:
# scp ./../doc/manual/*.epub ns207347.ovh.net:public_html/ebooks/

epub:
	$(DITAC) $(SCREEN_OPTIONS) \
		-p epub-identifier urn:x-mlmind:ditac:manual \
		-p number-toc-entries yes \
		-p cover-image ditac_manual.png \
		$(DOC_DIR)/manual.epub manual.ditamap
	$(EPUBCHECK) $(DOC_DIR)/manual.epub

epub3:
	$(DITAC) -f epub3 $(SCREEN_OPTIONS) -filter svg.ditaval \
		-p epub-identifier urn:x-mlmind:ditac:ditac_manual \
		-p number-toc-entries yes \
		-p cover-image ditac_manual2.png \
		$(DOC_DIR)/ditac_manual.epub manual.ditamap
	$(EPUBCHECK) $(DOC_DIR)/ditac_manual.epub

pdf:
	rm -rf $(DOC_DIR)/media
	$(DITAC) $(XEP) $(PRINT_OPTIONS) \
		-p two-sided yes \
		$(JUSTIFIED_OPTIONS) \
		$(DOC_DIR)/manual.pdf manual.ditamap

pdf-fop:
	rm -rf $(DOC_DIR)/media
	$(DITAC) $(FOP) $(PRINT_OPTIONS) \
		-t ditac-xsl:fo/fo_indent.xsl \
		-p two-sided no \
		-p page-inner-margin 1in \
		-p page-outer-margin 1in \
		$(JUSTIFIED_OPTIONS) \
		$(DOC_DIR)/manual-fop.pdf manual.ditamap

#
# Only for test purposes.
#

pdf-ahf:
	rm -rf $(DOC_DIR)/media
	$(DITAC) $(AHF) -keepfo $(PRINT_OPTIONS) \
		-t ditac-xsl:fo/fo_indent.xsl \
		-p two-sided yes \
		-p page-inner-margin 1in \
		-p page-outer-margin 1in \
		$(JUSTIFIED_OPTIONS) \
		$(DOC_DIR)/manual-ahf.pdf manual.ditamap

ps-fop:
	rm -rf $(DOC_DIR)/media
	$(DITAC) $(FOP) $(PRINT_OPTIONS) \
		-p two-sided yes \
		-p page-inner-margin 1in \
		-p page-outer-margin 1in \
		$(JUSTIFIED_OPTIONS) \
		$(DOC_DIR)/manual-fop.ps manual.ditamap

rtf: 
	rm -rf $(DOC_DIR)/media
	$(DITAC) $(XFC) $(PRINT_OPTIONS) \
		$(DOC_DIR)/manual.rtf manual.ditamap

wml:
	rm -rf $(DOC_DIR)/media
	$(DITAC) $(XFC) $(PRINT_OPTIONS) \
		$(DOC_DIR)/manual.word.xml manual.ditamap

docx:
	rm -rf $(DOC_DIR)/media
	$(DITAC) $(XFC) $(PRINT_OPTIONS) \
		$(DOC_DIR)/manual.docx manual.ditamap

odt:
	rm -rf $(DOC_DIR)/media
	$(DITAC) $(XFC) $(PRINT_OPTIONS) \
		$(DOC_DIR)/manual.odt manual.ditamap

# ----------------------------------------------------------------------------
clean:
	rm -rf *~ */*~ */*/*~ out

distclean: clean
#
	rm -rf sample_plugin/sample/out
#
	(cd embed; $(ANT) realclean)

realclean: distclean
	rm -rf $(DOC_DIR)/*
