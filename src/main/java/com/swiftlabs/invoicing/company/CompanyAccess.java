package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** A user's access to a company, and the profile (the privileges) they have in it. */
@Entity
@Table(name = "user_company", schema = CompanySchemas.SHARED)
public class CompanyAccess {

    @EmbeddedId
    private Key id = new Key();

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("companyId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    protected CompanyAccess() {
    }

    public CompanyAccess(User user, Company company, Profile profile) {
        this.user = user;
        this.company = company;
        this.profile = profile;
    }

    public User getUser() { return user; }
    public Company getCompany() { return company; }
    public Profile getProfile() { return profile; }
    public void setProfile(Profile profile) { this.profile = profile; }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "company_id")
        private Long companyId;

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(userId, key.userId) && Objects.equals(companyId, key.companyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, companyId);
        }
    }
}
