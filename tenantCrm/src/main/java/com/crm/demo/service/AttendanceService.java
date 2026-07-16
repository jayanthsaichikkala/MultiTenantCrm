package com.crm.demo.service;

import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crm.demo.model.Attendance;
import com.crm.demo.repository.AttendanceRepository;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final NotificationService notificationService;

    public AttendanceService(AttendanceRepository attendanceRepository, NotificationService notificationService) {
        this.attendanceRepository = attendanceRepository;
        this.notificationService = notificationService;
    }

    /**
     * Run every 10 minutes to auto-punchout active sessions older than 9 hours.
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    @Transactional
    public void scheduleAutoPunchOut() {
        processAutoPunchOuts();
    }

    /**
     * Scans for open attendance sessions and auto-punches them out if they exceed 9 hours or are from past days.
     * Also repairs any incorrect auto-punchouts caused by the previous midnight-wrap bug.
     */
    @Transactional
    public void processAutoPunchOuts() {
        var active = attendanceRepository.findByCheckOutIsNull();
        var now = LocalDateTime.now();
        
        // 1. Process active records that need auto punch-out
        autoPunchOutExpiredSessions(active, now);

        // 2. Self-healing: Repair any incorrect auto-punch-outs that were triggered today by the midnight-wrap bug
        repairIncorrectPunchOuts(now);
    }

    private void autoPunchOutExpiredSessions(List<Attendance> active, LocalDateTime now) {
        for (Attendance a : active) {
            if (a.getDate() == null || a.getCheckIn() == null) {
                continue;
            }
            var checkInDateTime = LocalDateTime.of(a.getDate(), a.getCheckIn());
            var limit = checkInDateTime.plusHours(9);
            
            if (now.isAfter(limit)) {
                var autoCheckOutTime = a.getCheckIn().plusHours(9);
                a.setCheckOut(autoCheckOutTime);
                
                // Recalculate status based on worked hours:
                long mins = a.getWorkedMinutes();
                if (mins >= 0 && mins < 240) {
                    a.setStatus("absent");
                } else if (mins >= 240 && mins < 360) {
                    a.setStatus("half-day");
                }
                
                attendanceRepository.save(a);
                if (a.getUser() != null) {
                    notificationService.notifyAttendanceUpdated(a.getUser(), "punch-out");
                }
            }
        }
    }

    private void repairIncorrectPunchOuts(LocalDateTime now) {
        var allRecords = attendanceRepository.findAll();
        for (Attendance a : allRecords) {
            repairSingleRecord(a, now);
        }
    }

    private void repairSingleRecord(Attendance a, LocalDateTime now) {
        if (a.getCheckIn() == null || a.getCheckOut() == null || a.getDate() == null) {
            return;
        }
        var checkInDateTime = LocalDateTime.of(a.getDate(), a.getCheckIn());
        var limit = checkInDateTime.plusHours(9);
        
        if (limit.isAfter(now)) {
            var expectedAutoCheckOut = a.getCheckIn().plusHours(9);
            if (expectedAutoCheckOut.equals(a.getCheckOut())) {
                // Restore back to active checked-in state
                a.setCheckOut(null);
                String originalStatus = a.getCheckIn().isAfter(LocalTime.of(9, 30)) ? "late" : "present";
                a.setStatus(originalStatus);
                attendanceRepository.save(a);
            }
        }
    }
}
