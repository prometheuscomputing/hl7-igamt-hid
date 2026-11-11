<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- Stability, extensibility and content definition, shown with the other value set facts. -->
	<xsl:template name="valueSetAttributes">
		<xsl:if test="$valueSetMetadata.stability = 'true' and normalize-space(@stability)!=''">
			<b>Stability : </b><xsl:value-of select="@stability"/><br/>
		</xsl:if>
		<xsl:if test="$valueSetMetadata.extensibility = 'true' and normalize-space(@extensibility)!=''">
			<b>Extensibility : </b><xsl:value-of select="@extensibility"/><br/>
		</xsl:if>
		<xsl:if test="$valueSetMetadata.contentDefinition = 'true' and normalize-space(@contentDefinition)!=''">
			<b>Content definition : </b><xsl:value-of select="@contentDefinition"/><br/>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
