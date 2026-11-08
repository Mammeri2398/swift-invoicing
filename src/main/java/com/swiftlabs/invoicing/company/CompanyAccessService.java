package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Privileges;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Who may work in which company, and with which profile there. */
@Service
public class CompanyAccessService {

    private final CompanyAccessRepository accessRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public CompanyAccessService(CompanyAccessRepository accessRepository, CompanyRepository companyRepository,
                                UserRepository userRepository) {
        this.accessRepository = accessRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    /** The active companies the user may work in, by name. */
    @Transactional(readOnly = true)
    public List<CompanyAccess> accessesOf(Long userId) {
        return accessRepository.findActiveByUserId(userId);
    }

    /** The user's access to an active company, with its profile and privileges; refused when they have none. */
    @Transactional(readOnly = true)
    public CompanyAccess require(Long userId, Long companyId) {
        return accessRepository.findByUserIdAndCompanyId(userId, companyId)
            .filter(access -> access.getCompany().isActive())
            .orElseThrow(() -> new BusinessException(MessageProperties.COMPANY_ERROR_NO_ACCESS));
    }

    /** The user's access to an active company, with its profile and privileges (checked on every page). */
    @Transactional(readOnly = true)
    public Optional<CompanyAccess> findActive(Long userId, Long companyId) {
        return accessRepository.findByUserIdAndCompanyId(userId, companyId).filter(access -> access.getCompany().isActive());
    }

    /** The user's profile in the company, if they may work there. */
    @Transactional(readOnly = true)
    public Optional<Profile> profileIn(Long userId, Long companyId) {
        return accessRepository.findByUserIdAndCompanyId(userId, companyId).map(CompanyAccess::getProfile);
    }

    /** Another enabled user than this one holds the privilege in the company (null: in any company). */
    @Transactional(readOnly = true)
    public boolean hasOtherHolder(String privilegeCode, Long companyId, Long userId) {
        return companyId != null ? accessRepository.existsOtherEnabledHolderIn(privilegeCode, companyId, userId)
            : accessRepository.existsOtherEnabledHolder(privilegeCode, userId);
    }

    /** The profile name of each user who may work in the company, by user id. */
    @Transactional(readOnly = true)
    public Map<Long, String> profileNamesIn(Long companyId) {
        Map<Long, String> names = new HashMap<>();
        accessRepository.findByCompanyId(companyId).forEach(access -> names.put(access.getUser().getId(), access.getProfile().getName()));
        return names;
    }

    /** The company opened at the user's next login. */
    @Transactional
    public void rememberLast(Long userId, Long companyId) {
        userRepository.findById(userId).ifPresent(user -> user.setLastCompanyId(companyId));
    }

    /** The companies where the user manages users (MANAGE_USERS in their profile there). */
    @Transactional(readOnly = true)
    public Set<Long> companiesManagedBy(Long userId) {
        Set<Long> companies = new HashSet<>();
        for (CompanyAccess access : accessRepository.findActiveByUserId(userId)) {
            if (access.getProfile().getPrivileges().stream().anyMatch(p -> Privileges.MANAGE_USERS.equals(p.getCode()))) {
                companies.add(access.getCompany().getId());
            }
        }
        return companies;
    }

    /** Every company the user may work in, active or not. */
    @Transactional(readOnly = true)
    public Set<Long> companyIdsOf(Long userId) {
        return new HashSet<>(accessRepository.findCompanyIdsByUserId(userId));
    }

    /** Takes away the user's access to the company (nothing when they had none). */
    @Transactional
    public void revoke(Long userId, Long companyId) {
        accessRepository.findByUserIdAndCompanyId(userId, companyId).ifPresent(accessRepository::delete);
    }

    /** Gives the user access to the company with this profile, or changes their profile there. */
    @Transactional
    public CompanyAccess grant(User user, Long companyId, Profile profile) {
        Optional<CompanyAccess> existing = accessRepository.findByUserIdAndCompanyId(user.getId(), companyId);
        if (existing.isPresent()) {
            existing.get().setProfile(profile);
            return existing.get();
        }
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new BusinessException(MessageProperties.COMPANY_ERROR_NO_ACCESS));
        return accessRepository.save(new CompanyAccess(user, company, profile));
    }
}
