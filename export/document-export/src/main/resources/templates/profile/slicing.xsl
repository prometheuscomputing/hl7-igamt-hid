<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- Slicing: which flavor applies to which occurrence (or under which assertion) of an element. -->
	<xsl:template name="Slicings">
		<xsl:if test="count(./Slicings/Slicing) &gt; 0">
			<p class="ig-subhead">Slicing</p>
			<xsl:for-each select="./Slicings/Slicing">
				<p class="ig-caption">
					<span class="ig-caption-code"><xsl:value-of select="concat(@path,'.',@name)"/></span>
					<xsl:text> sliced by </xsl:text>
					<xsl:choose>
						<xsl:when test="@type='OCCURRENCE'">occurrence</xsl:when>
						<xsl:otherwise>assertion</xsl:otherwise>
					</xsl:choose>
					<xsl:if test="normalize-space(@element)!=''">
						<xsl:text>, default flavor </xsl:text>
						<span class="ig-caption-code"><xsl:value-of select="@element"/></span>
					</xsl:if>
				</p>
				<table class="contentTable">
					<thead>
						<tr>
							<th>
								<xsl:choose>
									<xsl:when test="@type='OCCURRENCE'">Occurrence</xsl:when>
									<xsl:otherwise>Assertion</xsl:otherwise>
								</xsl:choose>
							</th>
							<th>Flavor</th>
							<th>Comment</th>
						</tr>
					</thead>
					<tbody>
						<xsl:for-each select="./Slice">
							<tr>
								<td>
									<xsl:choose>
										<xsl:when test="../@type='OCCURRENCE'"><xsl:value-of select="@occurence"/></xsl:when>
										<xsl:otherwise><xsl:value-of select="@assertion"/></xsl:otherwise>
									</xsl:choose>
								</td>
								<td><xsl:value-of select="@flavor"/></td>
								<td><xsl:value-of select="@comment"/></td>
							</tr>
						</xsl:for-each>
					</tbody>
				</table>
			</xsl:for-each>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
