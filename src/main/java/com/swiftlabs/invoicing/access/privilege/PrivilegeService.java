package com.swiftlabs.invoicing.access.privilege;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PrivilegeService {

    private final PrivilegeRepository privilegeRepository;

    public PrivilegeService(PrivilegeRepository privilegeRepository) {
        this.privilegeRepository = privilegeRepository;
    }

    public List<Privilege> findAll() {
        return privilegeRepository.findAll();
    }

    public Optional<Privilege> findById(Long id) {
        return privilegeRepository.findById(id);
    }

    /**
     * Creates or updates a privilege. The code is fixed once created: @PreAuthorize rules
     * reference it, so renaming it would silently remove that access.
     */
    @Transactional
    public Privilege save(PrivilegeForm form) {
        if (form.getName() == null || form.getName().trim().isEmpty()) {
            throw new BusinessException("name", "Le nom du privilège est obligatoire.");
        }
        Privilege privilege;
        if (form.getId() == null) {
            String code = form.getCode() != null ? form.getCode().trim().toUpperCase() : "";
            if (code.isEmpty()) {
                throw new BusinessException("code", "Le code du privilège est obligatoire.");
            }
            if (privilegeRepository.existsByCode(code)) {
                throw new BusinessException("code", "Ce code de privilège existe déjà.");
            }
            privilege = new Privilege();
            privilege.setCode(code);
        } else {
            privilege = privilegeRepository.findById(form.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Privilège introuvable."));
        }
        privilege.setName(form.getName().trim());
        privilege.setDescription(form.getDescription());
        return privilegeRepository.save(privilege);
    }

    @Transactional
    public void delete(Long id) {
        privilegeRepository.deleteById(id);
    }
}
