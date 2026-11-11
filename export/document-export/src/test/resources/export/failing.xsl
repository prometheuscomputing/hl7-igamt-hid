<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="html"/>
	<xsl:template match="/">
		<html><body>
			<p>before</p>
			<xsl:message terminate="yes">stylesheet failure on purpose</xsl:message>
			<p>after</p>
		</body></html>
	</xsl:template>
</xsl:stylesheet>
