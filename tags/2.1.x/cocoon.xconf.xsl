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
    <component-instance name="environment" class="org.apache.cocoon.components.modules.input.EnvironmentInputModule" logger="core.modules.input"/>
    <component-instance name="sitemap-path" class="org.apache.cocoon.components.modules.input.SitemapPathModule" logger="core.modules.input"/>
    <component-instance name="normalize-path" class="org.apache.cocoon.components.modules.input.NormalizePathInputModule" logger="core.modules.input"/>
    <component-instance name="first-non-empty" class="org.apache.cocoon.components.modules.input.FirstNonEmptyModule" logger="core.modules.input"/>
    <component-instance name="crumb" class="org.apache.cocoon.components.modules.input.CrumbInputModule" logger="core.modules.input"/>
    <component-instance name="regexp" class="org.apache.cocoon.components.modules.input.RegExpMatchInputModule" logger="core.modules.input"/>
    <component-instance name="textfile" class="org.apache.cocoon.components.modules.input.TextFileModule" logger="core.modules.input"/>
	</xsl:copy>
</xsl:template>
<!-- Remove old definitions. -->
<xsl:template match="input-modules/component-instance[@name = 'environment']"/>
<xsl:template match="input-modules/component-instance[@name = 'sitemap-path']"/>
<xsl:template match="input-modules/component-instance[@name = 'normalize-path']"/>
<xsl:template match="input-modules/component-instance[@name = 'first-non-empty']"/>
<xsl:template match="input-modules/component-instance[@name = 'crumb']"/>
<xsl:template match="input-modules/component-instance[@name = 'regexp']"/>
<xsl:template match="input-modules/component-instance[@name = 'textfile']"/>


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
