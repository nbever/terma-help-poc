<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format"
                version="2.0">

  <xsl:import href="ditac-xsl:fo/fo.xsl"/>

  <xsl:attribute-set name="kbd" use-attribute-sets="monospace-style">
    <xsl:attribute name="border">1px solid #C0C0C0</xsl:attribute>
    <xsl:attribute name="background-color">#F0F0F0</xsl:attribute>
    <xsl:attribute name="padding">0.25em</xsl:attribute>
  </xsl:attribute-set>

  <xsl:template match="*[contains(@class,' sample-d/kbd ')]">
    <fo:inline xsl:use-attribute-sets="kbd">
      <xsl:call-template name="commonAttributes"/>
      <xsl:apply-templates/>
    </fo:inline>
  </xsl:template>

  <xsl:attribute-set name="time">
    <xsl:attribute name="background-color">#FFFFCC</xsl:attribute>
    <xsl:attribute name="padding">0.25em</xsl:attribute>
  </xsl:attribute-set>

  <xsl:template match="*[contains(@class,' sample-d/time ')]">
    <fo:inline xsl:use-attribute-sets="time">
      <xsl:call-template name="commonAttributes"/>
      <xsl:apply-templates/>
    </fo:inline>
  </xsl:template>

</xsl:stylesheet>
