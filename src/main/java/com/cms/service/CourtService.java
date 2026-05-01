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

    public void saveCourtCase(CourtCase courtCase) {
        HibernateUtil.executeVoidTransaction(session -> {
            CourtRepository repo = new CourtRepository(session);
            repo.saveCase(courtCase);
        });
    }

    public void saveHearing(CourtHearing hearing) {
        HibernateUtil.executeVoidTransaction(session -> {
            CourtRepository repo = new CourtRepository(session);
            repo.saveHearing(hearing);
        });
    }
}
