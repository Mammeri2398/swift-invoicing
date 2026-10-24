package com.swiftlabs.invoicing.client;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.invoice.InvoiceRepository;

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

    public long count() {
        return clientRepository.count();
    }

    public Client findById(Long id) {
        return clientRepository.findById(id).orElseThrow(() ->
            new BusinessException("Client introuvable (id=" + id + ")."));
    }

    @Transactional
    public Client save(ClientForm form) {
        // Update the stored client so fields not on the form (createdAt) are kept
        Client client = form.getId() != null ? findById(form.getId()) : new Client();
        if (form.getType() == null || form.getType().isEmpty()) {
            throw new BusinessException("Le type de client est obligatoire.");
        }
        client.setType(ClientType.valueOf(form.getType()));
        client.setName(form.getName());
        client.setAddress(form.getAddress());
        client.setCity(form.getCity());
        client.setPhone(form.getPhone());
        client.setEmail(form.getEmail());
        client.setNif(form.getNif());
        validate(client);
        return clientRepository.save(client);
    }

    @Transactional
    public void delete(Long id) {
        Client client = findById(id);
        long invoices = invoiceRepository.countByClientId(client.getId());
        if (invoices > 0) {
            throw new BusinessException(
                "Impossible de supprimer ce client : il possède " + invoices + " facture(s) liée(s).");
        }
        clientRepository.delete(client);
    }

    private void validate(Client client) {
        if (client.getType() == ClientType.PROFESSIONNEL && (client.getNif() == null || client.getNif().trim().isEmpty())) {
            throw new BusinessException("Le NIF est obligatoire pour un client professionnel.");
        }
    }
}
