package com.swiftlabs.invoicing.access.profile;

import com.swiftlabs.invoicing.access.privilege.Privilege;

import java.util.ArrayList;
import java.util.List;

/** Profiles form; privilegeIds are the checked boxes (none checked = no privilege). */
public class ProfileForm {

    private Long id;
    private String name;
    private String description;
    private List<Long> privilegeIds = new ArrayList<>();

    public static ProfileForm from(Profile profile) {
        ProfileForm form = new ProfileForm();
        form.setId(profile.getId());
        form.setName(profile.getName());
        form.setDescription(profile.getDescription());
        form.setPrivilegeIds(profile.getPrivileges().stream().map(Privilege::getId).toList());
        return form;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Long> getPrivilegeIds() {
        return privilegeIds;
    }

    public void setPrivilegeIds(List<Long> privilegeIds) {
        this.privilegeIds = privilegeIds != null ? new ArrayList<>(privilegeIds) : new ArrayList<>();
    }
}
