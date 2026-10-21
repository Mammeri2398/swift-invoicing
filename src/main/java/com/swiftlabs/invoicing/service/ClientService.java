package com.swiftlabs.invoicing.service;

import com.swiftlabs.invoicing.domain.Client;
import com.swiftlabs.invoicing.domain.ClientType;
import com.swiftlabs.invoicing.domain.Invoice;
import com.swiftlabs.invoicing.exception.BusinessException;
import com.swiftlabs.invoicing.repository.ClientRepository;
import com.swiftlabs.invoicing.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;

    public ClientService(ClientRepository clientRepository, InvoiceRepository invoiceRepository) {
        this.clientRepository = clientRepository;
        this.invoiceRepository = invoiceRepository;
    }

    public List<Client> findAll() {
        return clientRepository.findAll();
    }

    public Client findById(Long id) {
        return clientRepository.findById(id).orElseThrow(() ->
            new BusinessException("Client introuvable (id=" + id + ")."));
    }

    @Transactional
    public Client save(Client client) {
        validate(client);
        return clientRepository.save(client);
    }

    @Transactional
    public void delete(Long id) {
        Client client = findById(id);
        List<Invoice> invoices = invoiceRepository.findByClientId(client.getId());
        if (!invoices.isEmpty()) {
            throw new BusinessException(
                "Impossible de supprimer ce client : il possède " + invoices.size() + " facture(s) liée(s).");
        }
        clientRepository.delete(client);
    }

    private void validate(Client client) {
        if (client.getType() == ClientType.PROFESSIONNEL && (client.getNif() == null || client.getNif().trim().isEmpty())) {
            throw new BusinessException("Le NIF est obligatoire pour un client professionnel.");
        }
    }
}
