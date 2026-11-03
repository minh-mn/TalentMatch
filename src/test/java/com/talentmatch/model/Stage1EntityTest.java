package com.talentmatch.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

/** Hoi quy co ban cho Entity da dong bo trong GD1 (khong can ket noi MySQL). */
class Stage1EntityTest {
    @Test void newInvitationIsWaitingForReply() {
        JobInvitation invitation = new JobInvitation();
        assertEquals("SENT", invitation.getStatus());
        invitation.setStatus("ACCEPTED");
        invitation.setRespondedAt(LocalDateTime.of(2026, 10, 31, 12, 0));
        assertEquals("ACCEPTED", invitation.getStatus());
        assertNotNull(invitation.getRespondedAt());
    }

    @Test void deliverySupportsFileOrExternalUrl() {
        ProjectDelivery delivery = new ProjectDelivery();
        delivery.setVersion(1);
        delivery.setExternalUrl("https://example.com/demo");
        delivery.setFileName(null);
        assertEquals(1, delivery.getVersion());
        assertNull(delivery.getFileName());
        assertEquals("https://example.com/demo", delivery.getExternalUrl());
    }

    @Test void projectMetadataFromDatabaseIsNotLost() {
        Project project = new Project();
        project.setOrderNumber("TM-2026-8891");
        project.setAgreementVersion(2);
        project.setPlatformFee(new BigDecimal("550000.00"));
        project.setAgreementTerms("Dieu khoan demo");
        assertEquals("TM-2026-8891", project.getOrderNumber());
        assertEquals(2, project.getAgreementVersion());
        assertEquals(new BigDecimal("550000.00"), project.getPlatformFee());
        assertEquals("Dieu khoan demo", project.getAgreementTerms());
    }

    @Test void disputeTracksResolutionSeparately() {
        Dispute dispute = new Dispute();
        assertEquals("OPEN", dispute.getStatus());
        dispute.setStatus("RESOLVED");
        dispute.setRefundEmployerAmount(BigDecimal.ZERO);
        assertEquals("RESOLVED", dispute.getStatus());
        assertEquals(BigDecimal.ZERO, dispute.getRefundEmployerAmount());
    }
}
