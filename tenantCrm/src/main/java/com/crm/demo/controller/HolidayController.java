package com.crm.demo.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.crm.demo.model.Holiday;
import com.crm.demo.repository.UserRepository;
import com.crm.demo.service.HolidayService;
import com.crm.demo.service.NotificationService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * REST endpoints consumed by the calendar pages via fetch().
 * All endpoints are tenant-scoped — each admin/HR sees only their company's holidays.
 */
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private static final String RESP_SUCCESS = "success";
    private static final String RESP_MESSAGE = "message";

    @Autowired private HolidayService       holidayService;
    @Autowired private UserRepository       userRepository;
    @Autowired private NotificationService  notificationService;

    // ── Tenant helper ─────────────────────────────────────────────────────
    private String getTenant(HttpServletRequest request) {
        var username = (String) request.getAttribute("loggedInUser");
        if (username == null) return "default";
        var user = userRepository.findByUsername(username);
        if (user == null || user.getEmail() == null) return "default";
        var email     = user.getEmail();
        var localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        var lastDot      = localPart.lastIndexOf('.');
        return lastDot >= 0 ? localPart.substring(lastDot + 1) : localPart;
    }

    private String validateHolidayParams(String date, String name, String type) {
        if (date == null || date.isBlank() || !date.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return "Please select a valid holiday date.";
        }
        if (name == null || name.trim().isBlank()) {
            return "Holiday name is required.";
        }
        if (name.trim().length() > 255) {
            return "Holiday name cannot exceed 255 characters.";
        }
        if (type == null || type.isBlank()) {
            return "Holiday type is required.";
        }
        return null;
    }

    /** GET /api/holidays — returns all holidays for the caller's tenant. */
    @GetMapping
    public List<Holiday> list(HttpServletRequest request) {
        return holidayService.getByTenant(getTenant(request));
    }

    /** POST /api/holidays — add a holiday (ADMIN or HR only). */
    @PostMapping
    public java.util.Map<String, Object> add(
            @RequestParam String date,
            @RequestParam String name,
            @RequestParam String type,
            HttpServletRequest request) {

        var tenant = getTenant(request);
        var resp = new java.util.LinkedHashMap<String, Object>();

        var validationError = validateHolidayParams(date, name, type);
        if (validationError != null) {
            resp.put(RESP_SUCCESS, false);
            resp.put(RESP_MESSAGE, validationError);
            return resp;
        }

        if (holidayService.dateExists(date, tenant)) {
            resp.put(RESP_SUCCESS, false);
            resp.put(RESP_MESSAGE, "A holiday already exists on " + date + " for your company.");
            return resp;
        }

        var h = new Holiday();
        h.setDate(date);
        h.setName(name.trim());
        h.setType(type);
        h.setTenantSegment(tenant);
        holidayService.save(h);
        notificationService.notifyHolidayAdded(tenant, name.trim(), date);

        resp.put(RESP_SUCCESS, true);
        resp.put(RESP_MESSAGE, "Holiday added.");
        resp.put("holiday", h);
        return resp;
    }

    /** PUT /api/holidays/{id} — update a holiday (ADMIN or HR only). */
    @PutMapping("/{id}")
    public java.util.Map<String, Object> update(
            @PathVariable Long id,
            @RequestParam String date,
            @RequestParam String name,
            @RequestParam String type,
            HttpServletRequest request) {

        var tenant = getTenant(request);
        var resp = new java.util.LinkedHashMap<String, Object>();

        var validationError = validateHolidayParams(date, name, type);
        if (validationError != null) {
            resp.put(RESP_SUCCESS, false);
            resp.put(RESP_MESSAGE, validationError);
            return resp;
        }

        var h = holidayService.getById(id).orElse(null);
        if (h == null || !tenant.equals(h.getTenantSegment())) {
            resp.put(RESP_SUCCESS, false);
            resp.put(RESP_MESSAGE, "Holiday not found.");
            return resp;
        }

        if (holidayService.dateExistsExcluding(date, tenant, id)) {
            resp.put(RESP_SUCCESS, false);
            resp.put(RESP_MESSAGE, "Another holiday already exists on " + date + " for your company.");
            return resp;
        }

        h.setDate(date);
        h.setName(name.trim());
        h.setType(type);
        holidayService.save(h);
        notificationService.sendLiveUpdateToTenant(tenant, "HOLIDAY", "Holiday Updated", "Holiday was updated", "/calendar");

        resp.put(RESP_SUCCESS, true);
        resp.put(RESP_MESSAGE, "Holiday updated.");
        resp.put("holiday", h);
        return resp;
    }

    /** DELETE /api/holidays/{id} — delete a holiday (ADMIN or HR only). */
    @DeleteMapping("/{id}")
    public java.util.Map<String, Object> delete(
            @PathVariable Long id,
            HttpServletRequest request) {

        var tenant = getTenant(request);
        var resp = new java.util.LinkedHashMap<String, Object>();

        var h = holidayService.getById(id).orElse(null);
        if (h == null || !tenant.equals(h.getTenantSegment())) {
            resp.put(RESP_SUCCESS, false);
            resp.put(RESP_MESSAGE, "Holiday not found.");
            return resp;
        }

        holidayService.deleteById(id);
        notificationService.sendLiveUpdateToTenant(tenant, "HOLIDAY", "Holiday Deleted", "Holiday was deleted", "/calendar");
        resp.put(RESP_SUCCESS, true);
        resp.put(RESP_MESSAGE, "Holiday deleted.");
        return resp;
    }
}
