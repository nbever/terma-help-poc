<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns="http://www.w3.org/1999/xhtml"
                version="2.0">

  <xsl:import href="ditac-xsl:xhtml/xhtml.xsl"/>

  <xsl:template match="*[contains(@class,' sample-d/kbd ')]">
    <tt>
      <xsl:call-template name="commonAttributes"/>
      <xsl:apply-templates/>
    </tt>
  </xsl:template>

  <xsl:template match="*[contains(@class,' sample-d/time ')]">
    <span>
      <xsl:call-template name="commonAttributes"/>
      <xsl:apply-templates/>
    </span>
  </xsl:template>

</xsl:stylesheet>
