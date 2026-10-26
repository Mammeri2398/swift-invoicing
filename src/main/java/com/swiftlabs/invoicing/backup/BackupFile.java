package com.swiftlabs.invoicing.backup;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A backup in the backup folder: swift-invoicing-2026-10-26-143005-auto.dump. Only files named this
 * way are listed, downloaded or restored, so a name sent by the browser can never reach another file.
 */
public record BackupFile(String name, LocalDateTime createdAt, BackupKind kind, long size) {

    static final String EXTENSION = ".dump";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final Pattern NAME = Pattern.compile(
        "swift-invoicing-(\\d{4}-\\d{2}-\\d{2}-\\d{6})-(auto|manuelle|avant-restauration|importee)\\.dump");

    static String nameFor(LocalDateTime createdAt, BackupKind kind) {
        return "swift-invoicing-" + createdAt.format(STAMP) + "-" + kind.suffix() + EXTENSION;
    }

    static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    static Optional<BackupFile> of(Path file, long size) {
        Matcher matcher = NAME.matcher(file.getFileName().toString());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new BackupFile(matcher.group(0), LocalDateTime.parse(matcher.group(1), STAMP),
            BackupKind.fromSuffix(matcher.group(2)), size));
    }

    /** Size for people, in French (logs): 512 Ko, 3,4 Mo. Pages use backup.size.kb / backup.size.mb. */
    public String displaySize() {
        if (!isMegabytes()) {
            return sizeInKilobytes() + " Ko";
        }
        return String.format(java.util.Locale.FRANCE, "%.1f Mo", sizeInMegabytes());
    }

    /** At least 1 MB: shown in MB, otherwise in KB. */
    public boolean isMegabytes() {
        return size >= 1024 * 1024;
    }

    /** Whole kilobytes (at least 1), as text so that it is never formatted with a thousands separator. */
    public String sizeInKilobytes() {
        return String.valueOf(Math.max(1, Math.round(size / 1024.0)));
    }

    public double sizeInMegabytes() {
        return size / (1024.0 * 1024.0);
    }
}
