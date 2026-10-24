package com.swiftlabs.invoicing.security.service;

import com.swiftlabs.invoicing.security.domain.Privilege;
import com.swiftlabs.invoicing.security.domain.Profile;
import com.swiftlabs.invoicing.security.repository.PrivilegeRepository;
import com.swiftlabs.invoicing.security.repository.ProfileRepository;
import com.swiftlabs.invoicing.security.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ProfileService {

    /** Privilege that can restore every other one: someone active must always keep it. */
    static final String MANAGE_PROFILES = "MANAGE_PROFILES";

    private final ProfileRepository profileRepository;
    private final PrivilegeRepository privilegeRepository;
    private final UserRepository userRepository;

    public ProfileService(ProfileRepository profileRepository, PrivilegeRepository privilegeRepository,
                          UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.privilegeRepository = privilegeRepository;
        this.userRepository = userRepository;
    }

    public List<Profile> findAll() {
        return profileRepository.findAll();
    }

    public Optional<Profile> findById(Long id) {
        return profileRepository.findById(id);
    }

    public List<Privilege> findAllPrivileges() {
        return privilegeRepository.findAll();
    }

    /**
     * Creates or updates a profile. Updates go through the stored entity so that the privileges
     * are exactly the ones checked on the form (never silently dropped by a detached merge).
     */
    @Transactional
    public Profile save(Long id, String name, String description, Collection<Long> privilegeIds) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom du profil est obligatoire.");
        }
        String trimmedName = name.trim();
        Profile profile = id == null ? new Profile() : profileRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Profil introuvable."));

        Profile sameName = profileRepository.findByName(trimmedName);
        if (sameName != null && !sameName.getId().equals(profile.getId())) {
            throw new IllegalArgumentException("Ce nom de profil existe déjà.");
        }

        Set<Privilege> privileges = new HashSet<>(
            privilegeIds == null ? List.of() : privilegeRepository.findAllById(privilegeIds));
        if (privilegeIds != null && privileges.size() != new HashSet<>(privilegeIds).size()) {
            throw new IllegalArgumentException("Privilège introuvable.");
        }
        if (profile.getId() != null && removesLastManageProfiles(profile, privileges)) {
            throw new IllegalArgumentException("Au moins un utilisateur actif doit conserver le privilège "
                + MANAGE_PROFILES + " : impossible de le retirer de ce profil.");
        }

        profile.setName(trimmedName);
        profile.setDescription(description);
        profile.getPrivileges().clear();
        profile.getPrivileges().addAll(privileges);
        return profileRepository.save(profile);
    }

    private boolean removesLastManageProfiles(Profile profile, Set<Privilege> newPrivileges) {
        boolean hadIt = profile.getPrivileges().stream().anyMatch(p -> MANAGE_PROFILES.equals(p.getCode()));
        boolean keepsIt = newPrivileges.stream().anyMatch(p -> MANAGE_PROFILES.equals(p.getCode()));
        return hadIt && !keepsIt
            && !userRepository.existsEnabledUserWithPrivilegeOutsideProfile(MANAGE_PROFILES, profile.getId());
    }

    @Transactional
    public void delete(Long id) {
        profileRepository.deleteById(id);
    }
}
