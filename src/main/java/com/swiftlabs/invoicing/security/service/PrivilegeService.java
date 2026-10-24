package com.swiftlabs.invoicing.security.service;

import com.swiftlabs.invoicing.security.domain.Privilege;
import com.swiftlabs.invoicing.security.repository.PrivilegeRepository;
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

    @Transactional
    public Privilege save(Privilege privilege) {
        if (privilegeRepository.existsByCode(privilege.getCode())
            && (privilege.getId() == null || !privilegeRepository.findById(privilege.getId())
            .map(p -> p.getCode().equals(privilege.getCode())).orElse(false))) {
            throw new IllegalArgumentException("Ce code de privilège existe déjà.");
        }
        return privilegeRepository.save(privilege);
    }

    @Transactional
    public void delete(Long id) {
        privilegeRepository.deleteById(id);
    }
}
