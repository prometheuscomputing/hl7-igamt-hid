package gov.nist.hit.hl7.igamt.serialization.newImplementation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;

import gov.nist.hit.hl7.igamt.coconstraints.model.CodeCell;
import gov.nist.hit.hl7.igamt.coconstraints.model.ColumnType;
import gov.nist.hit.hl7.igamt.coconstraints.model.DatatypeCell;
import gov.nist.hit.hl7.igamt.coconstraints.model.ValueSetCell;
import gov.nist.hit.hl7.igamt.common.base.domain.ValuesetBinding;
import gov.nist.hit.hl7.igamt.common.base.domain.ValuesetStrength;
import gov.nist.hit.hl7.igamt.datatype.service.DatatypeService;
import gov.nist.hit.hl7.igamt.valueset.domain.Valueset;
import gov.nist.hit.hl7.igamt.valueset.service.ValuesetService;
import nu.xom.Element;

public class CoConstraintCellSerializationTest {

  private CoConstraintSerializationServiceImpl serializer;

  @Before
  public void setUp() {
    serializer = new CoConstraintSerializationServiceImpl();
    serializer.valuesetService = mock(ValuesetService.class);
    serializer.datatypeService = mock(DatatypeService.class);
    when(serializer.valuesetService.findById("vs1")).thenReturn(valueSet("HL70396"));
    when(serializer.valuesetService.findById("vs2")).thenReturn(valueSet("IZ_FUNDING"));
  }

  private static Valueset valueSet(String bindingIdentifier) {
    Valueset valueSet = new Valueset();
    valueSet.setBindingIdentifier(bindingIdentifier);
    return valueSet;
  }

  private static ValuesetBinding binding(ValuesetStrength strength, Integer location, String... valueSets) {
    ValuesetBinding binding = new ValuesetBinding();
    binding.setStrength(strength);
    binding.setValuesetLocations(new HashSet<Integer>(Arrays.asList(location)));
    binding.setValueSets(Arrays.asList(valueSets));
    return binding;
  }

  @Test
  public void codeCellIsOneLineWithItsQualifiers() {
    CodeCell cell = new CodeCell();
    cell.setCode("64994-7");
    cell.setCodeSystem("LN");
    cell.setLocations(Arrays.asList(1, 4));

    Element td = serializer.serializeCellCompact(cell, ColumnType.CODE);

    assertThat(td.toXML()).isEqualTo("<td><div class=\"cc-item\"><span class=\"cc-code\">64994-7</span> "
        + "<span class=\"cc-sub\">LN, at 1 or 4</span></div></td>");
  }

  @Test
  public void valueSetCellWithSeveralBindingsListsEachOne() {
    ValueSetCell cell = new ValueSetCell();
    cell.setBindings(Arrays.asList(binding(ValuesetStrength.R, 1, "vs1"), binding(ValuesetStrength.S, 4, "vs2", "unknown")));

    // used to fail with MultipleParentException as soon as a cell held two bindings
    Element td = serializer.serializeCellCompact(cell, ColumnType.VALUESET);

    assertThat(td.getChildElements("div").size()).isEqualTo(2);
    assertThat(td.getChildElements("div").get(0).getValue()).isEqualTo("HL70396 Required, at 1");
    assertThat(td.getChildElements("div").get(1).getValue()).isEqualTo("IZ_FUNDING Suggested, at 4");
    assertThat(td.toXML()).doesNotContain("<table");
  }

  @Test
  public void emptyCellsStayEmpty() {
    assertThat(serializer.serializeCellCompact(null, ColumnType.CODE).toXML()).isEqualTo("<td />");
    assertThat(serializer.serializeCellCompact(new DatatypeCell(), ColumnType.DATATYPE).toXML()).isEqualTo("<td />");
    assertThat(serializer.serializeCellCompact(new ValueSetCell(), ColumnType.VALUESET).toXML()).isEqualTo("<td />");
  }
}
