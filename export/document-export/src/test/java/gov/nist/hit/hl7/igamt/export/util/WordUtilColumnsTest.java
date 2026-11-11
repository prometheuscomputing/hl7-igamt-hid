package gov.nist.hit.hl7.igamt.export.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WordUtilColumnsTest {

  private static final int AVAILABLE = 9638;

  private static int sum(int[] values) {
    int total = 0;
    for (int value : values) {
      total += value;
    }
    return total;
  }

  @Test
  public void shortColumnsKeepTheRoomOfTheirLongestWord() {
    // a value set: "Value", "Code System" / "HL70085", and a long description
    int[] widths = WordUtil.columnWidths(new int[] {646, 1008, 12426}, new int[] {5, 7, 14}, AVAILABLE);
    assertEquals(AVAILABLE, sum(widths));
    assertTrue("Value fits on one line: " + widths[0], widths[0] >= 5 * 105 + 240);
    assertTrue("HL70085 fits on one line: " + widths[1], widths[1] >= 7 * 105 + 240);
    assertTrue("the description keeps most of the table: " + widths[2], widths[2] > AVAILABLE * 0.75);
  }

  @Test
  public void columnsThatAlreadyFitKeepTheirProportions() {
    int[] widths = WordUtil.columnWidths(new int[] {2000, 2000, 4000}, new int[] {4, 4, 4}, AVAILABLE);
    assertEquals(AVAILABLE, sum(widths));
    assertEquals(widths[0], widths[1]);
    assertEquals(widths[2], widths[0] * 2, 2);
  }

  @Test
  public void tooManyWideColumnsShareTheTable() {
    int[] natural = new int[14];
    int[] words = new int[14];
    for (int i = 0; i < natural.length; i++) {
      natural[i] = 1000;
      words[i] = 30;
    }
    int[] widths = WordUtil.columnWidths(natural, words, AVAILABLE);
    assertEquals(AVAILABLE, sum(widths));
    for (int width : widths) {
      assertTrue("no column collapses or takes over: " + width, width > 600 && width < 800);
    }
  }

  @Test
  public void veryLongWordsDoNotTakeAColumnForThemselves() {
    int[] widths = WordUtil.columnWidths(new int[] {500, 500}, new int[] {80, 5}, AVAILABLE);
    assertEquals(AVAILABLE, sum(widths));
    assertEquals(widths[0], widths[1], 2);
  }

  @Test
  public void emptyColumnsStillGetACell() {
    int[] widths = WordUtil.columnWidths(new int[] {0, 9000}, new int[] {0, 10}, AVAILABLE);
    assertEquals(AVAILABLE, sum(widths));
    assertTrue(widths[0] >= 240);
  }
}
