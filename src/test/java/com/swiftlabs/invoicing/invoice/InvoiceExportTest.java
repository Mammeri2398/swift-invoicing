package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.client.Client;
import com.swiftlabs.invoicing.client.ClientRepository;
import com.swiftlabs.invoicing.client.ClientType;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = "VIEW_INVOICES")
class InvoiceExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ClientRepository clientRepository;

    private Invoice issued;

    @BeforeEach
    void data() {
        issued = invoiceService.issue(draft("Atlas; \"Services\"", "1500.50", LocalDate.of(2026, 3, 10)).getId());
        invoiceService.pay(issued.getId());
        // Someone typed a formula as the client name
        draft("=HYPERLINK(\"http://evil.example\")", "200.00", LocalDate.of(2026, 4, 2));
        draft("Client 2025", "99.00", LocalDate.of(2025, 12, 31));
    }

    private Invoice draft(String clientName, String price, LocalDate date) {
        Client client = new Client();
        client.setType(ClientType.PARTICULIER);
        client.setName(clientName);
        client = clientRepository.save(client);
        InvoiceLineForm line = new InvoiceLineForm();
        line.setDescription("Prestation");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(price));
        InvoiceForm form = new InvoiceForm();
        form.setClientId(client.getId());
        form.setIssueDate(date);
        form.setPaymentMethod(PaymentMethod.CCP);
        form.setLines(List.of(line));
        return invoiceService.saveDraft(form);
    }

    @Test
    void csvFollowsFrenchSpreadsheetConventions() throws Exception {
        MvcResult result = mockMvc.perform(get("/invoices/export/csv").param("year", "2026"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("text/csv")))
            .andExpect(header().string("Content-Disposition", containsString("factures-2026-export-")))
            .andReturn();
        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        String[] rows = csv.split("\r\n");

        assertThat(csv).startsWith("﻿N° facture;Statut;Date d'émission;");
        assertThat(rows).hasSize(3);
        assertThat(rows[1]).startsWith(issued.getInvoiceNumber() + ";Payée;10/03/2026;;\"Atlas; \"\"Services\"\"\";;Virement CCP;1500,50;0,00;1500,50;");
        // The formula is turned into text, and the 2025 invoice is filtered out
        assertThat(rows[2]).startsWith("Brouillon;Brouillon;02/04/2026;;\"'=HYPERLINK(\"\"http://evil.example\"\")\";");
        assertThat(csv).doesNotContain("Client 2025");
    }

    @Test
    void excelHasRealDatesAndNumbers() throws Exception {
        MvcResult result = mockMvc.perform(get("/invoices/export/xlsx").param("status", "PAID"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("spreadsheetml")))
            .andReturn();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheet("Factures");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(0).getCell(7).getStringCellValue()).isEqualTo("Total HT (DA)");
            assertThat(sheet.getRow(0).getCell(8).getStringCellValue()).isEqualTo("TVA (DA)");
            assertThat(sheet.getRow(0).getCell(9).getStringCellValue()).isEqualTo("Total TTC (DA)");
            Row row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo(issued.getInvoiceNumber());
            assertThat(DateUtil.isCellDateFormatted(row.getCell(2))).isTrue();
            assertThat(row.getCell(2).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(row.getCell(7).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(7).getNumericCellValue()).isEqualTo(1500.50);
            // Auto-entrepreneur: no TVA, TTC = HT
            assertThat(row.getCell(8).getNumericCellValue()).isZero();
            assertThat(row.getCell(9).getNumericCellValue()).isEqualTo(1500.50);
            // Paid on: a real date
            assertThat(row.getCell(11).getCellType()).isEqualTo(CellType.NUMERIC);
        }
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    void exportsNeedTheRightToViewInvoices() throws Exception {
        mockMvc.perform(get("/invoices/export/csv")).andExpect(status().isForbidden());
        mockMvc.perform(get("/invoices/export/xlsx")).andExpect(status().isForbidden());
    }
}
