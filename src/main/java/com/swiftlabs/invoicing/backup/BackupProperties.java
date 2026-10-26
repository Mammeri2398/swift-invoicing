package com.swiftlabs.invoicing.backup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Database backups (app.backup.*), made with the PostgreSQL tools (pg_dump, pg_restore, psql).
 * Off unless {@code enabled}: the on-premise install turns them on, the hosted deployment has its own.
 */
@ConfigurationProperties(prefix = "app.backup")
public record BackupProperties(
    @DefaultValue("false") boolean enabled,
    /* Folder of the PostgreSQL tools (the bundled PostgreSQL's bin); empty: found on the PATH. */
    @DefaultValue("") String toolsDirectory,
    /* Default folder of the backups, until one is chosen in the application. */
    @DefaultValue("./backups") String directory,
    /* Automatic backups kept; older ones are deleted. Manual backups are kept until deleted. */
    @DefaultValue("30") int keep,
    /* An automatic backup is made when the latest one is older than this (checked every hour). */
    @DefaultValue("24h") Duration every,
    /* Longest run of one tool before it is stopped. */
    @DefaultValue("30m") Duration timeout
) {
}
