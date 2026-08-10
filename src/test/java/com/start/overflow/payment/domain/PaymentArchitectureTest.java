package com.start.overflow.payment.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentArchitectureTest {

    @Test
    void domainImportsOnlyJdkTypes() throws IOException {
        Path domainDirectory = Path.of("src", "main", "java", "com", "start", "overflow",
                "payment", "domain");

        try (var files = Files.walk(domainDirectory)) {
            List<String> imports = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> readLines(path).stream())
                    .map(String::strip)
                    .filter(line -> line.startsWith("import "))
                    .toList();

            assertThat(imports)
                    .allMatch(line -> line.startsWith("import java."),
                            "o domínio de pagamento deve compilar apenas com o JDK");
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
