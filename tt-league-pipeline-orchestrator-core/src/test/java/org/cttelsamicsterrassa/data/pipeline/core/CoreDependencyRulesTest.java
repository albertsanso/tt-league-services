package org.cttelsamicsterrassa.data.pipeline.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class CoreDependencyRulesTest {

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "org.springframework.",
            "jakarta.persistence.",
            "org.hibernate.",
            "java.net.http.",
            "org.cttelsamicsterrassa.data.core.",
            "org.cttelsamicsterrassa.data.api.",
            "org.cttelsamicsterrassa.data.load.");

    private static final Path MODULE_ROOT = Path.of("").toAbsolutePath();

    @Test
    void mainSourcesDoNotImportForbiddenPackages() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MODULE_ROOT.resolve("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("import ")) {
                        continue;
                    }
                    String imported = trimmed.substring("import ".length()).replace("static ", "").trim();
                    if (FORBIDDEN_IMPORT_PREFIXES.stream().anyMatch(imported::startsWith)) {
                        violations.add(MODULE_ROOT.relativize(file) + ": " + trimmed);
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void pomDeclaresOnlyTestScopedDependencies() throws Exception {
        Element project = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(MODULE_ROOT.resolve("pom.xml").toFile()).getDocumentElement();
        List<String> nonTest = new ArrayList<>();
        NodeList dependencies = project.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            Node container = dependency.getParentNode();
            if (!"dependencies".equals(container.getNodeName())
                    || container.getParentNode() != project) {
                continue;
            }
            if (!"test".equals(childText(dependency, "scope"))) {
                nonTest.add(childText(dependency, "groupId") + ":" + childText(dependency, "artifactId"));
            }
        }
        assertThat(nonTest).isEmpty();
    }

    private static String childText(Element parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(element.getTagName())) {
                return element.getTextContent().trim();
            }
        }
        return "";
    }
}
