package com.cms.service;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

public class ReportingService {

    private static final Logger logger = LoggerFactory.getLogger(ReportingService.class);

    public void generateReport(String templatePath,
                               Map<String, Object> parameters,
                               Collection<?> data,
                               String outputPath) throws JRException {

        if (templatePath == null || outputPath == null) {
            throw new IllegalArgumentException("Template path and output path cannot be null");
        }

        if (data == null) {
            throw new IllegalArgumentException("Data collection cannot be null");
        }

        logger.info("Generating report from template: {}", templatePath);

        InputStream reportStream = getClass().getResourceAsStream(templatePath);

        if (reportStream == null) {
            throw new JRException("Report template not found: " + templatePath);
        }

        try (reportStream) {

            JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);

            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(data);

            JasperPrint jasperPrint = JasperFillManager.fillReport(
                    jasperReport,
                    parameters,
                    dataSource
            );

            JasperExportManager.exportReportToPdfFile(jasperPrint, outputPath);

            logger.info("Report successfully generated at: {}", outputPath);

        } catch (JRException e) {

            logger.error("Jasper report generation failed: {}", templatePath, e);
            throw e;

        } catch (Exception e) {

            logger.error("Unexpected error during report generation", e);
            throw new JRException("Unexpected error during report generation", e);
        }
    }
}
