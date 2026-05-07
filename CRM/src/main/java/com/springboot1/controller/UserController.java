package com.springboot1.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.springboot1.controller.Lead.LeadStatus;
import com.springboot1.repository.LeadRepository;
import com.springboot1.service.LeadService;

@Controller
@RequestMapping("/user")
public class UserController {

    private final LeadService leadService;
    private final LeadRepository leadRepo;

    public UserController(LeadService leadService, LeadRepository leadRepo) {
        this.leadService = leadService;
        this.leadRepo = leadRepo;
    }

    // ── Shared counts for sidebar badges ─────────────────────────────────────

    private void addCounts(Model model) {
        long total    = leadService.countTotal();
        long pending  = leadService.countPending();
        long approved = leadService.countApproved();
        long rejected = leadService.countRejected();

        model.addAttribute("totalLeads",    total);
        model.addAttribute("pendingLeads",  pending);
        model.addAttribute("approvedLeads", approved);
        model.addAttribute("rejectedLeads", rejected);
        model.addAttribute("approvedPct",   total > 0 ? (approved * 100 / total) : 0);
        model.addAttribute("messageCount",  approved + rejected);
        model.addAttribute("notifCount",    pending + rejected);
        model.addAttribute("approvedCount", approved);
        model.addAttribute("pendingCount",  pending);
    }

    // ── Home / Dashboard ──────────────────────────────────────────────────────

    @GetMapping({"/home", ""})
    public String home(Model model) {
        addCounts(model);
        return "user-home";
    }

    // ── Files ─────────────────────────────────────────────────────────────────

    @GetMapping("/files")
    public String files(Model model) {
        addCounts(model);
        model.addAttribute("allLeads", leadRepo.findAllByOrderByCreatedAtDesc());
        return "user-files";
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    @GetMapping("/messages")
    public String messages(Model model) {
        addCounts(model);
        model.addAttribute("approvedLeads", leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.APPROVED));
        model.addAttribute("rejectedLeads", leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.REJECTED));
        model.addAttribute("pendingLeads",  leadRepo.findByStatusOrderByCreatedAtDesc(LeadStatus.PENDING));
        return "user-messages";
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    @GetMapping("/notifications")
    public String notifications(Model model) {
        addCounts(model);
        model.addAttribute("allLeads", leadRepo.findAllByOrderByCreatedAtDesc());
        return "user-notifications";
    }

    // ── Location ──────────────────────────────────────────────────────────────

    @GetMapping("/location")
    public String location(Model model) {
        addCounts(model);
        model.addAttribute("allLeads", leadRepo.findAllByOrderByCreatedAtDesc());
        return "user-location";
    }

    // ── Graph ─────────────────────────────────────────────────────────────────

    @GetMapping("/graph")
    public String graph(Model model) {
        addCounts(model);
        return "user-graph";
    }
}
