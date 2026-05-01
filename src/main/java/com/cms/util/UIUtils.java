package com.cms.util;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Function;

public class UIUtils {

    /**
     * Converts a standard JavaFX ComboBox into an auto-suggesting component.
     * 
     * @param comboBox The ComboBox to upgrade
     * @param fetchFunction A function that takes a keyword and returns a list of matching items
     * @param displayFunction A function that defines how the item should be displayed (e.g., entity.getName())
     */
    public static <T> void makeAutoSuggest(ComboBox<T> comboBox, 
                                           Function<String, List<T>> fetchFunction, 
                                           Function<T, String> displayFunction) {
        
        comboBox.setEditable(true);
        TextField editor = comboBox.getEditor();
        
        // Setup converter to display items properly
        comboBox.setConverter(new StringConverter<T>() {
            @Override
            public String toString(T object) {
                return object == null ? "" : displayFunction.apply(object);
            }

            @Override
            public T fromString(String string) {
                // Return the currently selected item if the text matches
                T selected = comboBox.getSelectionModel().getSelectedItem();
                if (selected != null && displayFunction.apply(selected).equals(string)) {
                    return selected;
                }
                // We rely on selection rather than text input alone
                return null;
            }
        });

        // Add listener for typing
        editor.textProperty().addListener((observable, oldValue, newValue) -> {
            // Wait for user to finish typing to avoid spamming the DB
            // In a real app we'd debounce, but for local DB this is fine for now
            if (!comboBox.isShowing()) {
                comboBox.show();
            }

            // Only search if the text was changed by typing, not by selecting an item
            T selected = comboBox.getSelectionModel().getSelectedItem();
            if (selected == null || !displayFunction.apply(selected).equals(newValue)) {
                Platform.runLater(() -> {
                    List<T> results = fetchFunction.apply(newValue != null ? newValue : "");
                    ObservableList<T> list = FXCollections.observableArrayList(results);
                    
                    // Retain caret position
                    int caret = editor.getCaretPosition();
                    comboBox.setItems(list);
                    editor.setText(newValue);
                    editor.positionCaret(caret);
                });
            }
        });
        
        // Handle selection to commit it correctly
        comboBox.setOnAction(event -> {
            T selected = comboBox.getSelectionModel().getSelectedItem();
            if (selected != null) {
                editor.setText(displayFunction.apply(selected));
                editor.positionCaret(editor.getText().length());
            }
        });
    }
}
