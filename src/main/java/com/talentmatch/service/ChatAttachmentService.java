package com.talentmatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Lưu tệp chat ngoài thư mục public. Chỉ người tham gia dự án được tải tệp. */
@Service
public class ChatAttachmentService {
    public record SavedFile(String key, String name, long size) {}

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "txt", "doc", "docx", "xls", "xlsx", "zip");
    private final Path folder;

    public ChatAttachmentService(@Value("${app.upload.dir:./talentmatch-uploads}") String directory) {
        folder = Path.of(directory).toAbsolutePath().normalize().resolve("chat");
    }

    public SavedFile save(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Hãy chọn tệp đính kèm.");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("Tệp chat không được vượt quá 10 MB.");

        String name = file.getOriginalFilename() == null ? "tep" : file.getOriginalFilename();
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 200 || name.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Tên tệp không hợp lệ.");

        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension))
            throw new IllegalArgumentException("Chỉ hỗ trợ PDF, ảnh PNG/JPG, TXT, Word, Excel và ZIP.");

        String key = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(folder);
            Files.copy(file.getInputStream(), folder.resolve(key));
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể lưu tệp đính kèm.", ex);
        }
        return new SavedFile(key, name, file.getSize());
    }

    public Path get(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.(pdf|png|jpg|jpeg|txt|doc|docx|xls|xlsx|zip)"))
            throw new IllegalArgumentException("Đường dẫn tệp không hợp lệ.");
        Path path = folder.resolve(key).normalize();
        if (!path.getParent().equals(folder)) throw new IllegalArgumentException("Đường dẫn không hợp lệ.");
        return path;
    }
}
