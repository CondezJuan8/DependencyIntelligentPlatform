package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.component.domain.PackageCoordinates;
import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.dto.ManifestDependency;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Resolves direct Maven declarations using POMs present in the uploaded source only. */
final class MavenPomDependencyResolver {
    private static final Pattern PART = Pattern.compile("[A-Za-z0-9_.-]+(?:\\.[A-Za-z0-9_.-]+)*");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9_.+!-][A-Za-z0-9_.+!-]*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final int MAX_MODEL_DEPTH = 32;

    ManifestAnalysis resolve(Map<String, byte[]> documents) {
        Map<String, RawPom> rawPoms = new LinkedHashMap<>();
        int malformed = 0;
        for (var entry : documents.entrySet()) {
            try { rawPoms.put(entry.getKey(), parse(entry.getKey(), entry.getValue())); }
            catch (RuntimeException failure) { malformed++; }
        }
        ModelSet models = new ModelSet(rawPoms);
        Map<String, EffectivePom> byGav = models.indexByCoordinates();
        int unresolved = malformed;
        List<ManifestDependency> dependencies = new ArrayList<>();
        for (RawPom raw : rawPoms.values()) {
            EffectivePom project = models.model(raw.path(), byGav, new HashSet<>(), 0);
            for (RawDependency declared : raw.dependencies()) {
                String group = expand(declared.groupId(), project);
                String artifact = expand(declared.artifactId(), project);
                String key = key(group, artifact);
                ManagedDependency managed = project.management().get(key);
                String version = expand(declared.version(), project);
                if (version.isBlank() && managed != null) version = managed.version();
                if (!validPart(group) || !validPart(artifact) || !validVersion(version) || raw.path().length() > 240) {
                    unresolved++;
                    continue;
                }
                String scope = expand(declared.scope(), project);
                if (scope.isBlank() && managed != null) scope = managed.scope();
                if (scope.isBlank()) scope = "compile";
                if ("compile".equals(scope) || "system".equals(scope)) scope = "runtime";
                if (!Set.of("runtime", "test", "provided", "optional").contains(scope)) scope = "runtime";
                String optional = expand(declared.optional(), project);
                if ("true".equalsIgnoreCase(optional)) scope = "optional";
                dependencies.add(new ManifestDependency(raw.path(),
                    new PackageCoordinates("maven", group, artifact, version,
                        "pkg:maven/" + group + "/" + artifact + "@" + version), scope));
            }
        }
        return new ManifestAnalysis(dependencies, documents.size(), unresolved);
    }

    private static RawPom parse(String path, byte[] bytes) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            var root = builder.parse(new InputSource(new ByteArrayInputStream(bytes))).getDocumentElement();
            if (!"project".equals(localName(root))) throw new IllegalArgumentException("Invalid Maven POM root");
            Element parent = child(root, "parent");
            String relativePath = parent == null ? null : value(parent, "relativePath");
            boolean relativePathSpecified = parent != null && child(parent, "relativePath") != null;
            String parentPath = parentPath(path, relativePath, relativePathSpecified);
            Map<String, String> properties = new LinkedHashMap<>();
            Element propertyRoot = child(root, "properties");
            if (propertyRoot != null) for (Node item = propertyRoot.getFirstChild(); item != null; item = item.getNextSibling())
                if (item instanceof Element element) properties.put(localName(element), element.getTextContent().trim());
            List<RawDependency> dependencies = readDependencies(child(root, "dependencies"));
            List<RawDependency> managed = readDependencies(child(child(root, "dependencyManagement"), "dependencies"));
            return new RawPom(path, value(root, "groupId"), value(root, "artifactId"), value(root, "version"),
                parent == null ? null : value(parent, "groupId"), parent == null ? null : value(parent, "artifactId"),
                parent == null ? null : value(parent, "version"), parentPath, properties, dependencies, managed);
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("Invalid Maven POM", exception); }
    }

    private static List<RawDependency> readDependencies(Element container) {
        if (container == null) return List.of();
        List<RawDependency> result = new ArrayList<>();
        for (Node item = container.getFirstChild(); item != null; item = item.getNextSibling()) {
            if (!(item instanceof Element dependency) || !"dependency".equals(localName(dependency))) continue;
            result.add(new RawDependency(value(dependency, "groupId"), value(dependency, "artifactId"),
                value(dependency, "version"), value(dependency, "scope"), value(dependency, "optional"),
                value(dependency, "type"), value(dependency, "classifier")));
        }
        return result;
    }

    private static String parentPath(String pomPath, String relativePath, boolean explicitlySet) {
        if (explicitlySet && relativePath.isBlank()) return null;
        String relative = explicitlySet ? relativePath : "../pom.xml";
        try {
            Path base = Path.of(pomPath).getParent();
            Path resolved = (base == null ? Path.of("") : base).resolve(relative);
            if (!relative.endsWith(".xml")) resolved = resolved.resolve("pom.xml");
            Path normalized = resolved.normalize();
            String value = normalized.toString().replace('\\', '/');
            return value.startsWith("../") || value.equals("..") ? null : value;
        } catch (RuntimeException invalid) { return null; }
    }

    private static String expand(String input, EffectivePom model) {
        if (input == null || input.isBlank()) return "";
        String value = input.trim();
        Map<String, String> context = new HashMap<>(model.properties());
        context.put("project.groupId", model.groupId()); context.put("pom.groupId", model.groupId());
        context.put("project.artifactId", model.artifactId()); context.put("pom.artifactId", model.artifactId());
        context.put("project.version", model.version()); context.put("pom.version", model.version());
        context.put("project.parent.groupId", model.parentGroupId()); context.put("pom.parent.groupId", model.parentGroupId());
        context.put("project.parent.artifactId", model.parentArtifactId()); context.put("pom.parent.artifactId", model.parentArtifactId());
        context.put("project.parent.version", model.parentVersion()); context.put("pom.parent.version", model.parentVersion());
        for (int depth = 0; depth < MAX_MODEL_DEPTH; depth++) {
            var matcher = PROPERTY.matcher(value);
            if (!matcher.find()) return value;
            var replaced = new StringBuilder();
            do {
                String replacement = context.get(matcher.group(1));
                if (replacement == null || replacement.equals(matcher.group())) return "";
                matcher.appendReplacement(replaced, java.util.regex.Matcher.quoteReplacement(replacement));
            } while (matcher.find());
            matcher.appendTail(replaced);
            if (replaced.toString().equals(value)) return "";
            value = replaced.toString();
        }
        return PROPERTY.matcher(value).find() ? "" : value;
    }

    private static boolean validPart(String value) { return value != null && PART.matcher(value).matches() && value.length() <= 512; }
    private static boolean validVersion(String value) { return value != null && VERSION.matcher(value).matches() && value.length() <= 255; }
    private static String key(String group, String artifact) { return group + ":" + artifact; }

    private static Element child(Element parent, String name) {
        if (parent == null) return null;
        for (Node item = parent.getFirstChild(); item != null; item = item.getNextSibling())
            if (item instanceof Element element && name.equals(localName(element))) return element;
        return null;
    }

    private static String value(Element parent, String name) {
        Element element = child(parent, name);
        return element == null ? "" : element.getTextContent().trim();
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private static RawPom findParent(RawPom raw, Map<String, RawPom> rawPoms) {
        if (raw.parentPath() != null && rawPoms.containsKey(raw.parentPath())) return rawPoms.get(raw.parentPath());
        return rawPoms.values().stream().filter(candidate -> candidate.artifactId().equals(raw.parentArtifactId())
            && (raw.parentGroupId().isBlank() || candidate.groupId().equals(raw.parentGroupId()))
            && (raw.parentVersion().isBlank() || candidate.version().equals(raw.parentVersion()))).findFirst().orElse(null);
    }

    private record RawPom(String path, String groupId, String artifactId, String version,
                          String parentGroupId, String parentArtifactId, String parentVersion, String parentPath,
                          Map<String, String> properties, List<RawDependency> dependencies, List<RawDependency> managed) { }

    private record RawDependency(String groupId, String artifactId, String version, String scope,
                                 String optional, String type, String classifier) { }

    private record ManagedDependency(String version, String scope) { }

    private record EffectivePom(String path, String groupId, String artifactId, String version,
                                String parentGroupId, String parentArtifactId, String parentVersion,
                                Map<String, String> properties, Map<String, ManagedDependency> management) { }

    private static final class ModelSet {
        private final Map<String, RawPom> rawPoms;
        private final Map<String, EffectivePom> bases = new HashMap<>();
        private final Map<String, EffectivePom> complete = new HashMap<>();

        private ModelSet(Map<String, RawPom> rawPoms) { this.rawPoms = rawPoms; }

        private Map<String, EffectivePom> indexByCoordinates() {
            Map<String, EffectivePom> index = new HashMap<>();
            for (String path : rawPoms.keySet()) {
                EffectivePom model = base(path, new HashSet<>(), 0);
                if (validPart(model.groupId()) && validPart(model.artifactId()) && validVersion(model.version()))
                    index.putIfAbsent(key(model.groupId(), model.artifactId()) + ":" + model.version(), model);
            }
            return index;
        }

        private EffectivePom model(String path, Map<String, EffectivePom> byGav, Set<String> visiting, int depth) {
            if (complete.containsKey(path)) return complete.get(path);
            if (depth > MAX_MODEL_DEPTH || !visiting.add(path)) return bases.get(path);
            EffectivePom base = bases.computeIfAbsent(path, ignored -> base(path, new HashSet<>(), 0));
            RawPom raw = rawPoms.get(path);
            Map<String, ManagedDependency> management = new LinkedHashMap<>();
            RawPom parent = findParent(raw, rawPoms);
            if (parent != null) {
                EffectivePom parentModel = model(parent.path(), byGav, visiting, depth + 1);
                if (parentModel != null) management.putAll(parentModel.management());
            }
            Map<String, ManagedDependency> imports = new LinkedHashMap<>();
            for (RawDependency dependency : raw.managed()) {
                String type = expand(dependency.type(), base);
                String scope = expand(dependency.scope(), base);
                String group = expand(dependency.groupId(), base);
                String artifact = expand(dependency.artifactId(), base);
                String version = expand(dependency.version(), base);
                if ("pom".equals(type) && "import".equals(scope)) {
                    EffectivePom bom = byGav.get(key(group, artifact) + ":" + version);
                    if (bom != null && !visiting.contains(bom.path())) {
                        EffectivePom imported = model(bom.path(), byGav, visiting, depth + 1);
                        if (imported != null) imported.management().forEach(imports::putIfAbsent);
                    }
                }
            }
            management.putAll(imports);
            for (RawDependency dependency : raw.managed()) {
                String scope = expand(dependency.scope(), base);
                if ("import".equals(scope) && "pom".equals(expand(dependency.type(), base))) continue;
                String group = expand(dependency.groupId(), base);
                String artifact = expand(dependency.artifactId(), base);
                String version = expand(dependency.version(), base);
                if (validPart(group) && validPart(artifact) && validVersion(version))
                    management.put(key(group, artifact), new ManagedDependency(version, scope));
            }
            EffectivePom result = new EffectivePom(base.path(), base.groupId(), base.artifactId(), base.version(),
                base.parentGroupId(), base.parentArtifactId(), base.parentVersion(), base.properties(), Map.copyOf(management));
            visiting.remove(path);
            complete.put(path, result);
            return result;
        }

        private EffectivePom base(String path, Set<String> visiting, int depth) {
            if (bases.containsKey(path)) return bases.get(path);
            RawPom raw = rawPoms.get(path);
            if (raw == null || depth > MAX_MODEL_DEPTH || !visiting.add(path)) return null;
            RawPom parent = findParent(raw, rawPoms);
            EffectivePom parentModel = parent == null ? null : base(parent.path(), visiting, depth + 1);
            Map<String, String> properties = new LinkedHashMap<>();
            if (parentModel != null) properties.putAll(parentModel.properties());
            properties.putAll(raw.properties());
            String group = raw.groupId().isBlank() ? (parentModel == null ? raw.parentGroupId() : parentModel.groupId()) : raw.groupId();
            String artifact = raw.artifactId();
            String version = raw.version().isBlank() ? (parentModel == null ? raw.parentVersion() : parentModel.version()) : raw.version();
            EffectivePom provisional = new EffectivePom(path, "", "", "", raw.parentGroupId(), raw.parentArtifactId(), raw.parentVersion(),
                properties, Map.of());
            String resolvedGroup = expand(group, provisional);
            String resolvedArtifact = expand(artifact, provisional);
            String resolvedVersion = expand(version, provisional);
            EffectivePom result = new EffectivePom(path, resolvedGroup, resolvedArtifact, resolvedVersion,
                parentModel == null ? raw.parentGroupId() : parentModel.groupId(),
                parentModel == null ? raw.parentArtifactId() : parentModel.artifactId(),
                parentModel == null ? raw.parentVersion() : parentModel.version(), Map.copyOf(properties), Map.of());
            bases.put(path, result);
            visiting.remove(path);
            return result;
        }
    }
}
