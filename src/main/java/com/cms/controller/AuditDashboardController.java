package com.cms.controller;

import com.cms.model.AuditLog;
import com.cms.model.enums.AuditAction;
import com.cms.service.HibernateUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.hibernate.Session;
import org.hibernate.query.Query;

import com.cms.service.SessionManager;
import com.cms.model.User;
import com.cms.model.enums.Role;
import com.cms.util.NexusAlert;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuditDashboardController {

    @FXML private TextField searchField;
    @FXML private ComboBox<AuditAction> actionCombo;
    @FXML private TableView<AuditLog> auditTable;
    @FXML private TableColumn<AuditLog, String> timestampCol;
    @FXML private TableColumn<AuditLog, String> userCol;
    @FXML private TableColumn<AuditLog, String> actionCol;
    @FXML private TableColumn<AuditLog, String> entityCol;
    @FXML private TableColumn<AuditLog, String> descCol;

    private final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML
    public void initialize() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        Role role = currentUser != null ? currentUser.getRole() : null;
        
        // RBAC: Allow all law enforcement roles to view logs for transparency
        boolean hasAccess = role == Role.ADMINISTRATOR || 
                           role == Role.DETECTIVE || 
                           role == Role.OFFICER;

        if (!hasAccess) {
            Platform.runLater(() -> {
                NexusAlert.showWarning("ACCESS DENIED\n\nYou do not have clearance to view system audit logs.\nContact a System Administrator.");
                auditTable.setPlaceholder(new Label("Unauthorized Access: Law Enforcement clearance required."));
            });
            return;
        }

        setupTable();
        setupFilters();
        loadLogs();
    }

    private void setupFilters() {

        actionCombo.setItems(
                FXCollections.observableArrayList(AuditAction.values())
        );

        actionCombo.valueProperty().addListener((obs, oldVal, newVal) -> loadLogs());

        searchField.textProperty().addListener((obs, oldVal, newVal) -> loadLogs());
    }

    private void setupTable() {

        timestampCol.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        cell.getValue().getTimestamp().format(formatter)
                )
        );

        userCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getUserName())
        );

        actionCol.setCellValueFactory(new PropertyValueFactory<>("action"));
        entityCol.setCellValueFactory(new PropertyValueFactory<>("entityType"));
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
    }

    private void loadLogs() {

        new Thread(() -> {

            try (Session session = HibernateUtil.getSessionFactory().openSession()) {

                StringBuilder hql = new StringBuilder("from AuditLog where 1=1 ");
                Map<String, Object> params = new HashMap<>();

                String search = searchField.getText();
                if (search != null && !search.trim().isEmpty()) {
                    hql.append("and (userName like :search or entityType like :search or description like :search) ");
                    params.put("search", "%" + search + "%");
                }

                AuditAction action = actionCombo.getValue();
                if (action != null) {
                    hql.append("and action = :action ");
                    params.put("action", action);
                }

                hql.append("order by timestamp desc");

                Query<AuditLog> query = session.createQuery(hql.toString(), AuditLog.class);

                params.forEach(query::setParameter);

                List<AuditLog> logs = query.setMaxResults(100).list();

                Platform.runLater(() ->
                        auditTable.setItems(FXCollections.observableArrayList(logs))
                );
            }

        }, "audit-log-loader").start();
    }
}
