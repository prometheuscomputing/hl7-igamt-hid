package gov.nist.hit.hl7.igamt.examples.service;

import gov.nist.hit.hl7.igamt.common.base.domain.DomainInfo;
import gov.nist.hit.hl7.igamt.common.base.domain.Scope;
import gov.nist.hit.hl7.igamt.common.base.domain.SharePermission;
import gov.nist.hit.hl7.igamt.common.base.domain.Status;
import gov.nist.hit.hl7.igamt.common.base.domain.Type;
import gov.nist.hit.hl7.igamt.common.base.domain.display.DisplayElement;
import gov.nist.hit.hl7.igamt.common.base.exception.GenericException;
import gov.nist.hit.hl7.igamt.common.base.exception.ResourceNotFoundException;
import gov.nist.hit.hl7.igamt.compositeprofile.domain.CompositeProfileStructure;
import gov.nist.hit.hl7.igamt.compositeprofile.repository.CompositeProfileStructureRepository;
import gov.nist.hit.hl7.igamt.conformanceprofile.domain.ConformanceProfile;
import gov.nist.hit.hl7.igamt.conformanceprofile.repository.ConformanceProfileRepository;
import gov.nist.hit.hl7.igamt.display.service.DisplayInfoService;
import gov.nist.hit.hl7.igamt.examples.domain.ActorCapability;
import gov.nist.hit.hl7.igamt.examples.domain.ExampleActor;
import gov.nist.hit.hl7.igamt.examples.domain.ExampleMessage;
import gov.nist.hit.hl7.igamt.examples.domain.ExampleScenario;
import gov.nist.hit.hl7.igamt.examples.domain.IgExampleMessages;
import gov.nist.hit.hl7.igamt.examples.domain.MessageSnippet;
import gov.nist.hit.hl7.igamt.examples.domain.ScenarioStep;
import gov.nist.hit.hl7.igamt.examples.domain.ProfileInfo;
import gov.nist.hit.hl7.igamt.examples.dto.*;
import gov.nist.hit.hl7.igamt.examples.repository.IgExampleMessagesRepository;
import gov.nist.hit.hl7.igamt.ig.domain.Ig;
import gov.nist.hit.hl7.igamt.ig.repository.IgRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @Autowired
    private ExampleMessageReferenceService exampleMessageReferenceService;

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
        assertWritable(igId);
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
        igExampleMessagesDTO.setViewOnly(isViewOnly(ig));
        igExampleMessagesDTO.setActors(igExampleMessages.getActors());
        igExampleMessagesDTO.setScenarios(igExampleMessages.getScenarios());
        // Registry conversion returns HashSet, so sort by position or the TOC reshuffles on every load.
        List<DisplayElement> profiles = new ArrayList<DisplayElement>();
        profiles.addAll(this.displayInfoService.convertConformanceProfileRegistry(ig.getConformanceProfileRegistry()));
        profiles.addAll(this.displayInfoService.convertCompositeProfileRegistry(ig.getCompositeProfileRegistry()));
        Collections.sort(profiles, new Comparator<DisplayElement>() {
            @Override
            public int compare(DisplayElement left, DisplayElement right) {
                int byPosition = Integer.compare(left.getPosition(), right.getPosition());
                if (byPosition != 0) {
                    return byPosition;
                }
                String leftId = left.getId() == null ? "" : left.getId();
                String rightId = right.getId() == null ? "" : right.getId();
                return leftId.compareTo(rightId);
            }
        });
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
        assertWritable(id);
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
        assertWritable(igId);
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
        assertWritable(igId);
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
        assertWritable(igId);
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
        assertWritable(igId);
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

    public IgExampleMessagesDTO saveExchange(String igId, ExampleExchangeDTO exchange) throws ResourceNotFoundException, GenericException {
        assertWritable(igId);
        IgExampleMessages document = requireMessages(igId);
        List<ExampleActor> actors = normalizeActors(exchange == null ? null : exchange.getActors());
        List<ExampleScenario> scenarios = normalizeScenarios(exchange == null ? null : exchange.getScenarios(), actors, document);
        document.setActors(actors);
        document.setScenarios(scenarios);
        exampleMessagesRepository.save(document);
        return toDTO(document);
    }

    private List<ExampleActor> normalizeActors(List<ExampleActor> incoming) throws GenericException {
        List<ExampleActor> actors = new ArrayList<ExampleActor>();
        Set<String> ids = new HashSet<String>();
        Set<String> names = new HashSet<String>();
        if (incoming == null) {
            return actors;
        }
        for (ExampleActor actor : incoming) {
            if (actor == null) {
                continue;
            }
            String name = actor.getName() == null ? "" : actor.getName().trim();
            if (name.isEmpty()) {
                throw new GenericException("An actor needs a name.");
            }
            if (!names.add(name.toLowerCase())) {
                throw new GenericException("Two actors cannot share the name \"" + name + "\".");
            }
            String id = actor.getId() == null || actor.getId().trim().isEmpty() ? new ObjectId().toString() : actor.getId().trim();
            if (!ids.add(id)) {
                throw new GenericException("Two actors share the same id.");
            }
            ExampleActor stored = new ExampleActor();
            stored.setId(id);
            stored.setName(name);
            stored.setSystem(actor.getSystem() == null ? "" : actor.getSystem().trim());
            stored.setOrganization(actor.getOrganization() == null ? "" : actor.getOrganization().trim());
            String icon = actor.getIcon() == null ? "" : actor.getIcon().trim();
            stored.setIcon(icon.matches("^fa-[a-z0-9-]{1,40}$") ? icon : "");
            stored.setCapability(actor.getCapability() == null ? ActorCapability.BOTH : actor.getCapability());
            actors.add(stored);
        }
        return actors;
    }

    private List<ExampleScenario> normalizeScenarios(List<ExampleScenario> incoming, List<ExampleActor> actors, IgExampleMessages document) throws GenericException {
        Map<String, ExampleActor> actorById = new HashMap<String, ExampleActor>();
        for (ExampleActor actor : actors) {
            actorById.put(actor.getId(), actor);
        }
        Map<String, ExampleMessage> messageById = new HashMap<String, ExampleMessage>();
        for (ExampleMessage message : document.getExampleMessages()) {
            messageById.put(message.getId(), message);
        }
        List<ExampleScenario> scenarios = new ArrayList<ExampleScenario>();
        Set<String> ids = new HashSet<String>();
        if (incoming == null) {
            return scenarios;
        }
        for (ExampleScenario scenario : incoming) {
            if (scenario == null) {
                continue;
            }
            String name = scenario.getName() == null ? "" : scenario.getName().trim();
            if (name.isEmpty()) {
                throw new GenericException("A scenario needs a name.");
            }
            String id = scenario.getId() == null || scenario.getId().trim().isEmpty() ? new ObjectId().toString() : scenario.getId().trim();
            if (!ids.add(id)) {
                throw new GenericException("Two scenarios share the same id.");
            }
            ExampleScenario stored = new ExampleScenario();
            stored.setId(id);
            stored.setName(name);
            stored.setSummary(scenario.getSummary() == null ? "" : scenario.getSummary().trim());
            String left = blank(scenario.getLeftActorId()) ? "" : scenario.getLeftActorId().trim();
            String right = blank(scenario.getRightActorId()) ? "" : scenario.getRightActorId().trim();
            if (!left.isEmpty() && actorById.get(left) == null) {
                throw new GenericException("The left actor of \"" + name + "\" does not exist.");
            }
            if (!right.isEmpty() && actorById.get(right) == null) {
                throw new GenericException("The right actor of \"" + name + "\" does not exist.");
            }
            if (!left.isEmpty() && left.equals(right)) {
                throw new GenericException("\"" + name + "\" needs two different actors.");
            }
            stored.setLeftActorId(left);
            stored.setRightActorId(right);
            Set<String> stepIds = new HashSet<String>();
            for (ScenarioStep step : scenario.getSteps()) {
                if (step == null) {
                    continue;
                }
                if (left.isEmpty() || right.isEmpty()) {
                    throw new GenericException("Choose the two actors of \"" + name + "\" before adding a line.");
                }
                stored.getSteps().add(normalizeStep(step, stepIds, actorById, messageById, left, right, name));
            }
            scenarios.add(stored);
        }
        return scenarios;
    }

    private ScenarioStep normalizeStep(ScenarioStep step, Set<String> stepIds, Map<String, ExampleActor> actorById, Map<String, ExampleMessage> messageById, String leftId, String rightId, String scenarioName) throws GenericException {
        ExampleActor from = actorById.get(step.getFromActorId());
        ExampleActor to = actorById.get(step.getToActorId());
        if (from == null || to == null) {
            throw new GenericException("Each line in \"" + scenarioName + "\" needs a sender and a receiver.");
        }
        if (from.getId().equals(to.getId())) {
            throw new GenericException("A line in \"" + scenarioName + "\" cannot start and end on the same actor.");
        }
        if (!isPair(from.getId(), to.getId(), leftId, rightId)) {
            throw new GenericException("Lines in \"" + scenarioName + "\" stay between its two actors.");
        }
        if (from.getCapability() == ActorCapability.RECEIVER) {
            throw new GenericException(from.getName() + " can only receive, so it cannot send a line in \"" + scenarioName + "\".");
        }
        if (to.getCapability() == ActorCapability.SENDER) {
            throw new GenericException(to.getName() + " can only send, so it cannot receive a line in \"" + scenarioName + "\".");
        }
        String profileId = step.getProfileId() == null ? "" : step.getProfileId().trim();
        List<String> exampleIds = new ArrayList<String>();
        for (String messageId : step.getExampleMessageIds()) {
            if (messageId == null || messageId.trim().isEmpty() || exampleIds.contains(messageId)) {
                continue;
            }
            ExampleMessage message = messageById.get(messageId);
            if (message == null) {
                throw new GenericException("A line in \"" + scenarioName + "\" points at an example message that no longer exists.");
            }
            if (profileId.isEmpty()) {
                profileId = message.getProfileId();
            }
            if (!profileId.equals(message.getProfileId())) {
                throw new GenericException("Examples on a line must belong to that line's profile.");
            }
            exampleIds.add(messageId);
        }
        if (profileId.isEmpty()) {
            throw new GenericException("Each line in \"" + scenarioName + "\" needs a profile.");
        }
        String id = step.getId() == null || step.getId().trim().isEmpty() ? new ObjectId().toString() : step.getId().trim();
        if (!stepIds.add(id)) {
            id = new ObjectId().toString();
            stepIds.add(id);
        }
        ScenarioStep stored = new ScenarioStep();
        stored.setId(id);
        stored.setFromActorId(from.getId());
        stored.setToActorId(to.getId());
        stored.setProfileId(profileId);
        stored.setExampleMessageIds(exampleIds);
        stored.setTitle(clip(step.getTitle(), 120));
        stored.setDescription(clip(step.getDescription(), 2000));
        stored.setTrigger(clip(step.getTrigger(), 200));
        Set<String> attachedSnippetIds = new HashSet<String>();
        for (String messageId : exampleIds) {
            for (MessageSnippet snippet : messageById.get(messageId).getSnippets()) {
                attachedSnippetIds.add(snippet.getId());
            }
        }
        List<String> pinned = new ArrayList<String>();
        for (String snippetId : step.getPinnedSnippetIds()) {
            if (snippetId != null && attachedSnippetIds.contains(snippetId) && !pinned.contains(snippetId)) {
                pinned.add(snippetId);
            }
        }
        stored.setPinnedSnippetIds(pinned);
        return stored;
    }

    private static String clip(String value, int max) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static boolean isPair(String fromId, String toId, String leftId, String rightId) {
        return (fromId.equals(leftId) && toId.equals(rightId)) || (fromId.equals(rightId) && toId.equals(leftId));
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public List<ExampleMessageUsage> findUsages(String igId, String messageId, String snippetId) throws ResourceNotFoundException {
        Ig ig = requireIg(igId);
        requireMessage(igId, messageId);
        return exampleMessageReferenceService.findUsages(ig, messageId, snippetId);
    }

    public List<ExampleMessageUsage> detach(String igId, String messageId, String snippetId, String sectionId, String kind) throws ResourceNotFoundException, GenericException {
        assertWritable(igId);
        Ig ig = requireIg(igId);
        requireMessage(igId, messageId);
        exampleMessageReferenceService.detach(ig, messageId, snippetId, sectionId, kind);
        igRepository.save(ig);
        return exampleMessageReferenceService.findUsages(ig, messageId, snippetId);
    }

    public IgExampleMessagesDTO deleteExampleMessage(String igId, String messageId) throws ResourceNotFoundException, GenericException {
        assertWritable(igId);
        Ig ig = requireIg(igId);
        IgExampleMessages igExampleMessages = requireMessages(igId);
        List<ExampleMessageUsage> usages = exampleMessageReferenceService.findUsages(ig, messageId, null);
        if (!usages.isEmpty()) {
            throw new GenericException("This example message is still used in the guide. Detach those references before deleting it.");
        }
        for (ExampleScenario scenario : igExampleMessages.getScenarios()) {
            for (ScenarioStep step : scenario.getSteps()) {
                if (step.getExampleMessageIds().contains(messageId)) {
                    throw new GenericException("This example message is still attached to a line in the scenario \"" + scenario.getName() + "\". Remove it from that line before deleting it.");
                }
            }
        }
        boolean removed = false;
        Iterator<ExampleMessage> iterator = igExampleMessages.getExampleMessages().iterator();
        while (iterator.hasNext()) {
            if (messageId.equals(iterator.next().getId())) {
                iterator.remove();
                removed = true;
            }
        }
        if (!removed) {
            throw new ResourceNotFoundException(messageId, Type.EXAMPLEMESSAGE);
        }
        exampleMessagesRepository.save(igExampleMessages);
        return toDTO(igExampleMessages);
    }

    public IgExampleMessagesDTO deleteSnippet(String igId, String messageId, String snippetId) throws ResourceNotFoundException, GenericException {
        assertWritable(igId);
        Ig ig = requireIg(igId);
        IgExampleMessages igExampleMessages = requireMessages(igId);
        ExampleMessage exampleMessage = igExampleMessages.getExampleMessages().stream()
                .filter((m) -> messageId.equals(m.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(messageId, Type.EXAMPLEMESSAGE));
        List<ExampleMessageUsage> usages = exampleMessageReferenceService.findUsages(ig, messageId, snippetId);
        if (!usages.isEmpty()) {
            throw new GenericException("This snippet is still used in the guide. Detach those references before deleting it.");
        }
        boolean removed = exampleMessage.getSnippets().removeIf((snippet) -> snippetId.equals(snippet.getId()));
        if (!removed) {
            throw new ResourceNotFoundException(snippetId, null);
        }
        for (ExampleScenario scenario : igExampleMessages.getScenarios()) {
            for (ScenarioStep step : scenario.getSteps()) {
                step.getPinnedSnippetIds().remove(snippetId);
            }
        }
        exampleMessagesRepository.save(igExampleMessages);
        return toDTO(igExampleMessages);
    }

    private Ig requireIg(String igId) throws ResourceNotFoundException {
        Ig ig = igRepository.findById(igId).orElse(null);
        if (ig == null) {
            throw new ResourceNotFoundException(igId, Type.IGDOCUMENT);
        }
        return ig;
    }

    private IgExampleMessages requireMessages(String igId) throws ResourceNotFoundException {
        IgExampleMessages igExampleMessages = exampleMessagesRepository.findById(igId).orElse(null);
        if (igExampleMessages == null) {
            throw new ResourceNotFoundException(igId, Type.EXAMPLEMESSAGES);
        }
        return igExampleMessages;
    }

    private ExampleMessage requireMessage(String igId, String messageId) throws ResourceNotFoundException {
        return requireMessages(igId).getExampleMessages().stream()
                .filter((message) -> messageId.equals(message.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(messageId, Type.EXAMPLEMESSAGE));
    }

    private void assertWritable(String igId) throws GenericException, ResourceNotFoundException {
        if (isViewOnly(requireIg(igId))) {
            throw new GenericException("This implementation guide is read only.");
        }
    }

    static boolean isViewOnly(Ig ig) {
        DomainInfo info = ig.getDomainInfo();
        boolean notUser = info == null || info.getScope() != Scope.USER;
        Status status = ig.getStatus();
        boolean locked = status == Status.PUBLISHED || status == Status.LOCKED;
        boolean readShare = ig.getSharePermission() == SharePermission.READ;
        return notUser || locked || readShare;
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
