<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!--
		Turns the serialized document into the structural HTML of the export.
		Presentation is not decided here: ExportHtmlEnhancer takes this output,
		builds the navigation, decorates the tables and adds the stylesheet
		that fits the target format (templates/assets).
	-->
	<xsl:import href="/templates/section.xsl" />
	<xsl:import href="/templates/profileContent.xsl" />
	<xsl:import href="/templates/metadata.xsl" />
	<xsl:param name="inlineConstraints" select="'false'" />
	<xsl:param name="includeTOC" select="'false'" />
	<xsl:param name="targetFormat" select="'html'" />
	<xsl:param name="exportType" select="'ig'" />
	<xsl:variable name="documentTargetFormat" select="$targetFormat" />
	<xsl:param name="documentTitle" select="'Implementation Guide'" />
	<xsl:param name="imageLogo" select="''" />
	<xsl:variable name="inlineConstraintsVar" select="$inlineConstraints" />
	<xsl:variable name="imageLogoSrc" select="$imageLogo" />
	<xsl:variable name="exportTypeVar" select="$exportType" />


	<!-- Custom export font -->
	<xsl:param name="userFontFamily" />
	<xsl:variable name="fontFamily" select="$userFontFamily" />
	<xsl:param name="userFontSize" />
	<xsl:variable name="fontSize" select="$userFontSize" />

	<xsl:param name="appCurrentVersion" />
	<xsl:variable name="appVersion" select="$appCurrentVersion" />
	<!-- Parameters for the column filter -->
	<!-- Messages -->
	<xsl:param name="messageColumnPath" select="'true'" />
	<xsl:variable name="columnDisplay.message.path" select="$messageColumnPath" />
	<xsl:param name="messageColumnElementName" select="'true'" />
	<xsl:variable name="columnDisplay.message.name" select="$messageColumnElementName" />
	<xsl:param name="messageColumnUsage" select="'true'" />
	<xsl:variable name="columnDisplay.message.usage" select="$messageColumnUsage" />
	<xsl:param name="messageColumnCardinality" select="'true'" />
	<xsl:variable name="columnDisplay.message.cardinality"
		select="$messageColumnCardinality" />
	<xsl:param name="messageColumnSegment" select="'true'" />
	<xsl:variable name="columnDisplay.message.segment" select="$messageColumnSegment" />
	<xsl:param name="messageColumnFlavor" select="'true'" />
	<xsl:variable name="columnDisplay.message.flavor" select="$messageColumnFlavor" />
	<xsl:param name="messageColumnComment" select="'true'" />
	<xsl:variable name="columnDisplay.message.comment" select="$messageColumnComment" />
	<!-- Composite Profiles -->
	<xsl:param name="compositeProfileColumnName" select="'true'" />
	<xsl:variable name="columnDisplay.compositeProfile.name"
		select="$compositeProfileColumnName" />
	<xsl:param name="compositeProfileColumnUsage" select="'true'" />
	<xsl:variable name="columnDisplay.compositeProfile.usage"
		select="$compositeProfileColumnUsage" />
	<xsl:param name="compositeProfileColumnCardinality" select="'true'" />
	<xsl:variable name="columnDisplay.compositeProfile.cardinality"
		select="$compositeProfileColumnCardinality" />
	<xsl:param name="compositeProfileColumnSegment" select="'true'" />
	<xsl:variable name="columnDisplay.compositeProfile.segment"
		select="$compositeProfileColumnSegment" />
	<xsl:param name="compositeProfileColumnFlavor" select="'true'" />
	<xsl:variable name="columnDisplay.compositeProfile.flavor"
		select="$compositeProfileColumnFlavor" />
	<xsl:param name="compositeProfileColumnComment" select="'true'" />
	<xsl:variable name="columnDisplay.compositeProfile.comment"
		select="$compositeProfileColumnComment" />
	<!-- Profile Components -->
	<xsl:param name="profileComponentColumnName" select="'true'" />
	<xsl:variable name="columnDisplay.profileComponent.name"
		select="$profileComponentColumnName" />
	<xsl:param name="profileComponentColumnUsage" select="'true'" />
	<xsl:variable name="columnDisplay.profileComponent.usage"
		select="$profileComponentColumnUsage" />
	<xsl:param name="profileComponentColumnCardinality" select="'true'" />
	<xsl:variable name="columnDisplay.profileComponent.cardinality"
		select="$profileComponentColumnCardinality" />
	<xsl:param name="profileComponentColumnLength" select="'true'" />
	<xsl:variable name="columnDisplay.profileComponent.length"
		select="$profileComponentColumnLength" />
	<xsl:param name="profileComponentColumnConformanceLength"
		select="'true'" />
	<xsl:variable name="columnDisplay.profileComponent.conformanceLength"
		select="$profileComponentColumnConformanceLength" />
	<xsl:param name="profileComponentColumnDataType" select="'true'" />
	<xsl:variable name="columnDisplay.profileComponent.dataType"
		select="$profileComponentColumnDataType" />
	<xsl:param name="profileComponentColumnValueSet" select="'true'" />
	<xsl:variable name="columnDisplay.profileComponent.valueSet"
		select="$profileComponentColumnValueSet" />
	<xsl:param name="profileComponentColumnDefinitionText"
		select="'true'" />
	<xsl:variable name="columnDisplay.profileComponent.definitionText"
		select="$profileComponentColumnDefinitionText" />
	<xsl:param name="profileComponentColumnComment" select="'true'" />
	<xsl:variable name="columnDisplay.profileComponent.comment"
		select="$profileComponentColumnComment" />
	<!-- Segments -->
	<xsl:param name="segmentColumnName" select="'true'" />
	<xsl:variable name="columnDisplay.segment.name" select="$segmentColumnName" />
	<xsl:param name="segmentColumnUsage" select="'true'" />
	<xsl:variable name="columnDisplay.segment.usage" select="$segmentColumnUsage" />
	<xsl:param name="segmentColumnCardinality" select="'true'" />
	<xsl:variable name="columnDisplay.segment.cardinality"
		select="$segmentColumnCardinality" />
	<xsl:param name="segmentColumnLength" select="'true'" />
	<xsl:variable name="columnDisplay.segment.length" select="$segmentColumnLength" />
	<xsl:param name="segmentColumnConformanceLength" select="'true'" />
	<xsl:variable name="columnDisplay.segment.conformanceLength"
		select="$segmentColumnConformanceLength" />
	<xsl:param name="segmentColumnDataType" select="'true'" />
	<xsl:variable name="columnDisplay.segment.dataType"
		select="$segmentColumnDataType" />
	<xsl:param name="segmentColumnValueSet" select="'true'" />
	<xsl:variable name="columnDisplay.segment.valueSet"
		select="$segmentColumnValueSet" />
	<xsl:param name="segmentColumnDefinitionText" select="'true'" />
	<xsl:variable name="columnDisplay.segment.definitionText"
		select="$segmentColumnDefinitionText" />
	<xsl:param name="segmentColumnComment" select="'true'" />
	<xsl:variable name="columnDisplay.segment.comment" select="$segmentColumnComment" />
	<!-- DataTypes -->
	<xsl:param name="dataTypeColumnName" select="'true'" />
	<xsl:variable name="columnDisplay.dataType.name" select="$dataTypeColumnName" />
	<xsl:param name="dataTypeColumnUsage" select="'true'" />
	<xsl:variable name="columnDisplay.dataType.usage" select="$dataTypeColumnUsage" />
	<xsl:param name="dataTypeColumnLength" select="'true'" />
	<xsl:variable name="columnDisplay.dataType.length" select="$dataTypeColumnLength" />
	<xsl:param name="dataTypeColumnConformanceLength" select="'true'" />
	<xsl:variable name="columnDisplay.dataType.conformanceLength"
		select="$dataTypeColumnConformanceLength" />
	<xsl:param name="dataTypeColumnDataType" select="'true'" />
	<xsl:variable name="columnDisplay.dataType.dataType"
		select="$dataTypeColumnDataType" />
	<xsl:param name="dataTypeColumnValueSet" select="'true'" />
	<xsl:variable name="columnDisplay.dataType.valueSet"
		select="$dataTypeColumnValueSet" />
	<xsl:param name="dataTypeColumnComment" select="'true'" />
	<xsl:variable name="columnDisplay.dataType.comment"
		select="$dataTypeColumnComment" />
	<!-- Value Sets -->
	<xsl:param name="valueSetColumnValue" select="'true'" />
	<xsl:variable name="columnDisplay.valueSet.value" select="$valueSetColumnValue" />
	<xsl:param name="valueSetColumnUsage" select="'true'" />
	<xsl:variable name="columnDisplay.valueSet.usage" select="$valueSetColumnUsage" />
	<xsl:param name="valueSetColumnCodeSystem" select="'true'" />
	<xsl:variable name="columnDisplay.valueSet.codeSystem"
		select="$valueSetColumnCodeSystem" />
	<xsl:param name="valueSetColumnDescription" select="'true'" />
	<xsl:variable name="columnDisplay.valueSet.description"
		select="$valueSetColumnDescription" />
	<xsl:param name="valueSetColumnComment" select="'true'" />
	<xsl:variable name="columnDisplay.valueSet.comment"
		select="$valueSetColumnComment" />
	<xsl:param name="valueSetMetadataStability" select="'true'" />
	<xsl:variable name="valueSetMetadata.stability" select="$valueSetMetadataStability" />
	<xsl:param name="valueSetMetadataExtensibility" select="'true'" />
	<xsl:variable name="valueSetMetadata.extensibility"
		select="$valueSetMetadataExtensibility" />
	<xsl:param name="valueSetMetadataContentDefinition" select="'true'" />
	<xsl:variable name="valueSetMetadata.contentDefinition"
		select="$valueSetMetadataContentDefinition" />
	<xsl:param name="valueSetMetadataOid" select="'true'" />
	<xsl:variable name="valueSetMetadata.oid" select="$valueSetMetadataOid" />
	<xsl:param name="valueSetMetadataType" select="'true'" />
	<xsl:variable name="valueSetMetadata.type" select="$valueSetMetadataType" />
	<xsl:param name="datatypeMetadataDisplay" select="'false'" />
	<xsl:variable name="datatypeMetadata.display" select="$datatypeMetadataDisplay" />
	<xsl:param name="datatypeMetadataHL7Version" select="'false'" />
	<xsl:variable name="datatypeMetadata.hl7Version" select="$datatypeMetadataHL7Version" />
	<xsl:param name="datatypeMetadataPublicationDate" select="'false'" />
	<xsl:variable name="datatypeMetadata.publicationDate"
		select="$datatypeMetadataPublicationDate" />
	<xsl:param name="datatypeMetadataPublicationVersion" select="'false'" />
	<xsl:variable name="datatypeMetadata.publicationVersion"
		select="$datatypeMetadataPublicationVersion" />
	<xsl:param name="datatypeMetadataScope" select="'false'" />
	<xsl:variable name="datatypeMetadata.scope" select="$datatypeMetadataScope" />

	<xsl:param name="segmentMetadataDisplay" select="'false'" />
	<xsl:variable name="segmentMetadata.display" select="$segmentMetadataDisplay" />
	<xsl:param name="segmentMetadataHL7Version" select="'false'" />
	<xsl:variable name="segmentMetadata.hl7Version" select="$segmentMetadataHL7Version" />
	<xsl:param name="segmentMetadataPublicationDate" select="'false'" />
	<xsl:variable name="segmentMetadata.publicationDate"
		select="$segmentMetadataPublicationDate" />
	<xsl:param name="segmentMetadataPublicationVersion" select="'false'" />
	<xsl:variable name="segmentMetadata.publicationVersion"
		select="$segmentMetadataPublicationVersion" />
	<xsl:param name="segmentMetadataScope" select="'false'" />
	<xsl:variable name="segmentMetadata.scope" select="$segmentMetadataScope" />

	<xsl:param name="messageMetadataDisplay" select="'false'" />
	<xsl:variable name="messageMetadata.display" select="$messageMetadataDisplay" />
	<xsl:param name="messageMetadataHL7Version" select="'false'" />
	<xsl:variable name="messageMetadata.hl7Version" select="$messageMetadataHL7Version" />
	<xsl:param name="messageMetadataPublicationDate" select="'false'" />
	<xsl:variable name="messageMetadata.publicationDate"
		select="$messageMetadataPublicationDate" />
	<xsl:param name="messageMetadataPublicationVersion" select="'false'" />
	<xsl:variable name="messageMetadata.publicationVersion"
		select="$messageMetadataPublicationVersion" />
	<xsl:param name="messageMetadataScope" select="'false'" />
	<xsl:variable name="messageMetadata.scope" select="$messageMetadataScope" />

	<xsl:param name="compositeProfileMetadataDisplay" select="'false'" />
	<xsl:variable name="compositeProfileMetadata.display"
		select="$compositeProfileMetadataDisplay" />
	<xsl:param name="compositeProfileMetadataHL7Version" select="'false'" />
	<xsl:variable name="compositeProfileMetadata.hl7Version"
		select="$compositeProfileMetadataHL7Version" />
	<xsl:param name="compositeProfileMetadataPublicationDate"
		select="'false'" />
	<xsl:variable name="compositeProfileMetadata.publicationDate"
		select="$compositeProfileMetadataPublicationDate" />
	<xsl:param name="compositeProfileMetadataPublicationVersion"
		select="'false'" />
	<xsl:variable name="compositeProfileMetadata.publicationVersion"
		select="$compositeProfileMetadataPublicationVersion" />
	<xsl:param name="compositeProfileMetadataScope" select="'false'" />
	<xsl:variable name="compositeProfileMetadata.scope"
		select="$compositeProfileMetadataScope" />

	<xsl:output method="html" encoding="UTF-8" indent="no" />

	<xsl:template match="/">
		<html lang="en">
			<head>
				<title>
					<xsl:value-of select="$documentTitle" />
				</title>
			</head>
			<body>
				<xsl:attribute name="data-format">
					<xsl:value-of select="$targetFormat" />
				</xsl:attribute>
				<xsl:attribute name="data-export-type">
					<xsl:value-of select="$exportType" />
				</xsl:attribute>
				<xsl:attribute name="data-font-family">
					<xsl:value-of select="$userFontFamily" />
				</xsl:attribute>
				<xsl:attribute name="data-font-size">
					<xsl:value-of select="$userFontSize" />
				</xsl:attribute>
				<xsl:apply-templates select="Document/Metadata" />
				<div id="ig-content">
					<xsl:if test="$targetFormat = 'docx'">
						<xsl:call-template name="displayProfileContent">
							<xsl:with-param name="inlineConstraint" select="$inlineConstraints" />
						</xsl:call-template>
					</xsl:if>
					<xsl:call-template name="displaySection">
						<xsl:with-param name="includeTOC" select="$includeTOC" />
						<xsl:with-param name="inlineConstraint" select="$inlineConstraints" />
						<xsl:with-param name="target" select="$targetFormat" />
					</xsl:call-template>
				</div>
			</body>
		</html>
	</xsl:template>
</xsl:stylesheet>
