package com.swiftlabs.invoicing.issuer;

import com.swiftlabs.invoicing.common.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

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
            new BusinessException("Profil émetteur introuvable. Veuillez le configurer dans les paramètres."));
    }

    @Transactional
    public Issuer save(IssuerForm form) {
        // Validate before touching the managed row, so a rejected form never gets flushed
        requireText(form.getFullName(), "Le nom complet est obligatoire.");
        requireText(form.getAeCardNumber(), "Le numéro de carte auto-entrepreneur est obligatoire.");
        requireText(form.getNif(), "Le NIF est obligatoire.");

        // The logo is not on this form: it is kept
        Issuer issuer = getIssuer();
        issuer.setFullName(form.getFullName());
        issuer.setTradeName(form.getTradeName());
        issuer.setAddress(form.getAddress());
        issuer.setCity(form.getCity());
        issuer.setPhone(form.getPhone());
        issuer.setEmail(form.getEmail());
        issuer.setAeCardNumber(form.getAeCardNumber());
        issuer.setNif(form.getNif());
        issuer.setNis(form.getNis());
        issuer.setActivityDescription(form.getActivityDescription());
        issuer.setBankOrCcpAccount(form.getBankOrCcpAccount());
        return issuerRepository.save(issuer);
    }

    /**
     * Replaces the logo. Accepts PNG and JPEG only, recognised from the file content (never from
     * the name or the type sent by the browser), and checks that it really decodes as an image.
     */
    @Transactional
    public void updateLogo(byte[] data) {
        if (data == null || data.length == 0) {
            throw new BusinessException("Veuillez choisir une image.");
        }
        if (data.length > MAX_LOGO_BYTES) {
            throw new BusinessException("Le logo ne doit pas dépasser " + (MAX_LOGO_BYTES / 1024) + " Ko.");
        }
        String contentType = detectImageType(data);
        if (contentType == null) {
            throw new BusinessException("Format non accepté : utilisez une image PNG ou JPEG.");
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
                throw new BusinessException("Ce fichier n'est pas une image valide.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                if (reader.getWidth(0) > MAX_LOGO_PIXELS || reader.getHeight(0) > MAX_LOGO_PIXELS) {
                    throw new BusinessException("Le logo ne doit pas dépasser " + MAX_LOGO_PIXELS + " × " + MAX_LOGO_PIXELS + " pixels.");
                }
                reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw new BusinessException("Ce fichier n'est pas une image valide.");
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

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(message);
        }
    }
}
