package com.medexa.common.storage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Uploaded media and document retrieval")
public class FileStorageController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{bucket}/{filename}")
    @Operation(summary = "Serve uploaded image or license document")
    public ResponseEntity<Resource> serveFile(
            @PathVariable String bucket,
            @PathVariable String filename) {
        Resource resource = fileStorageService.loadAsResource(bucket, filename);
        MediaType mediaType = MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType)
                .header("X-Content-Type-Options", "nosniff");

        if (!FileStorageService.BUCKET_LICENSES.equals(bucket)) {
            builder.cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());
        } else {
            builder.cacheControl(CacheControl.noStore());
        }

        return builder.body(resource);
    }
}
