package com.crm.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "report_attachments")
public class ReportAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "report_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_report_attachment_report"))
    private Report report;

    private String originalFilename;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] fileData;

    private String contentType;

    private Long uploadedAt = System.currentTimeMillis();

    public ReportAttachment() {}

    public ReportAttachment(Report report, String originalFilename,
                            byte[] fileData, String contentType) {
        this.report = report;
        this.originalFilename = originalFilename;
        this.fileData = fileData;
        this.contentType = contentType;
        this.uploadedAt = System.currentTimeMillis();
    }

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }

    public Report getReport()                    { return report; }
    public void setReport(Report report)         { this.report = report; }

    public String getOriginalFilename()          { return originalFilename; }
    public void setOriginalFilename(String f)    { this.originalFilename = f; }

    public byte[] getFileData()                  { return fileData; }
    public void setFileData(byte[] fileData)     { this.fileData = fileData; }

    public String getContentType()               { return contentType; }
    public void setContentType(String ct)        { this.contentType = ct; }

    public Long getUploadedAt()                  { return uploadedAt; }
    public void setUploadedAt(Long uploadedAt)   { this.uploadedAt = uploadedAt; }
}
