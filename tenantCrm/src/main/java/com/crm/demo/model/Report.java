package com.crm.demo.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    /** Manager's username who sent this report */
    private String sentBy;

    /** Tenant segment — isolates reports per company */
    private String tenantSegment;

    /** Epoch-ms timestamp when sent */
    private Long sentAt = System.currentTimeMillis();

    /**
     * Recipients: stored as comma-separated user IDs.
     * e.g. "3,7,12"
     */
    @Column(length = 2000)
    private String recipientIds;

    /**
     * Recipient usernames: comma-separated display names for quick rendering.
     */
    @Column(length = 2000)
    private String recipientNames;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ReportAttachment> attachments = new ArrayList<>();

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Long getId()                              { return id; }
    public void setId(Long id)                       { this.id = id; }

    public String getTitle()                         { return title; }
    public void setTitle(String title)               { this.title = title; }

    public String getMessage()                       { return message; }
    public void setMessage(String message)           { this.message = message; }

    public String getSentBy()                        { return sentBy; }
    public void setSentBy(String sentBy)             { this.sentBy = sentBy; }

    public String getTenantSegment()                 { return tenantSegment; }
    public void setTenantSegment(String t)           { this.tenantSegment = t; }

    public Long getSentAt()                          { return sentAt; }
    public void setSentAt(Long sentAt)               { this.sentAt = sentAt; }

    public String getRecipientIds()                  { return recipientIds; }
    public void setRecipientIds(String recipientIds) { this.recipientIds = recipientIds; }

    public String getRecipientNames()                { return recipientNames; }
    public void setRecipientNames(String n)          { this.recipientNames = n; }

    public List<ReportAttachment> getAttachments()   { return attachments; }
    public void setAttachments(List<ReportAttachment> a) { this.attachments = a; }

    /** Parse recipientIds CSV into a list of Long */
    public List<Long> getRecipientIdList() {
        if (recipientIds == null || recipientIds.isBlank()) return java.util.Collections.emptyList();
        List<Long> ids = new ArrayList<>();
        for (String s : recipientIds.split(",")) {
            try { ids.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    /** Formatted sent date for display */
    public String getSentAtFormatted() {
        if (sentAt == null) return "—";
        java.time.Instant instant = java.time.Instant.ofEpochMilli(sentAt);
        java.time.LocalDateTime ldt = java.time.LocalDateTime.ofInstant(
                instant, java.time.ZoneId.systemDefault());
        return String.format("%04d-%02d-%02d %02d:%02d",
                ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(),
                ldt.getHour(), ldt.getMinute());
    }
}
