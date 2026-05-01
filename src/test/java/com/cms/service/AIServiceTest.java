package com.cms.service;

import com.cms.model.Person;
import com.cms.model.enums.RiskScore;
import com.cms.repository.CaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;

class AIServiceTest {

    @Mock
    private CaseRepository caseRepository;

    private AIService aiService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Use the test-friendly constructor to avoid HibernateUtil / Weka initialization
        aiService = new AIService(new java.util.Properties());
    }

    @Test
    void testCalculateRiskScore_ActiveWarrant() {
        Person person = new Person();
        person.setId(1L);
        person.setHasActiveWarrant(true);
        
        int score = aiService.calculateRiskScore(person);
        assertTrue(score >= 40, "Score should be at least 40 for active warrant");
    }

    @Test
    void testPredictRecidivism_HighRisk() {
        Person person = new Person();
        person.setId(10L);
        person.setHasActiveWarrant(true);
        person.setGangAffiliation("Nortenos");
        
        double recidivism = aiService.predictRecidivism(person);
        assertTrue(recidivism > 0.5, "Recidivism should be high for high-risk suspect");
    }

    @Test
    void testMapToEnum() {
        assertEquals(RiskScore.LOW, aiService.mapToEnum(10));
        assertEquals(RiskScore.MEDIUM, aiService.mapToEnum(45));
        assertEquals(RiskScore.HIGH, aiService.mapToEnum(75));
        assertEquals(RiskScore.CRITICAL, aiService.mapToEnum(95));
    }
}
