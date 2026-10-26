package com.swiftlabs.invoicing.backup;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupSettingsRepository extends JpaRepository<BackupSettings, Long> {
}
