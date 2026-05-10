package com.cms.service;

import com.cms.model.CourtCase;
import com.cms.model.CourtHearing;
import com.cms.model.CaseFile;
import com.cms.repository.CourtRepository;
import com.cms.repository.CaseRepository;

import java.util.List;

public class CourtService {

    public List<CourtCase> getAllCases() {
        return HibernateUtil.executeTransaction(session -> {
            CourtRepository repo = new CourtRepository(session);
            return repo.findAllCases(100, 0);
        });
    }

    public List<CourtHearing> getUpcomingHearings() {
        return HibernateUtil.executeTransaction(session -> {
            CourtRepository repo = new CourtRepository(session);
            return repo.findUpcomingHearings(100, 0);
        });
    }

    public java.util.Optional<CaseFile> getCaseByNumber(String caseNumber) {
        return HibernateUtil.executeTransaction(session -> {
            CaseRepository repo = new CaseRepository(session);
            return repo.findByNumber(caseNumber);
        });
    }

    public List<CaseFile> getUnlinkedCases() {
        return HibernateUtil.executeTransaction(session -> {
            return session.createQuery(
                "SELECT c FROM CaseFile c WHERE NOT EXISTS (SELECT 1 FROM CourtCase cc WHERE cc.caseFile.id = c.id)", 
                CaseFile.class).list();
        });
    }

    public void saveCourtCase(CourtCase courtCase) {
        HibernateUtil.executeVoidTransaction(session -> {
            // Ensure associated entities are managed
            if (courtCase.getCaseFile() != null) {
                courtCase.setCaseFile(session.get(CaseFile.class, courtCase.getCaseFile().getId()));
            }
            if (courtCase.getProsecutor() != null) {
                courtCase.setProsecutor(session.get(com.cms.model.User.class, courtCase.getProsecutor().getId()));
            }
            session.merge(courtCase);
        });
    }

    public void updateHearingOutcome(Long hearingId, String outcome, com.cms.model.enums.HearingStatus status, java.time.LocalDateTime nextDate) {
        HibernateUtil.executeVoidTransaction(session -> {
            CourtHearing hearing = session.get(CourtHearing.class, hearingId);
            if (hearing != null) {
                hearing.setOutcome(outcome);
                hearing.setHearingStatus(status);
                hearing.setNextHearingDate(nextDate);
                session.merge(hearing);
            }
        });
    }

    public void updateCourtCaseStatus(Long courtCaseId, com.cms.model.enums.CourtStatus status, String verdict, String sentence) {
        HibernateUtil.executeVoidTransaction(session -> {
            CourtCase cc = session.get(CourtCase.class, courtCaseId);
            if (cc != null) {
                cc.setStatus(status);
                if (verdict != null) cc.setVerdict(verdict);
                if (sentence != null) cc.setSentenceDetails(sentence);
                if (status == com.cms.model.enums.CourtStatus.CLOSED) {
                    cc.setVerdictDate(java.time.LocalDate.now());
                }
                session.merge(cc);
            }
        });
    }

    public void saveHearing(CourtHearing hearing) {
        HibernateUtil.executeVoidTransaction(session -> {
            if (hearing.getCourtCase() != null) {
                hearing.setCourtCase(session.get(CourtCase.class, hearing.getCourtCase().getId()));
            }
            if (hearing.getRecordedBy() != null) {
                hearing.setRecordedBy(session.get(com.cms.model.User.class, hearing.getRecordedBy().getId()));
            }
            session.persist(hearing);
        });
    }
}
