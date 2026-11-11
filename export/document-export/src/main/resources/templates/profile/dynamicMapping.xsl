<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- Dynamic mapping: the datatype of one field is chosen by the value of another. -->
	<xsl:template match="DynamicMapping">
		<xsl:if test="count(./DynamicMappingItem) &gt; 0">
			<p class="ig-subhead">Dynamic mapping</p>
			<xsl:if test="normalize-space(@variesDatatypePath)!='' and normalize-space(@referencePath)!=''">
				<p class="ig-caption">
					<xsl:text>The datatype of </xsl:text>
					<span class="ig-caption-code"><xsl:value-of select="concat(../@name,'-',@variesDatatypePath)"/></span>
					<xsl:text> is selected by the value of </xsl:text>
					<span class="ig-caption-code"><xsl:value-of select="concat(../@name,'-',@referencePath)"/></span>
				</p>
			</xsl:if>
			<table class="contentTable">
				<thead>
					<tr>
						<th>Reference Value</th>
						<th>Datatype</th>
					</tr>
				</thead>
				<tbody>
					<xsl:for-each select="./DynamicMappingItem">
						<tr>
							<td><xsl:value-of select="@value"/></td>
							<td><xsl:value-of select="@datatype"/></td>
						</tr>
					</xsl:for-each>
				</tbody>
			</table>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
