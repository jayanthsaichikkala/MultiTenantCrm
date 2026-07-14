package com.crm.demo.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.crm.demo.controller.ManagerController.FileUploadException;
import com.crm.demo.model.DomainCategory;
import com.crm.demo.model.Report;
import com.crm.demo.model.ReportAttachment;
import com.crm.demo.repository.ReportAttachmentRepository;

@ExtendWith(MockitoExtension.class)
class CrmApplicationTests {

    private static final String TEST_PDF = "test.pdf";
    private static final String APP_PDF = "application/pdf";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String CONTENT_TYPE = "Content-Type";

    @InjectMocks
    private AdminController adminController;

    @Mock
    private ReportAttachmentRepository reportAttachmentRepository;

    // ── Parameterized test sources ────────────────────────────────────────────

    static Stream<String> contentTypeVariants() {
        return Stream.of(APP_PDF, null);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

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

    @ParameterizedTest
    @MethodSource("contentTypeVariants")
    void testAdminControllerViewReportAttachment(String contentType) {
        ReportAttachment attachment = new ReportAttachment();
        attachment.setOriginalFilename(TEST_PDF);
        attachment.setContentType(contentType);
        attachment.setFileData(new byte[]{1, 2, 3});

        when(reportAttachmentRepository.findById(1L)).thenReturn(Optional.of(attachment));

        ResponseEntity<byte[]> response = adminController.viewReportAttachment(1L);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals(new byte[]{1, 2, 3}, response.getBody());
        assertEquals("inline; filename=\"" + TEST_PDF + "\"", response.getHeaders().getFirst(CONTENT_DISPOSITION));
        String expectedType = contentType != null ? contentType : "application/octet-stream";
        assertEquals(expectedType, response.getHeaders().getFirst(CONTENT_TYPE));
    }

    @ParameterizedTest
    @MethodSource("contentTypeVariants")
    void testAdminControllerDownloadReportAttachment(String contentType) {
        ReportAttachment attachment = new ReportAttachment();
        attachment.setOriginalFilename(TEST_PDF);
        attachment.setContentType(contentType);
        attachment.setFileData(new byte[]{1, 2, 3});

        when(reportAttachmentRepository.findById(1L)).thenReturn(Optional.of(attachment));

        ResponseEntity<byte[]> response = adminController.downloadReportAttachment(1L);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals(new byte[]{1, 2, 3}, response.getBody());
        assertEquals("attachment; filename=\"" + TEST_PDF + "\"", response.getHeaders().getFirst(CONTENT_DISPOSITION));
        String expectedType = contentType != null ? contentType : "application/octet-stream";
        assertEquals(expectedType, response.getHeaders().getFirst(CONTENT_TYPE));
    }

    @Test
    void testAdminControllerReportAttachmentNotFound() {
        when(reportAttachmentRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<byte[]> response = adminController.viewReportAttachment(99L);
        assertNotNull(response);
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void testAdminControllerDownloadReportAttachmentNotFound() {
        when(reportAttachmentRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<byte[]> response = adminController.downloadReportAttachment(99L);
        assertNotNull(response);
        assertEquals(404, response.getStatusCode().value());
    }

    @ParameterizedTest
    @MethodSource("contentTypeVariants")
    void testManagerControllerProcessAttachments(String contentType) throws Exception {
        ManagerController managerController = new ManagerController();

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.txt");
        when(file.getBytes()).thenReturn(new byte[]{4, 5});
        when(file.getContentType()).thenReturn(contentType);

        MultipartFile[] attachments = new MultipartFile[]{file};
        List<ManagerController.TaskAttachmentInfo> attachmentInfos = new ArrayList<>();

        managerController.processAttachments(attachments, attachmentInfos);

        assertEquals(1, attachmentInfos.size());
    }

    @Test
    void testManagerControllerProcessAttachmentsNullAndEmpty() throws Exception {
        ManagerController managerController = new ManagerController();
        List<ManagerController.TaskAttachmentInfo> attachmentInfos = new ArrayList<>();

        managerController.processAttachments(null, attachmentInfos);
        assertTrue(attachmentInfos.isEmpty());

        MultipartFile fileNull = null;
        MultipartFile fileEmpty = mock(MultipartFile.class);
        when(fileEmpty.isEmpty()).thenReturn(true);

        MultipartFile[] attachments = new MultipartFile[]{fileNull, fileEmpty};
        managerController.processAttachments(attachments, attachmentInfos);
        assertTrue(attachmentInfos.isEmpty());
    }

    @Test
    void testManagerControllerProcessAttachmentsException() throws Exception {
        ManagerController managerController = new ManagerController();

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenThrow(new IOException("Disk error"));

        MultipartFile[] attachments = new MultipartFile[]{file};
        List<ManagerController.TaskAttachmentInfo> attachmentInfos = new ArrayList<>();

        try {
            managerController.processAttachments(attachments, attachmentInfos);
            fail("Expected exception");
        } catch (FileUploadException e) {
            assertEquals("File upload failed: Disk error", e.getMessage());
        }
    }

    @ParameterizedTest
    @MethodSource("contentTypeVariants")
    void testManagerControllerProcessReportAttachments(String contentType) throws Exception {
        ManagerController managerController = new ManagerController();
        managerController.reportAttachmentRepository = reportAttachmentRepository;

        Report report = new Report();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("report.pdf");
        when(file.getBytes()).thenReturn(new byte[]{9, 9});
        when(file.getContentType()).thenReturn(contentType);

        MultipartFile[] attachments = new MultipartFile[]{file};

        managerController.processReportAttachments(report, attachments);

        verify(reportAttachmentRepository, times(1)).save(any(ReportAttachment.class));
    }

    @Test
    void testManagerControllerProcessReportAttachmentsNullAndEmpty() throws Exception {
        ManagerController managerController = new ManagerController();
        Report report = new Report();

        managerController.processReportAttachments(report, null);

        MultipartFile fileNull = null;
        MultipartFile fileEmpty = mock(MultipartFile.class);
        when(fileEmpty.isEmpty()).thenReturn(true);

        MultipartFile[] attachments = new MultipartFile[]{fileNull, fileEmpty};
        managerController.processReportAttachments(report, attachments);

        verifyNoInteractions(reportAttachmentRepository);
    }

    @Test
    void testManagerControllerProcessReportAttachmentsException() throws Exception {
        ManagerController managerController = new ManagerController();
        managerController.reportAttachmentRepository = reportAttachmentRepository;

        Report report = new Report();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenThrow(new IOException("Disk write error"));

        MultipartFile[] attachments = new MultipartFile[]{file};

        try {
            managerController.processReportAttachments(report, attachments);
            fail("Expected exception");
        } catch (FileUploadException e) {
            assertEquals("File upload failed: Disk write error", e.getMessage());
        }
    }
}
