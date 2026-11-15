package com.talentmatch.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

/** Entity defaults and fields used by the current application model. */
class Stage1EntityTest {
    @Test void newInvitationIsWaitingForReply() {
        JobInvitation invitation = new JobInvitation();
        assertEquals("SENT", invitation.getStatus());
        invitation.setStatus("ACCEPTED");
        assertEquals("ACCEPTED", invitation.getStatus());
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

    @Test void projectAgreementSignaturesAreTrackedSeparately() {
        Project project = new Project();
        project.setCustomerSignedAt(java.time.LocalDateTime.now());
        assertFalse(project.isAgreementSigned());
        project.setFreelancerSignedAt(java.time.LocalDateTime.now());
        assertTrue(project.isAgreementSigned());
        project.setTotalPrice(new BigDecimal("550000.00"));
        assertEquals(new BigDecimal("550000.00"), project.getTotalPrice());
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
