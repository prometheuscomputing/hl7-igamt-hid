<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- Rich text written by the author: rendered as a labelled note. -->
	<xsl:template name="AuthorNotes">
		<xsl:if test="normalize-space(@authorNotes)!=''">
			<div class="ig-note">
				<p class="ig-subhead">Author notes</p>
				<div class="ig-note-body ig-narrative">
					<xsl:value-of disable-output-escaping="yes" select="@authorNotes"/>
				</div>
			</div>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
