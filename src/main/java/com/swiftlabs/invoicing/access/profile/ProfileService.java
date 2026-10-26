package com.swiftlabs.invoicing.access.profile;

import com.swiftlabs.invoicing.access.privilege.Privilege;
import com.swiftlabs.invoicing.access.privilege.PrivilegeRepository;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Profile save(ProfileForm form) {
        Long id = form.getId();
        String name = form.getName();
        List<Long> privilegeIds = form.getPrivilegeIds();
        if (name == null || name.trim().isEmpty()) {
            throw BusinessException.onField("name", "profile.error.nameRequired");
        }
        String trimmedName = name.trim();
        Profile profile = id == null ? new Profile() : profileRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("profile.error.notFound"));

        Profile sameName = profileRepository.findByName(trimmedName);
        if (sameName != null && !sameName.getId().equals(profile.getId())) {
            throw BusinessException.onField("name", "profile.error.nameUsed");
        }

        Set<Privilege> privileges = new HashSet<>(privilegeRepository.findAllById(privilegeIds));
        if (privileges.size() != new HashSet<>(privilegeIds).size()) {
            throw BusinessException.onField("privilegeIds", "privilege.error.notFound");
        }
        if (profile.getId() != null && removesLastManageProfiles(profile, privileges)) {
            throw BusinessException.onField("privilegeIds", "profile.error.lastManageProfiles", MANAGE_PROFILES);
        }

        profile.setName(trimmedName);
        profile.setDescription(form.getDescription());
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
