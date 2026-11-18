<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<!-- Cover of the HTML export: title block and the facts about the document -->
	<xsl:template match="Metadata">
		<div id="ig-cover" class="ig-cover">
			<xsl:if test="normalize-space(CoverPicture) != ''">
				<xsl:variable name="pictureType">
					<xsl:choose>
						<!-- only a plain media type may be written into the tag below -->
						<xsl:when test="normalize-space(CoverPicture/@contentType) != '' and translate(CoverPicture/@contentType, 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/+.-', '') = ''">
							<xsl:value-of select="CoverPicture/@contentType" />
						</xsl:when>
						<xsl:otherwise>image/png</xsl:otherwise>
					</xsl:choose>
				</xsl:variable>
				<!-- Written as text: Xalan's HTML serializer fails on attribute values of this size. -->
				<xsl:text disable-output-escaping="yes">&lt;img class="ig-cover-picture" alt="" src="data:</xsl:text>
				<xsl:value-of select="$pictureType" />
				<xsl:text>;base64,</xsl:text>
				<xsl:value-of select="translate(normalize-space(CoverPicture), ' &quot;&lt;&gt;&amp;', '')" />
				<xsl:text disable-output-escaping="yes">"&gt;</xsl:text>
			</xsl:if>
			<p class="ig-title">
				<xsl:choose>
					<xsl:when test="normalize-space(@title) != ''">
						<xsl:value-of select="@title" />
					</xsl:when>
					<xsl:otherwise>
						<xsl:value-of select="$documentTitle" />
					</xsl:otherwise>
				</xsl:choose>
			</p>
			<xsl:if test="normalize-space(@subTitle) != ''">
				<p class="ig-subtitle">
					<xsl:value-of select="@subTitle" />
				</p>
			</xsl:if>
			<dl class="ig-facts">
				<xsl:call-template name="coverFact">
					<xsl:with-param name="label" select="'Organization'" />
					<xsl:with-param name="value" select="@orgName" />
				</xsl:call-template>
				<xsl:call-template name="coverFact">
					<xsl:with-param name="label" select="'Document version'" />
					<xsl:with-param name="value" select="@documentVersion" />
				</xsl:call-template>
				<xsl:call-template name="coverFact">
					<xsl:with-param name="label" select="'HL7 version'" />
					<xsl:with-param name="value" select="@hl7Version" />
				</xsl:call-template>
				<xsl:call-template name="coverFact">
					<xsl:with-param name="label" select="'Publication version'" />
					<xsl:with-param name="value" select="@publicationVersion" />
				</xsl:call-template>
				<xsl:call-template name="coverFact">
					<xsl:with-param name="label" select="'Publication date'" />
					<xsl:with-param name="value" select="@publicationDate" />
				</xsl:call-template>
				<xsl:call-template name="coverFact">
					<xsl:with-param name="label" select="'Specification'" />
					<xsl:with-param name="value" select="@description" />
				</xsl:call-template>
				<xsl:call-template name="coverFact">
					<xsl:with-param name="label" select="'Identifier'" />
					<xsl:with-param name="value" select="@identifier" />
				</xsl:call-template>
				<xsl:call-template name="coverFact">
					<xsl:with-param name="label" select="'Topics'" />
					<xsl:with-param name="value" select="@topics" />
				</xsl:call-template>
				<xsl:for-each select="customAttributes/customAttribute">
					<xsl:call-template name="coverFact">
						<xsl:with-param name="label" select="@name" />
						<xsl:with-param name="value" select="@value" />
					</xsl:call-template>
				</xsl:for-each>
			</dl>
		</div>
	</xsl:template>

	<xsl:template name="coverFact">
		<xsl:param name="label" />
		<xsl:param name="value" />
		<xsl:if test="normalize-space($value) != '' and normalize-space($label) != ''">
			<div class="ig-fact">
				<dt>
					<xsl:value-of select="$label" />
				</dt>
				<dd>
					<xsl:value-of select="$value" />
				</dd>
			</div>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
