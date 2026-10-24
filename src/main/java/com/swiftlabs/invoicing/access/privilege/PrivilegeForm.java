package com.swiftlabs.invoicing.access.privilege;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Privileges form: the entity is never bound from the request. */
public class PrivilegeForm {

    private Long id;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[A-Za-z_]*")
    private String code;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 500)
    private String description;

    public static PrivilegeForm from(Privilege privilege) {
        PrivilegeForm form = new PrivilegeForm();
        form.setId(privilege.getId());
        form.setCode(privilege.getCode());
        form.setName(privilege.getName());
        form.setDescription(privilege.getDescription());
        return form;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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
}
