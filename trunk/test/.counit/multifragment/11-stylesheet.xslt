<?xml version="1.0" ?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:n="http://code.google.com/p/cocooncomponents/"
>

  <xsl:template match="n:*">
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <xsl:attribute name="processed">yes</xsl:attribute>
      <xsl:apply-templates select="*"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="*">
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <xsl:apply-templates select="*"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
