package gov.nist.hit.hl7.igamt.examples.service;

import gov.nist.healthcare.unified.model.EnhancedReport;
import gov.nist.healthcare.unified.model.Section;
import gov.nist.healthcare.unified.model.StringRef;
import gov.nist.hit.hl7.igamt.examples.domain.ExampleMessage;
import gov.nist.hit.hl7.igamt.examples.dto.ExampleMessageValidationEntry;
import gov.nist.hit.hl7.igamt.examples.dto.ExampleMessageValidationResult;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.IgDataModel;
import gov.nist.hit.hl7.igamt.ig.service.IgService;
import gov.nist.hit.hl7.igamt.ig.service.XMLSerializeService;
import gov.nist.validation.report.Report;
import hl7.v2.validation.SyncHL7Validator;
import hl7.v2.validation.ValidationContext;
import hl7.v2.validation.ValidationContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import scala.collection.JavaConverters;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExampleMessageValidationService {

    private static final Logger log = LoggerFactory.getLogger(ExampleMessageValidationService.class);

    @Autowired
    private IgService igService;
    @Autowired
    private XMLSerializeService xmlSerializeService;
    @Autowired
    private ExampleMessagesService exampleMessagesService;

    public ExampleMessageValidationResult validate(String igId, String messageId, String er7Override) {
        ExampleMessageValidationResult result = new ExampleMessageValidationResult();
        try {
            ExampleMessage exampleMessage = exampleMessagesService.getExampleMessage(igId, messageId);
            String message = hasText(er7Override) ? er7Override : exampleMessage.getMessage();
            if (!hasText(message)) {
                result.setError("Message is missing");
                return result;
            }
            if (!hasText(exampleMessage.getProfileId())) {
                result.setError("Conformance profile is missing");
                return result;
            }

            Ig ig = igService.findById(igId);
            IgDataModel igDataModel = igService.generateDataModel(ig);

            String profileXml = xmlSerializeService.serializeProfileToDoc(igDataModel).toXML();
            String constraintXml = xmlSerializeService.serializeConstraintsXML(igDataModel).toXML();
            String valueSetXml = xmlSerializeService.serializeValueSetXML(igDataModel).toXML();
            String bindingsXml = optionalXml(() -> xmlSerializeService.serializeBindingsXML(igDataModel).toXML());
            String coConstraintsXml = optionalXml(() -> xmlSerializeService.serializeCoConstraintXML(igDataModel).toXML());
            String slicingXml = optionalXml(() -> xmlSerializeService.serializeSlicingXML(igDataModel).toXML());

            List<InputStream> confContexts = new ArrayList<InputStream>();
            if (hasText(constraintXml)) {
                confContexts.add(toStream(constraintXml));
            }

            ValidationContextBuilder builder = new ValidationContextBuilder(toStream(profileXml));
            if (hasText(valueSetXml)) {
                builder.useValueSetLibrary(toStream(legacyValueSetXml(valueSetXml)));
            }
            if (!confContexts.isEmpty()) {
                builder.useConformanceContext(
                        JavaConverters.asScalaBufferConverter(confContexts).asScala().toList());
            }
            applyOptional(builder, bindingsXml, "value set bindings", new ArtifactApplier() {
                public void apply(ValidationContextBuilder target, String xml) {
                    target.useVsBindings(toStream(xml));
                }
            });
            applyOptional(builder, coConstraintsXml, "co-constraints", new ArtifactApplier() {
                public void apply(ValidationContextBuilder target, String xml) {
                    target.useCoConstraintsContext(toStream(xml));
                }
            });
            applyOptional(builder, slicingXml, "slicings", new ArtifactApplier() {
                public void apply(ValidationContextBuilder target, String xml) {
                    target.useSlicingContext(toStream(xml));
                }
            });

            ValidationContext validationContext = builder.getValidationContext();
            Report report = new SyncHL7Validator(validationContext).check(message, exampleMessage.getProfileId());

            Section service = new Section("service");
            service.put("name", "IGAMT Example Messages");
            service.put("provider", "NIST");
            ArrayList<Section> metadata = new ArrayList<Section>();
            metadata.add(service);

            EnhancedReport enhanced = EnhancedReport.fromValidation(
                    report,
                    message,
                    profileXml,
                    exampleMessage.getProfileId(),
                    metadata,
                    "Context-Free");
            result.setHtml(enhanced.render("report", null));
            fillEntries(result, enhanced);
        } catch (Throwable e) {
            log.error("Context-free example message validation failed", e);
            result.setError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        return result;
    }

    private void fillEntries(ExampleMessageValidationResult result, EnhancedReport enhanced) {
        List<Section> detections = enhanced.getDetections() != null ? enhanced.getDetections().entries() : null;
        if (detections == null) {
            return;
        }
        List<ExampleMessageValidationEntry> entries = new ArrayList<ExampleMessageValidationEntry>();
        int errors = 0;
        int alerts = 0;
        int warnings = 0;
        int affirmatives = 0;
        int informationals = 0;
        for (Section section : detections) {
            ExampleMessageValidationEntry entry = new ExampleMessageValidationEntry();
            String classification = str(section, "classification");
            entry.setClassification(classification);
            entry.setCategory(str(section, "category"));
            entry.setDescription(str(section, "description"));
            String er7Path = firstNonBlank(str(section, "messageInstancePathName"), str(section, "path"));
            String igPath = firstNonBlank(str(section, "messageProfilePath"), stripInstances(er7Path));
            entry.setPath(er7Path);
            entry.setEr7Path(er7Path);
            entry.setIgPath(igPath);
            entry.setPositionalPath(firstNonBlank(
                    str(section, "messageInstancePositionPath"),
                    str(section, "messageProfilePositionPath")));
            entry.setLine(intVal(section, "line"));
            entry.setColumn(intVal(section, "column"));
            entries.add(entry);
            String kind = classification == null ? "" : classification.toLowerCase();
            if (kind.contains("error") && !kind.contains("spec")) {
                errors++;
            } else if (kind.contains("alert")) {
                alerts++;
            } else if (kind.contains("warning")) {
                warnings++;
            } else if (kind.contains("affirmative")) {
                affirmatives++;
            } else {
                informationals++;
            }
        }
        result.setEntries(entries);
        result.setErrors(errors);
        result.setAlerts(alerts);
        result.setWarnings(warnings);
        result.setAffirmatives(affirmatives);
        result.setInformationals(informationals);
    }

    private String optionalXml(XmlSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("Skipping optional validation artifact: {}", e.getMessage());
            return null;
        }
    }

    private void applyOptional(ValidationContextBuilder builder, String xml, String label, ArtifactApplier applier) {
        if (!hasText(xml)) {
            return;
        }
        try {
            applier.apply(builder, xml);
        } catch (Throwable e) {
            log.warn("Skipping {} for validation: {}", label, e.getMessage());
        }
    }

    private static String str(Section section, String key) {
        StringRef ref = new StringRef();
        if (section.accessPrimitive(key, ref)) {
            return ref.value;
        }
        return "";
    }

    private static int intVal(Section section, String key) {
        try {
            Integer value = section.getInt(key);
            return value != null ? value.intValue() : 0;
        } catch (Exception e) {
            String raw = str(section, key);
            if (!hasText(raw)) {
                return 0;
            }
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException nfe) {
                return 0;
            }
        }
    }

    private static String legacyValueSetXml(String xml) {
        return xml.replaceAll("\\s+CodePattern=\"[^\"]*\"", "").replaceAll("\\s+CodePattern='[^']*'", "");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static String stripInstances(String path) {
        if (!hasText(path)) {
            return "";
        }
        return path.replaceAll("\\[\\d+]", "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static InputStream toStream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    private interface ArtifactApplier {
        void apply(ValidationContextBuilder builder, String xml);
    }

    private interface XmlSupplier {
        String get() throws Exception;
    }
}
