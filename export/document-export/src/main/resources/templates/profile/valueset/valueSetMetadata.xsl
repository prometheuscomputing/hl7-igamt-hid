<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:template name="valueSetMetadata">
		<xsl:if test="$valueSetMetadata.type = 'true' and @sourceType != '' and @sourceType != 'UNSPECIFIED'">
			<b>Type : </b><xsl:value-of select="@sourceType"/><br/>
		</xsl:if>
		<xsl:if test="$valueSetMetadata.type = 'true' and @numberOfCodes != '' and @numberOfCodes != 'UNSPECIFIED'">
			<b>Number of codes : </b><xsl:value-of select="@numberOfCodes"/><br/>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
