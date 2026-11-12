<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:template name="globalStyle">
        <xsl:text>.masterDatatypeLabel {color:red;}</xsl:text>
        <xsl:text>
            body {
        </xsl:text>
        <xsl:choose>
            <xsl:when test="$fontFamily!=''">
                <xsl:value-of select="concat('font-family:',$fontFamily)"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:text>font-family: 'Arial Narrow',sans-serif;</xsl:text>
            </xsl:otherwise>
        </xsl:choose>
        <xsl:choose>
            <xsl:when test="$fontSize!=''">
                <xsl:value-of select="concat('font-size:',$fontSize,'pt;')"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:text>font-size: 11pt;</xsl:text>
            </xsl:otherwise>
        </xsl:choose>
        <xsl:text>
            }
        </xsl:text>
        <xsl:text>.contentTable { width:100%; border-spacing: 0px;border-collapse: collapse; border-top-width: 1px; border-right-width: 1px; border-bottom-width: 1px ; border-left-width: 1px; }</xsl:text>
         <xsl:text>.CoConsContentTable { width:100%; border-spacing: 0px;border-collapse: inherit; border-top-width: 1px; border-right-width: 1px; border-bottom-width: 1px ; border-left-width: 1px; }</xsl:text>  
        <xsl:text>.groupTable { width:100%; border-spacing: 1px;border-collapse: collapse; border-top-width: 2px; border-right-width: 1px; border-bottom-width: 1px ; border-left-width: 1px; }</xsl:text>
        <xsl:text>.groupContentTable {border: 1px solid black; width:100%; border-spacing: 0px;border-collapse: collapse; border-top-width: 1px; border-right-width: 1px; border-bottom-width: 1px ; border-left-width: 1px; }</xsl:text>
        <xsl:text>.contentThead { width:100%; background:#F0F0F0; color:#B21A1C; align:center; }</xsl:text>
        <xsl:text>.noBorderTable { border:none }</xsl:text>
        <xsl:text>.ifContentThead { background:rgb(0, 128, 255)}</xsl:text>
        <xsl:text>.ifContent{ background:#b1daff}</xsl:text>
        <xsl:text>img {text-align:center;}</xsl:text>
        <xsl:text>.contentTr { background-color:white; text-decoration:normal; }</xsl:text>
        <xsl:text>.constraintTr { background-color:#E8E8E8; text-decoration:normal; }</xsl:text>
        <xsl:text>.metadata { text-align:center; }</xsl:text>
        <xsl:text>.contentTable td, .contentTable th {border: 1px solid black;}</xsl:text>
        <xsl:text>.requiredDTM{background-color:#6495ed!important;}</xsl:text>
        <xsl:text>.greyCell{background-color:#808080;}</xsl:text>
        <xsl:text>.lightGreyCell{background-color:#F0F0F0;}</xsl:text>
        <xsl:text>.lightBlueCell{background-color:#b5d6fd;}</xsl:text>
        <xsl:text>.lightPinkCell{background-color:#ffe6e6;}</xsl:text>
        <xsl:text>.greenHeader{background-color:rgb(204, 255, 153);}</xsl:text>
   		<xsl:text>.alignCenter{text-align:center;}</xsl:text>
        <xsl:text>.greenContent{background-color:#a7d6a9;}</xsl:text>
        
        <xsl:text>.codeParagraph{display: block;padding: 9.5px;margin: 0 0 10px;line-height: 1.428571429;word-break: break-all;word-wrap: break-word;color: #333;background-color: #f5f5f5;border: 1px solid #ccc;border-radius: 4px;}</xsl:text>
        <xsl:text>.fr-exmsg{display:block;margin:10px 0;padding:0;border:0;background:transparent;}</xsl:text>
        <xsl:text>.fr-exmsg .fr-exmsg-info{border-collapse:collapse;margin:0 0 8px;border:0;background:transparent;}</xsl:text>
        <xsl:text>.fr-exmsg .fr-exmsg-info th,.fr-exmsg .fr-exmsg-info td{border:0;padding:1px 12px 1px 0;text-align:left;vertical-align:baseline;background:transparent;font-weight:normal;}</xsl:text>
        <xsl:text>.fr-exmsg .fr-exmsg-info th{color:#555;font-weight:600;white-space:nowrap;}</xsl:text>
        <xsl:text>.fr-exmsg .fr-exmsg-info a{color:#1565c0;}</xsl:text>
        <xsl:text>.fr-exmsg .fr-exmsg-warning{display:block;margin:0 0 8px;color:#b71c1c;}</xsl:text>
        <xsl:text>.fr-exmsg .fr-exmsg-description{display:block;margin:0 0 8px;line-height:1.5;}</xsl:text>
        <xsl:text>.fr-exmsg .fr-exmsg-body,.fr-exmsg pre.fr-exmsg-body{display:block;margin:0;padding:9.5px;font-family:Consolas,Monaco,monospace;font-size:10pt;line-height:1.4;white-space:pre-wrap;word-break:break-all;color:#333;background:#f5f5f5;border:1px solid #ccc;border-radius:4px;}</xsl:text>
        <xsl:text>.fr-exmsg .fr-exmsg-line{white-space:pre-wrap;}</xsl:text>
        <xsl:text>.fr-exmsg .fr-exmsg-mark{background:#fff176;border-bottom:2px solid #f9a825;color:inherit;}</xsl:text>
    </xsl:template>

</xsl:stylesheet>
