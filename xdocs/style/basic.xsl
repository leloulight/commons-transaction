<?xml version="1.0" encoding="ISO-8859-1"?>
<!-- Temporary Stylesheet for Catalina Developer Documentation -->
<!-- $Id: basic.xsl,v 1.2 2004/12/19 03:11:06 ozeigermann Exp $ -->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="1.0">

  <!-- Output method -->
  <xsl:output method="html" encoding="iso-8859-1" indent="yes"/>

  <!-- Defined parameters (overrideable) -->
  <xsl:param    name="relative-path"    select="'.'"/>
  <xsl:param    name="void-image"       select="'/images/void.gif'"/>

  <!-- Defined variables -->
  <xsl:variable name="body-bg"          select="'#ffffff'"/>
  <xsl:variable name="body-fg"          select="'#000000'"/>
  <xsl:variable name="body-link"        select="'#525D76'"/>
  <xsl:variable name="banner-bg"        select="'#525D76'"/>
  <xsl:variable name="banner-fg"        select="'#ffffff'"/>
  <xsl:variable name="sub-banner-bg"    select="'#828DA6'"/>
  <xsl:variable name="sub-banner-fg"    select="'#ffffff'"/>
  <xsl:variable name="source-color"     select="'#023264'"/>
  <xsl:variable name="attributes-color" select="'#023264'"/>
  <xsl:variable name="table-th-bg"      select="'#039acc'"/>
  <xsl:variable name="table-td-bg"      select="'#ffffff'"/>

  <!-- Process an entire document into an HTML page -->
  <xsl:template match="document">
    <xsl:variable name="project"
                select="document('../../project.xml')/project"/>
    <html>
    <head>
    <title><xsl:value-of select="$project/title"/> - <xsl:value-of select="properties/title"/></title>
    <xsl:for-each select="properties/author">
      <xsl:variable name="name">
        <xsl:value-of select="."/>
      </xsl:variable>
      <xsl:variable name="email">
        <xsl:value-of select="@email"/>
      </xsl:variable>
      <meta name="author" value="{$name}"/>
      <meta name="email" value="{$email}"/>
    </xsl:for-each>
    </head>

    <body bgcolor="{$body-bg}" text="{$body-fg}" link="{$body-link}"
          alink="{$body-link}" vlink="{$body-link}">

          <xsl:apply-templates select="body"/>
    </body>
    </html>

  </xsl:template>


  <!-- Process a menu for the navigation bar -->
  <xsl:template match="menu">
    <p><strong><xsl:value-of select="@name"/></strong></p>
    <ul>
      <xsl:apply-templates select="item"/>
    </ul>
  </xsl:template>


  <!-- Process a menu item for the navigation bar -->
  <xsl:template match="item">
    <xsl:variable name="href">
      <xsl:value-of select="@href"/>
    </xsl:variable>
    <li><a href="{$href}"><xsl:value-of select="@name"/></a></li>
  </xsl:template>


  <!-- Process a documentation section -->
  <xsl:template match="section">
    <xsl:variable name="name">
      <xsl:value-of select="@name"/>
    </xsl:variable>
    <table border="0" cellspacing="0" cellpadding="2" width="100%">
      <!-- Section heading -->
      <tr><td bgcolor="{$banner-bg}">
          <font color="{$banner-fg}" face="arial,helvetica.sanserif">
          <a name="{$name}">
          <strong><xsl:value-of select="@name"/></strong></a></font>
      </td></tr>
      <!-- Section body -->
      <tr><td><blockquote>
        <xsl:if test="not(@split-subsections)">
          <xsl:apply-templates/>
        </xsl:if>
        <xsl:if test="@split-subsections">
          <xsl:apply-templates select="p" />
          <xsl:call-template name="split-subsections" />
        </xsl:if>
      </blockquote></td></tr>
    </table>
  </xsl:template>

  <!-- display subsection in 2 columns -->
  <xsl:template name="split-subsections">
    <xsl:variable name="middle">
      <xsl:value-of select="ceiling((count(subsection) div 2))"/>
    </xsl:variable>

    <table width="100%"><tr><td valign="top" width="45%">

    <xsl:for-each select="subsection">
      <xsl:if test="position() &lt;= $middle">
        <xsl:apply-templates select="." />
      </xsl:if>
    </xsl:for-each>

    </td><td valign="top" width="10%">&#160;</td><td valign="top" width="*">

    <xsl:for-each select="subsection">
      <xsl:if test="position() &gt; $middle">
        <xsl:apply-templates select="." />
      </xsl:if>
    </xsl:for-each>

    </td></tr></table>

  </xsl:template>

  <!-- Process a documentation subsection -->
  <xsl:template match="subsection">
    <xsl:variable name="name">
      <xsl:value-of select="@name"/>
    </xsl:variable>
    <table border="0" cellspacing="0" cellpadding="2" width="100%">
      <!-- Subsection heading -->
      <tr><td bgcolor="{$sub-banner-bg}">
          <font color="{$sub-banner-fg}" face="arial,helvetica.sanserif">
          <a name="{$name}">
          <strong><xsl:value-of select="@name"/></strong></a></font>
      </td></tr>
      <!-- Subsection body -->
      <tr><td><blockquote>
        <xsl:apply-templates/>
      </blockquote></td></tr>
    </table>
  </xsl:template>

  <!-- create an index of the subsections of the current section -->
  <xsl:template match="section/subsection-index">
    <table><th><td><xsl:value-of select="@title" /></td></th>
    <tr><td>
    <ul>
    <xsl:for-each select="../subsection">
      <li>
          <a><xsl:attribute name="href">#<xsl:value-of select="@name" /></xsl:attribute><xsl:value-of select="@name" /></a>
      </li>  
    </xsl:for-each>
    </ul>
    </td></tr></table><br/>
  </xsl:template>

  <!-- process tables -->
  <xsl:template match="table">
    <table border="0" cellpadding="2" cellspacing="2">
      <xsl:apply-templates/>
    </table>
  </xsl:template>

  <!-- specially process td tags -->
  <xsl:template match="td">
    <td bgcolor="{$table-td-bg}" valign="top" align="left">
        <xsl:if test="@colspan"><xsl:attribute name="colspan"><xsl:value-of select="@colspan"/></xsl:attribute></xsl:if>
        <xsl:if test="@rowspan"><xsl:attribute name="rowspan"><xsl:value-of select="@rowspan"/></xsl:attribute></xsl:if>
        <xsl:if test="@width"><xsl:attribute name="width"><xsl:value-of select="@width"/></xsl:attribute></xsl:if>
        <font color="#000000" size="-1" face="arial,helvetica,sanserif">
            <xsl:apply-templates/>
        </font>
    </td>
  </xsl:template>
  
  <!-- handle th -->
  <xsl:template match="th">
    <td bgcolor="{$table-th-bg}" valign="top">
        <xsl:if test="@colspan"><xsl:attribute name="colspan"><xsl:value-of select="@colspan"/></xsl:attribute></xsl:if>
        <xsl:if test="@rowspan"><xsl:attribute name="rowspan"><xsl:value-of select="@rowspan"/></xsl:attribute></xsl:if>
        <font color="#ffffff" size="-1" face="arial,helvetica,sanserif">
          <b><xsl:apply-templates /></b>
        </font>
    </td>
  </xsl:template>
  
  <!-- Process a source code example -->
  <xsl:template match="source">
    <xsl:variable name="void">
      <xsl:value-of select="$relative-path"/><xsl:value-of select="$void-image"/>
    </xsl:variable>
    <div align="left">
      <table cellspacing="4" cellpadding="0" border="0">
        <tr>
          <td bgcolor="{$source-color}" width="1" height="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
          <td bgcolor="{$source-color}" height="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
          <td bgcolor="{$source-color}" width="1" height="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
        </tr>
        <tr>
          <td bgcolor="{$source-color}" width="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
          <td bgcolor="#ffffff" height="1"><pre>
            <xsl:value-of select="."/>
          </pre></td>
          <td bgcolor="{$source-color}" width="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
        </tr>
        <tr>
          <td bgcolor="{$source-color}" width="1" height="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
          <td bgcolor="{$source-color}" height="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
          <td bgcolor="{$source-color}" width="1" height="1">
            <img src="{$void}" width="1" height="1" vspace="0" hspace="0" border="0"/>
          </td>
        </tr>
      </table>
    </div>
  </xsl:template>


  <!-- Process an attributes list with nested attribute elements -->
  <xsl:template match="attributes">
    <table border="1" cellpadding="5">
      <tr>
        <th width="15%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Attribute</font>
        </th>
        <th width="85%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Description</font>
        </th>
        <xsl:for-each select="attribute">
        <tr>
          <td align="left" valign="center">
            <xsl:if test="@required = 'true'">
              <strong><code><xsl:value-of select="@name"/></code></strong>
            </xsl:if>
            <xsl:if test="@required != 'true'">
              <code><xsl:value-of select="@name"/></code>
            </xsl:if>
          </td>
          <td align="left" valign="center">
            <xsl:apply-templates/>
          </td>
        </tr>
        </xsl:for-each>
      </tr>
    </table>
  </xsl:template>


  <!-- Process everything else by just passing it through -->
  <xsl:template match="*|@*">
    <xsl:copy>
      <xsl:apply-templates select="@*|*|text()"/>
    </xsl:copy>
  </xsl:template>

  <!-- Changelog related tags -->
  <xsl:template match="changelog">
    <table border="0" cellpadding="2" cellspacing="2">
      <xsl:apply-templates/>
    </table>
  </xsl:template>

  <xsl:template name="displayDate">
    <xsl:if test="@date"><xsl:value-of select="@date"/>: </xsl:if>
  </xsl:template>

  <xsl:template name="displayAuthor">
    <xsl:if test="@author">(<xsl:value-of select="@author"/>)</xsl:if>
  </xsl:template>

  <xsl:template match="changelog/add">
    <tr>
      <td style="vertical-align: top;"><img alt="add" class="icon" src="images/add.gif"/></td>
      <td><xsl:call-template name="displayDate"/><xsl:apply-templates/><xsl:call-template name="displayAuthor"/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/update">
    <tr>
      <td style="vertical-align: top;"><img alt="update" class="icon" src="images/update.gif"/></td>
      <td><xsl:call-template name="displayDate"/><xsl:apply-templates/><xsl:call-template name="displayAuthor"/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/design">
    <tr>
      <td style="vertical-align: top;"><img alt="design" class="icon" src="images/design.gif"/></td>
      <td><xsl:call-template name="displayDate"/><xsl:apply-templates/><xsl:call-template name="displayAuthor"/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/docs">
    <tr>
      <td style="vertical-align: top;"><img alt="docs" class="icon" src="images/docs.gif"/></td>
      <td><xsl:call-template name="displayDate"/><xsl:apply-templates/><xsl:call-template name="displayAuthor"/></td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/fix">
    <tr>
      <td style="vertical-align: top;"><img alt="fix" class="icon" src="images/fix.gif"/></td>
      <td>
    <xsl:if test="@bugzilla-id"> [
    <xsl:element name="a">
    <xsl:attribute name="href">http://nagoya.apache.org/bugzilla/show_bug.cgi?id=<xsl:value-of select="@bugzilla-id"/></xsl:attribute>
    bug #<xsl:value-of select="@bugzilla-id"/>
    </xsl:element>
    ]</xsl:if>
   <xsl:call-template name="displayDate"/><xsl:apply-templates/><xsl:call-template name="displayAuthor"/>
    </td>
    </tr>
  </xsl:template>

  <xsl:template match="changelog/scode">
    <tr>
      <td style="vertical-align: top;"><img alt="code" class="icon" src="images/code.gif"/></td>
      <td><xsl:call-template name="displayDate"/><xsl:apply-templates/><xsl:call-template name="displayAuthor"/></td>
    </tr>
  </xsl:template>

  <!-- Process an attributes list with nested attribute elements -->
  <xsl:template match="status">
    <table border="1" cellpadding="5">
      <tr>
        <th width="15%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Priority</font>
        </th>
        <th width="50%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Action Item</font>
        </th>
        <th width="25%" bgcolor="{$attributes-color}">
          <font color="#ffffff">Volunteers</font>
        </th>
        <xsl:for-each select="item">
        <tr>
          <td align="left" valign="center">
            <xsl:value-of select="@priority"/>
          </td>
          <td align="left" valign="center">
            <xsl:apply-templates/>
          </td>
          <td align="left" valign="center">
            <xsl:value-of select="@owner"/>
          </td>
        </tr>
        </xsl:for-each>
      </tr>
    </table>
  </xsl:template>

  <!-- Process a tag library section -->
  <xsl:template match="taglib">
    <table border="0" cellspacing="5" cellpadding="5" width="100%">
      <tr><td bgcolor="{$banner-bg}">
        <font color="{$banner-fg}" face="arial,helvetica,sanserif" size="+1">
          <strong><xsl:value-of select="display-name"/></strong>
        </font>
      </td></tr>
      <tr><td>
        <blockquote>
          <xsl:apply-templates select="info"/>
        </blockquote>
      </td></tr>
      <tr><td>
        <blockquote>
          <table border="0" cellpadding="2" cellspacing="2">
            <tr>
              <td bgcolor="{$table-th-bg}" width="15%" valign="top">
                <font color="#ffffff" size="-1" face="arial,helvetica,sanserif"><b>Tag Name</b></font>
              </td>
              <td bgcolor="{$table-th-bg}" valign="top">
                <font color="#ffffff" size="-1" face="arial,helvetica,sanserif"><b>Description</b></font>
              </td>
            </tr>
            <xsl:for-each select="tag">
              <tr>
                <td bgcolor="{$table-td-bg}" align="left" valign="top">
                  <font color="#000000" size="-1" face="arial,helvetica,sanserif">
                    <xsl:variable name="name">
                      <xsl:value-of select="name"/>
                    </xsl:variable>
                    <a href="#{$name}"><xsl:value-of select="name"/></a>
                  </font>
                </td>
                <td bgcolor="{$table-td-bg}" valign="top" align="left">
                  <font color="#000000" size="-1" face="arial,helvetica,sanserif">
                    <xsl:value-of select="summary"/>
                  </font>
                </td>
              </tr>
            </xsl:for-each>
          </table>
        </blockquote>
      </td></tr>
    </table>
    <xsl:apply-templates select="tag"/>
  </xsl:template>

  <!-- Process an individual tag -->
  <xsl:template match="tag">
    <xsl:variable name="name">
      <xsl:value-of select="name"/>
    </xsl:variable>
    <a name="{$name}"></a>
    <table border="0" cellspacing="2" cellpadding="2">
      <tr><td bgcolor="{$banner-bg}">
        <font color="{$banner-fg}" face="arial,helvetica,sanserif">
          <strong><xsl:value-of select="name"/></strong> -
          <xsl:value-of select="summary"/>
        </font>
      </td></tr>
      <tr><td>
        <blockquote>
          <xsl:apply-templates select="info"/>
        </blockquote>
      </td></tr>
      <tr><td>
        <blockquote>
          <table border="0" cellspacing="2" cellpadding="2">
            <tr>
              <td bgcolor="{$table-th-bg}" width="15%" valign="top">
                <font color="#ffffff" size="-1" face="arial,helvetica,sanserif"><b>Attribute Name</b></font>
              </td>
              <td bgcolor="{$table-th-bg}" valign="top">
                <font color="#ffffff" size="-1" face="arial,helvetica,sanserif"><b>Description</b></font>
              </td>
            </tr>
            <xsl:for-each select="attribute">
              <tr>
                <td bgcolor="{$table-td-bg}" align="left" valign="top">
                  <font color="#000000" size="-1" face="arial,helvetica,sanserif">
                    <xsl:value-of select="name"/>
                  </font>
                </td>
                <td bgcolor="{$table-td-bg}" align="left" valign="top">
                  <font color="#000000" size="-1" face="arial,helvetica,sanserif">
                    <xsl:apply-templates select="info"/>
                    <xsl:variable name="required">
                      <xsl:value-of select="required"/>
                    </xsl:variable>
                    <xsl:if test="required='true'">
                      [Required]
                    </xsl:if>
                    <xsl:if test="rtexprvalue='true'">
                      [RT Expr]
                    </xsl:if>
                  </font>
                </td>
              </tr>
            </xsl:for-each>
          </table>
        </blockquote>
      </td></tr>
    </table>
  </xsl:template>

</xsl:stylesheet>
