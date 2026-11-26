package com.medexa.common.storage;

import com.medexa.common.exception.BadRequestException;
import com.medexa.common.exception.ResourceNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    public static final String BUCKET_CATEGORIES = "categories";
    public static final String BUCKET_MEDICINES = "medicines";
    public static final String BUCKET_LICENSES = "licenses";
    public static final String BUCKET_PRESCRIPTIONS = "prescriptions";

    private static final Set<String> ALLOWED_BUCKETS = Set.of(
            BUCKET_CATEGORIES, BUCKET_MEDICINES, BUCKET_LICENSES, BUCKET_PRESCRIPTIONS
    );

    private static final Map<String, String> IMAGE_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private static final Map<String, String> DOCUMENT_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "application/pdf", ".pdf"
    );

    private final StorageProperties storageProperties;

    public record StoredFileInfo(
            String urlPath,
            String storedFilename,
            String originalFilename,
            String contentType,
            long sizeBytes
    ) {
    }

    public String storeImage(String bucket, MultipartFile file) {
        return store(bucket, file, IMAGE_CONTENT_TYPES, "Only JPEG, PNG, and WEBP images are permitted.").urlPath();
    }

    public String storeDocument(String bucket, MultipartFile file) {
        return store(bucket, file, DOCUMENT_CONTENT_TYPES, "Only JPEG, PNG, WEBP, or PDF files are permitted.").urlPath();
    }

    public StoredFileInfo storePrescriptionDocument(MultipartFile file) {
        return store(BUCKET_PRESCRIPTIONS, file, DOCUMENT_CONTENT_TYPES,
                "Only JPEG, PNG, WEBP, or PDF prescription files are permitted.");
    }

    public Resource loadAsResource(String bucket, String filename) {
        validateBucket(bucket);
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BadRequestException("Invalid file reference.");
        }

        Path bucketRoot = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize().resolve(bucket);
        Path target = bucketRoot.resolve(filename).normalize();
        if (!target.startsWith(bucketRoot) || !Files.exists(target) || !Files.isReadable(target)) {
            throw new ResourceNotFoundException("Requested file was not found.");
        }

        try {
            return new UrlResource(target.toUri());
        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("Requested file was not found.");
        }
    }

    private StoredFileInfo store(
            String bucket,
            MultipartFile file,
            Map<String, String> allowedTypes,
            String invalidTypeMessage) {
        validateBucket(bucket);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file must not be empty.");
        }
        if (file.getSize() > storageProperties.getMaxFileSizeBytes()) {
            throw new BadRequestException("Uploaded file exceeds the maximum permitted size of 10 MB.");
        }

        String contentType = file.getContentType();
        String extension = contentType != null ? allowedTypes.get(contentType.toLowerCase()) : null;
        if (extension == null) {
            throw new BadRequestException(invalidTypeMessage);
        }

        String storedFilename = UUID.randomUUID() + extension;
        Path bucketRoot = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize().resolve(bucket);
        Path target = bucketRoot.resolve(storedFilename).normalize();
        if (!target.startsWith(bucketRoot)) {
            throw new BadRequestException("Invalid storage path.");
        }

        String originalName = file.getOriginalFilename();
        String safeOriginal = (originalName != null && !originalName.isBlank())
                ? Paths.get(originalName).getFileName().toString()
                : storedFilename;

        try {
            Files.createDirectories(bucketRoot);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String urlPath = "/api/v1/files/" + bucket + "/" + storedFilename;
            return new StoredFileInfo(urlPath, storedFilename, safeOriginal, contentType.toLowerCase(), file.getSize());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store uploaded file.", ex);
        }
    }

    private void validateBucket(String bucket) {
        if (bucket == null || !ALLOWED_BUCKETS.contains(bucket)) {
            throw new BadRequestException("Unsupported file bucket.");
        }
    }
}
