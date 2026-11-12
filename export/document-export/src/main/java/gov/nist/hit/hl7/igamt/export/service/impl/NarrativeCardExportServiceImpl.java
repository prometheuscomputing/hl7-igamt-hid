package gov.nist.hit.hl7.igamt.export.service.impl;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import gov.nist.hit.hl7.igamt.common.base.domain.NarrativeRequirement;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration;
import gov.nist.hit.hl7.igamt.examples.service.ExampleMessageExportDataService;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.IgDataModel;
import gov.nist.hit.hl7.igamt.ig.service.IgService;
import gov.nist.hit.hl7.igamt.serialization.newImplementation.service.ExampleMessageSerializationService;
import nu.xom.Element;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.hit.hl7.igamt.common.base.domain.Link;
import gov.nist.hit.hl7.igamt.common.base.domain.Ref;
import gov.nist.hit.hl7.igamt.common.base.domain.TextSection;
import gov.nist.hit.hl7.igamt.common.base.domain.Type;
import gov.nist.hit.hl7.igamt.common.base.domain.Usage;
import gov.nist.hit.hl7.igamt.conformanceprofile.domain.ConformanceProfile;
import gov.nist.hit.hl7.igamt.conformanceprofile.domain.Group;
import gov.nist.hit.hl7.igamt.conformanceprofile.domain.SegmentRef;
import gov.nist.hit.hl7.igamt.conformanceprofile.domain.SegmentRefOrGroup;
import gov.nist.hit.hl7.igamt.conformanceprofile.service.ConformanceProfileService;
import gov.nist.hit.hl7.igamt.datatype.domain.ComplexDatatype;
import gov.nist.hit.hl7.igamt.datatype.domain.Component;
import gov.nist.hit.hl7.igamt.datatype.domain.Datatype;
import gov.nist.hit.hl7.igamt.datatype.service.DatatypeService;
import gov.nist.hit.hl7.igamt.export.domain.ExportFormat;
import gov.nist.hit.hl7.igamt.export.domain.ExportedFile;
import gov.nist.hit.hl7.igamt.export.service.NarrativeCardExportService;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;
import gov.nist.hit.hl7.igamt.segment.domain.Field;
import gov.nist.hit.hl7.igamt.segment.domain.Segment;
import gov.nist.hit.hl7.igamt.segment.service.SegmentService;
import gov.nist.hit.hl7.igamt.serialization.util.FroalaSerializationUtil;
import gov.nist.hit.hl7.igamt.valueset.domain.Code;
import gov.nist.hit.hl7.igamt.valueset.domain.Valueset;
import gov.nist.hit.hl7.igamt.valueset.service.ValuesetService;

/**
 * Builds a multi-file public web IG ZIP (narratives + message structures +
 * segments + datatypes + value sets). Separate from classic Word/HTML export.
 */
import gov.nist.hit.hl7.igamt.common.base.domain.ValuesetBinding;
import gov.nist.hit.hl7.igamt.common.base.domain.ValuesetStrength;
import gov.nist.hit.hl7.igamt.common.binding.domain.ResourceBinding;
import gov.nist.hit.hl7.igamt.common.binding.domain.StructureElementBinding;
import gov.nist.hit.hl7.igamt.constraints.domain.AssertionConformanceStatement;
import gov.nist.hit.hl7.igamt.constraints.domain.AssertionPredicate;
import gov.nist.hit.hl7.igamt.constraints.domain.ConformanceStatement;
import gov.nist.hit.hl7.igamt.constraints.domain.Predicate;
import gov.nist.hit.hl7.igamt.common.base.domain.Comment;
import gov.nist.hit.hl7.igamt.common.base.domain.LengthType;
import gov.nist.hit.hl7.igamt.common.base.domain.Resource;

@Service
public class NarrativeCardExportServiceImpl implements NarrativeCardExportService {

  @Autowired
  private FroalaSerializationUtil froalaCleaner;
  @Autowired
  private ConformanceProfileService conformanceProfileService;
  @Autowired
  private SegmentService segmentService;
  @Autowired
  private DatatypeService datatypeService;
  @Autowired
  private ValuesetService valuesetService;

  @Autowired private IgService igService;
  @Autowired private ExampleMessageExportDataService exampleData;
  @Autowired private ExampleMessageSerializationService exampleSerialization;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ExportedFile exportNarrativeCardsHtml(Ig ig, ExampleMessageExportConfiguration configuration) throws Exception {
    IgDataModel model = igService.generateDataModel(ig);
    exampleData.populate(model, configuration);
    String title = "Implementation Guide";
    if (ig.getMetadata() != null && ig.getMetadata().getTitle() != null
        && !ig.getMetadata().getTitle().trim().isEmpty()) {
      title = ig.getMetadata().getTitle();
    }
    String igId = ig.getId() != null ? ig.getId() : "ig";

    List<Map<String, Object>> cards = new ArrayList<Map<String, Object>>();
    List<String> order = new ArrayList<String>();
    for (TextSection section : sorted(ig.getContent())) {
      walk(section, null, String.valueOf(section.getPosition()), cards, order, model, configuration);
    }
    Map<String, Map<String, Object>> cardById = new HashMap<String, Map<String, Object>>();
    for (Map<String, Object> card : cards) {
      cardById.put(String.valueOf(card.get("id")), card);
    }

    List<ConformanceProfile> profiles = loadProfiles(ig);
    List<Segment> segments = loadSegments(ig);
    List<Datatype> datatypes = loadDatatypes(ig);
    List<Valueset> valuesets = loadValuesets(ig);

    Map<String, Segment> segById = new HashMap<String, Segment>();
    Map<String, String> segSlug = new HashMap<String, String>();
    for (Segment s : segments) {
      segById.put(s.getId(), s);
      segSlug.put(s.getId(), slugify(s.getId()));
    }
    Map<String, Datatype> dtById = new HashMap<String, Datatype>();
    Map<String, String> dtSlug = new HashMap<String, String>();
    for (Datatype d : datatypes) {
      dtById.put(d.getId(), d);
      dtSlug.put(d.getId(), slugify(d.getId()));
    }
    Map<String, String> vsSlug = new HashMap<String, String>();
    for (Valueset v : valuesets) {
      String bid = v.getBindingIdentifier() != null ? v.getBindingIdentifier() : v.getId();
      vsSlug.put(v.getId(), slugify(v.getId()));
    }
    Map<String, String> cpSlug = new HashMap<String, String>();
    for (ConformanceProfile cp : profiles) {
      cpSlug.put(cp.getId(), slugify(cp.getId()));
    }

    Map<String, Object> payload = new HashMap<String, Object>();
    payload.put("igId", igId);
    payload.put("title", title);
    payload.put("cards", cards);
    payload.put("order", order);
    payload.put("profileCount", profiles.size());
    payload.put("segmentCount", segments.size());
    payload.put("datatypeCount", datatypes.size());
    payload.put("valuesetCount", valuesets.size());
    String guideJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PublicationArchive zip = new PublicationArchive(baos, model, configuration);
    String root = "igamt-public-ig/";

    put(zip, root + "README.txt", buildReadme(title, igId, cards.size(), profiles.size(), segments.size(),
        datatypes.size(), valuesets.size()));
    put(zip, root + "data/guide.json", guideJson);
    try (java.io.InputStream style = getClass().getResourceAsStream("/publication-accessibility.css")) {
      ByteArrayOutputStream styles = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096]; int count;
      while ((count = style.read(buffer)) != -1) styles.write(buffer, 0, count);
      put(zip, root + "assets/css/publication.css", readResource("/publication.css") + new String(styles.toByteArray(), StandardCharsets.UTF_8));
    }
    put(zip, root + "assets/js/publication.js", readResource("/publication.js"));
    put(zip, root + "index.html",
        buildIndex(title, cards, profiles, segments, datatypes, valuesets, cpSlug, segSlug, dtSlug, vsSlug, igId));

    for (int i = 0; i < cards.size(); i++) {
      Map<String, Object> card = cards.get(i);
      Map<String, Object> prev = i > 0 ? cards.get(i - 1) : null;
      Map<String, Object> next = i < cards.size() - 1 ? cards.get(i + 1) : null;
      put(zip, root + "sections/" + card.get("slug") + ".html", buildSectionPage(title, card, prev, next, cards,
          cardById, profiles, segments, datatypes, valuesets, cpSlug, segSlug, dtSlug, vsSlug, igId));
    }

    for (int i = 0; i < profiles.size(); i++) {
      ConformanceProfile cp = profiles.get(i);
      ConformanceProfile prev = i > 0 ? profiles.get(i - 1) : null;
      ConformanceProfile next = i < profiles.size() - 1 ? profiles.get(i + 1) : null;
      put(zip, root + "profiles/" + cpSlug.get(cp.getId()) + ".html", buildProfilePage(title, cp, prev, next,
          cards, profiles, segments, datatypes, valuesets, cpSlug, segSlug, dtSlug, vsSlug, segById, igId));
    }

    for (int i = 0; i < segments.size(); i++) {
      Segment seg = segments.get(i);
      Segment prev = i > 0 ? segments.get(i - 1) : null;
      Segment next = i < segments.size() - 1 ? segments.get(i + 1) : null;
      put(zip, root + "segments/" + segSlug.get(seg.getId()) + ".html", buildSegmentPage(title, seg, prev, next,
          cards, profiles, segments, datatypes, valuesets, cpSlug, segSlug, dtSlug, vsSlug, dtById, igId));
    }

    for (int i = 0; i < datatypes.size(); i++) {
      Datatype dt = datatypes.get(i);
      Datatype prev = i > 0 ? datatypes.get(i - 1) : null;
      Datatype next = i < datatypes.size() - 1 ? datatypes.get(i + 1) : null;
      put(zip, root + "datatypes/" + dtSlug.get(dt.getId()) + ".html", buildDatatypePage(title, dt, prev, next,
          cards, profiles, segments, datatypes, valuesets, cpSlug, segSlug, dtSlug, vsSlug, dtById, igId));
    }

    for (int i = 0; i < valuesets.size(); i++) {
      Valueset vs = valuesets.get(i);
      Valueset prev = i > 0 ? valuesets.get(i - 1) : null;
      Valueset next = i < valuesets.size() - 1 ? valuesets.get(i + 1) : null;
      put(zip, root + "valuesets/" + vsSlug.get(vs.getId()) + ".html", buildValuesetPage(title, vs, prev, next,
          cards, profiles, segments, datatypes, valuesets, cpSlug, segSlug, dtSlug, vsSlug, igId));
    }

    Map<String, byte[]> exampleFiles = new java.util.LinkedHashMap<>();
    exampleFiles.putAll(exampleData.collectStaticPages(model, configuration));
    // HIGHLIGHT governs visual context; raw snippet downloads carry extracted lines.
    // Use the shared collector's LINE mode for text only, leaving classic behavior intact.
    ExampleMessageExportConfiguration textConfiguration = objectMapper.convertValue(configuration, ExampleMessageExportConfiguration.class);
    textConfiguration.setSnippetMode(gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageSnippetExportMode.LINE);
    exampleFiles.putAll(exampleData.collectFiles(model, textConfiguration));
    for (Map.Entry<String, byte[]> file : exampleFiles.entrySet()) {
      zip.putNextEntry(new ZipEntry(root + file.getKey()));
      byte[] fileBytes = file.getValue();
      if (file.getKey().endsWith(".html")) {
        Document snapshot = Jsoup.parse(new String(fileBytes, StandardCharsets.UTF_8));
        snapshot.outputSettings().prettyPrint(false);
        for (org.jsoup.nodes.Element description : snapshot.select(".fr-exmsg-description")) {
          description.html(safeFragment(description.html()));
        }
        fileBytes = snapshot.outerHtml().getBytes(StandardCharsets.UTF_8);
      }
      zip.write(fileBytes);
      zip.closeEntry();
    }
    // A snippet-only citation may not collect its parent snapshot. Keep its name,
    // but do not publish a link to a file absent from this archive.
    for (Map.Entry<String, String> page : zip.pages.entrySet()) {
      Document html = Jsoup.parse(page.getValue());
      html.outputSettings().prettyPrint(false);
      for (org.jsoup.nodes.Element link : html.select(".fr-exmsg-info a[href]")) {
        String href = link.attr("href");
        if (href.startsWith("../")) {
          String target = java.net.URLDecoder.decode(href.substring(3).replace("+", "%2B"), "UTF-8");
          if (!exampleFiles.containsKey(target)) link.unwrap();
        }
      }
      zip.putNextEntry(new ZipEntry(page.getKey()));
      zip.write(html.outerHtml().getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    zip.close();
    final byte[] bytes = baos.toByteArray();
    return new ExportedFile(new ByteArrayInputStream(bytes), title + "-public-ig", igId, ExportFormat.HTML) {
      @Override
      public String getFileName() {
        String n = super.getFileName();
        return n.endsWith(".html") ? n.substring(0, n.length() - 5) + ".zip" : n;
      }
    };
  }

  private List<ConformanceProfile> loadProfiles(Ig ig) {
    List<ConformanceProfile> list = new ArrayList<ConformanceProfile>();
    if (ig.getConformanceProfileRegistry() == null || ig.getConformanceProfileRegistry().getChildren() == null) {
      return list;
    }
    for (Link link : ig.getConformanceProfileRegistry().getChildren()) {
      if (link == null || link.getId() == null) continue;
      ConformanceProfile cp = conformanceProfileService.findById(link.getId());
      if (cp != null) list.add(cp);
    }
    Collections.sort(list, new Comparator<ConformanceProfile>() {
      public int compare(ConformanceProfile a, ConformanceProfile b) {
        int c = nullSafe(a.getMessageType()).compareTo(nullSafe(b.getMessageType()));
        if (c != 0) return c;
        c = nullSafe(a.getEvent()).compareTo(nullSafe(b.getEvent()));
        if (c != 0) return c;
        return nullSafe(a.getName()).compareTo(nullSafe(b.getName()));
      }
    });
    return list;
  }

  private List<Segment> loadSegments(Ig ig) {
    List<Segment> list = new ArrayList<Segment>();
    if (ig.getSegmentRegistry() == null || ig.getSegmentRegistry().getChildren() == null) return list;
    for (Link link : ig.getSegmentRegistry().getChildren()) {
      if (link == null || link.getId() == null) continue;
      Segment s = segmentService.findById(link.getId());
      if (s != null) list.add(s);
    }
    Collections.sort(list, new Comparator<Segment>() {
      public int compare(Segment a, Segment b) {
        return labelOf(a).compareToIgnoreCase(labelOf(b));
      }
    });
    return list;
  }

  private List<Datatype> loadDatatypes(Ig ig) {
    List<Datatype> list = new ArrayList<Datatype>();
    if (ig.getDatatypeRegistry() == null || ig.getDatatypeRegistry().getChildren() == null) return list;
    for (Link link : ig.getDatatypeRegistry().getChildren()) {
      if (link == null || link.getId() == null) continue;
      Datatype d = datatypeService.findById(link.getId());
      if (d != null) list.add(d);
    }
    Collections.sort(list, new Comparator<Datatype>() {
      public int compare(Datatype a, Datatype b) {
        return labelOf(a).compareToIgnoreCase(labelOf(b));
      }
    });
    return list;
  }

  private List<Valueset> loadValuesets(Ig ig) {
    List<Valueset> list = new ArrayList<Valueset>();
    if (ig.getValueSetRegistry() == null || ig.getValueSetRegistry().getChildren() == null) return list;
    for (Link link : ig.getValueSetRegistry().getChildren()) {
      if (link == null || link.getId() == null) continue;
      Valueset v = valuesetService.findById(link.getId());
      if (v != null) list.add(v);
    }
    Collections.sort(list, new Comparator<Valueset>() {
      public int compare(Valueset a, Valueset b) {
        return nullSafe(a.getBindingIdentifier()).compareToIgnoreCase(nullSafe(b.getBindingIdentifier()));
      }
    });
    return list;
  }

  private static class PublicationArchive extends ZipOutputStream {
    final Map<String, String> pages = new java.util.LinkedHashMap<>();
    final IgDataModel model;
    final ExampleMessageExportConfiguration configuration;
    PublicationArchive(ByteArrayOutputStream output, IgDataModel model, ExampleMessageExportConfiguration configuration) {
      super(output);
      this.model = model;
      this.configuration = configuration;
    }
  }

  private void put(PublicationArchive zip, String name, String content) throws Exception {
    if (name.endsWith(".html")) {
      Document page = Jsoup.parse(content);
      page.outputSettings().prettyPrint(false);
      for (org.jsoup.nodes.Element script : page.select("script")) {
        if (!script.attr("src").equals("../assets/js/publication.js") && !script.attr("src").equals("assets/js/publication.js")) script.remove();
      }
      page.select("iframe,object,embed,form,base,svg,math,link:not([rel=stylesheet]),meta[http-equiv],input,textarea,select").remove();
      for (org.jsoup.nodes.Element element : page.getAllElements()) {
        for (org.jsoup.nodes.Attribute attribute : new ArrayList<>(element.attributes().asList())) {
          String nameLower = attribute.getKey().toLowerCase(java.util.Locale.ROOT);
          String valueLower = attribute.getValue().replaceAll("[\\x00-\\x20]", "").toLowerCase(java.util.Locale.ROOT);
          if (nameLower.startsWith("on") || nameLower.contains(":") || nameLower.equals("srcdoc") || ((nameLower.equals("href") || nameLower.equals("src")) &&
              (valueLower.startsWith("javascript:") || valueLower.startsWith("vbscript:") || valueLower.startsWith("data:text/html")))) element.removeAttr(attribute.getKey());
        }
      }
      // Technical artifact pre/post definitions may also contain the existing M widget.
      for (org.jsoup.nodes.Element widget : page.select(".fr-exmsg[data-message-id]")) {
        widget.before(publicationMarkup(exampleSerialization.rewriteNarrative(widget.outerHtml(), zip.model, zip.configuration)));
        widget.remove();
      }
      for (org.jsoup.nodes.Element heading : page.select("table th:not([scope])")) {
        heading.attr("scope", heading.parents().select("table.fr-exmsg-info").size() > 0 ? "row" : "col");
      }
      for (org.jsoup.nodes.Element active : page.select(".toc-item.active a.toc-link, .toc-branch.active > summary a.toc-link")) {
        active.attr("aria-current", "page");
      }
      for (org.jsoup.nodes.Element disabled : page.select("a.disabled")) {
        disabled.tagName("span").removeAttr("href").attr("aria-disabled", "true");
      }
      zip.pages.put(name, page.outerHtml());
      return;
    }
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  /** Both preview and ZIP use the same live references, configuration and renderer. */
  @Override
  public Map<String, Object> preview(Ig ig, ExampleMessageExportConfiguration configuration) throws Exception {
    IgDataModel model = igService.generateDataModel(ig);
    exampleData.populate(model, configuration);
    List<Map<String, Object>> cards = new ArrayList<>();
    List<String> order = new ArrayList<>();
    for (TextSection section : sorted(ig.getContent())) {
      walk(section, null, String.valueOf(section.getPosition()), cards, order, model, configuration);
    }
    // Preview snapshot links use authorized, same-origin downloads, rather than paths
    // that only exist inside the ZIP. Their content still comes from the configured renderer.
    Map<String, byte[]> availablePages = exampleData.collectStaticPages(model, configuration);
    Map<String, String> previewPaths = new HashMap<>();
    if (model.getExampleMessages() != null) {
      for (gov.nist.hit.hl7.igamt.ig.domain.datamodel.ExampleMessageDataModel example : model.getExampleMessages().values()) {
        String base = "/api/example-messages/" + ig.getId() + "/message/" + example.getModel().getId();
        if (availablePages.containsKey(ExampleMessageExportConfiguration.pagePath(example.getFilePath()))) previewPaths.put(ExampleMessageExportConfiguration.relativeHref(ExampleMessageExportConfiguration.pagePath(example.getFilePath())), previewSnapshotUrl(ig.getId(), example.getFilePath()));
        for (Map.Entry<String, String> snippet : example.getSnippetFilePaths().entrySet()) {
          if (availablePages.containsKey(ExampleMessageExportConfiguration.pagePath(snippet.getValue()))) previewPaths.put(ExampleMessageExportConfiguration.relativeHref(ExampleMessageExportConfiguration.pagePath(snippet.getValue())),
              previewSnapshotUrl(ig.getId(), snippet.getValue()));
        }
      }
    }
    for (Map<String, Object> card : cards) {
      Document html = Jsoup.parseBodyFragment(String.valueOf(card.get("narrativeHtml")));
      for (org.jsoup.nodes.Element link : html.select("a[href]")) {
        String href = link.attr("href");
        String path = href.startsWith("../") ? href.substring(3) : href;
        if (previewPaths.containsKey(path)) link.attr("href", previewPaths.get(path));
        else if (href.startsWith("../") && link.parents().select("table.fr-exmsg-info").size() > 0) link.unwrap();
      }
      card.put("narrativeHtml", html.body().html());
    }
    Map<String, Object> result = new HashMap<>();
    result.put("title", ig.getMetadata() == null ? "Implementation Guide" : ig.getMetadata().getTitle());
    result.put("cards", cards);
    result.put("order", order);
    return result;
  }

  private String previewSnapshotUrl(String igId, String path) throws Exception {
    return "/api/export/ig/" + igId + "/narrative-cards/example?path="
        + java.net.URLEncoder.encode(ExampleMessageExportConfiguration.pagePath(path), "UTF-8");
  }

  @Override
  public byte[] snapshot(Ig ig, ExampleMessageExportConfiguration configuration, String path) throws Exception {
    IgDataModel model = igService.generateDataModel(ig);
    exampleData.populate(model, configuration);
    List<Map<String, Object>> cards = new ArrayList<>();
    List<String> order = new ArrayList<>();
    for (TextSection section : sorted(ig.getContent())) {
      walk(section, null, String.valueOf(section.getPosition()), cards, order, model, configuration);
    }
    // Lookup only in generated archive entries, never on the filesystem.
    byte[] page = exampleData.collectStaticPages(model, configuration, false).get(path);
    if (page == null) throw new IllegalArgumentException("Snapshot is not included in this publication");
    Document snapshot = Jsoup.parse(new String(page, StandardCharsets.UTF_8));
    for (org.jsoup.nodes.Element description : snapshot.select(".fr-exmsg-description")) description.html(safeFragment(description.html()));
    return snapshot.outerHtml().getBytes(StandardCharsets.UTF_8);
  }

  private void walk(TextSection section, String parentId, String path, List<Map<String, Object>> cards,
      List<String> order, IgDataModel model, ExampleMessageExportConfiguration configuration) throws Exception {
    if (section == null) return;
    boolean messageSection = section.getType() == Type.MESSAGESECTION;
    if (messageSection && !configuration.isInclude()) return;
    if (section.getType() == Type.TEXT || messageSection) {
      List<TextSection> children = sorted(section.getChildren());
      List<String> childIds = new ArrayList<>();
      for (TextSection child : children) {
        if (child.getType() == Type.TEXT || (child.getType() == Type.MESSAGESECTION && configuration.isInclude())) childIds.add(child.getId());
      }
      String narrative = section.getDescription() != null ? section.getDescription() : "";
      narrative = froalaCleaner.cleanFroalaInput(narrative);
      narrative = exampleSerialization.rewriteNarrative(narrative, model, configuration);
      String title = section.getLabel() != null ? section.getLabel() : "Untitled section";
      if (messageSection) {
        Element reference = exampleSerialization.serializeReference(model, section.getMessageId(), section.getSnippetId(), configuration);
        if (reference != null) {
          narrative += renderExample(reference);
          if (title.trim().isEmpty() || "Message Example".equalsIgnoreCase(title.trim())) {
            title = "Message Example: " + reference.getAttributeValue("label");
          }
        }
      }
      Map<String, Object> card = new HashMap<>();
      card.put("id", section.getId());
      card.put("number", path);
      card.put("title", title);
      card.put("slug", slugify(section.getId()));
      card.put("parentId", parentId);
      card.put("narrativeHtml", publicationMarkup(narrative));
      List<NarrativeRequirement> requirements = section.getRequirements() == null
          ? new ArrayList<>() : new ArrayList<>(section.getRequirements());
      requirements.removeIf(java.util.Objects::isNull);
      requirements.sort(Comparator.comparingInt(NarrativeRequirement::getPosition));
      card.put("requirements", requirements);
      card.put("childIds", childIds);
      cards.add(card);
      order.add(section.getId());
      for (TextSection child : children) {
        walk(child, section.getId(), path + "." + child.getPosition(), cards, order, model, configuration);
      }
      return;
    }
    for (TextSection child : sorted(section.getChildren())) {
      walk(child, parentId, path + "." + child.getPosition(), cards, order, model, configuration);
    }
  }

  private String renderExample(Element reference) throws Exception {
    // Reuse the exact classic fragment stylesheet without modifying that pipeline.
    try (java.io.InputStream stylesheet = getClass().getResourceAsStream(
        "/templates/profile/conformanceProfile/exampleMessage.xsl")) {
      Transformer transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(stylesheet));
      transformer.setOutputProperty("omit-xml-declaration", "yes");
      transformer.setOutputProperty("method", "html");
      StringWriter result = new StringWriter();
      transformer.transform(new StreamSource(new StringReader(reference.toXML())), new StreamResult(result));
      return result.toString();
    }
  }

  private String safeFragment(String value) {
    org.jsoup.safety.Whitelist allowed = org.jsoup.safety.Whitelist.relaxed()
        .addTags("div", "span", "mark", "hr", "figure", "figcaption", "caption", "colgroup", "col")
        .addAttributes(":all", "id", "class", "title", "lang", "dir")
        .addAttributes("th", "scope", "headers").addAttributes("td", "headers")
        .addProtocols("img", "src", "data").preserveRelativeLinks(true);
    return Jsoup.clean(value, "https://igamt-public.invalid/", allowed, new Document.OutputSettings().prettyPrint(false));
  }

  private String publicationMarkup(String value) {
    Document html = Jsoup.parseBodyFragment(safeFragment(value));
    html.outputSettings().prettyPrint(false);
    html.select("script,iframe,object,embed,form,style").remove();
    for (org.jsoup.nodes.Element element : html.getAllElements()) {
      for (org.jsoup.nodes.Attribute attribute : new ArrayList<>(element.attributes().asList())) {
        String name = attribute.getKey().toLowerCase(java.util.Locale.ROOT);
        String content = attribute.getValue().trim().replaceAll("[\\x00-\\x20]", "").toLowerCase(java.util.Locale.ROOT);
        if (name.startsWith("on") || ((name.equals("href") || name.equals("src")) &&
            (content.startsWith("javascript:") || content.startsWith("vbscript:") || content.startsWith("data:text/html")))) {
          element.removeAttr(attribute.getKey());
        }
      }
    }
    // Shared services emit snapshot paths relative to the guide root. Cards are one level down.
    for (org.jsoup.nodes.Element link : html.select(".fr-exmsg-info a[href]")) {
      String href = link.attr("href");
      if (!href.startsWith("/") && !href.startsWith("../") && !href.startsWith("#") && !href.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
        link.attr("href", "../" + href);
      }
    }
    return html.body().html();
  }

  private List<TextSection> sorted(Set<TextSection> set) {
    if (set == null || set.isEmpty()) return Collections.emptyList();
    List<TextSection> list = new ArrayList<TextSection>(set);
    Collections.sort(list, new Comparator<TextSection>() {
      public int compare(TextSection a, TextSection b) {
        return Integer.compare(a.getPosition(), b.getPosition());
      }
    });
    return list;
  }

  private String slugify(String... parts) {
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      if (p == null || p.isEmpty()) continue;
      if (sb.length() > 0) sb.append('-');
      sb.append(p);
    }
    String base = sb.toString().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    if (base.length() > 90) base = base.substring(0, 90);
    return base.isEmpty() ? "item" : base;
  }

  private String esc(Object o) {
    String s = o == null ? "" : String.valueOf(o);
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  private String nullSafe(String s) { return s == null ? "" : s; }

  private String labelOf(Segment s) {
    if (s == null) return "";
    try { return s.getLabel() != null ? s.getLabel() : s.getName(); } catch (Exception e) {
      return s.getExt() != null && !s.getExt().isEmpty() ? s.getName() + "_" + s.getExt() : s.getName();
    }
  }

  private String labelOf(Datatype d) {
    if (d == null) return "";
    try { return d.getLabel() != null ? d.getLabel() : d.getName(); } catch (Exception e) {
      return d.getExt() != null && !d.getExt().isEmpty() ? d.getName() + "_" + d.getExt() : d.getName();
    }
  }

  private String usageBadge(Object usage) {
    String u = usage == null ? "?" : (usage instanceof Usage ? ((Usage) usage).getValue() : String.valueOf(usage));
    String cls = "usage-O";
    if ("R".equals(u)) cls = "usage-R";
    else if ("RE".equals(u)) cls = "usage-RE";
    else if ("C".equals(u) || "CE".equals(u) || "C(A/B)".equals(u)) cls = "usage-C";
    else if ("X".equals(u)) cls = "usage-X";
    else if ("B".equals(u)) cls = "usage-B";
    return "<span class=\"usage " + cls + "\">" + esc(u) + "</span>";
  }

  private String typeBadge(String kind) {
    String k = kind == null ? "" : kind.trim().toUpperCase().replace(" ", "");
    String cls = "badge-other";
    String label = kind == null ? "Item" : kind;
    if ("SEGMENTREF".equals(k) || "SEGMENT".equals(k)) { cls = "badge-seg"; label = "Segment"; }
    else if ("GROUP".equals(k)) { cls = "badge-grp"; label = "Group"; }
    else if ("PROFILE".equals(k) || "MESSAGE".equals(k)) { cls = "badge-profile"; label = "Profile"; }
    else if ("DATATYPE".equals(k)) { cls = "badge-dt"; label = "Datatype"; }
    else if ("COMPONENT".equals(k)) { cls = "badge-comp"; label = "Component"; }
    else if ("FIELD".equals(k)) { cls = "badge-field"; label = "Field"; }
    else if ("VALUESET".equals(k)) { cls = "badge-vs"; label = "Value set"; }
    else if ("NARRATIVE".equals(k) || "SECTION".equals(k) || "TEXT".equals(k)) { cls = "badge-nar"; label = "Narrative"; }
    return "<span class=\"type-badge " + cls + "\">" + esc(label) + "</span>";
  }


  private String scopeBadge(gov.nist.hit.hl7.igamt.common.base.domain.DomainInfo di,
      gov.nist.hit.hl7.igamt.common.base.domain.ResourceOrigin origin) {
    if (di == null || di.getScope() == null) return "";
    String scope = di.getScope().name();
    String version = di.getVersion() != null ? di.getVersion() : "";
    String originName = origin != null ? origin.name() : "";
    if ("USER".equals(scope)) {
      String shortLabel = "PHINVADS".equals(originName) ? "PHIN" : ("CSV".equals(originName) ? "CSV" : "USR");
      String ver = "PHINVADS".equals(originName) || version.isEmpty() ? ""
          : "<span class=\"scope-ver\">" + esc(version) + "</span>";
      return "<span class=\"scope-badge scope-user\">" + shortLabel + ver + "</span>";
    }
    if ("HL7STANDARD".equals(scope)) {
      String cls = "2.x".equals(version) ? "scope-hl7-dyn" : "scope-hl7";
      String ver = version.isEmpty() ? "" : "<span class=\"scope-ver\">" + esc(version) + "</span>";
      return "<span class=\"scope-badge " + cls + "\">HL7" + ver + "</span>";
    }
    if ("USERCUSTOM".equals(scope)) {
      String ver = version.isEmpty() ? "" : "<span class=\"scope-ver\">" + esc(version) + "</span>";
      return "<span class=\"scope-badge scope-custom\">CUSTOM" + ver + "</span>";
    }
    if ("PHINVADS".equals(scope)) {
      return "<span class=\"scope-badge scope-phin\">PHIN</span>";
    }
    if ("SDTF".equals(scope)) {
      String ver = version.isEmpty() ? "" : "<span class=\"scope-ver\">" + esc(version) + "</span>";
      return "<span class=\"scope-badge scope-hl7-dyn\">SDTF" + ver + "</span>";
    }
    String ver = version.isEmpty() ? "" : "<span class=\"scope-ver\">" + esc(version) + "</span>";
    return "<span class=\"scope-badge scope-other\">" + esc(scope) + ver + "</span>";
  }

  private String digitalIdentity(String fixedName, String variableName) {
    boolean hasFixed = fixedName != null && !fixedName.trim().isEmpty();
    boolean hasVar = variableName != null && !variableName.trim().isEmpty();
    if (hasFixed && hasVar) return fixedName + "_" + variableName;
    if (hasFixed) return fixedName;
    if (hasVar) return variableName;
    return "";
  }

  private String scopeName(gov.nist.hit.hl7.igamt.common.base.domain.DomainInfo di) {
    return di != null && di.getScope() != null ? di.getScope().name() : "";
  }

  private String scopeVersion(gov.nist.hit.hl7.igamt.common.base.domain.DomainInfo di) {
    return di != null && di.getVersion() != null ? di.getVersion() : "";
  }


  private String refId(Ref ref) {
    return ref == null ? null : ref.getId();
  }

  private String dtLink(String rid, Map<String, Datatype> dtById, Map<String, String> dtSlug, String prefix) {
    if (rid == null) return "<span class=\"empty\">—</span>";
    Datatype d = dtById.get(rid);
    if (d != null && dtSlug.containsKey(rid)) {
      return "<a class=\"mono\" href=\"" + prefix + "datatypes/" + esc(dtSlug.get(rid)) + ".html\">"
          + esc(labelOf(d)) + "</a>";
    }
    Datatype loaded = null;
    try { loaded = datatypeService.findById(rid); } catch (Exception e) { /* ignore */ }
    if (loaded != null) return "<span class=\"mono\">" + esc(labelOf(loaded)) + "</span>";
    return "<span class=\"mono\">" + esc(rid) + "</span>";
  }

  private String segLink(String rid, Map<String, Segment> segById, Map<String, String> segSlug, String prefix) {
    if (rid == null) return "<span class=\"empty\">—</span>";
    Segment s = segById.get(rid);
    if (s != null) {
      return "<a class=\"mono\" href=\"" + prefix + "segments/" + esc(segSlug.get(rid)) + ".html\">"
          + esc(labelOf(s)) + "</a>";
    }
    return "<span class=\"mono\">" + esc(rid) + "</span>";
  }

  private String buildReadme(String title, String igId, int n, int p, int s, int d, int v) {
    return "IGAMT public web IG export\n"
        + "=========================\n"
        + "Title: " + title + "\n"
        + "IG id: " + igId + "\n\n"
        + "Narratives: " + n + "\n"
        + "Message profiles: " + p + "\n"
        + "Segments: " + s + "\n"
        + "Datatypes: " + d + "\n"
        + "Value sets: " + v + "\n\n"
        + "Open index.html in a browser.\n"
        + "Classic Word/HTML document export is unchanged.\n";
  }

  private String buildIndex(String title, List<Map<String, Object>> cards, List<ConformanceProfile> profiles,
      List<Segment> segments, List<Datatype> datatypes, List<Valueset> valuesets, Map<String, String> cpSlug,
      Map<String, String> segSlug, Map<String, String> dtSlug, Map<String, String> vsSlug, String igId) {
    StringBuilder profileLinks = new StringBuilder();
    for (ConformanceProfile cp : profiles) {
      profileLinks.append("<a href=\"profiles/").append(esc(cpSlug.get(cp.getId()))).append(".html\"><span class=\"k\">")
          .append(esc(cp.getMessageType())).append("^").append(esc(cp.getEvent()))
          .append("</span><span class=\"s\">").append(esc(cp.getName())).append("</span></a>");
    }
    String firstSection = cards.isEmpty() ? "#" : ("sections/" + cards.get(0).get("slug") + ".html");
    String firstProfile = profiles.isEmpty() ? "#" : ("profiles/" + cpSlug.get(profiles.get(0).getId()) + ".html");
    String body = "<div class=\"page\"><p class=\"crumbs\">Public Implementation Guide</p><h1>" + esc(title)
        + "</h1><div class=\"meta\"><span class=\"chip cyan\">IGAMT export</span><span class=\"chip\">id "
        + esc(igId) + "</span></div>"
        + "<p class=\"lede\">Web-first publication: narratives, message structures, segments, datatypes, and value sets from live IGAMT data.</p>"
        + "<div class=\"stats\">"
        + "<div class=\"stat\"><div class=\"n\">" + cards.size() + "</div><div class=\"l\">Narratives</div></div>"
        + "<div class=\"stat\"><div class=\"n\">" + profiles.size() + "</div><div class=\"l\">Profiles</div></div>"
        + "<div class=\"stat\"><div class=\"n\">" + segments.size() + "</div><div class=\"l\">Segments</div></div>"
        + "<div class=\"stat\"><div class=\"n\">" + datatypes.size() + "</div><div class=\"l\">Datatypes</div></div>"
        + "<div class=\"stat\"><div class=\"n\">" + valuesets.size() + "</div><div class=\"l\">Value sets</div></div>"
        + "</div><div class=\"section-card\"><h2>Start here</h2><div class=\"grid-links\">"
        + "<a href=\"" + esc(firstSection) + "\"><span class=\"k\">Narratives</span><span class=\"s\">Ig.content sections</span></a>"
        + "<a href=\"" + esc(firstProfile) + "\"><span class=\"k\">Message structures</span><span class=\"s\">Conformance profiles</span></a>"
        + "</div></div><div class=\"section-card\"><h2>Message profiles</h2><div class=\"grid-links\">"
        + profileLinks + "</div></div>"
        + "<p class=\"note\">Classic Word/HTML document export is unchanged.</p></div>";
    return shell(title + " — Public IG", null, null, body, "", cards, profiles, segments, datatypes, valuesets,
        cpSlug, segSlug, dtSlug, vsSlug);
  }

  private String buildSectionPage(String guideTitle, Map<String, Object> card, Map<String, Object> prev,
      Map<String, Object> next, List<Map<String, Object>> all, Map<String, Map<String, Object>> byId,
      List<ConformanceProfile> profiles, List<Segment> segments, List<Datatype> datatypes,
      List<Valueset> valuesets, Map<String, String> cpSlug, Map<String, String> segSlug,
      Map<String, String> dtSlug, Map<String, String> vsSlug, String igId) {
    List<Map<String, Object>> chain = new ArrayList<Map<String, Object>>();
    Map<String, Object> cur = card;
    while (cur != null) {
      chain.add(0, cur);
      Object pid = cur.get("parentId");
      cur = pid != null ? byId.get(String.valueOf(pid)) : null;
    }
    StringBuilder crumbs = new StringBuilder();
    for (int i = 0; i < chain.size(); i++) {
      Map<String, Object> c = chain.get(i);
      if (i < chain.size() - 1) {
        crumbs.append("<a href=\"").append(esc(c.get("slug"))).append(".html\">").append(esc(c.get("number")))
            .append(" ").append(esc(c.get("title"))).append("</a><span class=\"sep\">/</span>");
      } else {
        crumbs.append("<span>").append(esc(c.get("number"))).append(" ").append(esc(c.get("title"))).append("</span>");
      }
    }
    @SuppressWarnings("unchecked")
    List<String> childIds = (List<String>) card.get("childIds");
    StringBuilder kids = new StringBuilder();
    if (childIds != null && !childIds.isEmpty()) {
      kids.append("<section class=\"children\"><h2>In this section</h2><ul>");
      for (String cid : childIds) {
        Map<String, Object> k = byId.get(cid);
        if (k != null) {
          kids.append("<li><a href=\"").append(esc(k.get("slug"))).append(".html\"><span class=\"num\">")
              .append(esc(k.get("number"))).append("</span> ").append(esc(k.get("title"))).append("</a></li>");
        }
      }
      kids.append("</ul></section>");
    }
    String prevA = prev == null ? "<a class=\"disabled\" href=\"#\">← Previous</a>"
        : "<a href=\"" + esc(prev.get("slug")) + ".html\">← Previous: " + esc(prev.get("number")) + "</a>";
    String nextA = next == null ? "<a class=\"disabled\" href=\"#\">Next →</a>"
        : "<a href=\"" + esc(next.get("slug")) + ".html\">Next: " + esc(next.get("number")) + " →</a>";
    Object narrativeObj = card.get("narrativeHtml");
    String narrative = narrativeObj != null && String.valueOf(narrativeObj).length() > 0
        ? String.valueOf(narrativeObj) : "<em class=\"empty\">No narrative text yet for this section.</em>";
    StringBuilder requirements = new StringBuilder();
    @SuppressWarnings("unchecked")
    List<NarrativeRequirement> statements = (List<NarrativeRequirement>) card.get("requirements");
    if (statements != null && !statements.isEmpty()) {
      requirements.append("<section class=\"requirements\" aria-label=\"Conformance statements\"><h2>Conformance statements</h2><ol>");
      for (NarrativeRequirement requirement : statements) {
        requirements.append("<li id=\"req-").append(esc(requirement.getId())).append("\" tabindex=\"-1\"><strong class=\"verb-badge\">")
          .append(esc(requirement.getVerb())).append("</strong> ").append(esc(requirement.getText()))
          .append(" <a aria-label=\"Link to this requirement\" href=\"#req-").append(esc(requirement.getId())).append("\">Permalink</a></li>");
      }
      requirements.append("</ol></section>");
    }
    String body = "<div class=\"page\"><div class=\"crumbs\"><a href=\"../index.html\">Home</a><span class=\"sep\">/</span>"
        + crumbs + "</div><h1><span class=\"num\">" + esc(card.get("number")) + "</span> " + esc(card.get("title"))
        + "</h1><section class=\"narrative\">" + narrative + "</section>" + requirements + kids + "<footer class=\"nav\">" + prevA
        + nextA + "</footer></div>";
    return shell(esc(card.get("number")) + " " + esc(card.get("title")) + " — " + esc(guideTitle),
        "section", String.valueOf(card.get("id")), body, "../", all, profiles, segments, datatypes, valuesets,
        cpSlug, segSlug, dtSlug, vsSlug);
  }

  private int countCpNodes(Set<SegmentRefOrGroup> nodes) {
    if (nodes == null) return 0;
    int n = 0;
    for (SegmentRefOrGroup child : nodes) {
      n++;
      if (child instanceof Group) {
        n += countCpNodes(((Group) child).getChildren());
      }
    }
    return n;
  }

  private String renderStructTree(Set<SegmentRefOrGroup> nodes, int depth, Map<String, Segment> segById,
      Map<String, String> segSlug) {
    if (nodes == null || nodes.isEmpty()) return "";
    List<SegmentRefOrGroup> list = new ArrayList<SegmentRefOrGroup>(nodes);
    Collections.sort(list, new Comparator<SegmentRefOrGroup>() {
      public int compare(SegmentRefOrGroup a, SegmentRefOrGroup b) {
        return Integer.compare(a.getPosition(), b.getPosition());
      }
    });
    StringBuilder items = new StringBuilder();
    for (SegmentRefOrGroup n : list) {
      String ntype = n.getType() != null ? n.getType().name() : "";
      String rid = null;
      if (n instanceof SegmentRef) {
        rid = refId(((SegmentRef) n).getRef());
      }
      String nameHtml;
      if ("SEGMENTREF".equals(ntype) && rid != null) {
        nameHtml = segLink(rid, segById, segSlug, "../");
      } else if ("GROUP".equals(ntype)) {
        nameHtml = "<strong>" + esc(n.getName() != null ? n.getName() : "Group") + "</strong>";
      } else {
        nameHtml = esc(n.getName());
      }
      String meta = typeBadge(ntype) + "<span class=\"struct-name\">"
          + nameHtml + "</span><span>" + usageBadge(n.getUsage()) + "</span><span class=\"mono\">"
          + esc(n.getMin()) + "</span><span class=\"mono\">" + esc(n.getMax()) + "</span>";
      boolean hasKids = n instanceof Group && ((Group) n).getChildren() != null
          && !((Group) n).getChildren().isEmpty();
      if ("GROUP".equals(ntype) || hasKids) {
        String openAttr = depth < 1 ? " open" : "";
        String inner = hasKids
            ? renderStructTree(((Group) n).getChildren(), depth + 1, segById, segSlug)
            : "";
        items.append("<li><details class=\"struct-branch group-row\"").append(openAttr).append(">")
            .append("<summary class=\"struct-row\"><span class=\"twisty\">▼</span>").append(meta)
            .append("</summary><ul class=\"struct-tree\">").append(inner).append("</ul></details></li>");
      } else {
        items.append("<li class=\"struct-leaf\"><div class=\"struct-row\"><span class=\"twisty-spacer\"></span>")
            .append(meta).append("</div></li>");
      }
    }
    return items.toString();
  }

  private String buildProfilePage(String guideTitle, ConformanceProfile cp, ConformanceProfile prev,
      ConformanceProfile next, List<Map<String, Object>> cards, List<ConformanceProfile> profiles,
      List<Segment> segments, List<Datatype> datatypes, List<Valueset> valuesets, Map<String, String> cpSlug,
      Map<String, String> segSlug, Map<String, String> dtSlug, Map<String, String> vsSlug,
      Map<String, Segment> segById, String igId) {
    String treeBody = renderStructTree(cp.getChildren(), 0, segById, segSlug);
    int nodeCount = countCpNodes(cp.getChildren());
    String table = treeBody.isEmpty() ? "<p class=\"empty\">No structure children.</p>"
        : "<div class=\"struct-head\"><span></span><span>Type</span><span>Segment / Group</span><span>Usage</span><span>Min</span><span>Max</span></div>"
            + "<ul class=\"struct-tree\" id=\"msg-struct-tree\">" + treeBody + "</ul>";
    String expandBtns = "<span class=\"tree-tools\">"
        + "<button type=\"button\" id=\"struct-expand-all\">Expand all</button>"
        + "<button type=\"button\" id=\"struct-collapse-all\">Collapse all</button></span>";
    String prevA = prev == null ? "<a class=\"disabled\" href=\"#\">← Previous</a>"
        : "<a href=\"" + esc(cpSlug.get(prev.getId())) + ".html\">← " + esc(prev.getMessageType()) + "^"
            + esc(prev.getEvent()) + "</a>";
    String nextA = next == null ? "<a class=\"disabled\" href=\"#\">Next →</a>"
        : "<a href=\"" + esc(cpSlug.get(next.getId())) + ".html\">" + esc(next.getMessageType()) + "^"
            + esc(next.getEvent()) + " →</a>";
    String body = "<div class=\"page\"><div class=\"crumbs\"><a href=\"../index.html\">Home</a><span class=\"sep\">/</span><span>Message profiles</span></div>"
        + "<h1>" + esc(cp.getName()) + "</h1>" + "<div class=\"identity-row\">" + scopeBadge(cp.getDomainInfo(), cp.getResourceOrigin()) + "<span class=\"dig-id\">" + esc(cp.getName()) + "</span></div>" + "<div class=\"meta\">" + typeBadge("PROFILE") + "<span class=\"chip cyan\">" + esc(cp.getMessageType()) + "^" + esc(cp.getEvent()) + "</span><span class=\"chip\">" + esc(scopeName(cp.getDomainInfo())) + "</span><span class=\"chip\">HL7 " + esc(scopeVersion(cp.getDomainInfo())) + "</span><span class=\"chip\">" + nodeCount + " structure nodes</span></div><div class=\"section-card\"><h2>" + typeBadge("PROFILE") + " Message structure " + expandBtns
        + "</h2>" + table + "</div><footer class=\"nav\">" + prevA + nextA + "</footer></div>";
    return shell(esc(cp.getMessageType()) + "^" + esc(cp.getEvent()) + " — " + esc(guideTitle), "profile",
        cp.getId(), body, "../", cards, profiles, segments, datatypes, valuesets, cpSlug, segSlug, dtSlug, vsSlug);
  }


  private String sectionCard(String title, String inner) {
    if (inner == null || inner.isEmpty()) return "";
    return "<div class=\"section-card\"><h2>" + title + "</h2>" + inner + "</div>";
  }

  private String vsLink(String vid, Map<String, String> vsSlug, Map<String, Valueset> vsById, String prefix) {
    if (vid == null || vid.isEmpty()) return "";
    Valueset v = vsById != null ? vsById.get(vid) : null;
    if (v == null) {
      for (Valueset cand : vsById != null ? vsById.values() : java.util.Collections.<Valueset>emptyList()) {
        if (vid.equals(cand.getBindingIdentifier())) { v = cand; break; }
      }
    }
    if (v == null) return "<span class=\"vs-chip\">" + esc(vid) + "</span>";
    String slug = vsSlug.get(v.getId());
    String bid = v.getBindingIdentifier() != null ? v.getBindingIdentifier() : v.getId();
    return "<a class=\"vs-chip\" href=\"" + prefix + "valuesets/" + esc(slug) + ".html\">" + esc(bid) + "</a>";
  }

  private String strengthBadge(ValuesetStrength s) {
    if (s == null) return "";
    String code = s.name();
    String label = s.value != null ? s.value : code;
    return "<span class=\"strength strength-" + esc(code) + "\">" + esc(label) + "</span>";
  }

  private String formatVsBindings(Set<ValuesetBinding> binds, Map<String, String> vsSlug,
      Map<String, Valueset> vsById, String prefix) {
    if (binds == null || binds.isEmpty()) return "—";
    StringBuilder sb = new StringBuilder();
    for (ValuesetBinding vb : binds) {
      if (vb.getValueSets() == null) continue;
      for (String vid : vb.getValueSets()) {
        sb.append(vsLink(vid, vsSlug, vsById, prefix));
        sb.append(strengthBadge(vb.getStrength()));
        if (vb.getValuesetLocations() != null && !vb.getValuesetLocations().isEmpty()) {
          sb.append(" <span style=\"color:var(--muted);font-size:10px\">@");
          boolean first = true;
          for (Integer loc : vb.getValuesetLocations()) {
            if (!first) sb.append(",");
            sb.append(loc);
            first = false;
          }
          sb.append("</span>");
        }
        sb.append(" ");
      }
    }
    String out = sb.toString().trim();
    return out.isEmpty() ? "—" : out;
  }

  private String lengthDisplay(String minLen, String maxLen) {
    boolean a = minLen != null && !minLen.isEmpty() && !"NA".equals(minLen);
    boolean b = maxLen != null && !maxLen.isEmpty() && !"NA".equals(maxLen);
    if (!a && !b) return "—";
    if (a && b) return minLen + ".." + maxLen;
    return a ? minLen : maxLen;
  }

  private String confLengthDisplay(String lengthType, String confLength) {
    if (confLength == null || confLength.isEmpty() || "NA".equals(confLength)) return "—";
    if (lengthType != null && "UNSET".equals(lengthType)) return "—";
    return confLength;
  }

  private String formatComments(java.util.Collection<? extends Comment> comments) {
    if (comments == null || comments.isEmpty()) return "—";
    StringBuilder sb = new StringBuilder();
    for (Comment c : comments) {
      if (c == null || c.getDescription() == null || c.getDescription().isEmpty()) continue;
      if (sb.length() > 0) sb.append("<br/>");
      sb.append(esc(c.getDescription()));
    }
    return sb.length() == 0 ? "—" : sb.toString();
  }

  private StructureElementBinding findBindingChild(ResourceBinding binding, String elementId, int position) {
    if (binding == null || binding.getChildren() == null) return null;
    return findBindingChild(binding.getChildren(), elementId, position);
  }

  private StructureElementBinding findBindingChild(Set<StructureElementBinding> nodes, String elementId, int position) {
    if (nodes == null) return null;
    for (StructureElementBinding n : nodes) {
      if (elementId != null && elementId.equals(n.getElementId())) return n;
      if (n.getLocationInfo() != null && n.getLocationInfo().getPosition() == position) return n;
      StructureElementBinding found = findBindingChild(n.getChildren(), elementId, position);
      if (found != null) return found;
    }
    return null;
  }

  private String renderVsBindingTable(ResourceBinding binding, String rootLabel, Map<String, String> vsSlug,
      Map<String, Valueset> vsById, String prefix) {
    if (binding == null || binding.getChildren() == null) return "";
    StringBuilder rows = new StringBuilder();
    renderVsBindingRows(binding.getChildren(), rootLabel, rows, vsSlug, vsById, prefix);
    if (rows.length() == 0) return "";
    return "<table class=\"struct\"><thead><tr><th>Location</th><th>Name</th><th>Value Set ID</th><th>Binding Strength</th><th>Binding Location</th></tr></thead><tbody>"
        + rows + "</tbody></table>";
  }

  private void renderVsBindingRows(Set<StructureElementBinding> nodes, String rootLabel, StringBuilder rows,
      Map<String, String> vsSlug, Map<String, Valueset> vsById, String prefix) {
    if (nodes == null) return;
    for (StructureElementBinding n : nodes) {
      String loc = rootLabel;
      String name = "";
      if (n.getLocationInfo() != null) {
        name = n.getLocationInfo().getName() != null ? n.getLocationInfo().getName() : "";
        int pos = n.getLocationInfo().getPosition();
        String typ = n.getLocationInfo().getType() != null ? n.getLocationInfo().getType().name() : "";
        if ("COMPONENT".equals(typ) || "SUBCOMPONENT".equals(typ)) loc = rootLabel + "." + pos;
        else loc = rootLabel + "-" + pos;
      } else if (n.getElementId() != null) {
        loc = rootLabel + "-" + n.getElementId();
      }
      if (n.getValuesetBindings() != null) {
        for (ValuesetBinding vb : n.getValuesetBindings()) {
          if (vb.getValueSets() == null) continue;
          String locDisp = "—";
          if (vb.getValuesetLocations() != null && !vb.getValuesetLocations().isEmpty()) {
            StringBuilder lb = new StringBuilder();
            for (Integer i : vb.getValuesetLocations()) {
              if (lb.length() > 0) lb.append(",");
              lb.append(i);
            }
            locDisp = lb.toString();
          }
          for (String vid : vb.getValueSets()) {
            rows.append("<tr><td class=\"mono\">").append(esc(loc)).append("</td><td>").append(esc(name))
                .append("</td><td>").append(vsLink(vid, vsSlug, vsById, prefix)).append("</td><td>")
                .append(strengthBadge(vb.getStrength())).append("</td><td class=\"mono\">")
                .append(esc(locDisp)).append("</td></tr>");
          }
        }
      }
      renderVsBindingRows(n.getChildren(), rootLabel, rows, vsSlug, vsById, prefix);
    }
  }

  private String renderPredicateTable(ResourceBinding binding, String rootLabel) {
    if (binding == null || binding.getChildren() == null) return "";
    StringBuilder rows = new StringBuilder();
    renderPredicateRows(binding.getChildren(), rootLabel, rows);
    if (rows.length() == 0) return "";
    return "<table class=\"struct\"><thead><tr><th>Location</th><th>Name</th><th>True Usage</th><th>False Usage</th><th>Condition</th></tr></thead><tbody>"
        + rows + "</tbody></table>";
  }

  private void renderPredicateRows(Set<StructureElementBinding> nodes, String rootLabel, StringBuilder rows) {
    if (nodes == null) return;
    for (StructureElementBinding n : nodes) {
      Predicate pred = n.getPredicate();
      if (pred != null) {
        String loc = rootLabel;
        String name = "";
        if (n.getLocationInfo() != null) {
          name = n.getLocationInfo().getName() != null ? n.getLocationInfo().getName() : "";
          int pos = n.getLocationInfo().getPosition();
          String typ = n.getLocationInfo().getType() != null ? n.getLocationInfo().getType().name() : "";
          if ("COMPONENT".equals(typ) || "SUBCOMPONENT".equals(typ)) loc = rootLabel + "." + pos;
          else loc = rootLabel + "-" + pos;
        }
        String desc = "";
        if (pred instanceof AssertionPredicate) {
          AssertionPredicate ap = (AssertionPredicate) pred;
          if (ap.getAssertion() != null) desc = nullSafe(ap.getAssertion().getDescription());
        }
        rows.append("<tr><td class=\"mono\">").append(esc(loc)).append("</td><td>").append(esc(name))
            .append("</td><td>").append(usageBadge(pred.getTrueUsage())).append("</td><td>")
            .append(usageBadge(pred.getFalseUsage())).append("</td><td class=\"wrap\">")
            .append(esc(desc)).append("</td></tr>");
      }
      renderPredicateRows(n.getChildren(), rootLabel, rows);
    }
  }

  private String renderCsTable(ResourceBinding binding) {
    if (binding == null || binding.getConformanceStatements() == null || binding.getConformanceStatements().isEmpty()) {
      return "";
    }
    StringBuilder rows = new StringBuilder();
    for (ConformanceStatement cs : binding.getConformanceStatements()) {
      String desc = "";
      if (cs instanceof AssertionConformanceStatement) {
        AssertionConformanceStatement acs = (AssertionConformanceStatement) cs;
        if (acs.getAssertion() != null) desc = nullSafe(acs.getAssertion().getDescription());
      }
      rows.append("<tr><td class=\"mono\">").append(esc(cs.getIdentifier())).append("</td><td class=\"wrap\">")
          .append(esc(desc)).append("</td></tr>");
    }
    return "<table class=\"struct\"><thead><tr><th>ID</th><th>Description</th></tr></thead><tbody>"
        + rows + "</tbody></table>";
  }

  private String renderPrePost(Resource res) {
    StringBuilder sb = new StringBuilder();
    if (res.getPreDef() != null && !res.getPreDef().trim().isEmpty()) {
      sb.append(sectionCard("Pre-definition", "<div class=\"def-text\">" + res.getPreDef() + "</div>"));
    }
    if (res.getPostDef() != null && !res.getPostDef().trim().isEmpty()) {
      sb.append(sectionCard("Post-definition", "<div class=\"def-text\">" + res.getPostDef() + "</div>"));
    }
    return sb.toString();
  }

  private String buildSegmentPage(String guideTitle, Segment seg, Segment prev, Segment next,
      List<Map<String, Object>> cards, List<ConformanceProfile> profiles, List<Segment> segments,
      List<Datatype> datatypes, List<Valueset> valuesets, Map<String, String> cpSlug,
      Map<String, String> segSlug, Map<String, String> dtSlug, Map<String, String> vsSlug,
      Map<String, Datatype> dtById, String igId) {
    List<Field> fields = new ArrayList<Field>();
    if (seg.getChildren() != null) fields.addAll(seg.getChildren());
    Collections.sort(fields, new Comparator<Field>() {
      public int compare(Field a, Field b) { return Integer.compare(a.getPosition(), b.getPosition()); }
    });
    Map<String, Valueset> vsById = new HashMap<String, Valueset>();
    for (Valueset v : valuesets) vsById.put(v.getId(), v);
    ResourceBinding binding = seg.getBinding();
    StringBuilder trs = new StringBuilder();
    for (Field f : fields) {
      StructureElementBinding seb = findBindingChild(binding, f.getId(), f.getPosition());
      String vsCell = formatVsBindings(seb != null ? seb.getValuesetBindings() : null, vsSlug, vsById, "../");
      String usageCell = usageBadge(f.getUsage());
      if (seb != null && seb.getPredicate() != null) {
        usageCell = usageCell + "<div style=\"margin-top:4px;font-size:10px;color:var(--muted)\">C: "
            + usageBadge(seb.getPredicate().getTrueUsage()) + "/"
            + usageBadge(seb.getPredicate().getFalseUsage()) + "</div>";
      }
      String card = esc(f.getMin()) + ".." + esc(f.getMax());
      String length = lengthDisplay(f.getMinLength(), f.getMaxLength());
      String conf = confLengthDisplay(f.getLengthType() != null ? f.getLengthType().name() : null, f.getConfLength());
      String comments = formatComments(f.getComments());
      trs.append("<tr><td class=\"mono\">").append(esc(seg.getName())).append("-").append(esc(f.getPosition()))
          .append("</td><td>").append(esc(f.getName())).append("</td><td>")
          .append(dtLink(refId(f.getRef()), dtById, dtSlug, "../")).append("</td><td>")
          .append(usageCell).append("</td><td class=\"mono\">").append(card)
          .append("</td><td class=\"mono\">").append(esc(length))
          .append("</td><td class=\"mono\">").append(esc(conf))
          .append("</td><td>").append(vsCell)
          .append("</td><td class=\"wrap\">").append(comments).append("</td></tr>");
    }
    String table = trs.length() == 0 ? "<p class=\"empty\">No fields.</p>"
        : "<table class=\"struct\"><thead><tr><th>Seq</th><th>Name</th><th>Data type</th><th>Usage</th><th>Cardinality</th><th>Length</th><th>ConfLength</th><th>Value Set</th><th>Comments</th></tr></thead><tbody>"
            + trs + "</tbody></table>";
    String extra = sectionCard("Value Set Bindings", renderVsBindingTable(binding, seg.getName(), vsSlug, vsById, "../"))
        + sectionCard("Conditional Predicates", renderPredicateTable(binding, seg.getName()))
        + sectionCard("Conformance Statements", renderCsTable(binding))
        + renderPrePost(seg);
    String prevA = prev == null ? "<a class=\"disabled\" href=\"#\">← Previous</a>"
        : "<a href=\"" + esc(segSlug.get(prev.getId())) + ".html\">← " + esc(labelOf(prev)) + "</a>";
    String nextA = next == null ? "<a class=\"disabled\" href=\"#\">Next →</a>"
        : "<a href=\"" + esc(segSlug.get(next.getId())) + ".html\">" + esc(labelOf(next)) + " →</a>";
    String body = "<div class=\"page\"><div class=\"crumbs\"><a href=\"../index.html\">Home</a><span class=\"sep\">/</span><span>Segments</span></div>"
        + "<h1><span class=\"mono\">" + esc(labelOf(seg)) + "</span></h1>" + "<div class=\"identity-row\">" + scopeBadge(seg.getDomainInfo(), seg.getResourceOrigin()) + "<span class=\"dig-id mono\">" + esc(labelOf(seg)) + "</span></div>" + "<div class=\"meta\">" + typeBadge("SEGMENT") + "<span class=\"chip\">" + esc(scopeName(seg.getDomainInfo())) + "</span>" + "<span class=\"chip\">HL7 " + esc(scopeVersion(seg.getDomainInfo())) + "</span>" + "<span class=\"chip\">"
        + fields.size() + " fields</span></div><div class=\"section-card\"><h2>" + typeBadge("FIELD") + " Fields</h2>" + table
        + "</div>" + extra + "<footer class=\"nav\">" + prevA + nextA + "</footer></div>";
    return shell(esc(labelOf(seg)) + " — " + esc(guideTitle), "segment", seg.getId(), body, "../", cards, profiles,
        segments, datatypes, valuesets, cpSlug, segSlug, dtSlug, vsSlug);
  }

  private String buildDatatypePage(String guideTitle, Datatype dt, Datatype prev, Datatype next,
      List<Map<String, Object>> cards, List<ConformanceProfile> profiles, List<Segment> segments,
      List<Datatype> datatypes, List<Valueset> valuesets, Map<String, String> cpSlug,
      Map<String, String> segSlug, Map<String, String> dtSlug, Map<String, String> vsSlug,
      Map<String, Datatype> dtById, String igId) {
    List<Component> comps = new ArrayList<Component>();
    if (dt instanceof ComplexDatatype && ((ComplexDatatype) dt).getComponents() != null) {
      comps.addAll(((ComplexDatatype) dt).getComponents());
    }
    Collections.sort(comps, new Comparator<Component>() {
      public int compare(Component a, Component b) { return Integer.compare(a.getPosition(), b.getPosition()); }
    });
    Map<String, Valueset> vsById = new HashMap<String, Valueset>();
    for (Valueset v : valuesets) vsById.put(v.getId(), v);
    ResourceBinding binding = dt.getBinding();
    StringBuilder trs = new StringBuilder();
    for (Component c : comps) {
      StructureElementBinding seb = findBindingChild(binding, c.getId(), c.getPosition());
      String vsCell = formatVsBindings(seb != null ? seb.getValuesetBindings() : null, vsSlug, vsById, "../");
      String usageCell = usageBadge(c.getUsage());
      if (seb != null && seb.getPredicate() != null) {
        usageCell = usageCell + "<div style=\"margin-top:4px;font-size:10px;color:var(--muted)\">C: "
            + usageBadge(seb.getPredicate().getTrueUsage()) + "/"
            + usageBadge(seb.getPredicate().getFalseUsage()) + "</div>";
      }
      String length = lengthDisplay(c.getMinLength(), c.getMaxLength());
      String conf = confLengthDisplay(c.getLengthType() != null ? c.getLengthType().name() : null, c.getConfLength());
      String comments = formatComments(c.getComments());
      trs.append("<tr><td class=\"mono\">").append(esc(dt.getName())).append(".").append(esc(c.getPosition()))
          .append("</td><td>").append(esc(c.getName())).append("</td><td>")
          .append(dtLink(refId(c.getRef()), dtById, dtSlug, "../")).append("</td><td>")
          .append(usageCell).append("</td><td class=\"mono\">—</td><td class=\"mono\">")
          .append(esc(length)).append("</td><td class=\"mono\">").append(esc(conf))
          .append("</td><td>").append(vsCell)
          .append("</td><td class=\"wrap\">").append(comments).append("</td></tr>");
    }
    String table = trs.length() == 0
        ? "<p class=\"empty\">Primitive datatype (no components) or components not present in this flavor.</p>"
        : "<table class=\"struct\"><thead><tr><th>Seq</th><th>Name</th><th>Data type</th><th>Usage</th><th>Cardinality</th><th>Length</th><th>ConfLength</th><th>Value Set</th><th>Comments</th></tr></thead><tbody>"
            + trs + "</tbody></table>";
    String extra = sectionCard("Value Set Bindings", renderVsBindingTable(binding, dt.getName(), vsSlug, vsById, "../"))
        + sectionCard("Conditional Predicates", renderPredicateTable(binding, dt.getName()))
        + sectionCard("Conformance Statements", renderCsTable(binding))
        + renderPrePost(dt);
    String prevA = prev == null ? "<a class=\"disabled\" href=\"#\">← Previous</a>"
        : "<a href=\"" + esc(dtSlug.get(prev.getId())) + ".html\">← " + esc(labelOf(prev)) + "</a>";
    String nextA = next == null ? "<a class=\"disabled\" href=\"#\">Next →</a>"
        : "<a href=\"" + esc(dtSlug.get(next.getId())) + ".html\">" + esc(labelOf(next)) + " →</a>";
    String body = "<div class=\"page\"><div class=\"crumbs\"><a href=\"../index.html\">Home</a><span class=\"sep\">/</span><span>Datatypes</span></div>"
        + "<h1><span class=\"mono\">" + esc(labelOf(dt)) + "</span></h1>" + "<div class=\"identity-row\">" + scopeBadge(dt.getDomainInfo(), dt.getResourceOrigin()) + "<span class=\"dig-id mono\">" + esc(labelOf(dt)) + "</span></div>" + "<div class=\"meta\">" + typeBadge("DATATYPE") + "<span class=\"chip\">" + esc(scopeName(dt.getDomainInfo())) + "</span>" + "<span class=\"chip\">HL7 " + esc(scopeVersion(dt.getDomainInfo())) + "</span>" + "<span class=\"chip\">"
        + comps.size() + " components</span></div><div class=\"section-card\"><h2>" + typeBadge("COMPONENT") + " Components</h2>" + table
        + "</div>" + extra + "<footer class=\"nav\">" + prevA + nextA + "</footer></div>";
    return shell(esc(labelOf(dt)) + " — " + esc(guideTitle), "datatype", dt.getId(), body, "../", cards, profiles,
        segments, datatypes, valuesets, cpSlug, segSlug, dtSlug, vsSlug);
  }

  private String buildValuesetPage(String guideTitle, Valueset vs, Valueset prev, Valueset next,
      List<Map<String, Object>> cards, List<ConformanceProfile> profiles, List<Segment> segments,
      List<Datatype> datatypes, List<Valueset> valuesets, Map<String, String> cpSlug,
      Map<String, String> segSlug, Map<String, String> dtSlug, Map<String, String> vsSlug, String igId) {
    List<Code> codes = new ArrayList<Code>();
    if (vs.getCodes() != null) codes.addAll(vs.getCodes());
    Collections.sort(codes, new Comparator<Code>() {
      public int compare(Code a, Code b) {
        return nullSafe(a.getValue()).compareToIgnoreCase(nullSafe(b.getValue()));
      }
    });
    StringBuilder trs = new StringBuilder();
    for (Code c : codes) {
      trs.append("<tr><td class=\"mono\">").append(esc(c.getValue())).append("</td><td>")
          .append(esc(c.getDescription())).append("</td><td class=\"mono\">").append(esc(c.getCodeSystem()))
          .append("</td><td class=\"mono\">").append(esc(c.getCodeSystemOid()))
          .append("</td><td>").append(usageBadge(c.getUsage()))
          .append("</td><td>").append(c.isDeprecated() ? "Yes" : "—").append("</td></tr>");
    }
    String table = trs.length() == 0 ? "<p class=\"empty\">No codes in this value set.</p>"
        : "<table class=\"struct\"><thead><tr><th>Code</th><th>Description</th><th>System</th><th>OID</th><th>Usage</th><th>Deprecated</th></tr></thead><tbody>"
            + trs + "</tbody></table>";
    String bid = vs.getBindingIdentifier() != null ? vs.getBindingIdentifier() : vs.getId();
    String prevA = prev == null ? "<a class=\"disabled\" href=\"#\">← Previous</a>"
        : "<a href=\"" + esc(vsSlug.get(prev.getId())) + ".html\">← "
            + esc(prev.getBindingIdentifier()) + "</a>";
    String nextA = next == null ? "<a class=\"disabled\" href=\"#\">Next →</a>"
        : "<a href=\"" + esc(vsSlug.get(next.getId())) + ".html\">" + esc(next.getBindingIdentifier())
            + " →</a>";
    String body = "<div class=\"page\"><div class=\"crumbs\"><a href=\"../index.html\">Home</a><span class=\"sep\">/</span><span>Value sets</span></div>"
        + "<h1>" + esc(vs.getName()) + "</h1>" + "<div class=\"identity-row\">" + scopeBadge(vs.getDomainInfo(), vs.getResourceOrigin()) + "<span class=\"dig-id mono\">" + esc(bid) + "</span></div>" + "<div class=\"meta\">" + typeBadge("VALUESET") + "<span class=\"chip cyan\">" + esc(bid) + "</span><span class=\"chip\">" + esc(scopeName(vs.getDomainInfo())) + "</span><span class=\"chip\">HL7 " + esc(scopeVersion(vs.getDomainInfo())) + "</span><span class=\"chip\">" + codes.size() + " codes</span></div><div class=\"section-card\"><h2>Codes</h2>"
        + table + "</div><footer class=\"nav\">" + prevA + nextA + "</footer></div>";
    return shell(esc(bid) + " — " + esc(guideTitle), "valueset", vs.getId(), body, "../", cards, profiles, segments,
        datatypes, valuesets, cpSlug, segSlug, dtSlug, vsSlug);
  }

  private String shell(String pageTitle, String activeKind, String activeId, String body, String assetPrefix,
      List<Map<String, Object>> cards, List<ConformanceProfile> profiles, List<Segment> segments,
      List<Datatype> datatypes, List<Valueset> valuesets, Map<String, String> cpSlug,
      Map<String, String> segSlug, Map<String, String> dtSlug, Map<String, String> vsSlug) {
    String tocTools = "<span class=\"toc-tools\">"
        + "<button type=\"button\" id=\"toc-expand-all\">Expand</button>"
        + "<button type=\"button\" id=\"toc-collapse-all\">Collapse</button></span>";
    String script = "<script src=\"" + assetPrefix + "assets/js/publication.js\" defer></script>";
    return "<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"UTF-8\"/>"
        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
        + "<title>" + pageTitle + "</title>"
        + "<link rel=\"stylesheet\" href=\"" + assetPrefix + "assets/css/publication.css\"/>"
        + "</head><body><a class=\"skip-link\" href=\"#publication-content\">Skip to content</a><header class=\"app-header\"><div class=\"app-brand\">"
        + "<span class=\"app-brand-mark\">IG</span><span>IGAMT Public</span></div>"
        + "<div class=\"app-guide-title\">Public web IG</div>"
        + "<span class=\"app-badge\">Static site</span></header>"
        + "<div class=\"app-body\"><aside class=\"toc-panel\"><div class=\"toc-header\">Contents "
        + tocTools + "</div>"
        + "<nav class=\"toc-scroll\" aria-label=\"Guide contents\">"
        + renderToc(cards, profiles, segments, datatypes, valuesets, activeKind, activeId, assetPrefix, cpSlug,
            segSlug, dtSlug, vsSlug)
        + "</nav></aside><main class=\"main-panel\" id=\"publication-content\" tabindex=\"-1\">" + body + "</main></div>" + script + "</body></html>";
  }

  private Set<String> ancestorSectionIds(List<Map<String, Object>> cards, String activeId) {
    Set<String> out = new HashSet<String>();
    if (activeId == null) return out;
    Map<String, Map<String, Object>> byId = new HashMap<String, Map<String, Object>>();
    for (Map<String, Object> c : cards) byId.put(String.valueOf(c.get("id")), c);
    Map<String, Object> cur = byId.get(activeId);
    while (cur != null && cur.get("parentId") != null) {
      String pid = String.valueOf(cur.get("parentId"));
      out.add(pid);
      cur = byId.get(pid);
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private String renderNarrativeTree(List<Map<String, Object>> nodes, Map<String, Map<String, Object>> byId,
      String activeKind, String activeId, Set<String> openAncestors, String prefix, int depth) {
    StringBuilder sb = new StringBuilder();
    for (Map<String, Object> n : nodes) {
      boolean active = "section".equals(activeKind) && activeId != null && activeId.equals(String.valueOf(n.get("id")));
      List<String> childIds = (List<String>) n.get("childIds");
      List<Map<String, Object>> kids = new ArrayList<Map<String, Object>>();
      if (childIds != null) {
        for (String cid : childIds) {
          if (byId.containsKey(cid)) kids.add(byId.get(cid));
        }
      }
      String link = "<a class=\"toc-link\" href=\"" + prefix + "sections/" + esc(n.get("slug"))
          + ".html\"><span class=\"num\">" + esc(n.get("number")) + "</span> " + esc(n.get("title")) + "</a>";
      if (!kids.isEmpty()) {
        boolean isOpen = openAncestors.contains(String.valueOf(n.get("id"))) || active || depth < 1;
        sb.append("<li class=\"toc-item").append(active ? " active" : "").append("\"><details class=\"toc-branch")
            .append(active ? " active" : "").append("\"").append(isOpen ? " open" : "").append(">")
            .append("<summary class=\"toc-row\"><span class=\"toc-twisty\">▼</span>").append(link)
            .append("</summary><ul class=\"toc-children\">")
            .append(renderNarrativeTree(kids, byId, activeKind, activeId, openAncestors, prefix, depth + 1))
            .append("</ul></details></li>");
      } else {
        sb.append("<li class=\"toc-item").append(active ? " active" : "")
            .append("\"><div class=\"toc-row\"><span class=\"toc-leaf-spacer\"></span>").append(link)
            .append("</div></li>");
      }
    }
    return sb.toString();
  }

  private String renderToc(List<Map<String, Object>> cards, List<ConformanceProfile> profiles,
      List<Segment> segments, List<Datatype> datatypes, List<Valueset> valuesets, String activeKind,
      String activeId, String assetPrefix, Map<String, String> cpSlug, Map<String, String> segSlug,
      Map<String, String> dtSlug, Map<String, String> vsSlug) {
    String p = assetPrefix == null ? "" : assetPrefix;
    StringBuilder sb = new StringBuilder();
    sb.append("<div class=\"toc-home\"><a href=\"").append(p).append("index.html\">Home</a></div>");

    Map<String, Map<String, Object>> byId = new HashMap<String, Map<String, Object>>();
    for (Map<String, Object> c : cards) byId.put(String.valueOf(c.get("id")), c);
    Set<String> openAncestors = "section".equals(activeKind) ? ancestorSectionIds(cards, activeId)
        : new HashSet<String>();
    List<Map<String, Object>> roots = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> c : cards) {
      if (c.get("parentId") == null) roots.add(c);
    }
    boolean narOpen = activeKind == null || "section".equals(activeKind);
    sb.append("<details class=\"toc-group\"").append(narOpen ? " open" : "").append(">")
        .append("<summary><span class=\"chev\">▼</span>" + typeBadge("NARRATIVE") + " Narratives <span style=\"opacity:.7\">(")
        .append(cards.size()).append(")</span></summary><ul class=\"toc-list\">")
        .append(renderNarrativeTree(roots, byId, activeKind, activeId, openAncestors, p, 0))
        .append("</ul></details>");

    sb.append("<details class=\"toc-group\"").append("profile".equals(activeKind) ? " open" : "")
        .append("><summary><span class=\"chev\">▼</span>" + typeBadge("PROFILE") + " Message profiles <span style=\"opacity:.7\">(")
        .append(profiles.size()).append(")</span></summary><ul class=\"toc-list\">");
    for (ConformanceProfile cp : profiles) {
      boolean active = "profile".equals(activeKind) && activeId != null && activeId.equals(cp.getId());
      String shortName = cp.getName() != null && cp.getName().length() > 40 ? cp.getName().substring(0, 40)
          : cp.getName();
      sb.append("<li class=\"toc-item").append(active ? " active" : "")
          .append("\"><div class=\"toc-row\"><span class=\"toc-leaf-spacer\"></span><a class=\"toc-link\" href=\"")
          .append(p).append("profiles/").append(esc(cpSlug.get(cp.getId())))
          .append(".html\">").append(scopeBadge(cp.getDomainInfo(), cp.getResourceOrigin()))
          .append("<span class=\"num\">").append(esc(cp.getMessageType())).append("^")
          .append(esc(cp.getEvent())).append("</span> ").append(esc(shortName)).append("</a></div></li>");
    }
    sb.append("</ul></details>");

    sb.append("<details class=\"toc-group\"").append("segment".equals(activeKind) ? " open" : "")
        .append("><summary><span class=\"chev\">▼</span>" + typeBadge("SEGMENT") + " Segments <span style=\"opacity:.7\">(")
        .append(segments.size()).append(")</span></summary><ul class=\"toc-list\">");
    for (Segment s : segments) {
      boolean active = "segment".equals(activeKind) && activeId != null && activeId.equals(s.getId());
      sb.append("<li class=\"toc-item").append(active ? " active" : "")
          .append("\"><div class=\"toc-row\"><span class=\"toc-leaf-spacer\"></span><a class=\"toc-link\" href=\"")
          .append(p).append("segments/").append(esc(segSlug.get(s.getId())))
          .append(".html\">").append(scopeBadge(s.getDomainInfo(), s.getResourceOrigin()))
          .append("<span class=\"dig-id mono\">").append(esc(labelOf(s))).append("</span></a></div></li>");
    }
    sb.append("</ul></details>");

    sb.append("<details class=\"toc-group\"").append("datatype".equals(activeKind) ? " open" : "")
        .append("><summary><span class=\"chev\">▼</span>" + typeBadge("DATATYPE") + " Datatypes <span style=\"opacity:.7\">(")
        .append(datatypes.size()).append(")</span></summary><ul class=\"toc-list\">");
    for (Datatype d : datatypes) {
      boolean active = "datatype".equals(activeKind) && activeId != null && activeId.equals(d.getId());
      sb.append("<li class=\"toc-item").append(active ? " active" : "")
          .append("\"><div class=\"toc-row\"><span class=\"toc-leaf-spacer\"></span><a class=\"toc-link\" href=\"")
          .append(p).append("datatypes/").append(esc(dtSlug.get(d.getId())))
          .append(".html\">").append(scopeBadge(d.getDomainInfo(), d.getResourceOrigin()))
          .append("<span class=\"dig-id mono\">").append(esc(labelOf(d))).append("</span></a></div></li>");
    }
    sb.append("</ul></details>");

    sb.append("<details class=\"toc-group\"").append("valueset".equals(activeKind) ? " open" : "")
        .append("><summary><span class=\"chev\">▼</span>" + typeBadge("VALUESET") + " Value sets <span style=\"opacity:.7\">(")
        .append(valuesets.size()).append(")</span></summary><ul class=\"toc-list\">");
    for (Valueset v : valuesets) {
      boolean active = "valueset".equals(activeKind) && activeId != null && activeId.equals(v.getId());
      sb.append("<li class=\"toc-item").append(active ? " active" : "")
          .append("\"><div class=\"toc-row\"><span class=\"toc-leaf-spacer\"></span><a class=\"toc-link\" href=\"")
          .append(p).append("valuesets/").append(esc(vsSlug.get(v.getId())))
          .append(".html\">").append(scopeBadge(v.getDomainInfo(), v.getResourceOrigin()))
          .append("<span class=\"dig-id mono\">").append(esc(v.getBindingIdentifier()))
          .append("</span></a></div></li>");
    }
    sb.append("</ul></details>");
    return sb.toString();
  }

  private static String readResource(String name) throws java.io.IOException {
    try (java.io.InputStream input = NarrativeCardExportServiceImpl.class.getResourceAsStream(name)) {
      if (input == null) throw new java.io.IOException("Missing publication asset: " + name);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096]; int count;
      while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
  }

}
