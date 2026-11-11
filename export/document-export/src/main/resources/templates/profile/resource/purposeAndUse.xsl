<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:template name="PurposeAndUse">
		<xsl:if test="normalize-space(@purposeAndUse)!=''">
			<b>Purpose and use : </b>
			<xsl:value-of disable-output-escaping="yes" select="@purposeAndUse"></xsl:value-of>
			<xsl:element name="br" />
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
