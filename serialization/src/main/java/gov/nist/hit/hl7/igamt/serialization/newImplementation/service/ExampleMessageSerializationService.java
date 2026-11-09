package gov.nist.hit.hl7.igamt.serialization.newImplementation.service;

import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.IgDataModel;
import nu.xom.Element;

/**
 * Serializes example message references for the document export: message sections become
 * an {@code <ExampleMessage>} element, and example messages inserted in narratives are
 * rewritten from the saved editor snapshot to the current content (or to a file link).
 */
public interface ExampleMessageSerializationService {

	/**
	 * @return the element for a message section, or null when nothing should be emitted
	 */
	Element serializeReference(IgDataModel igDataModel, String messageId, String snippetId,
			ExampleMessageExportConfiguration configuration);

	/**
	 * Rewrites every {@code .fr-exmsg} widget found in a narrative HTML fragment. Returns the
	 * input unchanged when it contains no widget.
	 */
	String rewriteNarrative(String html, IgDataModel igDataModel, ExampleMessageExportConfiguration configuration);
}
