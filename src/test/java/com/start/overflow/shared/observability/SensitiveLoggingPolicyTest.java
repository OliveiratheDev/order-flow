package com.start.overflow.shared.observability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveLoggingPolicyTest {
    private static final Pattern LOG_FIELD = Pattern.compile(
            "\\.addKeyValue\\(\\s*\"([^\"]+)\"");
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "authorization",
            "cardnumber",
            "cpfcnpj",
            "cvv",
            "document",
            "email",
            "eventenvelope",
            "password",
            "passwordhash",
            "payload",
            "taxid",
            "token"
    );

    @Test
    void structuredLogFieldsDoNotExposeSensitiveDataOrPayloads() throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(this::logFieldDeclarations)
                    .filter(this::isForbidden)
                    .toList();
        }

        assertThat(violations).isEmpty();
    }

    private Stream<String> logFieldDeclarations(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .map(LOG_FIELD::matcher)
                    .filter(Matcher::find)
                    .map(matcher -> matcher.group(1));
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao auditar logs em " + file, exception);
        }
    }

    private boolean isForbidden(String field) {
        return FORBIDDEN_FIELDS.contains(field.toLowerCase(Locale.ROOT));
    }
}
