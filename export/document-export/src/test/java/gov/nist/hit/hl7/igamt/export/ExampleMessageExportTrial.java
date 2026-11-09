package gov.nist.hit.hl7.igamt.export;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import gov.nist.hit.hl7.igamt.examples.domain.ExampleMessage;
import gov.nist.hit.hl7.igamt.examples.domain.MessageSnippet;
import gov.nist.hit.hl7.igamt.examples.dto.SnippetRenderResult;
import gov.nist.hit.hl7.igamt.examples.service.ExampleMessageExportDataService;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportMode;
import gov.nist.hit.hl7.igamt.export.domain.ExportParameters;
import gov.nist.hit.hl7.igamt.export.util.TransformationUtil;
import gov.nist.hit.hl7.igamt.export.util.WordUtil;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.ExampleMessageDataModel;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.IgDataModel;
import gov.nist.hit.hl7.igamt.serialization.newImplementation.service.ExampleMessageSerializationServiceImpl;
import nu.xom.Document;
import nu.xom.Element;

/**
 * Offline trial of the example-message Word/HTML export paths.
 * Run with the document-export test classpath; writes artifacts under /tmp/igamt-export-trial.
 */
public class ExampleMessageExportTrial {

	private static final Path OUT = Paths.get("/tmp/igamt-export-trial");

	public static void main(String[] args) throws Exception {
		Files.createDirectories(OUT);
		ExampleMessageSerializationServiceImpl serializer = new ExampleMessageSerializationServiceImpl();
		IgDataModel igDataModel = sampleModel();

		trialSerialize(serializer, igDataModel, inlineConfig(), "inline");
		trialSerialize(serializer, igDataModel, filesConfig(false), "files");
		trialCollect(igDataModel, filesConfig(false), "files-referenced");
		trialCollect(igDataModel, filesConfig(true), "files-all");
		trialXslt(serializer, igDataModel, inlineConfig(), "html-inline", "html");
		trialXslt(serializer, igDataModel, filesConfig(false), "html-files", "html");
		trialXslt(serializer, igDataModel, inlineConfig(), "word-inline", "docx");
		System.out.println("Wrote artifacts to " + OUT.toAbsolutePath());
	}

	private static void trialSerialize(ExampleMessageSerializationServiceImpl serializer, IgDataModel model,
			ExampleMessageExportConfiguration config, String name) throws Exception {
		Element sectionRef = serializer.serializeReference(model, "msg-1", "snip-1", config);
		String widget = "<div class=\"fr-exmsg\" data-message-id=\"msg-1\" data-snippet-id=\"snip-1\">"
				+ "<a class=\"fr-exmsg-link\" href=\"/example-messages/ig-1/message/msg-1?snippetId=snip-1\">Example: stale</a>"
				+ "<pre class=\"fr-exmsg-body\">Loading…</pre></div>";
		String rewritten = serializer.rewriteNarrative(widget, model, config);
		Files.write(OUT.resolve(name + "-section.xml"), sectionRef.toXML().getBytes(StandardCharsets.UTF_8));
		Files.write(OUT.resolve(name + "-widget.html"), rewritten.getBytes(StandardCharsets.UTF_8));
		System.out.println("=== " + name + " section ===");
		System.out.println(sectionRef.toXML());
		System.out.println("=== " + name + " widget ===");
		System.out.println(rewritten);
	}

	private static void trialCollect(IgDataModel model, ExampleMessageExportConfiguration config, String name)
			throws Exception {
		ExampleMessageExportDataService files = new ExampleMessageExportDataService();
		Map<String, byte[]> collected = files.collectFiles(model, config);
		byte[] zip = files.zipFiles(collected);
		Files.write(OUT.resolve(name + ".zip"), zip);
		StringBuilder listing = new StringBuilder();
		for (Map.Entry<String, byte[]> entry : collected.entrySet()) {
			listing.append(entry.getKey()).append(" (").append(entry.getValue().length).append(" bytes)\n");
			listing.append(new String(entry.getValue(), StandardCharsets.UTF_8)).append("\n---\n");
		}
		Files.write(OUT.resolve(name + "-listing.txt"), listing.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println("=== " + name + " files ===");
		System.out.println(listing);
	}

	private static void trialXslt(ExampleMessageSerializationServiceImpl serializer, IgDataModel model,
			ExampleMessageExportConfiguration config, String name, String target) throws Exception {
		Element document = new Element("Document");
		Element section = new Element("Section");
		section.addAttribute(new nu.xom.Attribute("id", "sec-1"));
		section.addAttribute(new nu.xom.Attribute("position", "1"));
		section.addAttribute(new nu.xom.Attribute("title", "Message Example"));
		section.addAttribute(new nu.xom.Attribute("type", "MESSAGESECTION"));
		section.addAttribute(new nu.xom.Attribute("h", "1"));
		String widget = "<div class=\"fr-exmsg\" data-message-id=\"msg-1\" data-snippet-id=\"snip-1\">"
				+ "<pre class=\"fr-exmsg-body\">Loading…</pre></div>";
		Element content = new Element("SectionContent");
		content.appendChild(serializer.rewriteNarrative(widget, model, config));
		section.appendChild(content);
		section.appendChild(serializer.serializeReference(model, "msg-1", "snip-1", config));
		document.appendChild(section);
		String xml = new Document(document).toXML();
		Files.write(OUT.resolve(name + "-source.xml"), xml.getBytes(StandardCharsets.UTF_8));
		ExportParameters parameters = new ExportParameters(false, false, target, "Example export trial", "trial");
		File html = TransformationUtil.doTransformToTempHtml(xml, "/IGDocumentExport.xsl", parameters);
		byte[] htmlBytes = Files.readAllBytes(html.toPath());
		Files.write(OUT.resolve(name + ".html"), htmlBytes);
		System.out.println("=== " + name + " html length " + htmlBytes.length + " ===");
		if ("docx".equals(target)) {
			try {
				gov.nist.hit.hl7.igamt.export.domain.ExportedFile exported =
						new gov.nist.hit.hl7.igamt.export.domain.ExportedFile(
								Files.newInputStream(html.toPath()), "trial", "ig-1",
								gov.nist.hit.hl7.igamt.export.domain.ExportFormat.WORD);
				exported.setContent(Files.newInputStream(html.toPath()));
				gov.nist.hit.hl7.igamt.common.base.domain.DocumentMetadata metadata =
						new gov.nist.hit.hl7.igamt.common.base.domain.DocumentMetadata();
				metadata.setTitle("Example export trial");
				gov.nist.hit.hl7.igamt.export.domain.ExportedFile word =
						WordUtil.convertHtmlToWord(exported, metadata, new java.util.Date(), "2.5.1");
				Path wordPath = OUT.resolve(name + ".docx");
				Files.copy(word.getContent(), wordPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				System.out.println("=== " + name + " docx " + Files.size(wordPath) + " bytes ===");
			} catch (Exception e) {
				System.out.println("=== " + name + " docx FAILED: " + e.getMessage() + " ===");
				e.printStackTrace(System.out);
			}
		}
	}

	private static IgDataModel sampleModel() {
		Ig ig = new Ig();
		ig.setId("ig-1");
		ig.setName("Trial IG");

		ExampleMessage message = new ExampleMessage();
		message.setId("msg-1");
		message.setName("ADT^A01 example");
		message.setDescription("Admission example");
		message.setProfileId("cp-1");
		message.setMessage("MSH|^~\\&|SEND|FAC|RECV|FAC|202601011200||ADT^A01|1|P|2.5.1\nPID|1||12345||DOE^JOHN");

		MessageSnippet snippet = new MessageSnippet();
		snippet.setId("snip-1");
		snippet.setName("PID only");
		snippet.setDescription("Patient identification");
		message.getSnippets().add(snippet);

		ExampleMessage unused = new ExampleMessage();
		unused.setId("msg-2");
		unused.setName("Unused ACK");
		unused.setProfileId("cp-1");
		unused.setMessage("MSH|^~\\&|SEND|FAC|RECV|FAC|202601011200||ACK^A01|2|P|2.5.1");

		SnippetRenderResult render = new SnippetRenderResult();
		render.setSnippetId("snip-1");
		render.setSnippetName("PID only");
		render.setRenderedContent("PID|1||12345||DOE^JOHN");
		render.setFullMessage(message.getMessage());

		ExampleMessageDataModel dataModel = new ExampleMessageDataModel();
		dataModel.setModel(message);
		dataModel.setProfileId("cp-1");
		dataModel.setProfileLabel("ADT_A01");
		dataModel.setFilePath("ExampleMessages/ADT_A01/ADT^A01 example.hl7");
		dataModel.getSnippetFilePaths().put("snip-1", "ExampleMessages/ADT_A01/ADT^A01 example - PID only.hl7");
		dataModel.getSnippets().put("snip-1", render);

		ExampleMessageDataModel unusedModel = new ExampleMessageDataModel();
		unusedModel.setModel(unused);
		unusedModel.setProfileId("cp-1");
		unusedModel.setProfileLabel("ADT_A01");
		unusedModel.setFilePath("ExampleMessages/ADT_A01/Unused ACK.hl7");

		IgDataModel igDataModel = new IgDataModel();
		igDataModel.setModel(ig);
		Map<String, ExampleMessageDataModel> map = new LinkedHashMap<String, ExampleMessageDataModel>();
		map.put(message.getId(), dataModel);
		map.put(unused.getId(), unusedModel);
		igDataModel.setExampleMessages(map);
		return igDataModel;
	}

	private static ExampleMessageExportConfiguration inlineConfig() {
		ExampleMessageExportConfiguration configuration = new ExampleMessageExportConfiguration();
		configuration.setInclude(true);
		configuration.setMode(ExampleMessageExportMode.INLINE);
		configuration.setExportAllMessages(false);
		return configuration;
	}

	private static ExampleMessageExportConfiguration filesConfig(boolean all) {
		ExampleMessageExportConfiguration configuration = new ExampleMessageExportConfiguration();
		configuration.setInclude(true);
		configuration.setMode(ExampleMessageExportMode.SEPARATE_FILES);
		configuration.setExportAllMessages(all);
		return configuration;
	}
}
