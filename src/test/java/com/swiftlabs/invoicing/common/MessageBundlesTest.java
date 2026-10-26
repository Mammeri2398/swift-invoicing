package com.swiftlabs.invoicing.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The texts in French, English and Arabic stay complete and consistent: same keys in the three files of
 * each bundle, valid MessageFormat patterns with the same arguments, and every key a template names exists.
 */
class MessageBundlesTest {

    private static final List<String> LANGUAGES = List.of("", "_en", "_ar");
    /** #{key} or #{key(args)} with a literal key; #{${...}} (computed keys) are skipped. */
    private static final Pattern TEMPLATE_KEY = Pattern.compile("#\\{([A-Za-z][\\w.-]*)[}(]");
    /** A lone apostrophe: MessageFormat would swallow it (write '' instead). */
    private static final Pattern LONE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");
    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)");

    private static Properties load(String basename, String language) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessageBundlesTest.class.getResourceAsStream("/" + basename + language + ".properties")) {
            assertThat(in).as(basename + language + ".properties").isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    void everyBundleHasTheSameKeysInTheThreeLanguages() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String basename : Messages.BASENAMES) {
            Set<String> french = new TreeSet<>(load(basename, "").stringPropertyNames());
            for (String language : List.of("_en", "_ar")) {
                Set<String> translated = new TreeSet<>(load(basename, language).stringPropertyNames());
                Set<String> missing = new TreeSet<>(french);
                missing.removeAll(translated);
                Set<String> extra = new TreeSet<>(translated);
                extra.removeAll(french);
                if (!missing.isEmpty()) problems.add(basename + language + " misses " + missing);
                if (!extra.isEmpty()) problems.add(basename + language + " has unknown keys " + extra);
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void everyTextIsAValidPatternWithTheSameArguments() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String basename : Messages.BASENAMES) {
            Properties french = load(basename, "");
            for (String language : LANGUAGES) {
                Properties texts = load(basename, language);
                for (String key : texts.stringPropertyNames()) {
                    String text = texts.getProperty(key);
                    String where = basename + language + ": " + key;
                    if (LONE_APOSTROPHE.matcher(text).find()) {
                        problems.add(where + " has a single apostrophe (write '')");
                    }
                    try {
                        new MessageFormat(text);
                    } catch (IllegalArgumentException ex) {
                        problems.add(where + " is not a valid pattern: " + ex.getMessage());
                    }
                    if (french.containsKey(key) && !arguments(text).equals(arguments(french.getProperty(key)))) {
                        problems.add(where + " uses arguments " + arguments(text) + " instead of " + arguments(french.getProperty(key)));
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void everyKeyUsedByATemplateExists() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (String basename : Messages.BASENAMES) {
            keys.addAll(load(basename, "").stringPropertyNames());
        }
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".html")).toList()) {
                Matcher matcher = TEMPLATE_KEY.matcher(Files.readString(file));
                while (matcher.find()) {
                    if (!keys.contains(matcher.group(1))) {
                        problems.add(file + ": " + matcher.group(1));
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    private static Set<String> arguments(String text) {
        Set<String> arguments = new TreeSet<>();
        Matcher matcher = ARGUMENT.matcher(text);
        while (matcher.find()) {
            arguments.add(matcher.group(1));
        }
        return arguments;
    }
}
