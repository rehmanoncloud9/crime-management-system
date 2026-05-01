package com.cms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Handles local image storage for officer and person profile photos.
 * Images are stored under d:/cms_data/images/ organized by category.
 */
public class ImageStorageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageStorageService.class);
    private static final String BASE_DIR = "d:/cms_data/images";

    private static ImageStorageService instance;

    private ImageStorageService() {
        // Ensure base directories exist
        ensureDirectoryExists(BASE_DIR + "/officers");
        ensureDirectoryExists(BASE_DIR + "/persons");
    }

    public static synchronized ImageStorageService getInstance() {
        if (instance == null) {
            instance = new ImageStorageService();
        }
        return instance;
    }

    /**
     * Saves a file to the specified category folder.
     * @param sourceFile The source file to copy (from FileChooser)
     * @param category "officers" or "persons"
     * @return The absolute path to the saved image, or null on failure
     */
    public String saveImage(File sourceFile, String category) {
        if (sourceFile == null || !sourceFile.exists()) {
            logger.warn("Source file is null or does not exist");
            return null;
        }

        String ext = getExtension(sourceFile.getName());
        if (!isAllowedExtension(ext)) {
            logger.warn("File extension not allowed: {}", ext);
            return null;
        }

        String targetDir = BASE_DIR + "/" + category;
        ensureDirectoryExists(targetDir);

        String uniqueName = UUID.randomUUID() + "." + ext;
        Path targetPath = Paths.get(targetDir, uniqueName);

        try {
            Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Image saved to: {}", targetPath);
            return targetPath.toString().replace("\\", "/");
        } catch (IOException e) {
            logger.error("Failed to save image", e);
            return null;
        }
    }

    /**
     * Deletes a previously saved image.
     */
    public void deleteImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) return;
        try {
            Files.deleteIfExists(Paths.get(imagePath));
            logger.info("Deleted image: {}", imagePath);
        } catch (IOException e) {
            logger.error("Failed to delete image: {}", imagePath, e);
        }
    }

    /**
     * Loads a JavaFX Image from a file path, with fallback to a default avatar.
     */
    public static javafx.scene.image.Image loadImage(String path) {
        if (path != null && !path.isBlank()) {
            File f = new File(path);
            if (f.exists()) {
                return new javafx.scene.image.Image(f.toURI().toString(), 80, 80, true, true);
            }
        }
        // Fallback: try to load default avatar from classpath
        try {
            return new javafx.scene.image.Image(
                ImageStorageService.class.getResourceAsStream("/images/default-avatar.png"),
                80, 80, true, true
            );
        } catch (Exception e) {
            // Ultimate fallback: return null (caller should handle)
            return null;
        }
    }

    private boolean isAllowedExtension(String ext) {
        return "jpg".equalsIgnoreCase(ext) || "jpeg".equalsIgnoreCase(ext) || "png".equalsIgnoreCase(ext);
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0) ? filename.substring(dot + 1) : "";
    }

    private void ensureDirectoryExists(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                logger.info("Created directory: {}", dirPath);
            }
        }
    }
}
