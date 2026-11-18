<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- Rich text written by the author: rendered as a labelled note. -->
	<xsl:template name="UsageNotes">
		<xsl:if test="normalize-space(@usageNotes)!=''">
			<div class="ig-note">
				<p class="ig-subhead">Usage notes</p>
				<div class="ig-note-body ig-narrative">
					<xsl:value-of disable-output-escaping="yes" select="@usageNotes"/>
				</div>
			</div>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
