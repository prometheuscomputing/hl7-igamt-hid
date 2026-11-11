/**
 *
 * This software was developed at the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 *
 */
package gov.nist.hit.hl7.igamt.export.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.docx4j.XmlUtils;
import org.docx4j.convert.in.xhtml.FormattingOption;
import org.docx4j.convert.in.xhtml.XHTMLImporterImpl;
import org.docx4j.dml.CTPositiveSize2D;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.jaxb.Context;
import org.docx4j.model.structure.PageSizePaper;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.DocumentSettingsPart;
import org.docx4j.openpackaging.parts.WordprocessingML.FooterPart;
import org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.CTBookmark;
import org.docx4j.wml.CTSettings;
import org.docx4j.wml.CTTblLayoutType;
import org.docx4j.wml.CTTblLook;
import org.docx4j.wml.CTTblPrBase.TblStyle;
import org.docx4j.wml.CTTrPrBase;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.FooterReference;
import org.docx4j.wml.Ftr;
import org.docx4j.wml.Hdr;
import org.docx4j.wml.HdrFtrRef;
import org.docx4j.wml.HeaderReference;
import org.docx4j.wml.JcEnumeration;
import org.docx4j.wml.Numbering;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.STTblLayoutType;
import org.docx4j.wml.SectPr;
import org.docx4j.wml.Styles;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.TblGridCol;
import org.docx4j.wml.TblPr;
import org.docx4j.wml.TblWidth;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.docx4j.wml.TrPr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.hit.hl7.igamt.common.base.domain.DocumentMetadata;
import gov.nist.hit.hl7.igamt.export.domain.ExportedFile;
import gov.nist.hit.hl7.igamt.export.exception.ExportException;

/**
 * Builds the Word export out of the XHTML prepared by {@link ExportHtmlEnhancer}.
 * <p>
 * The document is created from scratch: its styles and heading numbering come from
 * {@code templates/assets/word-styles.xml} and {@code word-numbering.xml}, the content from the
 * XHTML importer. What the importer cannot express is finished here: page set-up, running header
 * and footer, table of contents, table layout.
 */
public class WordUtil {

  private static final Logger logger = LoggerFactory.getLogger(WordUtil.class);

  private static final String ASSETS = "/templates/assets/";
  /** Paragraph {@link ExportHtmlEnhancer} leaves where the table of contents goes. */
  public static final String TOC_MARKER = "IG-TABLE-OF-CONTENTS";
  private static final int TOC_DEPTH = 3;
  private static final String TOC_INSTRUCTION = "TOC \\o \"1-" + TOC_DEPTH + "\" \\h \\z \\u ";

  private static final String REFERENCE_TABLE_STYLE = "IgTable";
  private static final String GRID_TABLE_STYLE = "IgGridTable";
  private static final String FACTS_TABLE_STYLE = "IgFacts";

  // US Letter, in twentieths of a point
  private static final int PAGE_WIDTH = 12240;
  private static final int MARGIN_SIDE = 1301;
  private static final int MARGIN_TOP = 1440;
  private static final int MARGIN_BOTTOM = 1300;
  private static final int MARGIN_HEADER = 680;
  private static final int TEXT_WIDTH = PAGE_WIDTH - 2 * MARGIN_SIDE;
  private static final long EMU_PER_TWIP = 635L;
  private static final long MAX_IMAGE_WIDTH_EMU = TEXT_WIDTH * EMU_PER_TWIP;
  /** Room, in twips, of one character of table text and of the side padding of a cell. */
  private static final int TWIPS_PER_CHARACTER = 105;
  private static final int CELL_PADDING = 240;
  /** Longer words (paths, long identifiers) may break; they do not get a column to themselves. */
  private static final int LONGEST_UNBROKEN_WORD = 16;
  private static final long MAX_COVER_PICTURE_HEIGHT_EMU = 1524000L; // 1.67 in

  // Document defaults of word-styles.xml
  private static final String DEFAULT_FONT = "Calibri";
  private static final String DEFAULT_COLOR = "15212b";
  private static final BigInteger DEFAULT_FONT_SIZE = BigInteger.valueOf(21);

  private static final String WORD_NAMESPACE = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";

  public static ExportedFile convertHtmlToWord(ExportedFile exportedFile, DocumentMetadata metadata,
      Date dateUpdated, String hl7Version) throws ExportException {
    try {
      // The content is the XHTML ExportHtmlEnhancer prepared for the importer
      String html = IOUtils.toString(exportedFile.getContent(), StandardCharsets.UTF_8.name());

      WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.createPackage(PageSizePaper.LETTER, false);
      MainDocumentPart document = wordMLPackage.getMainDocumentPart();
      document.getStyleDefinitionsPart().setJaxbElement((Styles) XmlUtils.unmarshalString(asset("word-styles.xml")));
      NumberingDefinitionsPart numbering = new NumberingDefinitionsPart();
      document.addTargetPart(numbering);
      numbering.setJaxbElement((Numbering) XmlUtils.unmarshalString(asset("word-numbering.xml")));

      String title = metadata != null && metadata.getTitle() != null ? metadata.getTitle().trim() : "";
      String organization = metadata != null && metadata.getOrgName() != null ? metadata.getOrgName().trim() : "";
      setUpPage(wordMLPackage, title, organization);

      XHTMLImporterImpl importer = new XHTMLImporterImpl(wordMLPackage);
      importer.setHyperlinkStyle("Hyperlink");
      importer.setParagraphFormatting(FormattingOption.CLASS_PLUS_OTHER);
      importer.setRunFormatting(FormattingOption.CLASS_PLUS_OTHER);
      importer.setTableFormatting(FormattingOption.CLASS_PLUS_OTHER);
      // A base URL is required even though nothing is resolved against it: without one the importer
      // prints a stack trace for every document
      document.getContent().addAll(importer.convert(html, "file:///"));

      cleanFormatting(document);
      formatTables(document);
      fitImages(document);
      keepCaptionsWithTables(document);
      insertTableOfContents(wordMLPackage);
      requestFieldUpdate(wordMLPackage);

      File tmpFile = File.createTempFile("IgDocument" + UUID.randomUUID().toString(), ".docx");
      try {
        wordMLPackage.save(tmpFile);
        exportedFile.setContent(new java.io.ByteArrayInputStream(FileUtils.readFileToByteArray(tmpFile)));
      } finally {
        FileUtils.deleteQuietly(tmpFile);
      }
      return exportedFile;
    } catch (Docx4JException | IOException | JAXBException e) {
      throw new ExportException(e);
    }
  }

  // ---------------------------------------------------------------- page

  /** Margins, and a header and footer on every page but the cover. */
  private static void setUpPage(WordprocessingMLPackage wordMLPackage, String title, String organization)
      throws Docx4JException, JAXBException {
    ObjectFactory factory = Context.getWmlObjectFactory();
    MainDocumentPart document = wordMLPackage.getMainDocumentPart();
    SectPr sectPr = document.getJaxbElement().getBody().getSectPr();
    if (sectPr == null) {
      sectPr = factory.createSectPr();
      document.getJaxbElement().getBody().setSectPr(sectPr);
    }
    SectPr.PgMar margins = sectPr.getPgMar() != null ? sectPr.getPgMar() : factory.createSectPrPgMar();
    margins.setLeft(BigInteger.valueOf(MARGIN_SIDE));
    margins.setRight(BigInteger.valueOf(MARGIN_SIDE));
    margins.setTop(BigInteger.valueOf(MARGIN_TOP));
    margins.setBottom(BigInteger.valueOf(MARGIN_BOTTOM));
    margins.setHeader(BigInteger.valueOf(MARGIN_HEADER));
    margins.setFooter(BigInteger.valueOf(MARGIN_HEADER));
    sectPr.setPgMar(margins);
    sectPr.setTitlePg(new BooleanDefaultTrue());

    HeaderPart headerPart = new HeaderPart();
    headerPart.setPackage(wordMLPackage);
    headerPart.setJaxbElement((Hdr) XmlUtils.unmarshalString("<w:hdr " + WORD_NAMESPACE + "><w:p><w:pPr><w:pStyle w:val=\"Header\"/></w:pPr>"
        + "<w:r><w:t xml:space=\"preserve\">" + escape(shorten(title, 110)) + "</w:t></w:r></w:p></w:hdr>"));
    Relationship headerRelationship = document.addTargetPart(headerPart);
    HeaderReference headerReference = factory.createHeaderReference();
    headerReference.setId(headerRelationship.getId());
    headerReference.setType(HdrFtrRef.DEFAULT);
    sectPr.getEGHdrFtrReferences().add(headerReference);

    FooterPart footerPart = new FooterPart();
    footerPart.setPackage(wordMLPackage);
    footerPart.setJaxbElement((Ftr) XmlUtils.unmarshalString("<w:ftr " + WORD_NAMESPACE + "><w:p><w:pPr><w:pStyle w:val=\"Footer\"/></w:pPr>"
        + "<w:r><w:t xml:space=\"preserve\">" + escape(shorten(organization, 90)) + "</w:t></w:r><w:r><w:tab/></w:r>"
        + "<w:r><w:t xml:space=\"preserve\">Page </w:t></w:r>" + field("PAGE")
        + "<w:r><w:t xml:space=\"preserve\"> of </w:t></w:r>" + field("NUMPAGES") + "</w:p></w:ftr>"));
    Relationship footerRelationship = document.addTargetPart(footerPart);
    FooterReference footerReference = factory.createFooterReference();
    footerReference.setId(footerRelationship.getId());
    footerReference.setType(HdrFtrRef.DEFAULT);
    sectPr.getEGHdrFtrReferences().add(footerReference);
  }

  private static String field(String instruction) {
    return "<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> " + instruction
        + " </w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:t>1</w:t></w:r>"
        + "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r>";
  }

  private static String shorten(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max - 1).trim() + "…";
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  // ---------------------------------------------------------------- table of contents

  /**
   * Replaces the marker paragraph with a table of contents. It is a regular Word TOC field, so Word
   * refreshes it (and adds the page numbers, which only Word can compute) when it opens the
   * document. Its entries are also written here, so the contents can be read and followed in any
   * other viewer.
   */
  private static void insertTableOfContents(WordprocessingMLPackage wordMLPackage) throws JAXBException {
    List<Object> content = wordMLPackage.getMainDocumentPart().getContent();
    int index = -1;
    for (int i = 0; i < content.size() && index < 0; i++) {
      Object item = unwrap(content.get(i));
      if (item instanceof P && TOC_MARKER.equals(textOf((P) item).trim())) {
        index = i;
      }
    }
    if (index < 0) {
      return;
    }
    content.remove(index);

    List<String[]> entries = new ArrayList<String[]>();
    int[] counters = new int[TOC_DEPTH + 1];
    int generated = 0;
    for (Object item : content) {
      Object object = unwrap(item);
      if (!(object instanceof P)) {
        continue;
      }
      P heading = (P) object;
      int level = headingLevel(heading);
      if (level < 1) {
        continue;
      }
      if (level <= TOC_DEPTH) {
        counters[level]++;
        for (int i = level + 1; i < counters.length; i++) {
          counters[i] = 0;
        }
      }
      String text = textOf(heading).trim();
      if (level > TOC_DEPTH || text.isEmpty()) {
        continue;
      }
      String bookmark = bookmarkOf(heading);
      if (bookmark == null) {
        bookmark = "_IgToc" + (++generated);
        heading.getContent().add(0, XmlUtils.unmarshalString("<w:bookmarkStart " + WORD_NAMESPACE + " w:id=\"" + (900000 + generated)
            + "\" w:name=\"" + bookmark + "\"/>"));
        heading.getContent().add(XmlUtils.unmarshalString("<w:bookmarkEnd " + WORD_NAMESPACE + " w:id=\"" + (900000 + generated) + "\"/>"));
      }
      StringBuilder number = new StringBuilder();
      for (int i = 1; i <= level; i++) {
        number.append(i > 1 ? "." : "").append(Math.max(counters[i], 1));
      }
      // [paragraph properties, runs]: the field opening goes between the two on the first entry
      entries.add(new String[] {"<w:pPr><w:pStyle w:val=\"TOC" + level + "\"/></w:pPr>", "<w:hyperlink w:anchor=\"" + bookmark + "\" w:history=\"1\">"
          + "<w:r><w:t xml:space=\"preserve\">" + number + "</w:t></w:r><w:r><w:tab/></w:r>"
          + "<w:r><w:t xml:space=\"preserve\">" + escape(text) + "</w:t></w:r></w:hyperlink>"});
    }
    if (entries.isEmpty()) {
      return;
    }
    List<Object> toc = new ArrayList<Object>();
    toc.add(XmlUtils.unmarshalString("<w:p " + WORD_NAMESPACE + "><w:pPr><w:pStyle w:val=\"TOCHeading\"/></w:pPr>"
        + "<w:r><w:t>Contents</w:t></w:r></w:p>"));
    for (int i = 0; i < entries.size(); i++) {
      String fieldStart = i > 0 ? "" : "<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> "
          + TOC_INSTRUCTION + "</w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"separate\"/></w:r>";
      toc.add(XmlUtils.unmarshalString("<w:p " + WORD_NAMESPACE + ">" + entries.get(i)[0] + fieldStart + entries.get(i)[1]
          + "</w:p>"));
    }
    toc.add(XmlUtils.unmarshalString("<w:p " + WORD_NAMESPACE + "><w:pPr><w:spacing w:after=\"0\"/></w:pPr>"
        + "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>"));
    content.addAll(index, toc);
  }

  /** 1 to 6 for a paragraph in a heading style, 0 otherwise. */
  private static int headingLevel(P paragraph) {
    PPr properties = paragraph.getPPr();
    if (properties == null || properties.getPStyle() == null || properties.getPStyle().getVal() == null) {
      return 0;
    }
    String style = properties.getPStyle().getVal();
    if (style.length() == 8 && style.startsWith("Heading") && Character.isDigit(style.charAt(7))) {
      return style.charAt(7) - '0';
    }
    return 0;
  }

  private static String bookmarkOf(P paragraph) {
    for (Object node : paragraph.getContent()) {
      Object object = unwrap(node);
      if (object instanceof CTBookmark && ((CTBookmark) object).getName() != null && !((CTBookmark) object).getName().isEmpty()) {
        return ((CTBookmark) object).getName();
      }
    }
    return null;
  }

  private static void requestFieldUpdate(WordprocessingMLPackage wordMLPackage) throws Docx4JException {
    MainDocumentPart document = wordMLPackage.getMainDocumentPart();
    DocumentSettingsPart settingsPart = document.getDocumentSettingsPart();
    if (settingsPart == null) {
      settingsPart = new DocumentSettingsPart();
      settingsPart.setJaxbElement(Context.getWmlObjectFactory().createCTSettings());
      document.addTargetPart(settingsPart);
    }
    CTSettings settings = settingsPart.getJaxbElement();
    settings.setUpdateFields(new BooleanDefaultTrue());
  }

  // ---------------------------------------------------------------- formatting

  /**
   * The importer writes the computed CSS of every paragraph and run as direct formatting, which
   * overrides the Word styles and repeats the document defaults thousands of times. Drop what
   * merely restates the defaults, and everything on paragraphs that have a style of their own.
   */
  private static void cleanFormatting(MainDocumentPart document) {
    for (Object node : getAllElementFromObject(document, P.class)) {
      P paragraph = (P) node;
      PPr properties = paragraph.getPPr();
      boolean styled = false;
      if (properties != null) {
        styled = properties.getPStyle() != null && isExportStyle(properties.getPStyle().getVal());
        if (properties.getJc() != null && properties.getJc().getVal() == JcEnumeration.LEFT) {
          properties.setJc(null);
        }
        if (properties.getInd() != null && isZero(properties.getInd().getLeft()) && isZero(properties.getInd().getRight())
            && isZero(properties.getInd().getFirstLine()) && isZero(properties.getInd().getHanging())) {
          properties.setInd(null);
        }
        if (styled) {
          properties.setSpacing(null);
          properties.setInd(null);
          properties.setJc(null);
        }
      }
      for (Object runNode : getAllElementFromObject(paragraph, R.class)) {
        RPr run = ((R) runNode).getRPr();
        if (run == null) {
          continue;
        }
        if (run.getRFonts() != null && (styled || DEFAULT_FONT.equalsIgnoreCase(run.getRFonts().getAscii()))) {
          run.setRFonts(null);
        }
        if (run.getColor() != null && (styled || DEFAULT_COLOR.equalsIgnoreCase(run.getColor().getVal()))) {
          run.setColor(null);
        }
        if (run.getB() != null && (styled || !run.getB().isVal())) {
          run.setB(null);
        }
        if (run.getSz() != null && (styled || DEFAULT_FONT_SIZE.equals(run.getSz().getVal()))) {
          run.setSz(null);
        }
        if (styled) {
          run.setSzCs(null);
        }
      }
    }
  }

  private static boolean isZero(BigInteger value) {
    return value == null || value.signum() == 0;
  }

  /** Styles defined in word-styles.xml that fully describe their paragraphs. */
  private static boolean isExportStyle(String style) {
    return style != null && (style.startsWith("Heading") || style.startsWith("Ig") || style.equals("Title") || style.equals("Subtitle"));
  }

  // ---------------------------------------------------------------- tables

  /**
   * Hands the look of the tables over to their Word style: full width, borders and header from the
   * style, header row repeated on each page, rows kept whole.
   */
  private static void formatTables(MainDocumentPart document) {
    ObjectFactory factory = Context.getWmlObjectFactory();
    for (Object node : getAllElementFromObject(document, Tbl.class)) {
      Tbl table = (Tbl) node;
      TblPr properties = table.getTblPr();
      if (properties == null) {
        properties = factory.createTblPr();
        table.setTblPr(properties);
      }
      String style = properties.getTblStyle() != null ? properties.getTblStyle().getVal() : null;
      if (style == null || !(style.equals(REFERENCE_TABLE_STYLE) || style.equals(GRID_TABLE_STYLE) || style.equals(FACTS_TABLE_STYLE))) {
        style = GRID_TABLE_STYLE;
        TblStyle tableStyle = factory.createCTTblPrBaseTblStyle();
        tableStyle.setVal(style);
        properties.setTblStyle(tableStyle);
      }
      boolean reference = style.equals(REFERENCE_TABLE_STYLE);

      TblWidth width = factory.createTblWidth();
      width.setType("pct");
      width.setW(BigInteger.valueOf(5000));
      properties.setTblW(width);
      properties.setTblInd(null);
      properties.setTblBorders(null);
      properties.setTblCellMar(null);
      CTTblLayoutType layout = factory.createCTTblLayoutType();
      layout.setType(STTblLayoutType.AUTOFIT);
      properties.setTblLayout(layout);
      CTTblLook look = factory.createCTTblLook();
      look.setVal(reference ? "0620" : "0600");
      properties.setTblLook(look);

      boolean first = true;
      for (Object rowNode : table.getContent()) {
        Object rowObject = unwrap(rowNode);
        if (!(rowObject instanceof Tr)) {
          continue;
        }
        Tr row = (Tr) rowObject;
        TrPr rowProperties = row.getTrPr();
        if (rowProperties == null) {
          rowProperties = factory.createTrPr();
          row.setTrPr(rowProperties);
        }
        rowProperties.getCnfStyleOrDivIdOrGridBefore().add(factory.createCTTrPrBaseCantSplit(new BooleanDefaultTrue()));
        if (first && reference) {
          rowProperties.getCnfStyleOrDivIdOrGridBefore().add(factory.createCTTrPrBaseTblHeader(new BooleanDefaultTrue()));
        }
        first = false;
        for (Object cellNode : row.getContent()) {
          Object cellObject = unwrap(cellNode);
          if (cellObject instanceof Tc && ((Tc) cellObject).getTcPr() != null) {
            ((Tc) cellObject).getTcPr().setTcBorders(null);
            ((Tc) cellObject).getTcPr().setTcMar(null);
          }
        }
      }
      fitColumns(table, factory);
    }
  }

  /**
   * The importer sizes the columns in proportion to their longest content, so one long description
   * leaves "Value" or "HL70085" too little room to stay on one line. Every column gets at least the
   * room of its longest word; the others give that room up.
   */
  static void fitColumns(Tbl table, ObjectFactory factory) {
    if (table.getTblGrid() == null || table.getTblGrid().getGridCol().size() < 2) {
      return;
    }
    List<TblGridCol> grid = table.getTblGrid().getGridCol();
    int[] natural = new int[grid.size()];
    int[] longestWord = new int[grid.size()];
    for (int i = 0; i < natural.length; i++) {
      natural[i] = grid.get(i).getW() != null ? Math.max(1, grid.get(i).getW().intValue()) : 1;
    }
    for (Tr row : rowsOf(table)) {
      int column = gridBeforeOf(row);
      for (Tc cell : cellsOf(row)) {
        int span = spanOf(cell);
        if (span == 1 && column < longestWord.length) {
          for (Object paragraph : getAllElementFromObject(cell, P.class)) {
            longestWord[column] = Math.max(longestWord[column], longestWordOf((P) paragraph));
          }
        }
        column += span;
      }
    }
    int[] widths = columnWidths(natural, longestWord, TEXT_WIDTH);
    for (int i = 0; i < widths.length; i++) {
      grid.get(i).setW(BigInteger.valueOf(widths[i]));
    }
    for (Tr row : rowsOf(table)) {
      int column = gridBeforeOf(row);
      for (Tc cell : cellsOf(row)) {
        int span = spanOf(cell);
        int total = 0;
        for (int i = column; i < column + span && i < widths.length; i++) {
          total += widths[i];
        }
        if (total > 0) {
          if (cell.getTcPr() == null) {
            cell.setTcPr(factory.createTcPr());
          }
          TblWidth cellWidth = factory.createTblWidth();
          cellWidth.setType("dxa");
          cellWidth.setW(BigInteger.valueOf(total));
          cell.getTcPr().setTcW(cellWidth);
        }
        column += span;
      }
    }
  }

  /**
   * Shares {@code available} twips between columns in proportion to their natural widths, after
   * giving each one the room of its longest word (in characters).
   */
  static int[] columnWidths(int[] natural, int[] longestWord, int available) {
    int count = natural.length;
    double[] minimum = new double[count];
    double minimumTotal = 0;
    double naturalTotal = 0;
    for (int i = 0; i < count; i++) {
      minimum[i] = Math.min(longestWord[i], LONGEST_UNBROKEN_WORD) * TWIPS_PER_CHARACTER + CELL_PADDING;
      minimumTotal += minimum[i];
      naturalTotal += Math.max(1, natural[i]);
    }
    if (minimumTotal > available) {
      for (int i = 0; i < count; i++) {
        minimum[i] = minimum[i] * available / minimumTotal;
      }
    }
    double[] width = new double[count];
    double missing = 0;
    double spare = 0;
    for (int i = 0; i < count; i++) {
      width[i] = Math.max(1, natural[i]) * available / naturalTotal;
      if (width[i] < minimum[i]) {
        missing += minimum[i] - width[i];
        width[i] = minimum[i];
      } else {
        spare += width[i] - minimum[i];
      }
    }
    int[] result = new int[count];
    int used = 0;
    for (int i = 0; i < count; i++) {
      if (missing > 0 && spare > 0 && width[i] > minimum[i]) {
        width[i] -= (width[i] - minimum[i]) * Math.min(1, missing / spare);
      }
      result[i] = (int) Math.round(width[i]);
      used += result[i];
    }
    int widest = 0;
    for (int i = 1; i < count; i++) {
      widest = result[i] > result[widest] ? i : widest;
    }
    result[widest] += available - used;
    return result;
  }

  private static List<Tr> rowsOf(Tbl table) {
    List<Tr> rows = new ArrayList<Tr>();
    for (Object node : table.getContent()) {
      if (unwrap(node) instanceof Tr) {
        rows.add((Tr) unwrap(node));
      }
    }
    return rows;
  }

  private static List<Tc> cellsOf(Tr row) {
    List<Tc> cells = new ArrayList<Tc>();
    for (Object node : row.getContent()) {
      if (unwrap(node) instanceof Tc) {
        cells.add((Tc) unwrap(node));
      }
    }
    return cells;
  }

  /** Grid columns a row leaves empty before its first cell. */
  private static int gridBeforeOf(Tr row) {
    if (row.getTrPr() != null) {
      for (Object property : row.getTrPr().getCnfStyleOrDivIdOrGridBefore()) {
        Object value = unwrap(property);
        if (value instanceof CTTrPrBase.GridBefore && ((CTTrPrBase.GridBefore) value).getVal() != null) {
          return Math.max(0, ((CTTrPrBase.GridBefore) value).getVal().intValue());
        }
      }
    }
    return 0;
  }

  /** Length of the longest word of a paragraph; a line break ends a word like a space does. */
  private static int longestWordOf(P paragraph) {
    StringBuilder text = new StringBuilder();
    for (Object run : getAllElementFromObject(paragraph, R.class)) {
      for (Object node : ((R) run).getContent()) {
        Object value = unwrap(node);
        text.append(value instanceof Text ? ((Text) value).getValue() : " ");
      }
    }
    int longest = 0;
    for (String word : text.toString().trim().split("\\s+")) {
      longest = Math.max(longest, word.length());
    }
    return longest;
  }

  private static int spanOf(Tc cell) {
    return cell.getTcPr() != null && cell.getTcPr().getGridSpan() != null && cell.getTcPr().getGridSpan().getVal() != null
        ? Math.max(1, cell.getTcPr().getGridSpan().getVal().intValue()) : 1;
  }

  /** A title or a line of facts right above a table stays on the page of the table. */
  private static void keepCaptionsWithTables(MainDocumentPart document) {
    List<Object> content = document.getContent();
    for (int i = 1; i < content.size(); i++) {
      if (unwrap(content.get(i)) instanceof Tbl && unwrap(content.get(i - 1)) instanceof P) {
        P paragraph = (P) unwrap(content.get(i - 1));
        PPr properties = paragraph.getPPr();
        if (properties == null) {
          properties = Context.getWmlObjectFactory().createPPr();
          paragraph.setPPr(properties);
        }
        properties.setKeepNext(new BooleanDefaultTrue());
      }
    }
  }

  // ---------------------------------------------------------------- images

  /** Pictures wider than the text area are scaled down; the cover picture is kept to logo size. */
  private static void fitImages(MainDocumentPart document) {
    boolean cover = true;
    for (Object node : getAllElementFromObject(document, Drawing.class)) {
      Drawing drawing = (Drawing) node;
      if (drawing.getAnchorOrInline().isEmpty() || !(drawing.getAnchorOrInline().get(0) instanceof Inline)) {
        continue;
      }
      Inline inline = (Inline) drawing.getAnchorOrInline().get(0);
      CTPositiveSize2D size = inline.getExtent();
      if (size == null || size.getCx() <= 0 || size.getCy() <= 0) {
        continue;
      }
      double scale = 1d;
      if (size.getCx() > MAX_IMAGE_WIDTH_EMU) {
        scale = (double) MAX_IMAGE_WIDTH_EMU / size.getCx();
      }
      if (cover && isCoverPicture(document, drawing) && size.getCy() * scale > MAX_COVER_PICTURE_HEIGHT_EMU) {
        scale = (double) MAX_COVER_PICTURE_HEIGHT_EMU / size.getCy();
      }
      cover = false;
      if (scale < 1d) {
        long width = Math.round(size.getCx() * scale);
        long height = Math.round(size.getCy() * scale);
        size.setCx(width);
        size.setCy(height);
        if (inline.getGraphic() != null && inline.getGraphic().getGraphicData() != null
            && inline.getGraphic().getGraphicData().getPic() != null
            && inline.getGraphic().getGraphicData().getPic().getSpPr() != null
            && inline.getGraphic().getGraphicData().getPic().getSpPr().getXfrm() != null
            && inline.getGraphic().getGraphicData().getPic().getSpPr().getXfrm().getExt() != null) {
          inline.getGraphic().getGraphicData().getPic().getSpPr().getXfrm().getExt().setCx(width);
          inline.getGraphic().getGraphicData().getPic().getSpPr().getXfrm().getExt().setCy(height);
        }
      }
    }
  }

  private static boolean isCoverPicture(MainDocumentPart document, Drawing drawing) {
    for (Object item : document.getContent()) {
      Object paragraph = unwrap(item);
      if (paragraph instanceof P && getAllElementFromObject(paragraph, Drawing.class).contains(drawing)) {
        PPr properties = ((P) paragraph).getPPr();
        return properties != null && properties.getPStyle() != null && "IgCoverPicture".equals(properties.getPStyle().getVal());
      }
    }
    return false;
  }

  // ---------------------------------------------------------------- helpers

  private static Object unwrap(Object object) {
    return object instanceof JAXBElement ? ((JAXBElement<?>) object).getValue() : object;
  }

  private static String textOf(P paragraph) {
    StringBuilder text = new StringBuilder();
    for (Object node : getAllElementFromObject(paragraph, Text.class)) {
      text.append(((Text) node).getValue());
    }
    return text.toString();
  }

  public static List<Object> getAllElementFromObject(Object obj, Class<?> toSearch) {
    List<Object> result = new ArrayList<Object>();
    obj = unwrap(obj);
    if (obj == null) {
      return result;
    }
    if (obj.getClass().equals(toSearch)) {
      result.add(obj);
    } else if (obj instanceof ContentAccessor) {
      for (Object child : ((ContentAccessor) obj).getContent()) {
        result.addAll(getAllElementFromObject(child, toSearch));
      }
    }
    return result;
  }

  private static String asset(String name) throws IOException {
    try (InputStream stream = WordUtil.class.getResourceAsStream(ASSETS + name)) {
      if (stream == null) {
        throw new IOException("Missing export asset " + ASSETS + name);
      }
      return IOUtils.toString(stream, StandardCharsets.UTF_8.name());
    }
  }
}
