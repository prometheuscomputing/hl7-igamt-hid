<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!--
		An example message attached to a message section. A widget pasted in rich text is written by
		ExampleMessageSerializationServiceImpl.rewriteNarrative: the two must produce the same block.
		data-text-href is the text file of a snapshot page; the Word export links to it, because its
		download carries the text files and not the pages.
	-->
	<xsl:template match="ExampleMessage">
		<xsl:element name="div">
			<xsl:attribute name="class">
				<xsl:text>fr-exmsg</xsl:text>
				<xsl:if test="@mode = 'MISSING'"><xsl:text> fr-exmsg-missing</xsl:text></xsl:if>
			</xsl:attribute>
			<xsl:if test="@mode != 'MISSING'">
				<xsl:element name="table">
					<xsl:attribute name="class">fr-exmsg-info</xsl:attribute>
					<xsl:element name="tr">
						<xsl:element name="th">Message</xsl:element>
						<xsl:element name="td">
							<xsl:choose>
								<xsl:when test="@messagePage != ''">
									<xsl:element name="a">
										<xsl:attribute name="href"><xsl:value-of select="@messagePage" /></xsl:attribute>
										<xsl:if test="@messageFile != ''">
											<xsl:attribute name="data-text-href"><xsl:value-of select="@messageFile" /></xsl:attribute>
										</xsl:if>
										<xsl:value-of select="@name" />
									</xsl:element>
								</xsl:when>
								<xsl:otherwise>
									<xsl:value-of select="@name" />
								</xsl:otherwise>
							</xsl:choose>
						</xsl:element>
					</xsl:element>
					<xsl:if test="@snippetName != ''">
						<xsl:element name="tr">
							<xsl:element name="th">Snippet</xsl:element>
							<xsl:element name="td">
								<xsl:choose>
									<xsl:when test="@snippetPage != ''">
										<xsl:element name="a">
											<xsl:attribute name="href"><xsl:value-of select="@snippetPage" /></xsl:attribute>
											<xsl:if test="@snippetFile != ''">
												<xsl:attribute name="data-text-href"><xsl:value-of select="@snippetFile" /></xsl:attribute>
											</xsl:if>
											<xsl:value-of select="@snippetName" />
										</xsl:element>
									</xsl:when>
									<xsl:otherwise>
										<xsl:value-of select="@snippetName" />
									</xsl:otherwise>
								</xsl:choose>
							</xsl:element>
						</xsl:element>
					</xsl:if>
					<xsl:if test="@igamtHref != ''">
						<xsl:element name="tr">
							<xsl:element name="th">IGAMT</xsl:element>
							<xsl:element name="td">
								<xsl:element name="a">
									<xsl:attribute name="href"><xsl:value-of select="@igamtHref" /></xsl:attribute>
									<xsl:text>Open in IGAMT</xsl:text>
								</xsl:element>
							</xsl:element>
						</xsl:element>
					</xsl:if>
				</xsl:element>
			</xsl:if>
			<xsl:if test="Description != ''">
				<xsl:element name="div">
					<xsl:attribute name="class">fr-exmsg-description</xsl:attribute>
					<xsl:value-of disable-output-escaping="yes" select="Description" />
				</xsl:element>
			</xsl:if>
			<xsl:for-each select="Warning">
				<xsl:element name="div">
					<xsl:attribute name="class">fr-exmsg-warning</xsl:attribute>
					<xsl:value-of select="." />
				</xsl:element>
			</xsl:for-each>
			<xsl:if test="Content">
				<xsl:element name="div">
					<xsl:attribute name="class">fr-exmsg-body</xsl:attribute>
					<xsl:for-each select="Content/Line">
						<xsl:element name="div">
							<xsl:attribute name="class">fr-exmsg-line</xsl:attribute>
							<xsl:choose>
								<xsl:when test="*">
									<xsl:for-each select="*">
										<xsl:choose>
											<xsl:when test="name() = 'Mark'">
												<xsl:element name="span">
													<xsl:attribute name="class">fr-exmsg-mark</xsl:attribute>
													<xsl:value-of select="." />
												</xsl:element>
											</xsl:when>
											<xsl:otherwise>
												<xsl:value-of select="." />
											</xsl:otherwise>
										</xsl:choose>
									</xsl:for-each>
								</xsl:when>
								<xsl:when test="string-length(.) = 0">
									<xsl:text>&#160;</xsl:text>
								</xsl:when>
								<xsl:otherwise>
									<xsl:value-of select="." />
								</xsl:otherwise>
							</xsl:choose>
						</xsl:element>
					</xsl:for-each>
				</xsl:element>
			</xsl:if>
		</xsl:element>
	</xsl:template>
</xsl:stylesheet>
