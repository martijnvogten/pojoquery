package org.pojoquery.integrationtest.orders;

import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

@Table("parts")
public class PartRef {
    @Id
    private Long id;
    private String partNumber;
    private Integer revision;

    protected PartRef() {}

    public PartRef(Long id, String partNumber, Integer revision) {
        this.id = id;
        this.partNumber = partNumber;
        this.revision = revision;
    }

    public Long getId() {
        return id;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public Integer getRevision() {
        return revision;
    }
}