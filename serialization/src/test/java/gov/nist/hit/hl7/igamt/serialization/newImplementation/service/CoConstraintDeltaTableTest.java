package gov.nist.hit.hl7.igamt.serialization.newImplementation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import gov.nist.hit.hl7.igamt.coconstraints.model.CoConstraint;
import gov.nist.hit.hl7.igamt.coconstraints.model.CoConstraintCardinality;
import gov.nist.hit.hl7.igamt.coconstraints.model.CoConstraintCell;
import gov.nist.hit.hl7.igamt.coconstraints.model.CoConstraintGroupBinding;
import gov.nist.hit.hl7.igamt.coconstraints.model.CoConstraintGroupBindingContained;
import gov.nist.hit.hl7.igamt.coconstraints.model.CoConstraintHeader;
import gov.nist.hit.hl7.igamt.coconstraints.model.CoConstraintRequirement;
import gov.nist.hit.hl7.igamt.coconstraints.model.CoConstraintUsage;
import gov.nist.hit.hl7.igamt.coconstraints.model.CodeCell;
import gov.nist.hit.hl7.igamt.coconstraints.model.ColumnType;
import gov.nist.hit.hl7.igamt.coconstraints.model.GroupBindingType;
import gov.nist.hit.hl7.igamt.coconstraints.model.HeaderType;
import gov.nist.hit.hl7.igamt.coconstraints.model.NarrativeHeader;
import gov.nist.hit.hl7.igamt.coconstraints.serialization.SerializableCoConstraintTable;
import gov.nist.hit.hl7.igamt.coconstraints.serialization.SerializableDataElementHeader;
import gov.nist.hit.hl7.igamt.coconstraints.serialization.SerializableGrouper;
import gov.nist.hit.hl7.igamt.coconstraints.serialization.SerializableHeaders;
import gov.nist.hit.hl7.igamt.datatype.service.DatatypeService;
import gov.nist.diff.domain.DeltaAction;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ConformanceProfileExportConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.domain.DeltaConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.domain.DeltaExportConfigMode;
import gov.nist.hit.hl7.igamt.valueset.service.ValuesetService;
import nu.xom.Element;
import nu.xom.Elements;

/**
 * The co-constraint table of a differential export. Its rows used to stop after the "then" columns,
 * two or more cells short of the header, and its groups were left out altogether.
 */
public class CoConstraintDeltaTableTest {

  private CoConstraintSerializationServiceImpl serializer;
  private SerializableCoConstraintTable table;

  @Before
  public void setUp() {
    serializer = new CoConstraintSerializationServiceImpl();
    serializer.valuesetService = mock(ValuesetService.class);
    serializer.datatypeService = mock(DatatypeService.class);

    SerializableHeaders headers = mock(SerializableHeaders.class);
    List<SerializableDataElementHeader> selectors = Arrays.asList(header("code", ColumnType.CODE, "OBX-3", false));
    List<SerializableDataElementHeader> constraints = Arrays.asList(header("type", ColumnType.DATATYPE, "OBX-2", false),
        header("value", ColumnType.VALUE, "OBX-5", true));
    NarrativeHeader comments = new NarrativeHeader();
    comments.setKey("comments");
    comments.setTitle("Comments");
    comments.setType(HeaderType.NARRATIVE);
    when(headers.getSerializableSelectors()).thenReturn(selectors);
    when(headers.getSerializableConstraints()).thenReturn(constraints);
    when(headers.getNarratives()).thenReturn(Arrays.<CoConstraintHeader>asList(comments));
    SerializableGrouper grouper = mock(SerializableGrouper.class);
    when(grouper.getName()).thenReturn("OBX-4");
    when(headers.getGrouper()).thenReturn(grouper);

    CoConstraintGroupBindingContained group = new CoConstraintGroupBindingContained();
    group.setType(GroupBindingType.CONTAINED);
    group.setName("Eligibility");
    group.setRequirement(requirement(CoConstraintUsage.O, 0, "*"));
    group.setCoConstraints(Arrays.asList(row(true), row(false)));

    table = mock(SerializableCoConstraintTable.class);
    when(table.getHeaders()).thenReturn(headers);
    when(table.getCoConstraints()).thenReturn(Arrays.asList(row(true), row(false)));
    when(table.getGroups()).thenReturn(Arrays.<CoConstraintGroupBinding>asList(group));
  }

  private static SerializableDataElementHeader header(String key, ColumnType column, String name, boolean cardinality) {
    SerializableDataElementHeader header = mock(SerializableDataElementHeader.class);
    when(header.getKey()).thenReturn(key);
    when(header.getColumnType()).thenReturn(column);
    when(header.getName()).thenReturn(name);
    when(header.getType()).thenReturn(HeaderType.DATAELEMENT);
    when(header.isCardinality()).thenReturn(cardinality);
    return header;
  }

  private static CoConstraintRequirement requirement(CoConstraintUsage usage, int min, String max) {
    CoConstraintRequirement requirement = new CoConstraintRequirement();
    requirement.setUsage(usage);
    CoConstraintCardinality cardinality = new CoConstraintCardinality();
    cardinality.setMin(min);
    cardinality.setMax(max);
    requirement.setCardinality(cardinality);
    return requirement;
  }

  /** A row with a code; the other columns are a key without a cell, or no key at all. */
  private static CoConstraint row(boolean keysWithoutCells) {
    CoConstraint row = new CoConstraint();
    row.setRequirement(requirement(CoConstraintUsage.R, 1, "1"));
    Map<String, CoConstraintCell> cells = new HashMap<String, CoConstraintCell>();
    CodeCell code = new CodeCell();
    code.setCode("64994-7");
    code.setCodeSystem("LN");
    code.setLocations(Arrays.asList(1));
    cells.put("code", code);
    if (keysWithoutCells) {
      cells.put("type", null);
      cells.put("value", null);
      cells.put("comments", null);
    }
    row.setCells(cells);
    return row;
  }

  private static ConformanceProfileExportConfiguration configuration(DeltaExportConfigMode mode) {
    DeltaConfiguration delta = new DeltaConfiguration();
    delta.setMode(mode);
    HashMap<DeltaAction, String> colors = new HashMap<DeltaAction, String>();
    for (DeltaAction action : DeltaAction.values()) {
      colors.put(action, "#eeeeee");
    }
    delta.setColors(colors);
    ConformanceProfileExportConfiguration configuration = new ConformanceProfileExportConfiguration();
    configuration.setDeltaConfig(delta);
    return configuration;
  }

  private static int columns(Element row) {
    int count = 0;
    Elements cells = row.getChildElements();
    for (int i = 0; i < cells.size(); i++) {
      String span = cells.get(i).getAttributeValue("colspan");
      count += span == null ? 1 : Integer.parseInt(span);
    }
    return count;
  }

  private void assertRectangular(Element coConstraints, int expectedBodyRows) {
    Element tableElement = coConstraints.getFirstChildElement("table");
    Elements headRows = tableElement.getFirstChildElement("thead").getChildElements("tr");
    // ID, usage, two for the cardinality, the code, the data type, the value and its cardinality, the grouper, the comments
    int width = columns(headRows.get(0));
    assertThat(width).isEqualTo(10);
    Elements bodyRows = tableElement.getFirstChildElement("tbody").getChildElements("tr");
    assertThat(bodyRows.size()).isEqualTo(expectedBodyRows);
    for (int i = 0; i < bodyRows.size(); i++) {
      assertThat(columns(bodyRows.get(i))).as("cells of row %d: %s", i + 1, bodyRows.get(i).toXML()).isEqualTo(width);
    }
  }

  @Test
  public void everyRowOfADifferentialTableHasTheColumnsOfItsHeader() {
    for (DeltaExportConfigMode mode : DeltaExportConfigMode.values()) {
      Element coConstraints = serializer.SerializeCoConstraintCompactDelta(table, Collections.emptyList(), configuration(mode));
      // two rows, the heading of the group, its two rows
      assertRectangular(coConstraints, 5);
      assertThat(coConstraints.toXML()).as("mode %s", mode).contains("Group Eligibility").contains("Within same group").contains("Distinct");
    }
  }

  private static CoConstraint row(DeltaAction delta) {
    CoConstraint row = row(true);
    row.setDelta(delta);
    return row;
  }

  private void rows(List<CoConstraint> ofTheTable, DeltaAction ofTheGroup, List<CoConstraint> ofTheGroupRows) {
    CoConstraintGroupBindingContained group = new CoConstraintGroupBindingContained();
    group.setType(GroupBindingType.CONTAINED);
    group.setName("Eligibility");
    group.setRequirement(requirement(CoConstraintUsage.O, 0, "*"));
    group.setDelta(ofTheGroup);
    group.setCoConstraints(ofTheGroupRows);
    when(table.getCoConstraints()).thenReturn(ofTheTable);
    when(table.getGroups()).thenReturn(Arrays.<CoConstraintGroupBinding>asList(group));
  }

  private static boolean hides(DeltaExportConfigMode mode) {
    return mode == DeltaExportConfigMode.HIDE || mode == DeltaExportConfigMode.HIDE_WITH_CHANGED_ONLY
        || mode == DeltaExportConfigMode.HIDE_WITH_OLD_VALUES;
  }

  @Test
  public void addedAndDeletedRowsKeepTheColumnsAndUnchangedOnesAreHiddenWhenAsked() {
    for (DeltaExportConfigMode mode : DeltaExportConfigMode.values()) {
      rows(Arrays.asList(row(DeltaAction.ADDED), row(DeltaAction.DELETED), row(DeltaAction.UNCHANGED)), DeltaAction.UNCHANGED,
          Arrays.asList(row(DeltaAction.UNCHANGED), row(DeltaAction.ADDED)));
      Element coConstraints = serializer.SerializeCoConstraintCompactDelta(table, Collections.emptyList(), configuration(mode));
      // hidden: the unchanged row of the table and the unchanged row of the group; the group stays for its added row
      assertRectangular(coConstraints, hides(mode) ? 4 : 6);
      assertThat(coConstraints.toXML()).as("mode %s", mode).contains("Group Eligibility");
    }
  }

  @Test
  public void aGroupWithNothingLeftToShowIsNotListed() {
    for (DeltaExportConfigMode mode : DeltaExportConfigMode.values()) {
      rows(Arrays.asList(row(DeltaAction.ADDED)), DeltaAction.UNCHANGED, Arrays.asList(row(DeltaAction.UNCHANGED), row(DeltaAction.UNCHANGED)));
      Element coConstraints = serializer.SerializeCoConstraintCompactDelta(table, Collections.emptyList(), configuration(mode));
      assertRectangular(coConstraints, hides(mode) ? 1 : 4);
      assertThat(coConstraints.toXML().contains("Group Eligibility")).as("mode %s", mode).isEqualTo(!hides(mode));
    }
  }

  @Test
  public void aGroupThatChangedItselfIsListedEvenWhenNoneOfItsRowsDid() {
    for (DeltaExportConfigMode mode : DeltaExportConfigMode.values()) {
      rows(Arrays.asList(row(DeltaAction.ADDED)), DeltaAction.CHANGED, Arrays.asList(row(DeltaAction.UNCHANGED), row(DeltaAction.UNCHANGED)));
      Element coConstraints = serializer.SerializeCoConstraintCompactDelta(table, Collections.emptyList(), configuration(mode));
      assertRectangular(coConstraints, hides(mode) ? 2 : 4);
      assertThat(coConstraints.toXML()).as("mode %s", mode).contains("Group Eligibility");
    }
  }

  @Test
  public void aGroupAddedOrDeletedAsAWholeIsListedWithAllItsRowsInItsColour() {
    for (DeltaAction action : new DeltaAction[] { DeltaAction.ADDED, DeltaAction.DELETED }) {
      for (DeltaExportConfigMode mode : DeltaExportConfigMode.values()) {
        rows(Arrays.asList(row(DeltaAction.UNCHANGED)), action, Arrays.asList(row((DeltaAction) null), row((DeltaAction) null)));
        Element coConstraints = serializer.SerializeCoConstraintCompactDelta(table, Collections.emptyList(), configuration(mode));
        assertRectangular(coConstraints, hides(mode) ? 3 : 4);
        assertThat(coConstraints.toXML()).as("%s, mode %s", action, mode).contains("Group Eligibility").contains("background-color: #eeeeee");
      }
    }
  }

  @Test
  public void theSameTableOutsideADifferentialExportIsRectangularToo() {
    assertRectangular(serializer.SerializeCoConstraintCompact(table), 5);
  }
}
