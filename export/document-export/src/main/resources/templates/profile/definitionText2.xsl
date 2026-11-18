<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- Definition texts attached to elements of a resource, one row per element. -->
	<xsl:template name="DefinitionText2">
		<xsl:if test="count(DefinitionTexts/DefinitionText) &gt; 0">
			<p class="ig-subhead">Definition texts</p>
			<table class="contentTable ig-annotations">
				<thead>
					<tr>
						<th>Location</th>
						<th>Definition</th>
					</tr>
				</thead>
				<tbody>
					<xsl:for-each select="DefinitionTexts/DefinitionText">
						<xsl:sort select="@position" data-type="number" order="ascending"/>
						<tr>
							<td><xsl:value-of select="@name"/></td>
							<td><xsl:value-of disable-output-escaping="yes" select="@text"/></td>
						</tr>
					</xsl:for-each>
				</tbody>
			</table>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
