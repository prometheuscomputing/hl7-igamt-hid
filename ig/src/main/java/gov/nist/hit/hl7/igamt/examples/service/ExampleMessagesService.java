package gov.nist.hit.hl7.igamt.examples.service;

import gov.nist.hit.hl7.igamt.common.base.domain.Type;
import gov.nist.hit.hl7.igamt.common.base.domain.display.DisplayElement;
import gov.nist.hit.hl7.igamt.common.base.exception.GenericException;
import gov.nist.hit.hl7.igamt.common.base.exception.ResourceNotFoundException;
import gov.nist.hit.hl7.igamt.compositeprofile.domain.CompositeProfileStructure;
import gov.nist.hit.hl7.igamt.compositeprofile.repository.CompositeProfileStructureRepository;
import gov.nist.hit.hl7.igamt.conformanceprofile.domain.ConformanceProfile;
import gov.nist.hit.hl7.igamt.conformanceprofile.repository.ConformanceProfileRepository;
import gov.nist.hit.hl7.igamt.display.service.DisplayInfoService;
import gov.nist.hit.hl7.igamt.examples.domain.ExampleMessage;
import gov.nist.hit.hl7.igamt.examples.domain.IgExampleMessages;
import gov.nist.hit.hl7.igamt.examples.domain.MessageSnippet;
import gov.nist.hit.hl7.igamt.examples.domain.ProfileInfo;
import gov.nist.hit.hl7.igamt.examples.dto.*;
import gov.nist.hit.hl7.igamt.examples.repository.IgExampleMessagesRepository;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;
import gov.nist.hit.hl7.igamt.ig.repository.IgRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExampleMessagesService {

    @Autowired
    private IgExampleMessagesRepository exampleMessagesRepository;
    @Autowired
    private IgRepository igRepository;
    @Autowired
    private ConformanceProfileRepository conformanceProfileRepository;
    @Autowired
    private CompositeProfileStructureRepository compositeProfileStructureRepository;
    @Autowired
    private DisplayInfoService displayInfoService;

    public IgExampleMessages getOrCreateIgExampleMessage(String igId) {
        IgExampleMessages igExampleMessages = this.exampleMessagesRepository.findById(igId).orElse(null);
        if(igExampleMessages == null) {
            igExampleMessages = new IgExampleMessages();
            igExampleMessages.setId(igId);
            this.exampleMessagesRepository.save(igExampleMessages);
        }
        return igExampleMessages;
    }

    public ExampleMessage getExampleMessage(String igId, String messageId) throws ResourceNotFoundException {
        IgExampleMessages igExampleMessages = this.exampleMessagesRepository.findById(igId).orElse(null);
        if(igExampleMessages == null) {
            throw new ResourceNotFoundException(igId, Type.EXAMPLEMESSAGES);
        }

        return igExampleMessages.getExampleMessages().stream().filter((m) -> m.getId().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(messageId, Type.EXAMPLEMESSAGE));
    }

    public ExampleMessage saveMessage(String igId, String messageId, SaveMessageDTO saveMessageDTO) throws ResourceNotFoundException, GenericException {
        IgExampleMessages igExampleMessages = this.exampleMessagesRepository.findById(igId).orElse(null);
        if(igExampleMessages == null) {
            throw new ResourceNotFoundException(igId, Type.EXAMPLEMESSAGES);
        }

        ExampleMessage exampleMessage = igExampleMessages.getExampleMessages().stream().filter((m) -> m.getId().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(messageId, Type.EXAMPLEMESSAGE));

        if (saveMessageDTO.getName() != null) {
            assertUniqueMessageName(igExampleMessages, exampleMessage.getProfileId(), saveMessageDTO.getName(), messageId);
        }
        exampleMessage.setMessage(saveMessageDTO.getMessage());
        exampleMessage.setNarrativeHTML(saveMessageDTO.getNarrative());
        if (saveMessageDTO.getName() != null) {
            exampleMessage.setName(saveMessageDTO.getName());
        }
        exampleMessage.setDescription(saveMessageDTO.getDescription());

        this.exampleMessagesRepository.save(igExampleMessages);
        return exampleMessage;
    }


    public IgExampleMessagesDTO toDTO(IgExampleMessages igExampleMessages) throws ResourceNotFoundException {
        Ig ig = this.igRepository.findById(igExampleMessages.getId()).orElse(null);
        if(ig == null) {
            throw new ResourceNotFoundException(igExampleMessages.getId(), Type.IGDOCUMENT);
        }
        IgExampleMessagesDTO igExampleMessagesDTO = new IgExampleMessagesDTO();
        igExampleMessagesDTO.setId(igExampleMessages.getId());
        igExampleMessagesDTO.setTitle(ig.getMetadata().getTitle());
        List<DisplayElement> profiles = new ArrayList<>();
        profiles.addAll(this.displayInfoService.convertConformanceProfileRegistry(ig.getConformanceProfileRegistry()));
        profiles.addAll(this.displayInfoService.convertCompositeProfileRegistry(ig.getCompositeProfileRegistry()));
        for(DisplayElement profile: profiles) {
            ProfileExampleMessages profileExampleMessages = new ProfileExampleMessages();
            profileExampleMessages.setProfile(profile);
            profileExampleMessages.setExampleMessages(
                    igExampleMessages.getExampleMessages().stream()
                            .filter((em) -> em.getProfileId().equals(profile.getId()))
                            .collect(Collectors.toList())
            );
            igExampleMessagesDTO.getProfileExampleMessages().add(profileExampleMessages);
        }
        return igExampleMessagesDTO;
    }

    public ExampleMessageDTO toDTO(ExampleMessage exampleMessage) {
        ExampleMessageDTO exampleMessageDTO = new ExampleMessageDTO();
        exampleMessageDTO.setId(exampleMessage.getId());
        exampleMessageDTO.setName(exampleMessage.getName());
        exampleMessageDTO.setDescription(exampleMessage.getDescription());
        exampleMessageDTO.setNarrativeHTML(exampleMessage.getNarrativeHTML());
        exampleMessageDTO.setMessage(exampleMessage.getMessage());

        ProfileInfo profileInfo = getProfileInfo(exampleMessage.getProfileId());
        if(profileInfo != null) {
            exampleMessageDTO.setProfile(profileInfo.getDisplayInfo());
        }

        return exampleMessageDTO;
    }

    public IgExampleMessagesDTO createExampleMessage(String id, CreateMessageDTO createMessageDTO) throws ResourceNotFoundException, GenericException {
        ProfileInfo profile = this.getProfileInfo(createMessageDTO.getProfileId());
        if(profile == null) {
            throw new ResourceNotFoundException(createMessageDTO.getProfileId(), Type.PROFILE);
        }

        if(!id.equals(profile.getDocumentInfo().getDocumentId())) {
            throw new ResourceNotFoundException(createMessageDTO.getProfileId(), Type.PROFILE);
        }

        IgExampleMessages igExampleMessages = this.getOrCreateIgExampleMessage(id);
        assertUniqueMessageName(igExampleMessages, createMessageDTO.getProfileId(), createMessageDTO.getName(), null);
        ExampleMessage exampleMessage = new ExampleMessage();
        exampleMessage.setId(new ObjectId().toString());
        exampleMessage.setName(createMessageDTO.getName());
        exampleMessage.setProfileId(createMessageDTO.getProfileId());
        igExampleMessages.getExampleMessages().add(exampleMessage);
        this.exampleMessagesRepository.save(igExampleMessages);
        IgExampleMessagesDTO dto = this.toDTO(igExampleMessages);
        dto.setCreatedId(exampleMessage.getId());
        return dto;
    }

    public IgExampleMessagesDTO copyExampleMessage(String igId, String messageId, CopyExampleItemDTO copyExampleItemDTO) throws ResourceNotFoundException, GenericException {
        IgExampleMessages igExampleMessages = this.exampleMessagesRepository.findById(igId).orElse(null);
        if (igExampleMessages == null) {
            throw new ResourceNotFoundException(igId, Type.EXAMPLEMESSAGES);
        }
        ExampleMessage source = igExampleMessages.getExampleMessages().stream()
                .filter((m) -> m.getId().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(messageId, Type.EXAMPLEMESSAGE));
        assertUniqueMessageName(igExampleMessages, source.getProfileId(), copyExampleItemDTO.getName(), null);

        ExampleMessage copy = new ExampleMessage();
        copy.setId(new ObjectId().toString());
        copy.setName(copyExampleItemDTO.getName());
        copy.setDescription(source.getDescription());
        copy.setMessage(source.getMessage());
        copy.setNarrativeHTML(source.getNarrativeHTML());
        copy.setProfileId(source.getProfileId());
        for (MessageSnippet snippet : source.getSnippets()) {
            copy.getSnippets().add(copySnippet(snippet, snippet.getName()));
        }
        igExampleMessages.getExampleMessages().add(copy);
        this.exampleMessagesRepository.save(igExampleMessages);
        IgExampleMessagesDTO dto = this.toDTO(igExampleMessages);
        dto.setCreatedId(copy.getId());
        return dto;
    }

    public IgExampleMessagesDTO createSnippet(String igId, String messageId, CreateSnippetDTO createSnippetDTO) throws ResourceNotFoundException, GenericException {
        IgExampleMessages igExampleMessages = this.exampleMessagesRepository.findById(igId).orElse(null);
        if (igExampleMessages == null) {
            throw new ResourceNotFoundException(igId, Type.EXAMPLEMESSAGES);
        }

        ExampleMessage exampleMessage = igExampleMessages.getExampleMessages().stream()
                .filter((m) -> m.getId().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(messageId, Type.EXAMPLEMESSAGE));

        assertUniqueSnippetName(exampleMessage, createSnippetDTO.getName(), null);
        MessageSnippet snippet = new MessageSnippet();
        snippet.setId(new ObjectId().toString());
        snippet.setName(createSnippetDTO.getName());
        snippet.setMessageReferences(new HashSet<>(createSnippetDTO.getMessageReferences()));

        exampleMessage.getSnippets().add(snippet);
        this.exampleMessagesRepository.save(igExampleMessages);
        IgExampleMessagesDTO dto = this.toDTO(igExampleMessages);
        dto.setCreatedId(snippet.getId());
        return dto;
    }

    public IgExampleMessagesDTO copySnippet(String igId, String messageId, String snippetId, CopyExampleItemDTO copyExampleItemDTO) throws ResourceNotFoundException, GenericException {
        IgExampleMessages igExampleMessages = this.exampleMessagesRepository.findById(igId).orElse(null);
        if (igExampleMessages == null) {
            throw new ResourceNotFoundException(igId, Type.EXAMPLEMESSAGES);
        }
        ExampleMessage exampleMessage = igExampleMessages.getExampleMessages().stream()
                .filter((m) -> m.getId().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(messageId, Type.EXAMPLEMESSAGE));
        MessageSnippet source = exampleMessage.getSnippets().stream()
                .filter((s) -> s.getId().equals(snippetId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(snippetId, null));
        assertUniqueSnippetName(exampleMessage, copyExampleItemDTO.getName(), null);
        MessageSnippet copy = copySnippet(source, copyExampleItemDTO.getName());
        exampleMessage.getSnippets().add(copy);
        this.exampleMessagesRepository.save(igExampleMessages);
        IgExampleMessagesDTO dto = this.toDTO(igExampleMessages);
        dto.setCreatedId(copy.getId());
        return dto;
    }

    public MessageSnippet saveSnippet(String igId, String messageId, String snippetId, SaveSnippetDTO saveSnippetDTO) throws ResourceNotFoundException, GenericException {
        IgExampleMessages igExampleMessages = this.exampleMessagesRepository.findById(igId).orElse(null);
        if (igExampleMessages == null) {
            throw new ResourceNotFoundException(igId, Type.EXAMPLEMESSAGES);
        }

        ExampleMessage exampleMessage = igExampleMessages.getExampleMessages().stream()
                .filter((m) -> m.getId().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(messageId, Type.EXAMPLEMESSAGE));

        MessageSnippet snippet = exampleMessage.getSnippets().stream()
                .filter((s) -> s.getId().equals(snippetId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(snippetId, null));

        assertUniqueSnippetName(exampleMessage, saveSnippetDTO.getName(), snippetId);
        snippet.setName(saveSnippetDTO.getName());
        snippet.setDescription(saveSnippetDTO.getDescription());

        this.exampleMessagesRepository.save(igExampleMessages);
        return snippet;
    }

    private MessageSnippet copySnippet(MessageSnippet source, String name) {
        MessageSnippet copy = new MessageSnippet();
        copy.setId(new ObjectId().toString());
        copy.setName(name);
        copy.setDescription(source.getDescription());
        copy.setNarrativeHTML(source.getNarrativeHTML());
        copy.setMessageReferences(new HashSet<>(source.getMessageReferences()));
        return copy;
    }

    private void assertUniqueMessageName(IgExampleMessages igExampleMessages, String profileId, String name, String excludeId) throws GenericException {
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) {
            throw new GenericException("Name is required.");
        }
        boolean taken = igExampleMessages.getExampleMessages().stream().anyMatch((message) ->
                profileId.equals(message.getProfileId())
                        && (excludeId == null || !excludeId.equals(message.getId()))
                        && normalized.equals(normalizeName(message.getName())));
        if (taken) {
            throw new GenericException("An example message named \"" + name.trim() + "\" already exists for this profile.");
        }
    }

    private void assertUniqueSnippetName(ExampleMessage exampleMessage, String name, String excludeId) throws GenericException {
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) {
            throw new GenericException("Name is required.");
        }
        boolean taken = exampleMessage.getSnippets().stream().anyMatch((snippet) ->
                (excludeId == null || !excludeId.equals(snippet.getId()))
                        && normalized.equals(normalizeName(snippet.getName())));
        if (taken) {
            throw new GenericException("A snippet named \"" + name.trim() + "\" already exists for this message.");
        }
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private ProfileInfo getProfileInfo(String profileId) {
        ProfileInfo profileInfo = new ProfileInfo();
        ConformanceProfile conformanceProfile = this.conformanceProfileRepository.findById(profileId).orElse(null);
        if(conformanceProfile == null) {
            CompositeProfileStructure compositeProfileStructure = this.compositeProfileStructureRepository.findById(profileId).orElse(null);
            if(compositeProfileStructure != null) {
                DisplayElement display = this.displayInfoService.convertCompositeProfile(compositeProfileStructure, 1);
                profileInfo.setDisplayInfo(display);
                profileInfo.setDocumentInfo(compositeProfileStructure.getDocumentInfo());
                return profileInfo;
            }
        } else {
            DisplayElement display =  this.displayInfoService.convertConformanceProfile(conformanceProfile, 1);
            profileInfo.setDisplayInfo(display);
            profileInfo.setDocumentInfo(conformanceProfile.getDocumentInfo());
            return profileInfo;
        }
        return null;
    }



}
