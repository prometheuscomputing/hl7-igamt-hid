<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:template match="ExampleMessage">
		<xsl:variable name="title">
			<xsl:choose>
				<xsl:when test="@mode = 'MISSING' or @label = ''">
					<xsl:value-of select="@label" />
				</xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="concat('Example: ', @label)" />
				</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<xsl:element name="div">
			<xsl:attribute name="class">fr-exmsg</xsl:attribute>
			<xsl:choose>
				<xsl:when test="@appHref != ''">
					<xsl:element name="a">
						<xsl:attribute name="class">fr-exmsg-link</xsl:attribute>
						<xsl:attribute name="href">
							<xsl:value-of select="@appHref" />
						</xsl:attribute>
						<xsl:value-of select="$title" />
					</xsl:element>
				</xsl:when>
				<xsl:otherwise>
					<xsl:element name="span">
						<xsl:attribute name="class">fr-exmsg-link</xsl:attribute>
						<xsl:value-of select="$title" />
					</xsl:element>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:if test="@mode = 'SEPARATE_FILES' and @href != ''">
				<xsl:element name="div">
					<xsl:attribute name="class">fr-exmsg-file</xsl:attribute>
					<xsl:value-of select="@href" />
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
