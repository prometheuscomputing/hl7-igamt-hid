package gov.nist.hit.hl7.igamt.web.app.examples;

import gov.nist.hit.hl7.igamt.common.base.exception.GenericException;
import gov.nist.hit.hl7.igamt.common.base.model.ResponseMessage;
import gov.nist.hit.hl7.igamt.examples.domain.ExampleMessage;
import gov.nist.hit.hl7.igamt.examples.domain.IgExampleMessages;
import gov.nist.hit.hl7.igamt.examples.domain.MessageSnippet;
import gov.nist.hit.hl7.igamt.examples.dto.*;
import gov.nist.hit.hl7.igamt.examples.dto.parser.MessageModel;
import gov.nist.hit.hl7.igamt.common.base.service.InMemoryDomainExtensionService;
import gov.nist.hit.hl7.igamt.examples.service.ExampleMessageExportDataService;
import gov.nist.hit.hl7.igamt.examples.service.ExampleMessageLocationService;
import gov.nist.hit.hl7.igamt.examples.service.ExampleMessageValidationService;
import gov.nist.hit.hl7.igamt.examples.service.ExampleMessagesService;
import gov.nist.hit.hl7.igamt.examples.service.MessageParserService;
import gov.nist.hit.hl7.igamt.examples.service.SnippetRenderService;
import gov.nist.hit.hl7.igamt.export.configuration.domain.ExportConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.domain.ExportType;
import gov.nist.hit.hl7.igamt.export.configuration.newModel.ExampleMessageExportConfiguration;
import gov.nist.hit.hl7.igamt.export.configuration.service.ExportConfigurationService;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;
import gov.nist.hit.hl7.igamt.ig.domain.datamodel.IgDataModel;
import gov.nist.hit.hl7.igamt.ig.service.IgService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class ExampleMessagesController {

    @Autowired
    ExampleMessagesService exampleMessagesService;
    @Autowired
    MessageParserService messageParserService;
    @Autowired
    SnippetRenderService snippetRenderService;
    @Autowired
    ExampleMessageValidationService exampleMessageValidationService;
    @Autowired
    ExampleMessageLocationService exampleMessageLocationService;
    @Autowired
    ExampleMessageExportDataService exampleMessageExportDataService;
    @Autowired
    IgService igService;
    @Autowired
    ExportConfigurationService exportConfigurationService;
    @Autowired
    InMemoryDomainExtensionService inMemoryDomainExtensionService;

    @RequestMapping(value = "/api/example-messages/{id}", method = RequestMethod.GET, produces = {"application/json" })
    public @ResponseBody
    IgExampleMessagesDTO getExampleMessages(
            @PathVariable("id") String id,
            Authentication authentication
    ) throws Exception {
        IgExampleMessages exampleMessages = this.exampleMessagesService.getOrCreateIgExampleMessage(id);
        return exampleMessagesService.toDTO(exampleMessages);
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}", method = RequestMethod.GET, produces = {"application/json" })
    public @ResponseBody
    ExampleMessageDTO getMessage(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            Authentication authentication
    ) throws Exception {
        ExampleMessage exampleMessage = this.exampleMessagesService.getExampleMessage(id, messageId);
        return exampleMessagesService.toDTO(exampleMessage);
    }


    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}", method = RequestMethod.POST, produces = {"application/json" })
    public @ResponseBody
    ResponseMessage<ExampleMessageDTO> saveMessage(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @RequestBody SaveMessageDTO saveMessageDTO
    ) throws Exception {
        ExampleMessage exampleMessage = this.exampleMessagesService.saveMessage(id, messageId, saveMessageDTO);
        ExampleMessageDTO dto = exampleMessagesService.toDTO(exampleMessage);

        // Validate all snippets against the new message content
        List<SnippetValidationInfo> validations = snippetRenderService.validateSnippets(id, exampleMessage);
        dto.setSnippetValidations(validations);

        // Build a message that includes info about affected snippets
        long brokenCount = validations.stream().filter(v -> !v.isFullyResolved()).count();
        String message = "Example Message Saved Successfully";
        if (brokenCount > 0) {
            message += ". Warning: " + brokenCount + " snippet(s) have references that no longer resolve.";
        }

        return new ResponseMessage<>(
                ResponseMessage.Status.SUCCESS,
                message,
                messageId,
                dto,
                new Date()
        );
    }


    @RequestMapping(value = "/api/example-messages/{id}/message", method = RequestMethod.POST, produces = {"application/json" })
    public @ResponseBody
    ResponseMessage<IgExampleMessagesDTO> createMessage(
            @PathVariable("id") String id,
            @RequestBody CreateMessageDTO createMessageDTO,
            Authentication authentication
    ) throws Exception {
        IgExampleMessagesDTO igExampleMessages = this.exampleMessagesService.createExampleMessage(id, createMessageDTO);
        return new ResponseMessage<>(
                ResponseMessage.Status.SUCCESS,
                "Example Message Created Successfully",
                igExampleMessages.getCreatedId(),
                igExampleMessages,
                new Date()
        );
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/copy", method = RequestMethod.POST, produces = {"application/json" })
    public @ResponseBody
    ResponseMessage<IgExampleMessagesDTO> copyMessage(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @RequestBody CopyExampleItemDTO copyExampleItemDTO,
            Authentication authentication
    ) throws Exception {
        IgExampleMessagesDTO igExampleMessages = this.exampleMessagesService.copyExampleMessage(id, messageId, copyExampleItemDTO);
        return new ResponseMessage<>(
                ResponseMessage.Status.SUCCESS,
                "Example Message Copied Successfully",
                igExampleMessages.getCreatedId(),
                igExampleMessages,
                new Date()
        );
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/snippet", method = RequestMethod.POST, produces = {"application/json" })
    public @ResponseBody
    ResponseMessage<IgExampleMessagesDTO> createSnippet(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @RequestBody CreateSnippetDTO createSnippetDTO,
            Authentication authentication
    ) throws Exception {
        IgExampleMessagesDTO igExampleMessages = this.exampleMessagesService.createSnippet(id, messageId, createSnippetDTO);
        return new ResponseMessage<>(
                ResponseMessage.Status.SUCCESS,
                "Snippet Created Successfully",
                igExampleMessages.getCreatedId(),
                igExampleMessages,
                new Date()
        );
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/snippet/{snippetId}/copy", method = RequestMethod.POST, produces = {"application/json" })
    public @ResponseBody
    ResponseMessage<IgExampleMessagesDTO> copySnippet(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @PathVariable("snippetId") String snippetId,
            @RequestBody CopyExampleItemDTO copyExampleItemDTO,
            Authentication authentication
    ) throws Exception {
        IgExampleMessagesDTO igExampleMessages = this.exampleMessagesService.copySnippet(id, messageId, snippetId, copyExampleItemDTO);
        return new ResponseMessage<>(
                ResponseMessage.Status.SUCCESS,
                "Snippet Copied Successfully",
                igExampleMessages.getCreatedId(),
                igExampleMessages,
                new Date()
        );
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/snippet/{snippetId}", method = RequestMethod.POST, produces = {"application/json" })
    public @ResponseBody
    ResponseMessage<MessageSnippet> saveSnippet(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @PathVariable("snippetId") String snippetId,
            @RequestBody SaveSnippetDTO saveSnippetDTO
    ) throws Exception {
        MessageSnippet snippet = this.exampleMessagesService.saveSnippet(id, messageId, snippetId, saveSnippetDTO);
        return new ResponseMessage<>(
                ResponseMessage.Status.SUCCESS,
                "Snippet Saved Successfully",
                snippetId,
                snippet,
                new Date()
        );
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/parse", method = RequestMethod.GET, produces = {"application/json" })
    public @ResponseBody
    MessageModel getParsedMessage(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            Authentication authentication
    ) throws Exception {
        ExampleMessage exampleMessage = this.exampleMessagesService.getExampleMessage(id, messageId);
        return messageParserService.parseMessage(id, exampleMessage.getProfileId(), exampleMessage.getMessage());
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/locate", method = RequestMethod.GET, produces = {"application/json" })
    public @ResponseBody
    ExampleMessageLocation locateMessageElement(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @RequestParam(value = "positionalPath", required = false) String positionalPath,
            @RequestParam(value = "hl7Path", required = false) String hl7Path,
            Authentication authentication
    ) {
        return exampleMessageLocationService.locate(id, messageId, positionalPath, hl7Path);
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/validate", method = RequestMethod.POST, produces = {"application/json" })
    public @ResponseBody
    ExampleMessageValidationResult validateMessage(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @RequestBody(required = false) ValidateMessageDTO validateMessageDTO,
            Authentication authentication
    ) {
        String er7 = validateMessageDTO != null ? validateMessageDTO.getMessage() : null;
        return exampleMessageValidationService.validate(id, messageId, er7);
    }

    /**
     * Render a saved snippet: parses the message, finds the referenced elements,
     * extracts the relevant segment lines, and returns the result.
     */
    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/snippet/{snippetId}/render", method = RequestMethod.GET, produces = {"application/json" })
    public @ResponseBody
    SnippetRenderResult renderSnippet(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @PathVariable("snippetId") String snippetId,
            Authentication authentication
    ) throws Exception {
        return snippetRenderService.renderSnippet(id, messageId, snippetId);
    }

    /**
     * Render from ad-hoc positional paths (without a saved snippet).
     * Useful when the user selects parts of a message in the UI.
     */
    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/render", method = RequestMethod.POST, produces = {"application/json" })
    public @ResponseBody
    SnippetRenderResult renderFromPaths(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @RequestBody RenderFromPathsDTO renderFromPathsDTO,
            Authentication authentication
    ) throws Exception {
        return snippetRenderService.renderFromPaths(id, messageId, renderFromPathsDTO.getPositionalPaths());
    }

    /**
     * The same folder the guide links to. Dropping it into a guide export keeps the
     * relative links working, including the link back to index.html.
     */
    @RequestMapping(value = "/api/example-messages/{id}/export", method = RequestMethod.GET)
    @PreAuthorize("AccessResource('IGDOCUMENT', #id, READ)")
    public void exportAll(
            @PathVariable("id") String id,
            HttpServletResponse response
    ) throws Exception {
        Set<String> dataExtensionTokens = new HashSet<String>();
        try {
            Ig ig = igService.findById(id);
            if (ig == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Implementation Guide not found");
                return;
            }
            IgDataModel igDataModel = igService.generateDataModel(ig);
            dataExtensionTokens.addAll(igDataModel.getDataExtensionTokens());
            ExampleMessageExportConfiguration configuration = ExampleMessageExportDataService.replacementFolder(guideExampleMessages());
            exampleMessageExportDataService.populate(igDataModel, configuration);
            Map<String, byte[]> files = exampleMessageExportDataService.collectStaticPages(igDataModel, configuration, true);
            files.putAll(exampleMessageExportDataService.collectFiles(igDataModel, configuration));
            byte[] zip = exampleMessageExportDataService.zipFiles(files);
            response.setContentType("application/zip");
            response.setHeader("Content-disposition", "attachment;filename=ExampleMessages-" + id + ".zip");
            FileCopyUtils.copy(new ByteArrayInputStream(zip), response.getOutputStream());
        } finally {
            dataExtensionTokens.forEach(token -> inMemoryDomainExtensionService.clear(token));
        }
    }

    /** Folder name and snippet presentation from the IG configuration that exports use by default. */
    private ExampleMessageExportConfiguration guideExampleMessages() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        ExportConfiguration exportConfiguration = exportConfigurationService.getConfigurationToApply(
                ExportType.IGDOCUMENT, authentication.getPrincipal().toString());
        if (exportConfiguration == null || exportConfiguration.getIgGeneralConfiguration() == null) {
            return null;
        }
        return exportConfiguration.getIgGeneralConfiguration().getExampleMessages();
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/export", method = RequestMethod.GET)
    @PreAuthorize("AccessResource('IGDOCUMENT', #id, READ)")
    public void exportMessageFile(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @RequestParam(value = "format", defaultValue = "html") String format,
            HttpServletResponse response
    ) throws Exception {
        ExampleMessage message = exampleMessagesService.getExampleMessage(id, messageId);
        String validationHtml = null;
        if ("validation".equalsIgnoreCase(format)) {
            ExampleMessageValidationResult result = exampleMessageValidationService.validate(id, messageId, null);
            validationHtml = result.getHtml();
            if (validationHtml == null || validationHtml.trim().isEmpty()) {
                String error = result.getError() != null ? result.getError() : "No validation report was produced.";
                validationHtml = "<p>" + error.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</p>";
            }
        }
        ExampleMessageExportDataService.Download download = exampleMessageExportDataService.exportMessageFile(message, format, validationHtml);
        response.setContentType(download.contentType);
        response.setHeader("Content-disposition", "attachment;filename=\"" + download.filename + "\"");
        FileCopyUtils.copy(new ByteArrayInputStream(download.body), response.getOutputStream());
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/export", method = RequestMethod.POST)
    @PreAuthorize("AccessResource('IGDOCUMENT', #id, READ)")
    public void exportOne(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @RequestBody(required = false) ExampleMessageExportRequest request,
            HttpServletResponse response
    ) throws Exception {
        ExampleMessage message = exampleMessagesService.getExampleMessage(id, messageId);
        boolean snippets = request == null || request.isIncludeSnippets();
        boolean validation = request != null && request.isIncludeValidation();
        String onlySnippet = request != null ? request.getSnippetId() : null;
        String validationHtml = null;
        if (validation && (onlySnippet == null || onlySnippet.isEmpty())) {
            ExampleMessageValidationResult result = exampleMessageValidationService.validate(id, messageId, null);
            validationHtml = result.getHtml();
            if (validationHtml == null || validationHtml.trim().isEmpty()) {
                String error = result.getError() != null ? result.getError() : "No validation report was produced.";
                validationHtml = "<p>" + error.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</p>";
            }
        }
        byte[] zip = exampleMessageExportDataService.exportOne(id, message, snippets, validation, validationHtml, onlySnippet);
        String filename = "Example-" + (message.getName() != null ? message.getName().replaceAll("[\\\\/:*?\"<>|]", "-") : messageId) + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Content-disposition", "attachment;filename=\"" + filename + "\"");
        FileCopyUtils.copy(new ByteArrayInputStream(zip), response.getOutputStream());
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/snippet/{snippetId}/export", method = RequestMethod.GET)
    @PreAuthorize("AccessResource('IGDOCUMENT', #id, READ)")
    public void exportSnippet(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @PathVariable("snippetId") String snippetId,
            @RequestParam(value = "format", defaultValue = "html") String format,
            HttpServletResponse response
    ) throws Exception {
        ExampleMessage message = exampleMessagesService.getExampleMessage(id, messageId);
        ExampleMessageExportDataService.Download download = exampleMessageExportDataService.exportSnippet(id, message, snippetId, format);
        if (download == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Snippet not found");
            return;
        }
        response.setContentType(download.contentType);
        response.setHeader("Content-disposition", "attachment;filename=\"" + download.filename + "\"");
        FileCopyUtils.copy(new ByteArrayInputStream(download.body), response.getOutputStream());
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/usages", method = RequestMethod.GET, produces = {"application/json"})
    @PreAuthorize("AccessResource('IGDOCUMENT', #id, READ)")
    public @ResponseBody List<ExampleMessageUsage> messageUsages(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId
    ) throws Exception {
        return exampleMessagesService.findUsages(id, messageId, null);
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/snippet/{snippetId}/usages", method = RequestMethod.GET, produces = {"application/json"})
    @PreAuthorize("AccessResource('IGDOCUMENT', #id, READ)")
    public @ResponseBody List<ExampleMessageUsage> snippetUsages(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @PathVariable("snippetId") String snippetId
    ) throws Exception {
        return exampleMessagesService.findUsages(id, messageId, snippetId);
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/detach", method = RequestMethod.POST, produces = {"application/json"})
    @PreAuthorize("AccessResource('IGDOCUMENT', #id, WRITE)")
    public @ResponseBody ResponseMessage<List<ExampleMessageUsage>> detachMessage(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @RequestBody(required = false) ExampleMessageDetachRequest request
    ) throws Exception {
        List<ExampleMessageUsage> remaining;
        try {
            remaining = exampleMessagesService.detach(id, messageId, null,
                    request != null ? request.getSectionId() : null,
                    request != null ? request.getKind() : null);
        } catch (GenericException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        return new ResponseMessage<>(ResponseMessage.Status.SUCCESS, "References detached", messageId, remaining, new Date());
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/snippet/{snippetId}/detach", method = RequestMethod.POST, produces = {"application/json"})
    @PreAuthorize("AccessResource('IGDOCUMENT', #id, WRITE)")
    public @ResponseBody ResponseMessage<List<ExampleMessageUsage>> detachSnippet(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @PathVariable("snippetId") String snippetId,
            @RequestBody(required = false) ExampleMessageDetachRequest request
    ) throws Exception {
        List<ExampleMessageUsage> remaining;
        try {
            remaining = exampleMessagesService.detach(id, messageId, snippetId,
                    request != null ? request.getSectionId() : null,
                    request != null ? request.getKind() : null);
        } catch (GenericException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        return new ResponseMessage<>(ResponseMessage.Status.SUCCESS, "References detached", snippetId, remaining, new Date());
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}", method = RequestMethod.DELETE, produces = {"application/json"})
    @PreAuthorize("AccessResource('IGDOCUMENT', #id, WRITE)")
    public @ResponseBody ResponseMessage<IgExampleMessagesDTO> deleteMessage(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId
    ) throws Exception {
        IgExampleMessagesDTO dto;
        try {
            dto = exampleMessagesService.deleteExampleMessage(id, messageId);
        } catch (GenericException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        return new ResponseMessage<>(ResponseMessage.Status.SUCCESS, "Example message deleted", messageId, dto, new Date());
    }

    @RequestMapping(value = "/api/example-messages/{id}/message/{messageId}/snippet/{snippetId}", method = RequestMethod.DELETE, produces = {"application/json"})
    @PreAuthorize("AccessResource('IGDOCUMENT', #id, WRITE)")
    public @ResponseBody ResponseMessage<IgExampleMessagesDTO> deleteSnippet(
            @PathVariable("id") String id,
            @PathVariable("messageId") String messageId,
            @PathVariable("snippetId") String snippetId
    ) throws Exception {
        IgExampleMessagesDTO dto;
        try {
            dto = exampleMessagesService.deleteSnippet(id, messageId, snippetId);
        } catch (GenericException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        return new ResponseMessage<>(ResponseMessage.Status.SUCCESS, "Snippet deleted", snippetId, dto, new Date());
    }

}
