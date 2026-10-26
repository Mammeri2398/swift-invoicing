package com.swiftlabs.invoicing.backup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Backup folder and retention chosen in Réglages > Sauvegardes (single row, id = 1). */
@Entity
@Table(name = "backup_settings")
public class BackupSettings {

    static final long ID = 1L;

    @Id
    private Long id = ID;

    @Column(name = "directory", nullable = false, length = 1000)
    private String directory;

    @Column(name = "keep_count", nullable = false)
    private int keepCount;

    protected BackupSettings() {
    }

    BackupSettings(String directory, int keepCount) {
        this.directory = directory;
        this.keepCount = keepCount;
    }

    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }
    public int getKeepCount() { return keepCount; }
    public void setKeepCount(int keepCount) { this.keepCount = keepCount; }
}
