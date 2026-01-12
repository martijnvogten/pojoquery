package org.pojoquery.integrationtest.orders;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * Vendor entity - supplies parts through VendorParts.
 */
@Table("vendors")
@GenerateQuery
public class Vendor {

    @Id
    private Long id;

    private String name;
    private String address;
    private String contact;
    private String phone;

    public Vendor() {
    }

    public Vendor(String name, String address, String contact, String phone) {
        this.name = name;
        this.address = address;
        this.contact = contact;
        this.phone = phone;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @Override
    public String toString() {
        return "Vendor{id=" + id + ", name='" + name + "', contact='" + contact + "'}";
    }
}
