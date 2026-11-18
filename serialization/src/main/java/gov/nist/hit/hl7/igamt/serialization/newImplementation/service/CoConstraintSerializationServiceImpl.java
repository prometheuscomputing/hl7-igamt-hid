package gov.nist.hit.hl7.igamt.serialization.newImplementation.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import gov.nist.diff.domain.DeltaAction;
import gov.nist.hit.hl7.igamt.coconstraints.model.*;
import gov.nist.hit.hl7.igamt.coconstraints.serialization.SerializableCoConstraintTable;
import gov.nist.hit.hl7.igamt.coconstraints.serialization.SerializableDataElementHeader;
import gov.nist.hit.hl7.igamt.export.configuration.domain.DeltaExportConfigMode;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ConformanceProfileExportConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import gov.nist.hit.hl7.igamt.coconstraints.service.CoConstraintService;
import gov.nist.hit.hl7.igamt.common.base.domain.ValuesetBinding;
import gov.nist.hit.hl7.igamt.datatype.domain.Datatype;
import gov.nist.hit.hl7.igamt.datatype.service.DatatypeService;
import gov.nist.hit.hl7.igamt.valueset.domain.Valueset;
import gov.nist.hit.hl7.igamt.valueset.service.ValuesetService;
import nu.xom.Attribute;
import nu.xom.Element;

@Service
public class CoConstraintSerializationServiceImpl implements CoConstraintSerializationService {

	@Autowired
	DatatypeService datatypeService;

	@Autowired
	CoConstraintService coConstraintService;

	@Autowired
	ValuesetService valuesetService;


	@Override
	public Element SerializeCoConstraintCompactDelta(SerializableCoConstraintTable coConstraintsTable, List<CoConstraintBinding> coConstraintDeltaChanged, ConformanceProfileExportConfiguration conformanceProfileExportConfiguration) {
		if (coConstraintsTable != null) {
			Element coConstraintsElement = new Element("coconstraints");
			Element tableElement = new Element("table");
			tableElement.addAttribute(new Attribute("class", "contentTable"));
			tableElement.addAttribute(new Attribute("id", "parent"));
			Element thead = serializeCoConstraintsTableHeaderCompact(coConstraintsTable);
			tableElement.appendChild(thead);
			Element tbody = serializeCoConstraintsTableContentCompactDelta(coConstraintsTable, generateHeadersList(coConstraintsTable),
					calculateGroupNameColspan(coConstraintsTable), coConstraintDeltaChanged, conformanceProfileExportConfiguration);
			tableElement.appendChild(tbody);
			coConstraintsElement.appendChild(tableElement);
			return coConstraintsElement;
		}
		return null;

	}

	@Override
	public Element SerializeCoConstraintCompact(SerializableCoConstraintTable coConstraintsTable) {
		if (coConstraintsTable != null) {
			Element coConstraintsElement = new Element("coconstraints");
			Element tableElement = new Element("table");
			tableElement.addAttribute(new Attribute("class", "contentTable"));
			tableElement.addAttribute(new Attribute("id", "parent"));
			Element thead = serializeCoConstraintsTableHeaderCompact(coConstraintsTable);
			tableElement.appendChild(thead);
			Element tbody = serializeCoConstraintsTableContentCompact(coConstraintsTable, generateHeadersList(coConstraintsTable), coConstraintsTable.getHeaders().getNarratives(),
					calculateGroupNameColspan(coConstraintsTable));
			tableElement.appendChild(tbody);
			coConstraintsElement.appendChild(tableElement);
			return coConstraintsElement;
		}
		return null;

	}

	@Override
	public Element SerializeCoConstraintVerbose(SerializableCoConstraintTable coConstraintsTable) {
		if (coConstraintsTable != null) {
			Element coConstraintsElement = new Element("coconstraints");
			Element tableElement = new Element("table");
			tableElement.addAttribute(new Attribute("class", "contentTable"));
			tableElement.addAttribute(new Attribute("id", "parent"));
			Element thead = serializeCoConstraintsTableHeaderVerbose(coConstraintsTable);
			tableElement.appendChild(thead);
			Element tbody = new Element("tbody");
			tableElement.appendChild(tbody);
			if (coConstraintsTable.getCoConstraints() != null) {
				for (CoConstraint row : coConstraintsTable.getCoConstraints()) {
					serializeRowVerbose(coConstraintsTable, row, generateHeadersList(coConstraintsTable),coConstraintsTable.getHeaders().getNarratives(), false , tbody);
					//					tableContent.appendChild(rowElement);
				}
			}
			coConstraintsElement.appendChild(tableElement);
			if (coConstraintsTable.getGroups() != null) {
				for (CoConstraintGroupBinding coConstraintGroupBinding : coConstraintsTable.getGroups()) {
					Element groupElement = serializeGroupVerbose(coConstraintsTable, coConstraintGroupBinding, generateHeadersList(coConstraintsTable), coConstraintsTable.getHeaders().getNarratives(),true,  calculateGroupNameColspan(coConstraintsTable));
					coConstraintsElement.appendChild(groupElement);
				}

				return coConstraintsElement;
			}
			return coConstraintsElement;
		}
		return null;
	}

	private Element serializeCoConstraintsTableContentCompactDelta(SerializableCoConstraintTable coConstraintsTable,
			List<SerializableDataElementHeader> headersList, int calculateGroupNameColspan, List<CoConstraintBinding> coConstraintDeltaChanged, ConformanceProfileExportConfiguration conformanceProfileExportConfiguration) {
		Element tableContent = new Element("tbody");
		// the same columns as the header: one for the grouper when the table has groups, one per narrative
		boolean isGroup = coConstraintsTable.getGroups() != null && !coConstraintsTable.getGroups().isEmpty();
		List<CoConstraintHeader> narratives = coConstraintsTable.getHeaders().getNarratives();
		if (coConstraintsTable.getCoConstraints() != null) {
			int position = 0;
			for (CoConstraint coConstraint : coConstraintsTable.getCoConstraints()) {
				position += 1;
				Element rowElement = serializeRowCompactDelta(coConstraint, headersList, position, conformanceProfileExportConfiguration);
				if (rowElement != null) {
					appendGroupAndNarrativeCells(rowElement, coConstraint, narratives, isGroup, false);
					tableContent.appendChild(rowElement);
				}
			}
		}
		if (coConstraintsTable.getGroups() != null) {
			for (CoConstraintGroupBinding coConstraintGroupBinding : coConstraintsTable.getGroups()) {
				appendGroupCompactDelta(tableContent, coConstraintGroupBinding, headersList, narratives, calculateGroupNameColspan,
						conformanceProfileExportConfiguration);
			}
		}
		return tableContent;
	}

	/**
	 * A group of a differential table: its heading row, then its rows as the rows outside a group
	 * are written. A group that did not change is left out when the configuration hides what did not
	 * change, and so is a group none of whose rows is left.
	 */
	private void appendGroupCompactDelta(Element tableContent, CoConstraintGroupBinding group, List<SerializableDataElementHeader> headersList,
			List<CoConstraintHeader> narratives, int colspanTableWidth, ConformanceProfileExportConfiguration configuration) {
		if (group == null || !GroupBindingType.CONTAINED.equals(group.getType())) {
			return;
		}
		// a group is listed for as long as one of its rows is: the rows decide what a mode hides
		boolean wholeGroup = DeltaAction.ADDED.equals(group.getDelta()) || DeltaAction.DELETED.equals(group.getDelta());
		String color = wholeGroup ? configuration.getDeltaConfig().getColors().get(group.getDelta()) : null;
		List<Element> rows = new ArrayList<Element>();
		List<CoConstraint> groupRows = ((CoConstraintGroupBindingContained) group).getCoConstraints();
		if (groupRows != null) {
			int position = 0;
			for (CoConstraint coConstraint : groupRows) {
				position += 1;
				Element row = wholeGroup ? serializeRowCompact(coConstraint, headersList, narratives, true, true, position)
						: serializeRowCompactDelta(coConstraint, headersList, position, configuration);
				if (row == null) {
					continue;
				}
				if (!wholeGroup) {
					appendGroupAndNarrativeCells(row, coConstraint, narratives, true, true);
				} else if (color != null) {
					row.addAttribute(new Attribute("style", "background-color: " + color));
				}
				rows.add(row);
			}
		}
		// a group whose name, usage or cardinality changed is a change of its own, whatever became of its rows
		boolean changedItself = DeltaAction.CHANGED.equals(group.getDelta()) || DeltaAction.UPDATED.equals(group.getDelta());
		if (rows.isEmpty() && !changedItself) {
			return;
		}
		Element heading = serializeGroupHeaderCompact(group, colspanTableWidth);
		if (changedItself && color == null) {
			color = configuration.getDeltaConfig().getColors().get(group.getDelta());
		}
		if (color != null && heading.getAttribute("style") == null) {
			heading.addAttribute(new Attribute("style", "background-color: " + color));
		}
		tableContent.appendChild(heading);
		for (Element row : rows) {
			tableContent.appendChild(row);
		}
	}

	/** The cells after the "if" and "then" columns: what the row is grouped by, then one cell per narrative column. */
	private void appendGroupAndNarrativeCells(Element tr, CoConstraint coConstraintTableRow, List<CoConstraintHeader> narrativesHeadersList,
			boolean isGroup, boolean isWithinGroup) {
		if (isGroup) {
			Element tdGroup = new Element("td");
			tdGroup.appendChild(isWithinGroup ? "Within same group" : "Distinct");
			tr.appendChild(tdGroup);
		}
		if (narrativesHeadersList == null) {
			return;
		}
		for (CoConstraintHeader header : narrativesHeadersList) {
			if (header == null) {
				continue;
			}
			CoConstraintCell cell = coConstraintTableRow.getCells() != null ? coConstraintTableRow.getCells().get(header.getKey()) : null;
			tr.appendChild(cell != null ? serializeCellCompact(cell, cell.getType()) : new Element("td"));
		}
	}

	private Element serializeCoConstraintsTableContentCompact(SerializableCoConstraintTable coConstraintsTable,
			List<SerializableDataElementHeader> ifAndThenHeadersList, List<CoConstraintHeader> narrativeHeadersList, int calculateGroupNameColspan) {
		Element tableContent = new Element("tbody");
		boolean isGroup = false;
		if(coConstraintsTable.getGroups().isEmpty()) {
			isGroup = false;
		} else {
			isGroup = true;
		}
		if (coConstraintsTable.getCoConstraints() != null) {
			int position = 0;
			for (CoConstraint coConstraint : coConstraintsTable.getCoConstraints()) {
				position += 1;

				Element rowElement = serializeRowCompact(coConstraint, ifAndThenHeadersList, narrativeHeadersList, isGroup, false, position);
				tableContent.appendChild(rowElement);
			}
		}
		// A group is a heading row followed by its own rows, all in the same table body
		for (CoConstraintGroupBinding coConstraintGroupBinding : coConstraintsTable.getGroups()) {
			if (coConstraintGroupBinding == null || !GroupBindingType.CONTAINED.equals(coConstraintGroupBinding.getType())) {
				continue;
			}
			tableContent.appendChild(serializeGroupHeaderCompact(coConstraintGroupBinding, calculateGroupNameColspan));
			List<CoConstraint> groupRows = ((CoConstraintGroupBindingContained) coConstraintGroupBinding).getCoConstraints();
			if (groupRows != null) {
				int groupPosition = 0;
				for (CoConstraint coConstraint : groupRows) {
					groupPosition += 1;
					tableContent.appendChild(serializeRowCompact(coConstraint, ifAndThenHeadersList, narrativeHeadersList, true, true, groupPosition));
				}
			}
		}
		return tableContent;
	}

	public Element serializeCoConstraintsTableHeaderCompact(SerializableCoConstraintTable coConstraintTable) {
		Element thead = new Element("thead");
		thead.addAttribute(new Attribute("class", "contentThead"));
		Element tr = new Element("tr");
		Element th = new Element("th");
		th.addAttribute(new Attribute("rowspan", "2"));
		th.appendChild("ID");
		tr.appendChild(th);
		th = new Element("th");
		th.addAttribute(new Attribute("rowspan", "2"));
		th.appendChild("Usage");
		tr.appendChild(th);
		th = new Element("th");
		th.addAttribute(new Attribute("rowspan", "2"));
		th.addAttribute(new Attribute("colspan", "2"));
		th.appendChild("Cardinality");
		tr.appendChild(th);
		th = new Element("th");
//		th.addAttribute(new Attribute("class", "ifContentThead"));
		th.addAttribute(
				new Attribute("colspan", String.valueOf(calculateHeadersNumber(coConstraintTable.getHeaders().getSerializableSelectors()))));
		th.appendChild("If");
		tr.appendChild(th);
		th = new Element("th");
		th.addAttribute(new Attribute("colspan", String.valueOf(calculateHeadersNumber(coConstraintTable.getHeaders().getSerializableConstraints()))));
		th.appendChild("Then");
		tr.appendChild(th);
		if (!coConstraintTable.getGroups().isEmpty()) {
			th = new Element("th");
//			th.addAttribute(new Attribute("class", "contentThead"));
			th.addAttribute(new Attribute("colspan", "1"));
			th.appendChild("Group By");
			tr.appendChild(th);
		}
		if (!coConstraintTable.getHeaders().getNarratives().isEmpty()) {
			th = new Element("th");
			th.addAttribute(new Attribute("class", "greenHeader"));
			th.addAttribute(new Attribute("colspan", String.valueOf(coConstraintTable.getHeaders().getNarratives().size())));
			th.appendChild("Narrative");
			tr.appendChild(th);
		}
		thead.appendChild(tr);
		tr = new Element("tr");
		for (SerializableDataElementHeader coConstraintHeader : coConstraintTable.getHeaders().getSerializableSelectors()) {
			if (coConstraintHeader != null) {
				if (coConstraintHeader.isCardinality()) {
					Element thIF = new Element("th");
					thIF.addAttribute(new Attribute("class", "ifContentThead"));
					String headerLabel = columnLabel(((DataElementHeader) coConstraintHeader).getColumnType(), coConstraintHeader.getName());
					thIF.appendChild(headerLabel);
					tr.appendChild(thIF);
					Element thCard = new Element("th");
					thCard.addAttribute(new Attribute("class", "ifContentThead"));
					thCard.appendChild("Cardinality");
					tr.appendChild(thCard);
				} else {
					Element thIF = new Element("th");
					thIF.addAttribute(new Attribute("class", "ifContentThead"));
					String headerLabel = columnLabel(coConstraintHeader.getColumnType(), coConstraintHeader.getName());
					thIF.appendChild(headerLabel);
					tr.appendChild(thIF);
				}
			}
		}
		for (SerializableDataElementHeader coConstraintHeader : coConstraintTable.getHeaders().getSerializableConstraints()) {
			if (coConstraintHeader != null) {
				if (coConstraintHeader.isCardinality()) {
					Element thThen = new Element("th");
					String headerLabel = columnLabel(coConstraintHeader.getColumnType(), coConstraintHeader.getName());
					thThen.appendChild(headerLabel);
					tr.appendChild(thThen);
					Element thCard = new Element("th");
					thCard.appendChild("Cardinality");
					tr.appendChild(thCard);
				} else {
					Element thThen = new Element("th");
					String headerLabel = columnLabel(coConstraintHeader.getColumnType(), coConstraintHeader.getName());
					thThen.appendChild(headerLabel);
					tr.appendChild(thThen);
				}
			}
		}
		if (!coConstraintTable.getGroups().isEmpty()) {
			Element thGroupBy = new Element("th");
			String headerLabel = coConstraintTable.getHeaders().getGrouper().getName() + " (group ID)";
			thGroupBy.appendChild(headerLabel);
			tr.appendChild(thGroupBy);
		}

		for (CoConstraintHeader coConstraintHeader : coConstraintTable.getHeaders().getNarratives()) {
			if (coConstraintHeader != null) {
				Element thUser = new Element("th");
				thUser.addAttribute(new Attribute("class", "greenHeader"));
				String headerLabel = ((NarrativeHeader) coConstraintHeader).getTitle();
				thUser.appendChild(headerLabel);
				tr.appendChild(thUser);
			}
		}
		thead.appendChild(tr);
		return thead;
	}

	public Element serializeCellCompact(CoConstraintCell coConstraintTableCell, ColumnType columnType) {
		Element tdCell = new Element("td");
		if (coConstraintTableCell == null || columnType == null) {
			return tdCell;
		}
		switch (columnType) {
		case CODE:
			CodeCell codeCell = (CodeCell) coConstraintTableCell;
			Element code = cellItem(tdCell);
			code.appendChild(cellPart("cc-code", codeCell.getCode()));
			List<String> codeDetails = new ArrayList<String>();
			if (!Strings.isNullOrEmpty(codeCell.getCodeSystem())) {
				codeDetails.add(codeCell.getCodeSystem());
			}
			if (codeCell.getLocations() != null && !codeCell.getLocations().isEmpty()) {
				codeDetails.add("at " + joinLocations(codeCell.getLocations()));
			}
			appendDetails(code, codeDetails);
			break;
		case VALUE:
			ValueCell dataCell = (ValueCell) coConstraintTableCell;
			if (dataCell.getValue() != null) {
				tdCell.appendChild(dataCell.getValue());
			}
			break;
		case VALUESET:
			ValueSetCell vSCell = (ValueSetCell) coConstraintTableCell;
			if (vSCell.getBindings() != null) {
				for (ValuesetBinding valuesetBinding : vSCell.getBindings()) {
					Element binding = cellItem(tdCell);
					boolean first = true;
					for (String name : valuesetNames(valuesetBinding.getValueSets())) {
						if (!first) {
							binding.appendChild(", ");
						}
						binding.appendChild(cellPart("cc-valueset", name));
						first = false;
					}
					List<String> bindingDetails = new ArrayList<String>();
					if (valuesetBinding.getStrength() != null) {
						bindingDetails.add(valuesetBinding.getStrength().value);
					}
					if (valuesetBinding.getValuesetLocations() != null && !valuesetBinding.getValuesetLocations().isEmpty()) {
						bindingDetails.add("at " + joinLocations(valuesetBinding.getValuesetLocations()));
					}
					appendDetails(binding, bindingDetails);
				}
			}
			break;
		case VARIES:
			VariesCell variesCell = (VariesCell) coConstraintTableCell;
			if (variesCell.getCellType() != null && variesCell.getCellValue() != null) {
				tdCell = serializeCellCompact(variesCell.getCellValue(), variesCell.getCellType());
			}
			break;
		case DATATYPE:
			DatatypeCell datatypeCell = (DatatypeCell) coConstraintTableCell;
			if (!Strings.isNullOrEmpty(datatypeCell.getDatatypeId())) {
				Datatype datatype = datatypeService.findById(datatypeCell.getDatatypeId());
				if (datatype != null) {
					Element item = cellItem(tdCell);
					item.appendChild(cellPart("cc-datatype", datatype.getLabel()));
					if (datatype.getName() != null && !datatype.getName().equals(datatype.getLabel())) {
						List<String> datatypeDetails = new ArrayList<String>();
						datatypeDetails.add("flavor of " + datatype.getName());
						appendDetails(item, datatypeDetails);
					}
				}
			}
			break;
		default:
			break;
		}
		return tdCell;
	}

	/** One line of a co-constraint cell: a value followed by its qualifiers. */
	private static Element cellItem(Element cell) {
		Element item = new Element("div");
		item.addAttribute(new Attribute("class", "cc-item"));
		cell.appendChild(item);
		return item;
	}

	private static Element cellPart(String cssClass, String text) {
		Element part = new Element("span");
		part.addAttribute(new Attribute("class", cssClass));
		part.appendChild(text == null ? "" : text);
		return part;
	}

	private static void appendDetails(Element item, List<String> details) {
		if (details.isEmpty()) {
			return;
		}
		item.appendChild(" ");
		item.appendChild(cellPart("cc-sub", String.join(", ", details)));
	}

	private static String joinLocations(java.util.Collection<Integer> locations) {
		List<String> parts = new ArrayList<String>();
		for (Integer location : new java.util.TreeSet<Integer>(locations)) {
			parts.add(String.valueOf(location));
		}
		return String.join(" or ", parts);
	}

	private static String columnLabel(ColumnType type, String name) {
		String kind = type == null ? "" : type.name().toLowerCase().replace("valueset", "value set").replace("datatype", "data type");
		return kind.isEmpty() ? name : name + " (" + kind + ")";
	}

	private List<String> valuesetNames(List<String> ids) {
		List<String> names = new ArrayList<String>();
		if (ids != null) {
			for (String id : ids) {
				Valueset vs = valuesetService.findById(id);
				if (vs != null) {
					names.add(vs.getBindingIdentifier());
				}
			}
		}
		return names;
	}

	public Element serializeRowCompactDelta(CoConstraint coConstraintTableRow, List<SerializableDataElementHeader> headersList, int position, ConformanceProfileExportConfiguration conformanceProfileExportConfiguration) {
		if (conformanceProfileExportConfiguration.getDeltaConfig().getMode().equals(DeltaExportConfigMode.HIDE) ||
				conformanceProfileExportConfiguration.getDeltaConfig().getMode().equals(DeltaExportConfigMode.HIDE_WITH_CHANGED_ONLY) ||
				conformanceProfileExportConfiguration.getDeltaConfig().getMode().equals(DeltaExportConfigMode.HIDE_WITH_OLD_VALUES)) {
			if (coConstraintTableRow.getDelta()!= null && coConstraintTableRow.getDelta().equals(DeltaAction.UNCHANGED)) {
				return null;
			}
		}
		if (coConstraintTableRow.getDelta() != null && (coConstraintTableRow.getDelta().equals(DeltaAction.ADDED) || coConstraintTableRow.getDelta().equals(DeltaAction.DELETED))) {
			Element tr = new Element("tr");

			tr.addAttribute(new Attribute("style", "background-color: "+conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(coConstraintTableRow.getDelta())));
			Element td = new Element("td");
			if (position == 1) {
				td.appendChild("1");
			td.appendChild(" ");
			td.appendChild(cellPart("cc-sub", "primary"));
			} else {
				td.appendChild(String.valueOf(position));
			}
			tr.appendChild(td);

			td = new Element("td");
			td.appendChild(coConstraintTableRow.getRequirement().getUsage().toString());
			tr.appendChild(td);

			td = new Element("td");
			td.appendChild(Integer.toString(coConstraintTableRow.getRequirement().getCardinality().getMin()));
			tr.appendChild(td);

			td = new Element("td");
			td.appendChild(coConstraintTableRow.getRequirement().getCardinality().getMax());
			tr.appendChild(td);

			for (SerializableDataElementHeader header : headersList) {
				if (coConstraintTableRow.getCells().containsKey(header.getKey())) {
					CoConstraintCell coConstraintTableCell = coConstraintTableRow.getCells().get(header.getKey());
					tr.appendChild(coConstraintTableCell != null ? serializeCellCompact(coConstraintTableCell, coConstraintTableCell.getType()) : new Element("td"));
					if (header.isCardinality()) {
						Element tdCard = new Element("td");
						if (coConstraintTableCell != null && coConstraintTableCell.getCardinalityMax() != null) {
							tdCard.appendChild(coConstraintTableCell.getCardinalityMax());
						}
						tr.appendChild(tdCard);
					}
				} else {
					tr.appendChild(new Element("td"));
					if (header.isCardinality()) {
						// the column of the value and the one of its cardinality
						tr.appendChild(new Element("td"));
					}
				}

			}
			return tr;
		} else {
			Element tr = new Element("tr");

			Element td = new Element("td");
			if (position == 1) {
				td.appendChild("1");
			td.appendChild(" ");
			td.appendChild(cellPart("cc-sub", "primary"));
			} else {
				td.appendChild(String.valueOf(position));
			}
			tr.appendChild(td);

			if (conformanceProfileExportConfiguration.getDeltaConfig().getMode().equals(DeltaExportConfigMode.HIDE_WITH_CHANGED_ONLY)) {
				if (coConstraintTableRow.getRequirement().getUsageDelta() == null || coConstraintTableRow.getRequirement().getUsageDelta().getDelta() == null || coConstraintTableRow.getRequirement().getUsageDelta().getDelta().equals(DeltaAction.UNCHANGED)) {
					td = new Element("td");
					tr.appendChild(td);
				} else {
					td = new Element("td");
					td.appendChild(coConstraintTableRow.getRequirement().getUsageDelta().getCurrent().toString());
					td.addAttribute(new Attribute("style", "background-color: "+conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(DeltaAction.UPDATED)));
					tr.appendChild(td);
				}

				if (coConstraintTableRow.getRequirement().getCardinalityDelta() == null || coConstraintTableRow.getRequirement().getCardinalityDelta().getDelta() == null ||  coConstraintTableRow.getRequirement().getCardinalityDelta().getDelta().equals(DeltaAction.UNCHANGED)) {
					td = new Element("td");
					tr.appendChild(td);
					td = new Element("td");
					tr.appendChild(td);
				} else {
					td = new Element("td");
					td.appendChild(Integer.toString(coConstraintTableRow.getRequirement().getCardinalityDelta().getCurrent().getMin()));
					td.addAttribute(new Attribute("style", "background-color: "+conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(DeltaAction.UPDATED)));
					tr.appendChild(td);

					td = new Element("td");
					td.appendChild(coConstraintTableRow.getRequirement().getCardinalityDelta().getCurrent().getMax());
					td.addAttribute(new Attribute("style", "background-color: "+ conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(DeltaAction.UPDATED)));
					tr.appendChild(td);
				}

			}
			else if (conformanceProfileExportConfiguration.getDeltaConfig().getMode().equals(DeltaExportConfigMode.HIGHLIGHT_WITH_OLD_VALUES)
					|| conformanceProfileExportConfiguration.getDeltaConfig().getMode().equals(DeltaExportConfigMode.HIDE_WITH_OLD_VALUES)) {
				td = new Element("td");
				Element div = new Element("div");
				div.addAttribute(new Attribute("style", "display:flex;"));
				if (coConstraintTableRow.getRequirement().getUsageDelta() != null && !coConstraintTableRow.getRequirement().getUsageDelta().getDelta().equals(DeltaAction.UNCHANGED)) {
					if (coConstraintTableRow.getRequirement().getUsageDelta().getPrevious() != null) {
						Element span = new Element("span");
						span.addAttribute(new Attribute("style", "width:50%;background-color: " + conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(DeltaAction.DELETED)));
						span.appendChild(coConstraintTableRow.getRequirement().getUsageDelta().getPrevious().toString());
						div.appendChild(span);
					}
					if (coConstraintTableRow.getRequirement().getUsageDelta().getCurrent() != null) {
						Element span = new Element("span");
						span.addAttribute(new Attribute("style", "width:50%;background-color: " + conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(DeltaAction.ADDED)));
						span.appendChild(coConstraintTableRow.getRequirement().getUsageDelta().getCurrent().toString());
						div.appendChild(span);
					}
				} else {
					Element span = new Element("span");
					span.addAttribute(new Attribute("style", "width:100%;"));
					span.appendChild(coConstraintTableRow.getRequirement().getUsage().toString());
					div.appendChild(span);
				}
				td.appendChild(div);
				tr.appendChild(td);

				td = new Element("td");
				div = new Element("div");
				div.addAttribute(new Attribute("style", "display:flex;"));
				if (coConstraintTableRow.getRequirement().getCardinalityDelta() != null && !coConstraintTableRow.getRequirement().getCardinalityDelta().getDelta().equals(DeltaAction.UNCHANGED)) {

					if (coConstraintTableRow.getRequirement().getCardinalityDelta().getPrevious() != null) {
						Element span = new Element("span");
						span.addAttribute(new Attribute("style", "width:50%;background-color: " + conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(DeltaAction.DELETED)));
						span.appendChild(Integer.toString(coConstraintTableRow.getRequirement().getCardinalityDelta().getPrevious().getMin()));
						div.appendChild(span);

					}
					if (coConstraintTableRow.getRequirement().getCardinalityDelta().getCurrent() != null) {
						Element span = new Element("span");
						span.addAttribute(new Attribute("style", "width:50%;background-color: " + conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(DeltaAction.ADDED)));
						span.appendChild(Integer.toString(coConstraintTableRow.getRequirement().getCardinalityDelta().getCurrent().getMin()));
						div.appendChild(span);
					}
				} else {
					Element span = new Element("span");
					span.addAttribute(new Attribute("style", "width:100%;"));
					span.appendChild(Integer.toString(coConstraintTableRow.getRequirement().getCardinality().getMin()));
					div.appendChild(span);
				}
				td.appendChild(div);
				tr.appendChild(td);

				td = new Element("td");
				div = new Element("div");

				div.addAttribute(new Attribute("style", "display:flex;"));
				if (coConstraintTableRow.getRequirement().getCardinalityDelta() != null && !coConstraintTableRow.getRequirement().getCardinalityDelta().getDelta().equals(DeltaAction.UNCHANGED)) {
					if (coConstraintTableRow.getRequirement().getCardinalityDelta().getPrevious() != null) {
						Element span = new Element("span");
						span.addAttribute(new Attribute("style", "width:50%;background-color: " + conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(DeltaAction.DELETED)));
						span.appendChild(coConstraintTableRow.getRequirement().getCardinalityDelta().getPrevious().getMax());
						div.appendChild(span);

					}
					if (coConstraintTableRow.getRequirement().getCardinalityDelta().getCurrent() != null) {
						Element span = new Element("span");
						span.addAttribute(new Attribute("style", "width:50%;background-color: " + conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(DeltaAction.ADDED)));
						span.appendChild(coConstraintTableRow.getRequirement().getCardinalityDelta().getCurrent().getMax());
						div.appendChild(span);
					}
				} else {
					Element span = new Element("span");
					span.addAttribute(new Attribute("style", "width:100%;"));
					span.appendChild(coConstraintTableRow.getRequirement().getCardinality().getMax());
					div.appendChild(span);

				}
				td.appendChild(div);
				tr.appendChild(td);

			}
			else {
				td = new Element("td");
				if (coConstraintTableRow.getRequirement().getUsageDelta() != null  && !coConstraintTableRow.getRequirement().getUsageDelta().getDelta().equals(DeltaAction.UNCHANGED) ) {
					td.appendChild(coConstraintTableRow.getRequirement().getUsageDelta().getCurrent().toString());
					td.addAttribute(new Attribute("style", "background-color: "+ conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(DeltaAction.UPDATED)));

				} else {
					td.appendChild(coConstraintTableRow.getRequirement().getUsage().toString());
				}
				tr.appendChild(td);

				td = new Element("td");

				if (coConstraintTableRow.getRequirement().getCardinalityDelta() != null && !coConstraintTableRow.getRequirement().getCardinalityDelta().getDelta().equals(DeltaAction.UNCHANGED)) {
					td.appendChild(Integer.toString(coConstraintTableRow.getRequirement().getCardinalityDelta().getCurrent().getMin()));
					td.addAttribute(new Attribute("style", "background-color: "+ conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(DeltaAction.UPDATED)));

				} else {
					td.appendChild(Integer.toString(coConstraintTableRow.getRequirement().getCardinality().getMin()));
				}
				tr.appendChild(td);

				td = new Element("td");
				if (coConstraintTableRow.getRequirement().getCardinalityDelta() != null  && !coConstraintTableRow.getRequirement().getCardinalityDelta().getDelta().equals(DeltaAction.UNCHANGED)) {
					td.appendChild(coConstraintTableRow.getRequirement().getCardinalityDelta().getCurrent().getMax());
					td.addAttribute(new Attribute("style", "background-color: "+ conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(DeltaAction.UPDATED)));

				} else {
					td.appendChild(coConstraintTableRow.getRequirement().getCardinality().getMax());
				}
				tr.appendChild(td);
			}


			for (SerializableDataElementHeader header : headersList) {
				if (coConstraintTableRow.getCells().containsKey(header.getKey())) {
					CoConstraintCell coConstraintTableCell = coConstraintTableRow.getCells().get(header.getKey());
					// I added this if close because it throws an error in a context outside of Delta where coConstraintTableCell is null
					tr.appendChild(coConstraintTableCell != null ? serializeCellCompact(coConstraintTableCell, coConstraintTableCell.getType()) : new Element("td"));
					if (header.isCardinality()) {
						Element tdCard = new Element("td");
						if (coConstraintTableCell != null && coConstraintTableCell.getCardinalityMax() != null) {
							tdCard.appendChild(coConstraintTableCell.getCardinalityMax());
						}
						tr.appendChild(tdCard);
					}
				} else {
					tr.appendChild(new Element("td"));
					if (header.isCardinality()) {
						// the column of the value and the one of its cardinality
						tr.appendChild(new Element("td"));
					}
				}

			}
			return tr;
		}


	}


	public Element serializeRowCompact(CoConstraint coConstraintTableRow, List<SerializableDataElementHeader> ifAndThenHeadersList, List<CoConstraintHeader> narrativesHeadersList, boolean isGroup, boolean isWithinGroup, int position) {
		Element tr = new Element("tr");
		Element td = new Element("td");
		if (position == 1) {
			td.appendChild("1");
			td.appendChild(" ");
			td.appendChild(cellPart("cc-sub", "primary"));
		} else {
			td.appendChild(String.valueOf(position));
		}
		tr.appendChild(td);
		td = new Element("td");
		td.appendChild(coConstraintTableRow.getRequirement().getUsage().toString());
		tr.appendChild(td);
		td = new Element("td");
		td.appendChild(Integer.toString(coConstraintTableRow.getRequirement().getCardinality().getMin()));
		tr.appendChild(td);
		td = new Element("td");
		td.appendChild(coConstraintTableRow.getRequirement().getCardinality().getMax());
		tr.appendChild(td);
		for (SerializableDataElementHeader header : ifAndThenHeadersList) {
			if (coConstraintTableRow.getCells().containsKey(header.getKey())) {
				CoConstraintCell coConstraintTableCell = coConstraintTableRow.getCells().get(header.getKey());
				tr.appendChild(coConstraintTableCell != null ? serializeCellCompact(coConstraintTableCell, coConstraintTableCell.getType()) : new Element("td"));
				if (header.getType().equals(HeaderType.DATAELEMENT)) {
					if (header.isCardinality()) {
						Element tdCard = new Element("td");
						if (coConstraintTableCell != null && coConstraintTableCell.getCardinalityMax() != null) {
							tdCard.appendChild(coConstraintTableCell.getCardinalityMax());
						}
						tr.appendChild(tdCard);
					}
				}
			} else {
				tr.appendChild(new Element("td"));
				if (header.isCardinality()) {
					// the column of the value and the one of its cardinality
					tr.appendChild(new Element("td"));
				}
			}
		}
		appendGroupAndNarrativeCells(tr, coConstraintTableRow, narrativesHeadersList, isGroup, isWithinGroup);
		return tr;

	}


	public Element serializeGroupCompactDelta(CoConstraintGroupBinding coConstraintGroupBinding, List<SerializableDataElementHeader> headersList,
			int colspanTableWidth, ConformanceProfileExportConfiguration conformanceProfileExportConfiguration) {
		return null;
		//        Element groupElement = new Element("tr");
		//        // groupElement.addAttribute(new Attribute("class", "contentTable"));
		//        Element groupeTableHeader = serializeGroupHeaderCompact(coConstraintGroupBinding, colspanTableWidth);
		//        if (coConstraintGroupBinding != null && coConstraintGroupBinding.getType().equals(GroupBindingType.CONTAINED)) {
		//            if (conformanceProfileExportConfiguration.getDeltaConfig().getMode().equals(DeltaExportConfigMode.HIDE) ||
		//                    conformanceProfileExportConfiguration.getDeltaConfig().getMode().equals(DeltaExportConfigMode.HIDE_WITH_CHANGED_ONLY) ||
		//                    conformanceProfileExportConfiguration.getDeltaConfig().getMode().equals(DeltaExportConfigMode.HIDE_WITH_OLD_VALUES)) {
		//                if ( coConstraintGroupBinding.getDelta() != null && coConstraintGroupBinding.getDelta().equals(DeltaAction.UNCHANGED)) {
		//                    return null;
		//                }
		//            }
		//            if(coConstraintGroupBinding.getDelta() != null &&(coConstraintGroupBinding.getDelta().equals(DeltaAction.ADDED) || coConstraintGroupBinding.getDelta().equals(DeltaAction.DELETED))){
		//                Element groupTableContent = serializeGroupContentCompact(((CoConstraintGroupBindingContained) coConstraintGroupBinding).getCoConstraints(), headersList,
		//                        colspanTableWidth);
		//                groupTableContent.addAttribute(new Attribute("style", "background-color: "+conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(coConstraintGroupBinding.getDelta())));
		//                groupeTableHeader.addAttribute(new Attribute("style", "background-color: "+conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(coConstraintGroupBinding.getDelta())));
		//                groupTableContent.addAttribute(new Attribute("style", "background-color: "+conformanceProfileExportConfiguration.getDeltaConfig().getColors().get(coConstraintGroupBinding.getDelta())));
		//
		//                groupElement.appendChild(groupeTableHeader);
		//                groupElement.appendChild(groupTableContent);
		//            } else {
		//                Element groupTableContent = serializeGroupContentCompactDelta(((CoConstraintGroupBindingContained) coConstraintGroupBinding).getCoConstraints(), headersList,
		//                        colspanTableWidth, conformanceProfileExportConfiguration);
		//
		//                groupElement.appendChild(groupeTableHeader);
		//                groupElement.appendChild(groupTableContent);
		//            }
		//
		//        }
		//        return groupElement;
	}

	public Element serializeGroupCompact(CoConstraintGroupBinding coConstraintGroupBinding, List<SerializableDataElementHeader> ifAndThenHeadersList, List<CoConstraintHeader> narrativesHeadersList, boolean isGroup, boolean isWithinGroup,
			int colspanTableWidth) {
		Element groupElement = new Element("tr");
		// groupElement.addAttribute(new Attribute("class", "contentTable"));
		Element groupeTableHeader = serializeGroupHeaderCompact(coConstraintGroupBinding, colspanTableWidth);
		if (coConstraintGroupBinding != null && coConstraintGroupBinding.getType().equals(GroupBindingType.CONTAINED)) {
			Element groupeTableContent = serializeGroupContentCompact(((CoConstraintGroupBindingContained) coConstraintGroupBinding).getCoConstraints(), ifAndThenHeadersList, narrativesHeadersList, true,
					colspanTableWidth);

			groupElement.appendChild(groupeTableHeader);
			groupElement.appendChild(groupeTableContent);
		}
		return groupElement;
	}

	public Element serializeGroupHeaderCompact(CoConstraintGroupBinding coConstraintGroupBinding, int colspanTableWidth) {
		// Element tbody = new Element("thead");
		Element tr = new Element("tr");
		tr.addAttribute(new Attribute("class", "cc-group"));
		// the group has no identifier: an empty cell, not an empty heading
		tr.appendChild(new Element("td"));
		Element th = new Element("th");
		th.appendChild(coConstraintGroupBinding.getRequirement().getUsage().toString());
		tr.appendChild(th);
		th = new Element("th");
		th.appendChild(Integer.toString(coConstraintGroupBinding.getRequirement().getCardinality().getMin()));
		tr.appendChild(th);
		th = new Element("th");
		th.appendChild(coConstraintGroupBinding.getRequirement().getCardinality().getMax());
		tr.appendChild(th);
		th = new Element("th");
		th.addAttribute(new Attribute("colspan", Integer.toString(colspanTableWidth)));
		if (coConstraintGroupBinding != null && coConstraintGroupBinding.getType().equals(GroupBindingType.CONTAINED)) {
			th.appendChild("Group " + ((CoConstraintGroupBindingContained) coConstraintGroupBinding).getName());
		}
		tr.appendChild(th);
		// tbody.appendChild(tr);
		return tr;
	}


	public Element serializeGroupContentCompactDelta(List<CoConstraint> coConstraints, List<SerializableDataElementHeader> headersList, int colspanTableWidth, ConformanceProfileExportConfiguration conformanceProfileExportConfiguration) {
		Element tableContent = new Element("tbody");
		if (coConstraints != null) {
			int position = 0;
			for (CoConstraint coConstraint : coConstraints) {
				position += 1;
				Element rowElement = serializeRowCompactDelta(coConstraint, headersList, position, conformanceProfileExportConfiguration);
				if(rowElement != null){
					tableContent.appendChild(rowElement);
				}
			}
		}

		return tableContent;
	}

	public Element serializeGroupContentCompact(List<CoConstraint> coConstraints, List<SerializableDataElementHeader> ifAndThenHeadersList, List<CoConstraintHeader> narrativesHeadersList, boolean isGroup, int colspanTableWidth) {
		Element tableContent = new Element("tbody");
		if (coConstraints != null) {
			int position = 0;
			for (CoConstraint coConstraint : coConstraints) {
				position += 1;
				Element rowElement = serializeRowCompact(coConstraint, ifAndThenHeadersList, narrativesHeadersList,isGroup,true, position);
				tableContent.appendChild(rowElement);
			}
		}
		//		if (content.getGroups() != null) {
			//			for (CoConstraintTableGroup group : content.getGroups()) {
		//				Element groupElement = serializeGroup(group, headerIds, colspanTableWidth);
		//				tableContent.appendChild(groupElement);
		//			}
		//		}
		return tableContent;
	}

	private Element serializeGroupContentVerbose(SerializableCoConstraintTable coConstraintTable, List<CoConstraint> coConstraints, List<SerializableDataElementHeader> ifAndThenHeadersList, List<CoConstraintHeader> narrativesHeadersList,
			int colspanTableWidth) {
		Element tableContent = new Element("tbody");
		if (coConstraints != null) {
			for (CoConstraint coConstraint : coConstraints) {
				serializeRowVerbose(coConstraintTable, coConstraint, ifAndThenHeadersList, narrativesHeadersList, true, tableContent);
			}
		}
		return tableContent;
	}

	private Element serializeGroupVerbose(SerializableCoConstraintTable coConstraintTable, CoConstraintGroupBinding coConstraintGroupBinding, List<SerializableDataElementHeader> ifAndThenHeadersList, List<CoConstraintHeader> narrativesHeadersList, boolean isGroup, int colspanTableWidth) {
		Element groupElement = new Element("table");
		groupElement.addAttribute(new Attribute("class", "contentTable"));
		Element groupeTableHeader = serializeGroupHeaderVerbose(coConstraintGroupBinding, colspanTableWidth);
		groupElement.appendChild(groupeTableHeader);
		if (coConstraintGroupBinding != null && coConstraintGroupBinding.getType().equals(GroupBindingType.CONTAINED)) {
			Element groupeTableContent = serializeGroupContentVerbose(coConstraintTable, ((CoConstraintGroupBindingContained) coConstraintGroupBinding).getCoConstraints(), ifAndThenHeadersList, narrativesHeadersList,
					colspanTableWidth);
			groupElement.appendChild(groupeTableContent);
		}
		return groupElement;
	}


	private Element serializeGroupHeaderVerbose(CoConstraintGroupBinding coConstraintGroupBinding, int colspanTableWidth) {
		Element thead = new Element("thead");
		Element th = new Element("th");
		th.addAttribute(new Attribute("style", "border-top:4pt solid black"));
		th.addAttribute(new Attribute("colspan", "4"));
		th.addAttribute(new Attribute("class", "lightPinkCell"));
		if (coConstraintGroupBinding != null && coConstraintGroupBinding.getType().equals(GroupBindingType.CONTAINED)) {
			th.appendChild("Group " + ((CoConstraintGroupBindingContained) coConstraintGroupBinding).getName() + "  |  " + "Usage : "
					+ coConstraintGroupBinding.getRequirement().getUsage().toString() + "  |  " + "Cardinality : ["
					+ Integer.toString(coConstraintGroupBinding.getRequirement().getCardinality().getMin()) + " : "
					+ coConstraintGroupBinding.getRequirement().getCardinality().getMax() + "]");
		}
		thead.appendChild(th);
		return thead;
	}

	private void serializeRowVerbose(SerializableCoConstraintTable coConstraintsTable, CoConstraint coConstraintTableRow, List<SerializableDataElementHeader> ifAndThenHeadersList, List<CoConstraintHeader> narrativesHeadersList, Boolean isGroup, Element tableContent) {
		Map<String, List<String>> headerIdsMap = filterHeadersIds(coConstraintsTable);
		Map<String, String> mapIdToLabel = generateMapIdToLabel(coConstraintsTable);
		// Element thColumn = new Element("th");
		// thColumn.appendChild("Column");
		// rowElement.appendChild(thColumn);
		// Element thValue = new Element("th");
		// thValue.appendChild("Value");
		// rowElement.appendChild(thValue);
		// Element td1 = new Element("td");
		// rowElement.appendChild(td1);
		Element trRow1 = new Element("tr");
		// tr.addAttribute(new Attribute("rowspan",
		// String.valueOf(coConstraintsTable.getHeaders().getData().size()) +
		// String.valueOf(coConstraintsTable.getHeaders().getUser().size())+String.valueOf(coConstraintsTable.getHeaders().getSelectors().size())
		// +2));
		Element td1 = new Element("td");
		td1.addAttribute(new Attribute("width", "30%"));
		td1.addAttribute(new Attribute("style", "vertical-align:top"));
		// td1.addAttribute(new Attribute("rowspan",
		// String.valueOf(coConstraintsTable.getHeaders().getData().size() +
		// coConstraintsTable.getHeaders().getUser().size() +3)));
		Element tableTd1 = new Element("table");
		tableTd1.addAttribute(new Attribute("class", "contentTable"));
		tableTd1.addAttribute(new Attribute("padding", "unset"));
		Element tbodyTd1 = new Element("tbody");
		td1.appendChild(tableTd1);
		tableTd1.appendChild(tbodyTd1);
		Element th1 = new Element("th");
		tbodyTd1.appendChild(th1);
		th1.appendChild("Column");
		th1.addAttribute(new Attribute("width", "30%"));
		Element th2 = new Element("th");
		tbodyTd1.appendChild(th2);
		th2.appendChild("Value");
		th2.addAttribute(new Attribute("width", "70%"));
		Element trIfHeader = new Element("tr");
		tbodyTd1.appendChild(trIfHeader);
		Element tdIfHeader = new Element("td");
		trIfHeader.appendChild(tdIfHeader);
		tdIfHeader.appendChild("IF");
		tdIfHeader.addAttribute(new Attribute("colspan", "2"));
		tdIfHeader.addAttribute(new Attribute("class", "lightBlueCell"));
		tdIfHeader.addAttribute(new Attribute("align", "center"));
		for (String id : coConstraintTableRow.getCells().keySet()) {
			if (headerIdsMap.get("If").contains(id)) {
				Element trIf = new Element("tr");
				tbodyTd1.appendChild(trIf);
				Element td = new Element("td");
				td.appendChild(mapIdToLabel.get(id));
				trIf.appendChild(td);
				td = serializeCellCompact(coConstraintTableRow.getCells().get(id), coConstraintTableRow.getCells().get(id).getType());
				trIf.appendChild(td);
			}
		}
		trRow1.appendChild(td1);
		Element tableTd2 = new Element("table");
		tableTd2.addAttribute(new Attribute("class", "contentTable"));
		Element tbodyTd2 = new Element("tbody");
		tableTd2.appendChild(tbodyTd2);
		Element td2 = new Element("td");
		trRow1.appendChild(td2);
		td2.addAttribute(new Attribute("width", "70%"));
		td2.addAttribute(new Attribute("style", "vertical-align:top"));
		td2.addAttribute(new Attribute("padding", "unset"));
		td2.addAttribute(new Attribute("colspan", "2"));
		td2.appendChild(tableTd2);
		Element tr1 = new Element("th");
		tbodyTd2.appendChild(tr1);
		tr1.appendChild("Column");
		tr1.addAttribute(new Attribute("width", "30%"));
		Element tr2 = new Element("th");
		tbodyTd2.appendChild(tr2);
		tr2.appendChild("Value");
		tr2.addAttribute(new Attribute("width", "70%"));
		Element trHeaderThen = new Element("tr");
		tbodyTd2.appendChild(trHeaderThen);
		Element tdThen = new Element("td");
		trHeaderThen.appendChild(tdThen);
		tdThen.appendChild("THEN");
		tdThen.addAttribute(new Attribute("colspan", "2"));
		tdThen.addAttribute(new Attribute("class", "lightGreyCell"));
		tdThen.addAttribute(new Attribute("align", "center"));
		for (String id : coConstraintTableRow.getCells().keySet()) {
			if (headerIdsMap.get("Then").contains(id)) {
				Element trThen = new Element("tr");
				Element tdColumn = new Element("td");
				tdColumn.appendChild(mapIdToLabel.get(id));
				trThen.appendChild(tdColumn);
				Element tdValue = serializeCellCompact(coConstraintTableRow.getCells().get(id), coConstraintTableRow.getCells().get(id).getType());
				trThen.appendChild(tdValue);
				tbodyTd2.appendChild(trThen);
			}
		}
		Element td3 = new Element("td");
		td3.addAttribute(new Attribute("colspan", "3"));
		Element trRow2 = new Element("tr");
		trRow2.appendChild(td3);
		Element tableTd3 = new Element("table");
		td3.appendChild(tableTd3);
		tableTd3.addAttribute(new Attribute("class", "contentTable"));
		Element tbodyTd3 = new Element("tbody");
		tableTd3.appendChild(tbodyTd3);
		Element trHeaderUser = new Element("tr");
		Element tdUser = new Element("th");
		tdUser.appendChild("Narratives");
		tdUser.addAttribute(new Attribute("class", "greenHeader"));
		tdUser.addAttribute(new Attribute("colspan", "2"));
		tdUser.addAttribute(new Attribute("align", "center"));
		trHeaderUser.appendChild(tdUser);
		tbodyTd3.appendChild(trHeaderUser);
		for (String id : coConstraintTableRow.getCells().keySet()) {
			if (headerIdsMap.get("Narratives").contains(id)) {
				Element trUser = new Element("tr");
				Element tdColumn = new Element("th");
				tdColumn.addAttribute(new Attribute("width", "20%"));
				tdColumn.appendChild(mapIdToLabel.get(id));
				tdColumn.addAttribute(new Attribute("class", "greenHeader"));
				trUser.appendChild(tdColumn);
				Element tdValue = serializeCellCompact(coConstraintTableRow.getCells().get(id), coConstraintTableRow.getCells().get(id).getType());
				tdValue.addAttribute(new Attribute("class", "greenHeader"));
				trUser.appendChild(tdValue);
				tbodyTd3.appendChild(trUser);
			}
		}
		//		Element trRow = new Element("tr");
		tableContent.appendChild(trRow1);
		tableContent.appendChild(trRow2);

		return;
	}

	private Element serializeCoConstraintsTableHeaderVerbose(SerializableCoConstraintTable coConstraintsTable) {
		Element thead = new Element("thead");
		thead.addAttribute(new Attribute("class", "contentThead"));
		Element tr = new Element("tr");
		Element th = new Element("th");
		th.addAttribute(new Attribute("class", "ifContentThead"));
		th.addAttribute(new Attribute("width", "30%"));
		// th.addAttribute(new Attribute("colspan", "2"));
		th.appendChild("If");
		tr.appendChild(th);
		th = new Element("th");
		th.appendChild("Then");
		th.addAttribute(new Attribute("width", "70%"));
		th.addAttribute(new Attribute("colspan", "2"));
		tr.appendChild(th);
		thead.appendChild(tr);
		return thead;
	}

	//	public Element serializeCoConstraintsTableHeader(CoConstraintTable coConstraintTable) {
	//		Element thead = new Element("thead");
	//		thead.addAttribute(new Attribute("class", "contentThead"));
	//		Element tr = new Element("tr");
	//		Element th = new Element("th");
	//		th.addAttribute(new Attribute("rowspan", "2"));
	//		th.appendChild("Usage");
	//		tr.appendChild(th);
	//		th = new Element("th");
	//		th.addAttribute(new Attribute("rowspan", "2"));
	//		th.addAttribute(new Attribute("colspan", "2"));
	//		th.appendChild("Cardinality");
	//		tr.appendChild(th);
	//		th = new Element("th");
	//		th.addAttribute(new Attribute("class", "ifContentThead"));
	//		th.addAttribute(
	//				new Attribute("colspan", String.valueOf(calculateHeadersNumber(coConstraintTable.getHeaders().getSelectors()))));
	//		th.appendChild("If");
	//		tr.appendChild(th);
	//		th = new Element("th");
	//		th.addAttribute(new Attribute("colspan", String.valueOf(calculateHeadersNumber(coConstraintTable.getHeaders().getConstraints()))));
	//		th.appendChild("Then");
	//		tr.appendChild(th);
	//		th = new Element("th");
	//		th.addAttribute(new Attribute("class", "greenHeader"));
	//		th.addAttribute(new Attribute("colspan", String.valueOf(calculateHeadersNumber(coConstraintTable.getHeaders().getNarratives()))));
	//		th.appendChild("USER");
	//		tr.appendChild(th);
	//		thead.appendChild(tr);
	//		tr = new Element("tr");
	//		for (CoConstraintHeader coConstraintTableHeader : coConstraintTable.getHeaders().getSelectors()) {
	//			if (coConstraintTableHeader != null) {
	//				Element thIF = new Element("th");
	//				thIF.addAttribute(new Attribute("class", "ifContentThead"));
	//				thIF.appendChild(coConstraintTableHeader.get);
	//				tr.appendChild(thIF);
	//			}
	//		}
	//		for (CoConstraintHeader coConstraintTableHeader : coConstraintTable.getHeaders().getConstraints()) {
	//			if (coConstraintTableHeader != null) {
	//				Element thThen = new Element("th");
	//				thThen.appendChild(coConstraintTableHeader.getLabel());
	//				tr.appendChild(thThen);
	//			}
	//		}
	//		for (CoConstraintHeader coConstraintTableHeader : coConstraintTable.getHeaders().getNarratives()) {
	//			if (coConstraintTableHeader != null) {
	//				Element thUser = new Element("th");
	//				thUser.addAttribute(new Attribute("class", "greenHeader"));
	//				thUser.appendChild(coConstraintTableHeader.getLabel());
	//				tr.appendChild(thUser);
	//			}
	//		}
	//		thead.appendChild(tr);
	//		return thead;
	//	}

	public List<String> generateHeadersIDs(SerializableCoConstraintTable coConstraintTable) {
		List<String> headerIds = new ArrayList<String>();
		for (CoConstraintHeader header : coConstraintTable.getHeaders().getSerializableSelectors()) {
			String id = header.getKey();
			headerIds.add(id);
		}
		for (CoConstraintHeader header : coConstraintTable.getHeaders().getSerializableConstraints()) {
			String id = header.getKey();
			headerIds.add(id);
		}
		for (CoConstraintHeader header : coConstraintTable.getHeaders().getNarratives()) {
			String id = header.getKey();
			headerIds.add(id);
		}
		return headerIds;
	}

	public int calculateGroupNameColspan(SerializableCoConstraintTable coConstraintTable) {
		int i = calculateHeadersNumber(coConstraintTable.getHeaders().getSerializableSelectors()) + calculateHeadersNumber(coConstraintTable.getHeaders().getSerializableConstraints())
		+ coConstraintTable.getHeaders().getNarratives().size();
		if(!coConstraintTable.getGroups().isEmpty()) {
			i++;
		}
		return i;
	}

	public String generateValuesetNames(List<String> ids) {
		List<String> valuesetBindings = new ArrayList<String>();
		if (ids != null) {
			for (String id : ids) {
				Valueset vs = valuesetService.findById(id);
				if(vs != null) {
					valuesetBindings.add(vs.getBindingIdentifier());
				}
			}
		}
		return valuesetBindings.toString();
	}

	public int calculateHeadersNumber(List<SerializableDataElementHeader> headers) {
		int number = headers.size();
		for (CoConstraintHeader header : headers) {
			if (header.getType().equals(HeaderType.DATAELEMENT)) {
				if (((SerializableDataElementHeader) header).isCardinality()) {
					number = number + 1;
				}
			}
		}
		return number;
	}

	private List<SerializableDataElementHeader> generateHeadersList(SerializableCoConstraintTable coConstraintTable) {
		List<SerializableDataElementHeader> headersList = new ArrayList<SerializableDataElementHeader>();
		headersList.addAll(coConstraintTable.getHeaders().getSerializableSelectors());
		headersList.addAll(coConstraintTable.getHeaders().getSerializableConstraints());
		//        for (CoConstraintHeader header : coConstraintTable.getHeaders().getNarratives()) {
		//            headersList.add(header);
		//        }

		return headersList;
	}


	//	public Map<String, String> getIdPathMap() {
	//		// TODO Auto-generated method stub
	//		return null;
	//	}

	public Map<String, List<String>> filterHeadersIds(SerializableCoConstraintTable coConstraintTable) {
		Map<String, List<String>> headerIdsMap = new HashMap<>();
		List<String> ifListIds = new ArrayList<>();
		List<String> thenListIds = new ArrayList<>();
		List<String> narrativeListIds = new ArrayList<>();
		for (SerializableDataElementHeader coConstraintTableHeader : coConstraintTable.getHeaders().getSerializableSelectors()) {
			String id = coConstraintTableHeader.getKey();
			ifListIds.add(id);
		}
		for (SerializableDataElementHeader coConstraintTableHeader : coConstraintTable.getHeaders().getSerializableConstraints()) {
			String id = coConstraintTableHeader.getKey();
			thenListIds.add(id);
		}
		for (CoConstraintHeader coConstraintTableHeader : coConstraintTable.getHeaders().getNarratives()) {
			String id = coConstraintTableHeader.getKey();
			narrativeListIds.add(id);
		}
		headerIdsMap.put("If", ifListIds);
		headerIdsMap.put("Then", thenListIds);
		headerIdsMap.put("Narratives", narrativeListIds);
		return headerIdsMap;
	}

	public Map<String, String> generateMapIdToLabel(SerializableCoConstraintTable coConstraintTable) {
		Map<String, String> mapIdToLabel = new HashMap<>();
		for (SerializableDataElementHeader coConstraintTableHeader : coConstraintTable.getHeaders().getSerializableSelectors()) {
			String headerLabel = coConstraintTableHeader.getColumnType().name() + " " + coConstraintTableHeader.getName();
			mapIdToLabel.put(coConstraintTableHeader.getKey(), headerLabel);
		}
		for (SerializableDataElementHeader coConstraintTableHeader : coConstraintTable.getHeaders().getSerializableConstraints()) {
			String headerLabel = coConstraintTableHeader.getColumnType().name() + " " + coConstraintTableHeader.getName();
			mapIdToLabel.put(coConstraintTableHeader.getKey(), headerLabel);
		}
		for (CoConstraintHeader coConstraintTableHeader : coConstraintTable.getHeaders().getNarratives()) {
			String headerLabel = "TEXT " + ((NarrativeHeader) coConstraintTableHeader).getTitle();
			mapIdToLabel.put(coConstraintTableHeader.getKey(), headerLabel);
		}
		return mapIdToLabel;
	}



}
