package com.swiftlabs.invoicing.access.privilege;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Privileges;
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
            throw BusinessException.onField("name", MessageProperties.PRIVILEGE_ERROR_NAME_REQUIRED);
        }
        Privilege privilege;
        if (form.getId() == null) {
            String code = form.getCode() != null ? form.getCode().trim().toUpperCase() : "";
            if (code.isEmpty()) {
                throw BusinessException.onField("code", MessageProperties.PRIVILEGE_ERROR_CODE_REQUIRED);
            }
            if (privilegeRepository.existsByCode(code)) {
                throw BusinessException.onField("code", MessageProperties.PRIVILEGE_ERROR_CODE_USED);
            }
            privilege = new Privilege();
            privilege.setCode(code);
        } else {
            privilege = privilegeRepository.findById(form.getId())
                .orElseThrow(() -> new ResourceNotFoundException(MessageProperties.PRIVILEGE_ERROR_NOT_FOUND));
        }
        privilege.setName(form.getName().trim());
        privilege.setDescription(form.getDescription());
        return privilegeRepository.save(privilege);
    }

    @Transactional
    public void delete(Long id) {
        // Deleting a privilege the application checks would take it from every profile at once (MANAGE_USERS,
        // MANAGE_PROFILES...): nobody could give it back without fixing the database
        privilegeRepository.findById(id)
            .filter(privilege -> Privileges.builtIn().contains(privilege.getCode()))
            .ifPresent(privilege -> {
                throw new BusinessException(MessageProperties.PRIVILEGE_ERROR_BUILT_IN, privilege.getCode());
            });
        privilegeRepository.deleteById(id);
    }
}
