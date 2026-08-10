package com.start.overflow.order.event;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderModuleArchitectureTest {

    @Test
    void orderModuleDoesNotImportCatalogOrNotificationImplementations() throws IOException {
        Path orderDirectory = Path.of(
                "src", "main", "java", "com", "start", "overflow", "order");

        try (var files = Files.walk(orderDirectory)) {
            List<String> forbiddenImports = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> readLines(path).stream())
                    .map(String::strip)
                    .filter(line -> line.startsWith("import com.start.overflow.catalog")
                            || line.startsWith("import com.start.overflow.notification"))
                    .toList();

            assertThat(forbiddenImports).isEmpty();
        }
    }

    private List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível inspecionar " + path, exception);
        }
    }
}
