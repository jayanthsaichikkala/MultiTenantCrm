package com.crm.demo.model;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.FutureOrPresent;

@Entity
@Table(name = "meetings")
public class Meeting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank(message = "Title is required")
	@Size(max = 255, message = "Title cannot exceed 255 characters")
	private String title;

	@NotNull(message = "Meeting date is required")
	@FutureOrPresent(message = "Meeting date cannot be in the past")
	private LocalDate meetingDate;

	@NotNull(message = "Meeting time is required")
	private LocalTime meetingTime;

	@NotNull(message = "Duration is required")
	@Min(value = 1, message = "Duration must be at least 1 minute")
	@Max(value = 1440, message = "Duration cannot exceed 24 hours (1440 minutes)")
	private Integer duration;

	@NotBlank(message = "Meeting type is required")
	private String meetingType;

	private String location;

	@NotBlank(message = "Participants are required")
	private String participants;

	@Column(length = 2000)
	@Size(max = 255, message = "Agenda cannot exceed 255 characters")
	private String agenda;

	private boolean sendNotification;

	private String tenantSegment;

	/** Username of the person who scheduled this meeting. */
	@Column(name = "created_by")
	private String scheduledBy;

	// Getters & Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public LocalDate getMeetingDate() {
		return meetingDate;
	}

	public void setMeetingDate(LocalDate d) {
		this.meetingDate = d;
	}

	public LocalTime getMeetingTime() {
		return meetingTime;
	}

	public void setMeetingTime(LocalTime t) {
		this.meetingTime = t;
	}

	public Integer getDuration() {
		return duration;
	}

	public void setDuration(Integer duration) {
		this.duration = duration;
	}

	public String getMeetingType() {
		return meetingType;
	}

	public void setMeetingType(String type) {
		this.meetingType = type;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getParticipants() {
		return participants;
	}

	public void setParticipants(String p) {
		this.participants = p;
	}

	public String getAgenda() {
		return agenda;
	}

	public void setAgenda(String agenda) {
		this.agenda = agenda;
	}

	public boolean isSendNotification() {
		return sendNotification;
	}

	public void setSendNotification(boolean b) {
		this.sendNotification = b;
	}

	public String getTenantSegment() {
		return tenantSegment;
	}

	public void setTenantSegment(String tenantSegment) {
		this.tenantSegment = tenantSegment;
	}

	public String getScheduledBy() {
		return scheduledBy;
	}

	public void setScheduledBy(String scheduledBy) {
		this.scheduledBy = scheduledBy;
	}
}