package com.swiftlabs.invoicing.licence;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes the licence status to every view as {@code licence} (banner, menu). */
@ControllerAdvice
public class LicenceAdvice {

    private final LicenceService licenceService;

    public LicenceAdvice(LicenceService licenceService) {
        this.licenceService = licenceService;
    }

    @ModelAttribute("licence")
    public LicenceStatus licence() {
        try {
            return licenceService.status();
        } catch (RuntimeException ex) {
            // Never break a page (error pages included) over the banner
            return new LicenceStatus(LicenceStatus.State.NOT_REQUIRED, null, 0, null);
        }
    }
}
