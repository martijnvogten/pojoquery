package org.pojoquery.integrationtest.orders;

import java.time.LocalDate;

import org.pojoquery.annotations.GenerateQuery;
import org.pojoquery.annotations.Id;
import org.pojoquery.annotations.Table;

/**
 * Part entity - represents a part in the inventory system.
 * Parts can have bill-of-materials relationships (parts made up of other parts).
 */
@Table("parts")
@GenerateQuery
public class Part {

    @Id
    private Long id;

    private String partNumber;
    private Integer revision;
    private String description;
    private LocalDate revisionDate;
    // @Lob
    private String specification;  // CLOB in original - detailed specs
    // @Lob
    private byte[] drawing;        // BLOB in original - schematic data

    // Self-referential: bill-of-materials parent part
    private PartRef bomPart;

    public Part() {
    }

    public Part(String partNumber, Integer revision, String description, LocalDate revisionDate) {
        this.partNumber = partNumber;
        this.revision = revision;
        this.description = description;
        this.revisionDate = revisionDate;
    }

    public PartRef getRef() {
        return new PartRef(id, partNumber, revision);
    }

    public Long getId() {
        return id;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getRevisionDate() {
        return revisionDate;
    }

    public void setRevisionDate(LocalDate revisionDate) {
        this.revisionDate = revisionDate;
    }

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String specification) {
        this.specification = specification;
    }

    public byte[] getDrawing() {
        return drawing;
    }

    public void setDrawing(byte[] drawing) {
        this.drawing = drawing;
    }

    public PartRef getBomPart() {
        return bomPart;
    }

    public void setBomPart(PartRef bomPart) {
        this.bomPart = bomPart;
    }

    @Override
    public String toString() {
        return "Part{id=" + id + ", partNumber='" + partNumber + "', rev=" + revision +
               ", description='" + description + "'}";
    }
}
