package com.crm.demo.model;

import java.time.LocalDate;

/**
 * View-model for a single calendar day in the attendance log.
 * Either wraps a real Attendance record, or represents a synthetic
 * absent / weekend / holiday day when no punch-in exists.
 */
public class AttendanceDay {

    private static final String STATUS_ABSENT = "absent";
    private static final String STATUS_HALF_DAY = "half-day";

    private final LocalDate date;
    private final Attendance attendance;      // null for synthetic days
    private final String     status;      // "present","late","half-day","absent","leave","weekend","holiday"
    private final String     holidayName; // non-null only when status == "holiday"

    /** Wrap a real attendance record */
    public AttendanceDay(Attendance attendance) {
        this.date        = attendance.getDate();
        this.attendance  = attendance;
        this.holidayName = null;
        // Derive status: if worked < 4h -> absent, 4-6h -> half-day, 6+h -> base status (present/late)
        long mins = attendance.getWorkedMinutes();
        String base = attendance.getStatus(); // "present" or "late"
        if (attendance.getCheckOut() != null) {
            if (mins >= 0 && mins < 240) {
                this.status = STATUS_ABSENT;
            } else if (mins >= 240 && mins < 360) {
                this.status = STATUS_HALF_DAY;
            } else {
                this.status = STATUS_ABSENT.equals(base) || STATUS_HALF_DAY.equals(base) ? "present" : base;
            }
        } else {
            this.status = base;
        }
    }

    /** Synthetic day (absent, weekend, or holiday) */
    public AttendanceDay(LocalDate date, String status) {
        this.date        = date;
        this.attendance  = null;
        this.status      = status;
        this.holidayName = null;
    }

    /** Holiday day */
    public AttendanceDay(LocalDate date, String holidayName, boolean isHoliday) {
        this.date        = date;
        this.attendance  = null;
        this.status      = isHoliday ? "holiday" : STATUS_ABSENT;
        this.holidayName = holidayName;
    }

    public LocalDate  getDate()        { return date; }
    public Attendance getRecord()      { return attendance; }
    public String     getStatus()      { return status; }
    public String     getHolidayName() { return holidayName; }

    public boolean isReal()     { return attendance != null; }
    public boolean isWeekend()  { return "weekend".equals(status); }
    public boolean isAbsent()   { return STATUS_ABSENT.equals(status); }
    public boolean isOnLeave()  { return "leave".equals(status); }
    public boolean isHoliday()  { return "holiday".equals(status); }
    public boolean isHalfDay()  { return STATUS_HALF_DAY.equals(status); }

    // ── Delegating helpers so Thymeleaf can call them directly ────────────

    public String getCheckInDisplay()  { return attendance != null ? attendance.getCheckInDisplay()  : "—"; }
    public String getCheckOutDisplay() { return attendance != null ? attendance.getCheckOutDisplay() : "—"; }
    public String getWorkedHours()     { return attendance != null ? attendance.getWorkedHours()     : "—"; }
    public String getBreakDuration()   { return attendance != null ? attendance.getBreakDuration()   : "—"; }
    public String getBreakSummary()    { return attendance != null ? attendance.getBreakSummary()    : "—"; }
    public String getBreak1Summary()   { return attendance != null ? attendance.getBreak1Summary()   : "—"; }
    public String getBreak2Summary()   { return attendance != null ? attendance.getBreak2Summary()   : "—"; }
    public String getDayType()         { return attendance != null ? attendance.getDayType()         : "—"; }
}
