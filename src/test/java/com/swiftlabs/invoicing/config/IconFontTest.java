package com.swiftlabs.invoicing.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The icon font holds only the icons of src/main/frontend/icons/icons.txt (see subset-icons.py): an icon used in a
 * template or a script but missing from the list would show as its name in plain text.
 */
class IconFontTest {

    private static final Path ICONS = Path.of("src/main/frontend/icons/icons.txt");
    /** {@code <span class="icon ...">name</span>}, and the names chosen by an expression: {@code th:text="... 'name'"}. */
    private static final Pattern STATIC_ICON = Pattern.compile("class=\"icon[^\"]*\"[^>]*>([a-z0-9_]+)<");
    private static final Pattern DYNAMIC_ICON = Pattern.compile("class=\"icon[^\"]*\"[^>]*th:text=\"([^\"]+)\"");
    private static final Pattern QUOTED = Pattern.compile("'([a-z0-9_]+)'");
    /** In scripts: {@code .icon').textContent = visible ? 'visibility' : 'visibility_off'}. */
    private static final Pattern SCRIPT_ICON = Pattern.compile("\\.icon'\\)\\.textContent = ([^;]+);");

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Set<String> used() throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".html") || f.toString().endsWith(".js")).toList()) {
                String text = read(file);
                Matcher icon = STATIC_ICON.matcher(text);
                while (icon.find()) {
                    names.add(icon.group(1));
                }
                for (Pattern expression : new Pattern[] {DYNAMIC_ICON, SCRIPT_ICON}) {
                    Matcher dynamic = expression.matcher(text);
                    while (dynamic.find()) {
                        Matcher quoted = QUOTED.matcher(dynamic.group(1));
                        while (quoted.find()) {
                            names.add(quoted.group(1));
                        }
                    }
                }
            }
        }
        return names;
    }

    @Test
    void everyIconUsedIsInTheFont() throws IOException {
        Set<String> inFont = Files.readAllLines(ICONS).stream().map(String::strip).filter(line -> !line.isEmpty())
            .collect(Collectors.toSet());
        Set<String> used = used();
        assertThat(used).as("icons found in the templates and scripts").hasSizeGreaterThan(50);
        assertThat(inFont).as("add the missing names to %s, then run subset-icons.py", ICONS).containsAll(used);
    }
}
