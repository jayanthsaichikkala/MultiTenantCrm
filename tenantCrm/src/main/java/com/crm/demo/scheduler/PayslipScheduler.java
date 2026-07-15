package com.crm.demo.scheduler;

import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.crm.demo.service.PayslipService;

@Component
public class PayslipScheduler {

    private final PayslipService payslipService;

    public PayslipScheduler(PayslipService payslipService) {
        this.payslipService = payslipService;
    }

    /**
     * Runs at midnight on the 2nd of every month.
     * Generates payslips for the previous month.
     */
    @Scheduled(cron = "0 0 0 2 * *")
    public void generateMonthlyPayslips() {
        var today = LocalDate.now();
        var prevMonthDate = today.minusMonths(1);
        int month = prevMonthDate.getMonthValue();
        int year = prevMonthDate.getYear();
        payslipService.generateAllPayslips(month, year);
    }
}
