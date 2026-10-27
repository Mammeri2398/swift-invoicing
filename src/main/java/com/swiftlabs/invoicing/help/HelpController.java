package com.swiftlabs.invoicing.help;

import com.swiftlabs.invoicing.backup.BackupService;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.setup.NetworkAddresses;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Aide: the user guide (templates/help/content/<language>.html) and "À propos", what support asks first:
 * the version, the licence, where the data is and how the other PCs reach the application.
 */
@Controller
public class HelpController {

    private final ObjectProvider<BuildProperties> buildProperties;
    private final NetworkAddresses networkAddresses;
    private final BackupService backupService;
    private final String supportContact;

    public HelpController(ObjectProvider<BuildProperties> buildProperties, NetworkAddresses networkAddresses,
                          BackupService backupService, @Value("${app.support.contact:}") String supportContact) {
        this.buildProperties = buildProperties;
        this.networkAddresses = networkAddresses;
        this.backupService = backupService;
        this.supportContact = supportContact;
    }

    @GetMapping(Endpoints.HELP)
    public String help(Model model) {
        BuildProperties build = buildProperties.getIfAvailable();
        // Run from the IDE without a Maven build: no build-info.properties
        model.addAttribute(CommonCodes.Attributes.VERSION, build != null ? build.getVersion() : "dev");
        model.addAttribute(CommonCodes.Attributes.NETWORK_ADDRESSES, networkAddresses.urls());
        model.addAttribute(CommonCodes.Attributes.BACKUP_DIRECTORY, backupService.isEnabled() ? backupService.directory().toString() : null);
        model.addAttribute(CommonCodes.Attributes.SUPPORT_CONTACT, supportContact.isBlank() ? null : supportContact);
        return CommonCodes.Views.HELP_INDEX;
    }
}
