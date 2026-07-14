package com.crm.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.crm.demo.controller.AdminController;
import com.crm.demo.controller.ManagerController;
import com.crm.demo.controller.ManagerController.FileUploadException;
import com.crm.demo.model.DomainCategory;
import com.crm.demo.model.Report;
import com.crm.demo.model.ReportAttachment;
import com.crm.demo.repository.ReportAttachmentRepository;

@ExtendWith(MockitoExtension.class)
class CrmApplicationTests {

    @InjectMocks
    private AdminController adminController;

    @Mock
    private ReportAttachmentRepository reportAttachmentRepository;

    @Test
    void testDomainCategory() {
        DomainCategory category = new DomainCategory();
        category.setId(1L);
        category.setName("Sales");
        category.setTenantSegment("tenant1");

        assertEquals(1L, category.getId());
        assertEquals("Sales", category.getName());
        assertEquals("tenant1", category.getTenantSegment());
    }

    @Test
    void testAdminControllerViewReportAttachment() {
        ReportAttachment attachment = new ReportAttachment();
        attachment.setOriginalFilename("test.pdf");
        attachment.setContentType("application/pdf");
        attachment.setFileData(new byte[]{1, 2, 3});

        when(reportAttachmentRepository.findById(1L)).thenReturn(Optional.of(attachment));

        ResponseEntity<byte[]> response = adminController.viewReportAttachment(1L);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals(new byte[]{1, 2, 3}, response.getBody());
        assertEquals("inline; filename=\"test.pdf\"", response.getHeaders().getFirst("Content-Disposition"));
        assertEquals("application/pdf", response.getHeaders().getFirst("Content-Type"));
    }

    @Test
    void testAdminControllerDownloadReportAttachment() {
        ReportAttachment attachment = new ReportAttachment();
        attachment.setOriginalFilename("test.pdf");
        attachment.setContentType(null);
        attachment.setFileData(new byte[]{1, 2, 3});

        when(reportAttachmentRepository.findById(1L)).thenReturn(Optional.of(attachment));

        ResponseEntity<byte[]> response = adminController.downloadReportAttachment(1L);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals(new byte[]{1, 2, 3}, response.getBody());
        assertEquals("attachment; filename=\"test.pdf\"", response.getHeaders().getFirst("Content-Disposition"));
        assertEquals("application/octet-stream", response.getHeaders().getFirst("Content-Type"));
    }

    @Test
    void testAdminControllerReportAttachmentNotFound() {
        when(reportAttachmentRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<byte[]> response = adminController.viewReportAttachment(99L);
        assertNotNull(response);
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void testManagerControllerProcessAttachmentsSuccess() throws Exception {
        ManagerController managerController = new ManagerController();

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.txt");
        when(file.getBytes()).thenReturn(new byte[]{4, 5});
        when(file.getContentType()).thenReturn("text/plain");

        MultipartFile[] attachments = new MultipartFile[]{file};
        List<Object> attachmentInfos = new ArrayList<>();

        Method method = ManagerController.class.getDeclaredMethod(
            "processAttachments", 
            MultipartFile[].class, 
            List.class
        );
        method.setAccessible(true);
        method.invoke(managerController, attachments, attachmentInfos);

        assertEquals(1, attachmentInfos.size());
    }

    @Test
    void testManagerControllerProcessAttachmentsNullAndEmpty() throws Exception {
        ManagerController managerController = new ManagerController();
        List<Object> attachmentInfos = new ArrayList<>();

        Method method = ManagerController.class.getDeclaredMethod(
            "processAttachments", 
            MultipartFile[].class, 
            List.class
        );
        method.setAccessible(true);

        method.invoke(managerController, (Object) null, attachmentInfos);
        assertTrue(attachmentInfos.isEmpty());

        MultipartFile fileNull = null;
        MultipartFile fileEmpty = mock(MultipartFile.class);
        when(fileEmpty.isEmpty()).thenReturn(true);

        MultipartFile[] attachments = new MultipartFile[]{fileNull, fileEmpty};
        method.invoke(managerController, attachments, attachmentInfos);
        assertTrue(attachmentInfos.isEmpty());
    }

    @Test
    void testManagerControllerProcessAttachmentsException() throws Exception {
        ManagerController managerController = new ManagerController();

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenThrow(new IOException("Disk error"));

        MultipartFile[] attachments = new MultipartFile[]{file};
        List<Object> attachmentInfos = new ArrayList<>();

        Method method = ManagerController.class.getDeclaredMethod(
            "processAttachments", 
            MultipartFile[].class, 
            List.class
        );
        method.setAccessible(true);

        try {
            method.invoke(managerController, attachments, attachmentInfos);
            fail("Expected exception");
        } catch (Exception e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof FileUploadException);
            assertEquals("File upload failed: Disk error", cause.getMessage());
        }
    }

    @Test
    void testManagerControllerProcessReportAttachmentsSuccess() throws Exception {
        ManagerController managerController = new ManagerController();

        java.lang.reflect.Field repoField = ManagerController.class.getDeclaredField("reportAttachmentRepository");
        repoField.setAccessible(true);
        repoField.set(managerController, reportAttachmentRepository);

        Report report = new Report();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("report.pdf");
        when(file.getBytes()).thenReturn(new byte[]{9, 9});
        when(file.getContentType()).thenReturn("application/pdf");

        MultipartFile[] attachments = new MultipartFile[]{file};

        Method method = ManagerController.class.getDeclaredMethod(
            "processReportAttachments", 
            Report.class,
            MultipartFile[].class
        );
        method.setAccessible(true);
        method.invoke(managerController, report, attachments);

        verify(reportAttachmentRepository, times(1)).save(any(ReportAttachment.class));
    }

    @Test
    void testManagerControllerProcessReportAttachmentsNullAndEmpty() throws Exception {
        ManagerController managerController = new ManagerController();
        Report report = new Report();

        Method method = ManagerController.class.getDeclaredMethod(
            "processReportAttachments", 
            Report.class,
            MultipartFile[].class
        );
        method.setAccessible(true);

        method.invoke(managerController, report, (Object) null);

        MultipartFile fileNull = null;
        MultipartFile fileEmpty = mock(MultipartFile.class);
        when(fileEmpty.isEmpty()).thenReturn(true);

        MultipartFile[] attachments = new MultipartFile[]{fileNull, fileEmpty};
        method.invoke(managerController, report, attachments);

        verifyNoInteractions(reportAttachmentRepository);
    }

    @Test
    void testManagerControllerProcessReportAttachmentsException() throws Exception {
        ManagerController managerController = new ManagerController();

        java.lang.reflect.Field repoField = ManagerController.class.getDeclaredField("reportAttachmentRepository");
        repoField.setAccessible(true);
        repoField.set(managerController, reportAttachmentRepository);

        Report report = new Report();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenThrow(new IOException("Disk write error"));

        MultipartFile[] attachments = new MultipartFile[]{file};

        Method method = ManagerController.class.getDeclaredMethod(
            "processReportAttachments", 
            Report.class,
            MultipartFile[].class
        );
        method.setAccessible(true);

        try {
            method.invoke(managerController, report, attachments);
            fail("Expected exception");
        } catch (Exception e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof FileUploadException);
            assertEquals("File upload failed: Disk write error", cause.getMessage());
        }
    }
}
