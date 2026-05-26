package com.crm.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'pending'")
    private String status = "pending";

    @Column(nullable = false, columnDefinition = "VARCHAR(10) DEFAULT 'medium'")
    private String priority = "medium";

    private String dueDate;

    public Long getId()                    { return id; }
    public void setId(Long id)             { this.id = id; }

    public String getTitle()               { return title; }
    public void setTitle(String title)     { this.title = title; }

    public String getDescription()         { return description; }
    public void setDescription(String d)   { this.description = d; }

    public String getStatus()              { return status != null ? status : "pending"; }
    public void setStatus(String status)   { this.status = status; }

    public String getPriority()            { return priority != null ? priority : "medium"; }
    public void setPriority(String p)      { this.priority = p; }

    public String getDueDate()             { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
}
