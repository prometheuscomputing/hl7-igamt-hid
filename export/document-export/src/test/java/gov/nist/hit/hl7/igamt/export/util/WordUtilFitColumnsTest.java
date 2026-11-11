package gov.nist.hit.hl7.igamt.export.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;

import org.docx4j.jaxb.Context;
import org.docx4j.wml.Br;
import org.docx4j.wml.CTTrPrBase;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.TblGridCol;
import org.docx4j.wml.Tc;
import org.docx4j.wml.TcPrInner.GridSpan;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.junit.Test;

public class WordUtilFitColumnsTest {

  private static final int TEXT_WIDTH = 12240 - 2 * 1301;
  private final ObjectFactory factory = Context.getWmlObjectFactory();

  private Tbl table(int... grid) {
    Tbl table = factory.createTbl();
    table.setTblGrid(factory.createTblGrid());
    for (int width : grid) {
      TblGridCol column = factory.createTblGridCol();
      column.setW(BigInteger.valueOf(width));
      table.getTblGrid().getGridCol().add(column);
    }
    return table;
  }

  private Tc cell(int span, String... lines) {
    Tc cell = factory.createTc();
    if (span > 1) {
      cell.setTcPr(factory.createTcPr());
      GridSpan gridSpan = factory.createTcPrInnerGridSpan();
      gridSpan.setVal(BigInteger.valueOf(span));
      cell.getTcPr().setGridSpan(gridSpan);
    }
    P paragraph = factory.createP();
    R run = factory.createR();
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        run.getContent().add(new Br());
      }
      Text text = factory.createText();
      text.setValue(lines[i]);
      run.getContent().add(factory.createRT(text));
    }
    paragraph.getContent().add(run);
    cell.getContent().add(paragraph);
    return cell;
  }

  private Tr row(Tbl table, Tc... cells) {
    Tr row = factory.createTr();
    for (Tc cell : cells) {
      row.getContent().add(cell);
    }
    table.getContent().add(row);
    return row;
  }

  private static int width(Tc cell) {
    return cell.getTcPr().getTcW().getW().intValue();
  }

  private static int grid(Tbl table, int index) {
    return table.getTblGrid().getGridCol().get(index).getW().intValue();
  }

  @Test
  public void cellsTakeTheWidthOfTheirColumn() {
    Tbl table = table(646, 1008, 12426);
    Tr row = row(table, cell(1, "Value"), cell(1, "HL70085"), cell(1, "A long description of the code that takes the rest"));
    WordUtil.fitColumns(table, factory);

    assertEquals(TEXT_WIDTH, grid(table, 0) + grid(table, 1) + grid(table, 2));
    assertTrue(grid(table, 0) >= 5 * 105 + 240);
    assertTrue(grid(table, 1) >= 7 * 105 + 240);
    for (int i = 0; i < 3; i++) {
      assertEquals(grid(table, i), width((Tc) row.getContent().get(i)));
    }
  }

  @Test
  public void aSpanningCellTakesTheWidthOfTheColumnsItCovers() {
    Tbl table = table(1000, 3000, 5000);
    row(table, cell(1, "a"), cell(1, "b"), cell(1, "c"));
    Tr spanning = row(table, cell(2, "Averyveryverylongwordthatspans"), cell(1, "c"));
    WordUtil.fitColumns(table, factory);

    assertEquals(grid(table, 0) + grid(table, 1), width((Tc) spanning.getContent().get(0)));
    assertEquals(grid(table, 2), width((Tc) spanning.getContent().get(1)));
    // the word of the spanning cell does not widen the first column
    assertTrue(grid(table, 0) < grid(table, 1));
  }

  @Test
  public void aRowThatSkipsTheFirstColumnsKeepsItsCellsInPlace() {
    Tbl table = table(1000, 3000, 5638);
    row(table, cell(1, "a"), cell(1, "b"), cell(1, "c"));
    Tr shifted = row(table, cell(1, "b"), cell(1, "c"));
    shifted.setTrPr(factory.createTrPr());
    CTTrPrBase.GridBefore before = factory.createCTTrPrBaseGridBefore();
    before.setVal(BigInteger.ONE);
    shifted.getTrPr().getCnfStyleOrDivIdOrGridBefore().add(factory.createCTTrPrBaseGridBefore(before));
    WordUtil.fitColumns(table, factory);

    assertEquals(grid(table, 1), width((Tc) shifted.getContent().get(0)));
    assertEquals(grid(table, 2), width((Tc) shifted.getContent().get(1)));
  }

  @Test
  public void aLineBreakEndsAWord() {
    Tbl broken = table(1000, 1000);
    row(broken, cell(1, "abcdefgh", "ijklmnop"), cell(1, "x"));
    WordUtil.fitColumns(broken, factory);
    Tbl whole = table(1000, 1000);
    row(whole, cell(1, "abcdefghijklmnop"), cell(1, "x"));
    WordUtil.fitColumns(whole, factory);

    // equal natural widths: only a word longer than the share of the column would widen it
    assertEquals(grid(broken, 0), grid(whole, 0));
    Tbl narrow = table(100, 9000);
    row(narrow, cell(1, "abcdefgh", "ijklmnop"), cell(1, "x"));
    WordUtil.fitColumns(narrow, factory);
    assertEquals(8 * 105 + 240, grid(narrow, 0));
  }

  @Test
  public void tablesWithoutAGridAreLeftAlone() {
    Tbl table = factory.createTbl();
    row(table, cell(1, "a"), cell(1, "b"));
    WordUtil.fitColumns(table, factory);
    assertEquals(null, ((Tc) ((Tr) table.getContent().get(0)).getContent().get(0)).getTcPr());
  }
}
