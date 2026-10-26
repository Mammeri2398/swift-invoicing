package com.swiftlabs.invoicing.issuer;

import com.swiftlabs.invoicing.common.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@Service
@Transactional(readOnly = true)
public class IssuerService {

    /** The issuer profile is a single row. */
    private static final long ISSUER_ID = 1L;

    static final int MAX_LOGO_BYTES = 500 * 1024;
    private static final int MAX_LOGO_PIXELS = 2000;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final IssuerRepository issuerRepository;

    public IssuerService(IssuerRepository issuerRepository) {
        this.issuerRepository = issuerRepository;
    }

    public Issuer getIssuer() {
        return issuerRepository.findById(ISSUER_ID).orElseThrow(() ->
            new BusinessException("issuer.error.notFound"));
    }

    @Transactional
    public Issuer save(IssuerForm form) {
        // Validate before touching the managed row, so a rejected form never gets flushed
        CompanyType type = form.getCompanyType() != null ? form.getCompanyType() : CompanyType.AUTO_ENTREPRENEUR;
        requireText("fullName", form.getFullName(), type.isCompany() ? "issuer.error.companyNameRequired" : "issuer.error.fullNameRequired");
        requireText("nif", form.getNif(), "issuer.error.nifRequired");
        // Identifiers required on invoices, by legal status (décret exécutif 05-468)
        if (type.isCompany()) {
            requireText("legalForm", form.getLegalForm(), "issuer.error.legalFormRequired");
            if (form.getShareCapital() == null) {
                throw BusinessException.onField("shareCapital", "issuer.error.shareCapitalRequired");
            }
            requireText("rcNumber", form.getRcNumber(), "issuer.error.rcRequired");
            requireText("taxArticle", form.getTaxArticle(), "issuer.error.taxArticleRequired");
        } else {
            requireText("aeCardNumber", form.getAeCardNumber(), "issuer.error.aeCardRequired");
        }
        if (form.getAnnualCeiling() == null || form.getAnnualCeiling().signum() <= 0) {
            throw BusinessException.onField("annualCeiling", "issuer.error.ceilingPositive");
        }

        // The logo is not on this form: it is kept
        Issuer issuer = getIssuer();
        issuer.setCompanyType(type);
        issuer.setFullName(form.getFullName());
        issuer.setTradeName(form.getTradeName());
        issuer.setAddress(form.getAddress());
        issuer.setCity(form.getCity());
        issuer.setPhone(form.getPhone());
        issuer.setEmail(form.getEmail());
        // The other status's identifiers are kept, in case the type is switched back
        issuer.setAeCardNumber(form.getAeCardNumber());
        issuer.setLegalForm(form.getLegalForm());
        issuer.setShareCapital(form.getShareCapital());
        issuer.setRcNumber(form.getRcNumber());
        issuer.setTaxArticle(form.getTaxArticle());
        issuer.setNif(form.getNif());
        issuer.setNis(form.getNis());
        issuer.setActivityDescription(form.getActivityDescription());
        issuer.setBankOrCcpAccount(form.getBankOrCcpAccount());
        issuer.setAnnualCeiling(form.getAnnualCeiling());
        return issuerRepository.save(issuer);
    }

    /**
     * Replaces the logo. Accepts PNG and JPEG only, recognised from the file content (never from
     * the name or the type sent by the browser), and checks that it really decodes as an image.
     */
    @Transactional
    public void updateLogo(byte[] data) {
        if (data == null || data.length == 0) {
            throw new BusinessException("issuer.error.logoMissing");
        }
        if (data.length > MAX_LOGO_BYTES) {
            throw new BusinessException("issuer.error.logoTooBig", String.valueOf(MAX_LOGO_BYTES / 1024));
        }
        String contentType = detectImageType(data);
        if (contentType == null) {
            throw new BusinessException("issuer.error.logoFormat");
        }
        checkDecodableWithinLimits(data);
        getIssuer().replaceLogo(data, contentType);
    }

    /**
     * Reads the dimensions from the image header before decoding, so a small file declaring a huge
     * image (decompression bomb) is rejected without being decoded.
     */
    private static void checkDecodableWithinLimits(byte[] data) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException("issuer.error.logoInvalid");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                if (reader.getWidth(0) > MAX_LOGO_PIXELS || reader.getHeight(0) > MAX_LOGO_PIXELS) {
                    throw new BusinessException("issuer.error.logoTooLarge", String.valueOf(MAX_LOGO_PIXELS));
                }
                reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw new BusinessException("issuer.error.logoInvalid");
        }
    }

    @Transactional
    public void removeLogo() {
        getIssuer().removeLogo();
    }

    /** Content type from the file signature ("magic number"), or null if neither PNG nor JPEG. */
    private static String detectImageType(byte[] data) {
        if (startsWith(data, PNG_SIGNATURE)) {
            return "image/png";
        }
        if (startsWith(data, JPEG_SIGNATURE)) {
            return "image/jpeg";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }

    private void requireText(String field, String value, String code) {
        if (value == null || value.trim().isEmpty()) {
            throw BusinessException.onField(field, code);
        }
    }
}
