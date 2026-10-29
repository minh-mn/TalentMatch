package com.talentmatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Lưu tài liệu đính kèm công việc. Không dùng tên tệp do khách hàng gửi để tạo đường dẫn. */
@Service
public class JobDocumentService {
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf", "doc", "docx");
    private final Path uploadFolder;

    public JobDocumentService(@Value("${talentmatch.job-uploads-dir:uploads/job-documents}") String directory) {
        uploadFolder = Path.of(directory).toAbsolutePath().normalize();
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        String extension = extension(file.getOriginalFilename());
        if (!EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Chỉ hỗ trợ tệp JPG, PNG, PDF, DOC hoặc DOCX.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Tệp đính kèm không được vượt quá 10 MB.");
        }
    }

    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return null;
        validate(file);
        String key = UUID.randomUUID() + "." + extension(file.getOriginalFilename());
        Files.createDirectories(uploadFolder);
        Path target = uploadFolder.resolve(key).normalize();
        if (!target.getParent().equals(uploadFolder)) {
            throw new IllegalArgumentException("Đường dẫn tệp không hợp lệ.");
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target);
        }
        return key;
    }

    public Path filePath(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.(jpg|jpeg|png|pdf|doc|docx)")) {
            throw new IllegalArgumentException("Tệp đính kèm không hợp lệ.");
        }
        Path result = uploadFolder.resolve(key).normalize();
        if (!result.getParent().equals(uploadFolder)) {
            throw new IllegalArgumentException("Đường dẫn tệp không hợp lệ.");
        }
        return result;
    }

    public String safeDisplayName(MultipartFile file) {
        String supplied = file.getOriginalFilename();
        if (supplied == null) return "tai-lieu";
        String leafName = supplied.replace('\\', '/');
        leafName = leafName.substring(leafName.lastIndexOf('/') + 1);
        leafName = leafName.replaceAll("[\\p{Cntrl}]", "").trim();
        if (leafName.isBlank()) return "tai-lieu";
        return leafName.length() > 240 ? leafName.substring(leafName.length() - 240) : leafName;
    }

    private String extension(String filename) {
        if (filename == null || filename.lastIndexOf('.') < 0) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
