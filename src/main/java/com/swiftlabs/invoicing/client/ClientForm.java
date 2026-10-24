package com.swiftlabs.invoicing.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Clients form. Optional fields may be null (blank input is bound as null). */
@NifRequiredForProfessional
public class ClientForm {

    private Long id;

    @NotNull
    private ClientType type;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String address;

    @Size(max = 255)
    private String city;

    @Size(max = 50)
    @Pattern(regexp = "[0-9+ ().-]*")
    private String phone;

    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 50)
    @Pattern(regexp = "[0-9]*")
    private String nif;

    public ClientForm() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ClientType getType() { return type; }
    public void setType(ClientType type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNif() { return nif; }
    public void setNif(String nif) { this.nif = nif; }

    public static ClientForm from(Client client) {
        ClientForm form = new ClientForm();
        form.setId(client.getId());
        form.setType(client.getType());
        form.setName(client.getName());
        form.setAddress(client.getAddress());
        form.setCity(client.getCity());
        form.setPhone(client.getPhone());
        form.setEmail(client.getEmail());
        form.setNif(client.getNif());
        return form;
    }
}
