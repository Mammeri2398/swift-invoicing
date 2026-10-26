package com.swiftlabs.invoicing.backup;

/** Why a backup was made, written in its file name. */
public enum BackupKind {

    AUTO("auto", "Automatique"),
    MANUAL("manuelle", "Manuelle"),
    BEFORE_RESTORE("avant-restauration", "Avant restauration"),
    IMPORTED("importee", "Importée");

    private final String suffix;
    private final String label;

    BackupKind(String suffix, String label) {
        this.suffix = suffix;
        this.label = label;
    }

    public String suffix() { return suffix; }
    public String label() { return label; }

    static BackupKind fromSuffix(String suffix) {
        for (BackupKind kind : values()) {
            if (kind.suffix.equals(suffix)) {
                return kind;
            }
        }
        throw new IllegalArgumentException(suffix);
    }
}
