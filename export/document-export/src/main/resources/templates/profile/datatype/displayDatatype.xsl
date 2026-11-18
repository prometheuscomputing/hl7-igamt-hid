<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:import href="/templates/profile/resource/preDef.xsl" />
	<xsl:import href="/templates/profile/resource/postDef.xsl" />
	<xsl:import href="/templates/profile/resource/versionDisplay.xsl" />

	<xsl:template name="displayPrimitiveDatatype">
		<xsl:param name="includeTOC" select="'true'"></xsl:param>
		<xsl:param name="inlineConstraint" select="'true'"></xsl:param>
		<xsl:param name="target" select="'html'"></xsl:param>
		<xsl:param name="increment" select="0"></xsl:param>
		<xsl:variable name="h" select="@h + $increment" />
		
		<xsl:for-each select="Datatype">
			<xsl:call-template name="igHeading">
				<xsl:with-param name="level" select="$h" />
				<xsl:with-param name="id" select="@id" />
				<xsl:with-param name="title">
					<xsl:value-of select="@label" />
					<xsl:if test="normalize-space(../@description) != ''">
						<xsl:value-of select="concat('-', ../@description)" />
					</xsl:if>
					<xsl:if test="normalize-space(@domainCompatibilityVersion) != ''">
						<xsl:value-of select="concat(' (', @domainCompatibilityVersion, ')')" />
					</xsl:if>
				</xsl:with-param>
				<xsl:with-param name="kind" select="'Datatype'" />
				<xsl:with-param name="code" select="@label" />
				<xsl:with-param name="target" select="$target" />
				<xsl:with-param name="master">
					<xsl:choose>
						<xsl:when test="@scope = 'MASTER'">true</xsl:when>
						<xsl:otherwise>false</xsl:otherwise>
					</xsl:choose>
				</xsl:with-param>
			</xsl:call-template>
			<!-- in a data type library, the facts of the flavor come first -->
			<xsl:if test="$exportTypeVar != 'ig'">
				<xsl:call-template name="LibraryFlavorFacts" />
			</xsl:if>
			<xsl:if test="@name = 'DTM' or @name = 'DT' ">
			<!-- <xsl:apply-templates select="DateTimeDatatype"/> -->
			<xsl:element name="span">
				<xsl:element name="b">
					Data Type Definition
				</xsl:element>
			</xsl:element>
			<xsl:element name="span">
				<xsl:element name="table">
					<xsl:attribute name="class">
                    <xsl:text>contentTable</xsl:text>
                </xsl:attribute>
					<xsl:element name="col">
						<xsl:attribute name="width">
                        <xsl:text>5%</xsl:text>
                    </xsl:attribute>
					</xsl:element>
					<xsl:element name="col">
						<xsl:attribute name="width">
                        <xsl:text>40%</xsl:text>
                    </xsl:attribute>
					</xsl:element>
					<xsl:element name="col">
						<xsl:attribute name="width">
                        <xsl:text>40%</xsl:text>
                    </xsl:attribute>
					</xsl:element>
					<xsl:element name="col">
						<xsl:attribute name="width">
                        <xsl:text>15%</xsl:text>
                    </xsl:attribute>
					</xsl:element>
					<xsl:element name="thead">
						<xsl:attribute name="class">
                        <xsl:text>contentThead</xsl:text>
                    </xsl:attribute>
						<xsl:element name="tr">
							<xsl:element name="th">
								<xsl:text>#</xsl:text>
							</xsl:element>
							<xsl:element name="th">
								<xsl:text>Value</xsl:text>
							</xsl:element>
							<xsl:element name="th">
								<xsl:text>Format</xsl:text>
							</xsl:element>
							<xsl:element name="th">
								<xsl:text>Usage</xsl:text>
							</xsl:element>

						</xsl:element>
					</xsl:element>
					<xsl:element name="tbody">
						<xsl:for-each select="DateTimeComponentDefinition">
							<xsl:sort select="@position" data-type="number"></xsl:sort>
							<xsl:element name="tr">
								<xsl:element name="td">
									<xsl:value-of select="@position" />
								</xsl:element>
								<xsl:element name="td">
									<xsl:value-of select="@name" />
								</xsl:element>
								<xsl:element name="td">
									<xsl:choose>
										<xsl:when test="@format != ''">
											<xsl:value-of select="@format" />
										</xsl:when>
										<xsl:otherwise>
											<xsl:attribute name="class">
                                            <xsl:text>greyCell</xsl:text>
                                        </xsl:attribute>
										</xsl:otherwise>
									</xsl:choose>
								</xsl:element>
								<xsl:element name="td">
									<xsl:value-of select="@usage" />
								</xsl:element>

							</xsl:element>
						</xsl:for-each>
					</xsl:element>
				</xsl:element>
			</xsl:element>
		</xsl:if>
			
			<xsl:call-template name="PreDef" />
			<xsl:call-template name="PostDef" />


		</xsl:for-each>

	</xsl:template>
</xsl:stylesheet>
