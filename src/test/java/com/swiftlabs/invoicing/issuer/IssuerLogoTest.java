package com.swiftlabs.invoicing.issuer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IssuerLogoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IssuerService issuerService;

    private static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static MockMultipartFile file(String name, String contentType, byte[] data) {
        return new MockMultipartFile("logo", name, contentType, data);
    }

    @Test
    @WithMockUser(authorities = {"VIEW_ISSUER", "EDIT_ISSUER"})
    void pngAndJpegAreAcceptedAndServed() throws Exception {
        byte[] png = image("png", 200, 80);
        mockMvc.perform(multipart("/settings/issuer/logo").file(file("logo.png", "image/png", png)).with(csrf()))
            .andExpect(redirectedUrl("/settings/issuer"))
            .andExpect(flash().attributeExists("success"));
        mockMvc.perform(get("/settings/issuer/logo"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(png));

        // Type detected from the content, not from what the browser claims
        byte[] jpeg = image("jpg", 100, 100);
        mockMvc.perform(multipart("/settings/issuer/logo").file(file("photo.png", "image/png", jpeg)).with(csrf()));
        assertThat(issuerService.getIssuer().getLogoContentType()).isEqualTo("image/jpeg");
    }

    @Test
    @WithMockUser(authorities = {"VIEW_ISSUER", "EDIT_ISSUER"})
    void otherFilesAreRejected() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/settings/issuer/logo").file(file("logo.svg", "image/svg+xml", svg)).with(csrf()))
            .andExpect(flash().attribute("error", "Format non accepté : utilisez une image PNG ou JPEG."));

        // PNG signature followed by garbage
        byte[] fake = new byte[200];
        System.arraycopy(image("png", 1, 1), 0, fake, 0, 8);
        mockMvc.perform(multipart("/settings/issuer/logo").file(file("fake.png", "image/png", fake)).with(csrf()))
            .andExpect(flash().attribute("error", "Ce fichier n'est pas une image valide."));

        // Small file, too many pixels: rejected from the header, before decoding
        mockMvc.perform(multipart("/settings/issuer/logo").file(file("wide.png", "image/png", image("png", 3000, 1))).with(csrf()))
            .andExpect(flash().attribute("error", "Le logo ne doit pas dépasser 2000 × 2000 pixels."));

        assertThat(issuerService.getIssuer().hasLogo()).isFalse();
    }

    @Test
    @WithMockUser(authorities = {"VIEW_ISSUER", "EDIT_ISSUER"})
    void oversizedFileIsRejected() throws Exception {
        byte[] big = new byte[IssuerService.MAX_LOGO_BYTES + 1];
        System.arraycopy(image("png", 1, 1), 0, big, 0, 8);
        mockMvc.perform(multipart("/settings/issuer/logo").file(file("big.png", "image/png", big)).with(csrf()))
            .andExpect(flash().attribute("error", "Le logo ne doit pas dépasser 500 Ko."));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_ISSUER", "EDIT_ISSUER"})
    void logoCanBeRemoved() throws Exception {
        issuerService.updateLogo(image("png", 50, 50));
        mockMvc.perform(post("/settings/issuer/logo/delete").with(csrf())).andExpect(redirectedUrl("/settings/issuer"));
        assertThat(issuerService.getIssuer().hasLogo()).isFalse();
        mockMvc.perform(get("/settings/issuer/logo")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "VIEW_ISSUER")
    void uploadRequiresEditPrivilege() throws Exception {
        mockMvc.perform(multipart("/settings/issuer/logo").file(file("logo.png", "image/png", image("png", 10, 10))).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/issuer/logo/delete").with(csrf())).andExpect(status().isForbidden());
    }
}
