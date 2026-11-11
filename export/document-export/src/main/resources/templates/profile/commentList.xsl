<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- Author comments attached to elements of a resource, one row per element. -->
	<xsl:template name="CommentList">
		<xsl:if test="count(Comments/Comment) &gt; 0">
			<p class="ig-subhead">Comments</p>
			<table class="contentTable ig-annotations">
				<thead>
					<tr>
						<th>Location</th>
						<th>Comment</th>
					</tr>
				</thead>
				<tbody>
					<xsl:for-each select="Comments/Comment">
						<xsl:sort select="@position" data-type="number" order="ascending"/>
						<tr>
							<td><xsl:value-of select="@name"/></td>
							<td><xsl:value-of disable-output-escaping="yes" select="@description"/></td>
						</tr>
					</xsl:for-each>
				</tbody>
			</table>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
