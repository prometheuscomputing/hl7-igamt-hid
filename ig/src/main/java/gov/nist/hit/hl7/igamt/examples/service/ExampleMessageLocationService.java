package gov.nist.hit.hl7.igamt.examples.service;

import gov.nist.hit.hl7.igamt.common.base.domain.LocationInfo;
import gov.nist.hit.hl7.igamt.common.base.domain.Type;
import gov.nist.hit.hl7.igamt.common.base.domain.ValuesetBinding;
import gov.nist.hit.hl7.igamt.common.base.domain.display.DisplayElement;
import gov.nist.hit.hl7.igamt.common.binding.domain.ResourceBinding;
import gov.nist.hit.hl7.igamt.common.binding.domain.StructureElementBinding;
import gov.nist.hit.hl7.igamt.examples.domain.ExampleMessage;
import gov.nist.hit.hl7.igamt.examples.dto.ExampleMessageLocation;
import gov.nist.hit.hl7.igamt.examples.dto.ExampleMessageLocationContext;
import gov.nist.hit.hl7.igamt.examples.dto.ExampleMessageLocationHop;
import gov.nist.hit.hl7.igamt.ig.model.ResourceRef;
import gov.nist.hit.hl7.igamt.ig.model.ResourceSkeleton;
import gov.nist.hit.hl7.igamt.ig.model.ResourceSkeletonBone;
import gov.nist.hit.hl7.igamt.service.impl.ResourceSkeletonService;
import gov.nist.hit.hl7.igamt.valueset.domain.Valueset;
import gov.nist.hit.hl7.igamt.valueset.service.ValuesetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Resolves a parsed or validated message path to the IG resource actually used
 * at that location. Segment names alone are not enough (two PIDs can be two
 * flavors); the profile positional path from the parser is.
 */
@Service
public class ExampleMessageLocationService {

    @Autowired
    private ExampleMessagesService exampleMessagesService;
    @Autowired
    private ResourceSkeletonService resourceSkeletonService;
    @Autowired
    private ValuesetService valuesetService;

    public ExampleMessageLocation locate(String igId, String messageId, String positionalPath, String hl7Path) {
        ExampleMessageLocation result = new ExampleMessageLocation();
        result.setIgId(igId);
        try {
            ExampleMessage exampleMessage = exampleMessagesService.getExampleMessage(igId, messageId);
            if (exampleMessage == null || isBlank(exampleMessage.getProfileId())) {
                result.setError("Conformance profile is missing");
                return result;
            }
            result.setProfileId(exampleMessage.getProfileId());

            ResourceSkeleton skeleton = new ResourceSkeleton(
                    new ResourceRef(Type.CONFORMANCEPROFILE, exampleMessage.getProfileId()),
                    resourceSkeletonService
            );
            skeleton.get();

            String profilePositional = toProfilePositional(positionalPath);
            ResourceSkeletonBone bone = null;
            if (!isBlank(profilePositional)) {
                bone = skeleton.getByPositionPath(profilePositional);
            }

            boolean ambiguous = false;
            if (bone == null && !isBlank(hl7Path)) {
                List<ResourceSkeletonBone> matches = new ArrayList<ResourceSkeletonBone>();
                collectByHl7Path(skeleton, normalizeHl7(hl7Path), matches);
                int instance = firstInstance(hl7Path);
                if (matches.size() > 1) {
                    ambiguous = true;
                }
                if (!matches.isEmpty()) {
                    int index = instance > 0 ? instance - 1 : 0;
                    if (index >= matches.size()) {
                        index = 0;
                    }
                    bone = matches.get(index);
                }
            }

            if (bone == null) {
                result.setError("Could not locate this path in the conformance profile");
                return result;
            }

            bone.get();
            fill(result, skeleton, bone, ambiguous);
        } catch (Exception e) {
            result.setError(e.getMessage() != null ? e.getMessage() : "Failed to locate path in the IG");
        }
        return result;
    }

    private void fill(ExampleMessageLocation result, ResourceSkeleton skeleton, ResourceSkeletonBone bone, boolean ambiguous) throws Exception {
        LocationInfo info = bone.getLocationInfo();
        result.setHl7Path(info != null ? info.getHl7Path() : null);
        result.setType(info != null && info.getType() != null ? info.getType().name() : null);
        result.setName(info != null ? info.getName() : null);
        result.setAmbiguous(ambiguous);
        result.setHops(buildHops(bone));
        result.setContexts(buildContexts(result, skeleton, bone));
        applyDefaultContext(result);
    }

    private List<ExampleMessageLocationContext> buildContexts(ExampleMessageLocation result, ResourceSkeleton skeleton, ResourceSkeletonBone leaf) throws Exception {
        List<ExampleMessageLocationContext> contexts = new ArrayList<ExampleMessageLocationContext>();
        LocationInfo leafInfo = leaf.getLocationInfo();
        Type leafType = leafInfo != null ? leafInfo.getType() : null;
        String leafPathId = leafInfo != null ? leafInfo.getPathId() : null;
        String leafHl7 = leafInfo != null ? leafInfo.getHl7Path() : null;

        DisplayElement profile = skeleton.getResource();
        String profileName = displayName(profile);
        addContext(contexts,
                "PROFILE",
                "Conformance profile",
                isBlank(profileName) ? "Open this location in the message structure" : profileName,
                "conformanceprofile",
                result.getProfileId(),
                profileName,
                leafPathId,
                leafHl7);

        String segmentLocation = needsRelative(leafType, Type.FIELD) ? relativePathFrom(leaf, Type.FIELD) : null;
        String componentLocation = needsRelative(leafType, Type.COMPONENT) ? relativePathFrom(leaf, Type.COMPONENT) : null;
        String subcomponentLocation = needsRelative(leafType, Type.SUBCOMPONENT) ? relativePathFrom(leaf, Type.SUBCOMPONENT) : null;

        for (ExampleMessageLocationHop hop : result.getHops()) {
            if (hop.getResourceId() == null || hop.getResourceType() == null) {
                continue;
            }
            String hopHl7 = hop.getHl7Path();
            String detail = joinDetail(hopHl7, hop.getName());
            if ("SEGMENT".equals(hop.getResourceType()) && "SEGMENTREF".equals(hop.getType())) {
                addContext(contexts,
                        "SEGMENT",
                        "Segment " + hop.getResourceName(),
                        detail,
                        "segment",
                        hop.getResourceId(),
                        hop.getResourceName(),
                        segmentLocation,
                        hopHl7);
            }
            if ("DATATYPE".equals(hop.getResourceType()) && "FIELD".equals(hop.getType())) {
                addContext(contexts,
                        "DATATYPE",
                        "Datatype " + hop.getResourceName(),
                        "Used at " + detail,
                        "datatype",
                        hop.getResourceId(),
                        hop.getResourceName(),
                        componentLocation,
                        hopHl7);
            }
            if ("DATATYPE".equals(hop.getResourceType()) && "COMPONENT".equals(hop.getType())) {
                addContext(contexts,
                        "DATATYPE",
                        "Datatype " + hop.getResourceName(),
                        "Used at " + detail,
                        "datatype",
                        hop.getResourceId(),
                        hop.getResourceName(),
                        subcomponentLocation,
                        hopHl7);
            }
            if ("DATATYPE".equals(hop.getResourceType()) && "SUBCOMPONENT".equals(hop.getType())) {
                addContext(contexts,
                        "DATATYPE",
                        "Datatype " + hop.getResourceName(),
                        "Used at " + detail,
                        "datatype",
                        hop.getResourceId(),
                        hop.getResourceName(),
                        null,
                        hopHl7);
            }
        }
        addBindingContexts(contexts, leaf);
        return contexts;
    }

    private void applyDefaultContext(ExampleMessageLocation result) {
        List<ExampleMessageLocationContext> contexts = result.getContexts();
        if (contexts == null || contexts.isEmpty()) {
            return;
        }
        ExampleMessageLocationContext chosen = contexts.get(0);
        ExampleMessageLocationContext valueset = null;
        ExampleMessageLocationContext requiredValueset = null;
        ExampleMessageLocationContext structure = null;
        for (ExampleMessageLocationContext context : contexts) {
            if ("VALUESET".equals(context.getKind())) {
                if (valueset == null) {
                    valueset = context;
                }
                if (context.getDetail() != null && context.getDetail().indexOf("Required") >= 0) {
                    requiredValueset = context;
                }
            } else if ("SEGMENT".equals(context.getKind()) || "DATATYPE".equals(context.getKind())) {
                structure = context;
            }
        }
        if (requiredValueset != null) {
            chosen = requiredValueset;
        } else if (valueset != null) {
            chosen = valueset;
        } else if (structure != null) {
            chosen = structure;
        } else {
            chosen = contexts.get(contexts.size() - 1);
        }
        result.setRouteType(chosen.getRouteType());
        result.setResourceId(chosen.getResourceId());
        result.setResourceName(chosen.getResourceName());
        result.setLocation(chosen.getLocation());
    }

    private void addBindingContexts(List<ExampleMessageLocationContext> contexts, ResourceSkeletonBone leaf) throws Exception {
        ResourceSkeleton current = leaf;
        while (current instanceof ResourceSkeletonBone) {
            ResourceSkeletonBone bone = (ResourceSkeletonBone) current;
            ResourceSkeleton parent = bone.getParentSkeleton();
            if (parent != null) {
                parent.get();
                addBindingsFromResource(contexts, parent, leaf);
            }
            current = bone.getParentSkeleton();
        }
    }

    private void addBindingsFromResource(List<ExampleMessageLocationContext> contexts, ResourceSkeleton resource, ResourceSkeletonBone leaf) throws Exception {
        ResourceBinding binding = resource.getResourceBindings();
        if (binding == null || binding.getChildren() == null) {
            return;
        }
        String relative = elementPathFrom(resource, leaf);
        collectBindings(binding.getChildren(), "", relative, leaf, contexts);
    }

    private void collectBindings(Set<StructureElementBinding> children, String parentPath, String relative,
                                 ResourceSkeletonBone leaf, List<ExampleMessageLocationContext> contexts) {
        if (children == null || isBlank(relative)) {
            return;
        }
        for (StructureElementBinding binding : children) {
            if (binding == null || isBlank(binding.getElementId())) {
                continue;
            }
            String pathId = isBlank(parentPath) ? binding.getElementId() : parentPath + "-" + binding.getElementId();
            if (bindingApplies(pathId, relative)) {
                addValuesetContexts(binding.getValuesetBindings(), pathId, relative, leaf, contexts);
            }
            if (binding.getChildren() != null && !binding.getChildren().isEmpty()) {
                collectBindings(binding.getChildren(), pathId, relative, leaf, contexts);
            }
        }
    }

    private boolean bindingApplies(String pathId, String relative) {
        return pathId.equals(relative) || relative.startsWith(pathId + "-");
    }

    private void addValuesetContexts(Set<ValuesetBinding> bindings, String pathId, String relative,
                                     ResourceSkeletonBone leaf, List<ExampleMessageLocationContext> contexts) {
        if (bindings == null) {
            return;
        }
        LocationInfo leafInfo = leaf.getLocationInfo();
        String leafHl7 = leafInfo != null ? leafInfo.getHl7Path() : null;
        Integer childPosition = childPositionAfter(pathId, relative, leaf);
        for (ValuesetBinding binding : bindings) {
            if (!locationApplies(binding, pathId, relative, childPosition)) {
                continue;
            }
            if (binding.getValueSets() == null) {
                continue;
            }
            String strength = binding.getStrength() != null ? binding.getStrength().value : null;
            for (String valuesetId : binding.getValueSets()) {
                if (isBlank(valuesetId)) {
                    continue;
                }
                Valueset valueset = valuesetService.findById(valuesetId);
                String identifier = valueset != null ? valueset.getBindingIdentifier() : null;
                String name = valueset != null ? valueset.getName() : null;
                String label = !isBlank(identifier) ? identifier : (!isBlank(name) ? name : valuesetId);
                String detail = !isBlank(leafHl7) ? "Bound at " + leafHl7 : "Associated value set binding";
                if (!isBlank(name) && !name.equals(label)) {
                    detail = name + " — " + detail;
                }
                if (!isBlank(strength)) {
                    detail = detail + " (" + strength + ")";
                }
                addContext(contexts,
                        "VALUESET",
                        "Value set " + label,
                        detail,
                        "valueset",
                        valuesetId,
                        label,
                        null,
                        leafHl7);
            }
        }
    }

    private boolean locationApplies(ValuesetBinding binding, String pathId, String relative, Integer childPosition) {
        if (pathId.equals(relative)) {
            return true;
        }
        Set<Integer> locations = binding.getValuesetLocations();
        if (locations == null || locations.isEmpty()) {
            return true;
        }
        return childPosition != null && locations.contains(childPosition);
    }

    private Integer childPositionAfter(String pathId, String relative, ResourceSkeletonBone leaf) {
        if (isBlank(pathId) || isBlank(relative) || !relative.startsWith(pathId + "-")) {
            return null;
        }
        String remainder = relative.substring(pathId.length() + 1);
        int dash = remainder.indexOf('-');
        String childId = dash >= 0 ? remainder.substring(0, dash) : remainder;
        ResourceSkeleton current = leaf;
        while (current instanceof ResourceSkeletonBone) {
            ResourceSkeletonBone bone = (ResourceSkeletonBone) current;
            if (childId.equals(bone.getElementId())) {
                return bone.getPosition();
            }
            current = bone.getParentSkeleton();
        }
        return null;
    }

    private String elementPathFrom(ResourceSkeleton root, ResourceSkeletonBone leaf) {
        List<String> ids = new ArrayList<String>();
        ResourceSkeleton current = leaf;
        while (current instanceof ResourceSkeletonBone) {
            ResourceSkeletonBone bone = (ResourceSkeletonBone) current;
            ids.add(0, bone.getElementId());
            current = bone.getParentSkeleton();
            if (current == root) {
                break;
            }
        }
        if (ids.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                builder.append('-');
            }
            builder.append(ids.get(i));
        }
        return builder.toString();
    }

    private void addContext(List<ExampleMessageLocationContext> contexts, String kind, String label, String detail,
                            String routeType, String resourceId, String resourceName, String location, String hl7Path) {
        if (isBlank(resourceId) || isBlank(routeType)) {
            return;
        }
        for (ExampleMessageLocationContext existing : contexts) {
            if (routeType.equals(existing.getRouteType()) && resourceId.equals(existing.getResourceId())) {
                return;
            }
        }
        ExampleMessageLocationContext context = new ExampleMessageLocationContext();
        context.setKind(kind);
        context.setLabel(label);
        context.setDetail(detail);
        context.setRouteType(routeType);
        context.setResourceId(resourceId);
        context.setResourceName(resourceName);
        context.setLocation(location);
        context.setHl7Path(hl7Path);
        contexts.add(context);
    }

    private boolean needsRelative(Type leafType, Type startType) {
        if (leafType == null) {
            return false;
        }
        if (startType == Type.FIELD) {
            return leafType == Type.FIELD || leafType == Type.COMPONENT || leafType == Type.SUBCOMPONENT;
        }
        if (startType == Type.COMPONENT) {
            return leafType == Type.COMPONENT || leafType == Type.SUBCOMPONENT;
        }
        return leafType == startType;
    }

    private String joinDetail(String hl7Path, String name) {
        if (!isBlank(hl7Path) && !isBlank(name)) {
            return hl7Path + " — " + name;
        }
        if (!isBlank(hl7Path)) {
            return hl7Path;
        }
        return name;
    }

    private List<ExampleMessageLocationHop> buildHops(ResourceSkeletonBone bone) throws Exception {
        List<ResourceSkeletonBone> chain = new ArrayList<ResourceSkeletonBone>();
        ResourceSkeleton current = bone;
        while (current instanceof ResourceSkeletonBone) {
            ResourceSkeletonBone hop = (ResourceSkeletonBone) current;
            hop.get();
            chain.add(hop);
            current = hop.getParentSkeleton();
        }
        Collections.reverse(chain);
        List<ExampleMessageLocationHop> hops = new ArrayList<ExampleMessageLocationHop>();
        for (ResourceSkeletonBone hop : chain) {
            hops.add(toHop(hop));
        }
        return hops;
    }

    private ExampleMessageLocationHop toHop(ResourceSkeletonBone bone) {
        ExampleMessageLocationHop hop = new ExampleMessageLocationHop();
        LocationInfo info = bone.getLocationInfo();
        if (info != null) {
            hop.setType(info.getType() != null ? info.getType().name() : null);
            hop.setHl7Path(info.getHl7Path());
            hop.setName(info.getName());
            hop.setPathId(info.getPathId());
            hop.setPositionalPath(info.getPositionalPath());
        }
        DisplayElement resource = bone.getResource();
        if (resource == null && bone.getResourceRef() != null) {
            resource = bone.getParent();
        }
        if (resource != null) {
            hop.setResourceType(resource.getType() != null ? resource.getType().name() : null);
            hop.setResourceId(resource.getId());
            hop.setResourceName(displayName(resource));
        }
        return hop;
    }

    private ResourceSkeletonBone findAncestor(ResourceSkeletonBone bone, Type type) throws Exception {
        ResourceSkeleton current = bone;
        while (current instanceof ResourceSkeletonBone) {
            ResourceSkeletonBone hop = (ResourceSkeletonBone) current;
            hop.get();
            LocationInfo info = hop.getLocationInfo();
            if (info != null && info.getType() == type) {
                return hop;
            }
            current = hop.getParentSkeleton();
        }
        return null;
    }

    private String relativePathFrom(ResourceSkeletonBone bone, Type startType) throws Exception {
        List<String> ids = new ArrayList<String>();
        ResourceSkeleton current = bone;
        boolean started = false;
        List<ResourceSkeletonBone> reverse = new ArrayList<ResourceSkeletonBone>();
        while (current instanceof ResourceSkeletonBone) {
            reverse.add((ResourceSkeletonBone) current);
            current = ((ResourceSkeletonBone) current).getParentSkeleton();
        }
        Collections.reverse(reverse);
        for (ResourceSkeletonBone hop : reverse) {
            hop.get();
            LocationInfo info = hop.getLocationInfo();
            Type type = info != null ? info.getType() : null;
            if (!started && type == startType) {
                started = true;
            }
            if (started) {
                ids.add(hop.getElementId());
            }
        }
        if (ids.isEmpty()) {
            return bone.getElementId();
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                builder.append('-');
            }
            builder.append(ids.get(i));
        }
        return builder.toString();
    }

    private void collectByHl7Path(ResourceSkeleton node, String target, List<ResourceSkeletonBone> matches) throws Exception {
        if (node == null || isBlank(target)) {
            return;
        }
        node.get();
        if (node instanceof ResourceSkeletonBone) {
            ResourceSkeletonBone bone = (ResourceSkeletonBone) node;
            LocationInfo info = bone.getLocationInfo();
            if (info != null && target.equals(normalizeHl7(info.getHl7Path()))) {
                matches.add(bone);
            }
        }
        List<ResourceSkeletonBone> children = node.getChildren();
        if (children == null) {
            return;
        }
        for (ResourceSkeletonBone child : children) {
            collectByHl7Path(child, target, matches);
        }
    }

    static String toProfilePositional(String positionalPath) {
        if (isBlank(positionalPath)) {
            return null;
        }
        String stripped = positionalPath.replaceAll("\\[\\d+]", "");
        stripped = stripped.replace('/', '.');
        stripped = stripped.replaceAll("\\.+", ".").replaceAll("^\\.|\\.$", "");
        return isBlank(stripped) ? null : stripped;
    }

    static String normalizeHl7(String hl7Path) {
        if (isBlank(hl7Path)) {
            return "";
        }
        return hl7Path.replaceAll("\\[\\d+]", "").trim();
    }

    static int firstInstance(String hl7Path) {
        if (isBlank(hl7Path)) {
            return 1;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[(\\d+)]").matcher(hl7Path);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 1;
    }

    private static String displayName(DisplayElement element) {
        if (element == null) {
            return "";
        }
        String fixed = element.getFixedName() != null ? element.getFixedName() : "";
        String variable = element.getVariableName() != null ? element.getVariableName() : "";
        if (!isBlank(fixed) && !isBlank(variable)) {
            return fixed + "_" + variable;
        }
        if (!isBlank(variable)) {
            return variable;
        }
        return fixed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
