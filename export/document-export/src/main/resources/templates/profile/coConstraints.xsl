<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- Co-constraint tables of a conformance profile, segment or profile component context. -->
	<xsl:template match="coConstraintsBindingsElement">
		<xsl:if test="count(./coConstraintBindingElement) &gt; 0">
			<p class="ig-subhead">Co-constraints</p>
			<xsl:for-each select="./coConstraintBindingElement">
				<xsl:variable name="context" select="normalize-space(./coConstraintContext)"/>
				<xsl:for-each select="./coConstraintBindingSegmentElement">
				<xsl:variable name="segment" select="normalize-space(./coConstraintSegmentName)"/>
				<xsl:variable name="segmentId" select="normalize-space(./coConstraintSegmentName/@segmentId)"/>
				<xsl:for-each select="./coConstraintTableConditionalBindingElement">
					<div class="ig-coconstraint">
						<!-- the segment flavor at that path, for the links from its fields to this table -->
						<xsl:if test="$segmentId != ''">
							<xsl:attribute name="data-segment">
								<xsl:value-of select="$segmentId"/>
							</xsl:attribute>
						</xsl:if>
						<!-- differential export: the whole table was added or removed -->
						<xsl:if test="./@background">
							<xsl:attribute name="style">
								<xsl:value-of select="concat('background-color:', ./@background)"/>
							</xsl:attribute>
						</xsl:if>
						<p class="ig-caption">
							<xsl:text>Segment </xsl:text>
							<span class="ig-caption-code"><xsl:value-of select="$segment"/></span>
							<xsl:if test="$context!=''">
								<xsl:text> in context </xsl:text>
								<span class="ig-caption-code"><xsl:value-of select="$context"/></span>
							</xsl:if>
						</p>
						<xsl:if test="normalize-space(./coConstraintCondition)!=''">
							<p class="ig-condition">
								<b>Applies when: </b>
								<xsl:choose>
									<xsl:when test="./coConstraintCondition/span">
										<xsl:copy-of select="./coConstraintCondition/span"/>
									</xsl:when>
									<xsl:otherwise>
										<xsl:value-of select="normalize-space(./coConstraintCondition)"/>
									</xsl:otherwise>
								</xsl:choose>
							</p>
						</xsl:if>
						<xsl:copy-of select="./coConstraintsTable/coconstraints/table"/>
					</div>
				</xsl:for-each>
				</xsl:for-each>
			</xsl:for-each>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
