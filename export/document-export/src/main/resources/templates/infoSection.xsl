<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:import href="/templates/sectionContent.xsl" />
	<xsl:import href="/templates/profileContent.xsl" />
	<xsl:import href="/templates/profile/datatype/displayDatatype.xsl" />

	<!--
		Heading of a section. The data-* attributes tell ExportHtmlEnhancer what the
		section holds (a registry, a segment, a value set...) so that it can build the
		navigation and the cross references without guessing from the title.
	-->
	<xsl:template name="igHeading">
		<xsl:param name="level" />
		<xsl:param name="id" />
		<xsl:param name="title" />
		<xsl:param name="kind" />
		<xsl:param name="code" />
		<xsl:param name="target" />
		<xsl:param name="master" select="'false'" />
		<xsl:variable name="headingLevel">
			<xsl:choose>
				<xsl:when test="$level &gt; 6">6</xsl:when>
				<xsl:when test="$level &lt; 1">1</xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$level" />
				</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<xsl:element name="{concat('h', $headingLevel)}">
			<xsl:if test="normalize-space($id) != ''">
				<xsl:attribute name="id">
					<xsl:value-of select="$id" />
				</xsl:attribute>
			</xsl:if>
			<xsl:if test="$target = 'docx'">
				<xsl:attribute name="class">
					<xsl:value-of select="concat('Heading', $headingLevel)" />
				</xsl:attribute>
			</xsl:if>
			<xsl:if test="normalize-space($kind) != ''">
				<xsl:attribute name="data-kind">
					<xsl:value-of select="$kind" />
				</xsl:attribute>
			</xsl:if>
			<xsl:if test="normalize-space($code) != ''">
				<xsl:attribute name="data-code">
					<xsl:value-of select="$code" />
				</xsl:attribute>
			</xsl:if>
			<xsl:if test="$master = 'true'">
				<span class="masterDatatypeLabel">MAS</span>
				<xsl:text> - </xsl:text>
			</xsl:if>
			<xsl:value-of select="$title" />
		</xsl:element>
	</xsl:template>

	<xsl:template name="displayInfoSection" mode="disp">
		<xsl:param name="includeTOC" select="'true'"></xsl:param>
		<xsl:param name="inlineConstraint" select="'true'"></xsl:param>
		<xsl:param name="target" select="'html'"></xsl:param>
		<xsl:param name="increment" select="0"></xsl:param>
		<xsl:variable name="h" select="@h + $increment" />

		<xsl:if test="name() = 'Section'">
			<!-- What the section documents: the registry type, or the resource it wraps -->
			<xsl:variable name="resource" select="*[not(self::Section) and not(self::SectionContent)][1]" />
			<xsl:variable name="kind">
				<xsl:choose>
					<xsl:when test="normalize-space(@type) != '' and @type != 'TEXT'">
						<xsl:value-of select="@type" />
					</xsl:when>
					<xsl:when test="$resource">
						<xsl:value-of select="name($resource)" />
					</xsl:when>
				</xsl:choose>
			</xsl:variable>
			<xsl:variable name="code">
				<xsl:choose>
					<xsl:when test="normalize-space($resource/@bindingIdentifier) != ''">
						<xsl:value-of select="$resource/@bindingIdentifier" />
					</xsl:when>
					<xsl:when test="normalize-space($resource/@label) != ''">
						<xsl:value-of select="$resource/@label" />
					</xsl:when>
					<xsl:when test="$resource and normalize-space($resource/@name) != ''">
						<xsl:value-of select="$resource/@name" />
					</xsl:when>
				</xsl:choose>
			</xsl:variable>
			<xsl:call-template name="igHeading">
				<xsl:with-param name="level" select="$h" />
				<xsl:with-param name="id" select="@id" />
				<xsl:with-param name="title" select="@title" />
				<xsl:with-param name="kind" select="$kind" />
				<xsl:with-param name="code" select="$code" />
				<xsl:with-param name="target" select="$target" />
				<xsl:with-param name="master">
					<xsl:choose>
						<xsl:when test="@scope = 'MASTER'">true</xsl:when>
						<xsl:otherwise>false</xsl:otherwise>
					</xsl:choose>
				</xsl:with-param>
			</xsl:call-template>
			<xsl:if test="normalize-space(SectionContent) != ''">
				<div class="ig-narrative">
					<xsl:call-template name="displaySectionContent" />
				</div>
			</xsl:if>

			<xsl:choose>
				<xsl:when test="@type = 'DATATYPEREGISTRY'">
					<xsl:if test="count(Section[Datatype/@isPrimitive = 'true']) &gt; 0">
						<xsl:call-template name="igHeading">
							<xsl:with-param name="level" select="$h + 1" />
							<xsl:with-param name="id" select="'primitiveDatatypes'" />
							<xsl:with-param name="title" select="'Primitive Datatypes'" />
							<xsl:with-param name="kind" select="'DATATYPEGROUP'" />
							<xsl:with-param name="target" select="$target" />
						</xsl:call-template>
						<xsl:for-each select="Section[Datatype/@isPrimitive = 'true']">
							<xsl:sort select="@position" data-type="number"></xsl:sort>
							<xsl:call-template name="displayPrimitiveDatatype">
								<xsl:with-param name="includeTOC" select="$includeTOC" />
								<xsl:with-param name="inlineConstraint" select="$inlineConstraint" />
								<xsl:with-param name="target" select="$target" />
								<xsl:with-param name="increment" select="$increment + 1" />
							</xsl:call-template>
						</xsl:for-each>
					</xsl:if>
					<xsl:if test="count(Section[Datatype/@isPrimitive = 'false']) &gt; 0">
						<xsl:call-template name="igHeading">
							<xsl:with-param name="level" select="$h + 1" />
							<xsl:with-param name="id" select="'complexDatatypes'" />
							<xsl:with-param name="title" select="'Complex Datatypes'" />
							<xsl:with-param name="kind" select="'DATATYPEGROUP'" />
							<xsl:with-param name="target" select="$target" />
						</xsl:call-template>
						<xsl:for-each select="Section[Datatype/@isPrimitive = 'false']">
							<xsl:sort select="@position" data-type="number"></xsl:sort>
							<xsl:call-template name="displayInfoSection">
								<xsl:with-param name="includeTOC" select="$includeTOC" />
								<xsl:with-param name="inlineConstraint" select="$inlineConstraint" />
								<xsl:with-param name="target" select="$target" />
								<xsl:with-param name="increment" select="$increment + 1" />
							</xsl:call-template>
						</xsl:for-each>
					</xsl:if>
				</xsl:when>
				<xsl:otherwise>
					<xsl:call-template name="displayProfileContent">
						<xsl:with-param name="inlineConstraint" select="$inlineConstraint" />
					</xsl:call-template>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
