<?xml version="1.0"?>

<!--
  Stylesheet to modify cocoon.xconf, so the components can be found.
  The transformation should be idempotent, i.e., if it is applied multiple times, every new component is added only once.
-->

<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
>

<xsl:param name="host"/>


<!-- Input modules. -->
<!-- Insert new definitions. -->
<xsl:template match="input-modules">
	<xsl:copy>
		<xsl:copy-of select="@*"/>
    <xsl:apply-templates select="node()"/>
    <component-instance class="org.apache.cocoon.components.modules.input.EnvironmentInputModule" logger="core.modules.input" name="environment"/>
    <component-instance class="org.apache.cocoon.components.modules.input.SitemapPathModule" logger="core.modules.input" name="sitemap-path"/>
	</xsl:copy>
</xsl:template>
<!-- Remove old definitions. -->
<xsl:template match="input-modules/component-instance[@name = 'environment']"/>
<xsl:template match="input-modules/component-instance[@name = 'sitemap-path']"/>


<!-- Copy everything else. -->

<xsl:template match="*">
  <xsl:copy>
    <xsl:copy-of select="@*"/>
    <xsl:apply-templates select="node()"/>
  </xsl:copy>
</xsl:template>

<xsl:template match="@*|text()|comment()|processing-instruction()">
  <xsl:copy-of select="."/>
</xsl:template>

</xsl:stylesheet>
