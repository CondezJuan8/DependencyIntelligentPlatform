package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.component.domain.PackageCoordinates;
import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.dto.ManifestDependency;
import com.dependencyintelligence.scan.application.port.out.MavenManifestReader;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/** Reads POM declarations without extracting files or executing project code. */
@Component
public final class BoundedMavenArchiveReader implements MavenManifestReader {
    private static final long MAX_UNCOMPRESSED = 120L * 1024 * 1024;
    private static final int MAX_ENTRIES = 5_000;
    private static final int MAX_MANIFESTS = 64;
    private static final int MAX_POM_BYTES = 1024 * 1024;
    private static final int MAX_DEPENDENCIES = 5_000;
    private static final Pattern PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9_.+-]+");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    @Override public ManifestAnalysis read(InputStream archive) {
        var dependencies = new ArrayList<ManifestDependency>();
        var paths = new HashSet<String>();
        int manifests = 0, unresolved = 0, entries = 0;
        long uncompressed = 0;
        try (var zip = new ZipInputStream(archive)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > MAX_ENTRIES) throw invalid("El ZIP contiene demasiados archivos.");
                String path = entry.getName();
                if (path == null || path.length() > 512 || path.startsWith("/") || path.contains("\\")
                    || path.indexOf('\0') >= 0 || List.of(path.split("/", -1)).contains(".."))
                    throw invalid("El ZIP contiene una ruta no permitida.");
                if (!paths.add(path)) throw invalid("El ZIP contiene rutas duplicadas.");
                boolean pom = !entry.isDirectory() && (path.equals("pom.xml") || path.endsWith("/pom.xml"));
                if (pom && ++manifests > MAX_MANIFESTS) throw invalid("Demasiados archivos pom.xml.");
                var pomBytes = pom ? new ByteArrayOutputStream() : null;
                byte[] buffer = new byte[8192];
                for (int count; (count = zip.read(buffer)) != -1;) {
                    uncompressed += count;
                    if (uncompressed > MAX_UNCOMPRESSED) throw invalid("El ZIP supera el límite de datos descomprimidos.");
                    if (pom) {
                        if (pomBytes.size() + count > MAX_POM_BYTES) throw invalid("Un pom.xml supera 1 MiB.");
                        pomBytes.write(buffer, 0, count);
                    }
                }
                if (pom) {
                    unresolved += parsePom(path, pomBytes.toByteArray(), dependencies);
                    if (dependencies.size() > MAX_DEPENDENCIES) throw invalid("Demasiadas dependencias declaradas.");
                }
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo leer el ZIP del repositorio.", exception);
        }
        return new ManifestAnalysis(dependencies, manifests, unresolved);
    }

    private static int parsePom(String path, byte[] bytes, List<ManifestDependency> output) {
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
            if (!"project".equals(root.getTagName())) throw invalid("Un pom.xml no tiene raíz project.");
            Map<String, String> properties = new HashMap<>();
            var propertyRoot = child(root, "properties");
            if (propertyRoot != null) {
                for (Node item = propertyRoot.getFirstChild(); item != null; item = item.getNextSibling()) {
                    if (item instanceof Element element) properties.put(element.getTagName(), element.getTextContent().trim());
                }
            }
            String projectVersion = value(root, "version");
            String projectGroup = value(root, "groupId");
            var parent = child(root, "parent");
            if (projectVersion.isBlank() && parent != null) projectVersion = value(parent, "version");
            if (projectGroup.isBlank() && parent != null) projectGroup = value(parent, "groupId");
            properties.put("project.version", projectVersion);
            properties.put("pom.version", projectVersion);
            properties.put("project.groupId", projectGroup);
            properties.put("pom.groupId", projectGroup);
            var declared = child(root, "dependencies");
            if (declared == null) return 0;
            int unresolved = 0;
            for (Node item = declared.getFirstChild(); item != null; item = item.getNextSibling()) {
                if (!(item instanceof Element dependency) || !"dependency".equals(dependency.getTagName())) continue;
                String group = resolve(value(dependency, "groupId"), properties);
                String artifact = resolve(value(dependency, "artifactId"), properties);
                String version = resolve(value(dependency, "version"), properties);
                if (!PART.matcher(group).matches() || !PART.matcher(artifact).matches()
                    || !VERSION.matcher(version).matches() || group.length() > 255
                    || artifact.length() > 512 || version.length() > 255 || path.length() > 240) {
                    unresolved++; continue;
                }
                String scope = value(dependency, "scope");
                if ("true".equalsIgnoreCase(value(dependency, "optional"))) scope = "optional";
                else if (!Set.of("runtime", "test", "provided", "system", "compile").contains(scope)) scope = "compile";
                if ("compile".equals(scope) || "system".equals(scope)) scope = "runtime";
                var coordinates = new PackageCoordinates("maven", group, artifact, version,
                    "pkg:maven/" + group + "/" + artifact + "@" + version);
                output.add(new ManifestDependency(path, coordinates, scope));
            }
            return unresolved;
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("No se pudo interpretar " + path + ".", exception); }
    }

    private static Element child(Element parent, String name) {
        for (Node item = parent.getFirstChild(); item != null; item = item.getNextSibling()) {
            if (item instanceof Element element && name.equals(element.getTagName())) return element;
        }
        return null;
    }

    private static String value(Element parent, String name) {
        var element = child(parent, name);
        return element == null ? "" : element.getTextContent().trim();
    }

    private static String resolve(String input, Map<String, String> properties) {
        String value = input;
        for (int index = 0; index < 5; index++) {
            var matcher = PROPERTY.matcher(value);
            if (!matcher.find()) break;
            var replaced = new StringBuffer();
            do {
                String property = properties.get(matcher.group(1));
                if (property == null) return "";
                matcher.appendReplacement(replaced, java.util.regex.Matcher.quoteReplacement(property));
            } while (matcher.find());
            matcher.appendTail(replaced);
            value = replaced.toString();
        }
        return PROPERTY.matcher(value).find() ? "" : value;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
