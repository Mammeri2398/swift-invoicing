package com.swiftlabs.invoicing.client;

public class ClientForm {

    private Long id;
    private String type;
    private String name;
    private String address;
    private String city;
    private String phone;
    private String email;
    private String nif;

    public ClientForm() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
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
        form.setType(client.getType() != null ? client.getType().name() : null);
        form.setName(client.getName());
        form.setAddress(client.getAddress());
        form.setCity(client.getCity());
        form.setPhone(client.getPhone());
        form.setEmail(client.getEmail());
        form.setNif(client.getNif());
        return form;
    }
}
