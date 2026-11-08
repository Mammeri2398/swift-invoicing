package com.swiftlabs.invoicing.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CompanyAccessRepository extends JpaRepository<CompanyAccess, CompanyAccess.Key> {

    /** The active companies a user may work in, by name, each with the profile and its privileges. */
    @Query("SELECT a FROM CompanyAccess a JOIN FETCH a.company c JOIN FETCH a.profile p LEFT JOIN FETCH p.privileges"
        + " WHERE a.user.id = ?1 AND c.active = true ORDER BY c.name, c.id")
    List<CompanyAccess> findActiveByUserId(Long userId);

    @Query("SELECT a FROM CompanyAccess a JOIN FETCH a.company c JOIN FETCH a.profile p LEFT JOIN FETCH p.privileges"
        + " WHERE a.user.id = ?1 AND c.id = ?2")
    Optional<CompanyAccess> findByUserIdAndCompanyId(Long userId, Long companyId);

    /** The accesses to a company, with their users and profiles (the user list of that company). */
    @Query("SELECT a FROM CompanyAccess a JOIN FETCH a.user JOIN FETCH a.profile WHERE a.company.id = ?1")
    List<CompanyAccess> findByCompanyId(Long companyId);

    @Query("SELECT a.company.id FROM CompanyAccess a WHERE a.user.id = ?1")
    List<Long> findCompanyIdsByUserId(Long userId);

    /** {company id, number of users} for every company with at least one user. */
    @Query("SELECT a.company.id, COUNT(a) FROM CompanyAccess a GROUP BY a.company.id")
    List<Object[]> countByCompany();

    /** Another enabled user still holds this privilege somewhere, through a profile other than this one. */
    @Query("SELECT COUNT(a) > 0 FROM CompanyAccess a JOIN a.user u JOIN a.profile p JOIN p.privileges pr"
        + " WHERE u.enabled = true AND pr.code = ?1 AND p.id <> ?2")
    boolean existsEnabledUserWithPrivilegeOutsideProfile(String privilegeCode, Long profileId);

    /** Another enabled user than this one holds the privilege in this company. */
    @Query("SELECT COUNT(a) > 0 FROM CompanyAccess a JOIN a.user u JOIN a.profile p JOIN p.privileges pr"
        + " WHERE u.enabled = true AND pr.code = ?1 AND a.company.id = ?2 AND u.id <> ?3")
    boolean existsOtherEnabledHolderIn(String privilegeCode, Long companyId, Long userId);

    /** Another enabled user than this one holds the privilege, in any company. */
    @Query("SELECT COUNT(a) > 0 FROM CompanyAccess a JOIN a.user u JOIN a.profile p JOIN p.privileges pr"
        + " WHERE u.enabled = true AND pr.code = ?1 AND u.id <> ?2")
    boolean existsOtherEnabledHolder(String privilegeCode, Long userId);
}
