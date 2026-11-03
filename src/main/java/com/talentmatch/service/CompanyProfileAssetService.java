package com.talentmatch.service;

import com.talentmatch.model.*;
import com.talentmatch.repository.CompanyProfileAssetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class CompanyProfileAssetService {
    private final CompanyProfileAssetRepository repository;
    private final Path storage;
    public CompanyProfileAssetService(CompanyProfileAssetRepository repository,
            @Value("${talentmatch.company-profile.storage:uploads/company-profiles}") String root) {
        this.repository = repository;
        this.storage = Path.of(root).toAbsolutePath().normalize();
    }
    public List<CompanyProfileAsset> forOwner(User owner) {
        return repository.findByOwnerOrderByCreatedAtDesc(owner);
    }
    public CompanyProfileAsset own(User owner, Long id) {
        return repository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài liệu của doanh nghiệp."));
    }
    public CompanyProfileAsset store(User owner, CompanyProfileAsset.Kind kind, MultipartFile file) throws IOException {
        if (owner == null || owner.getRole() != Role.CUSTOMER)
            throw new IllegalArgumentException("Chỉ doanh nghiệp được quản lý hồ sơ công ty.");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Vui lòng chọn tệp.");
        long max = kind == CompanyProfileAsset.Kind.DOCUMENT ? 10_000_000L : 5_000_000L;
        if (file.getSize() > max) throw new IllegalArgumentException("Tệp vượt dung lượng cho phép.");
        byte[] raw = file.getBytes();
        String contentType;
        String ext;
        if (kind == CompanyProfileAsset.Kind.DOCUMENT) {
            if (!(raw.length > 5 && raw[0] == '%' && raw[1] == 'P' && raw[2] == 'D' && raw[3] == 'F' && raw[4] == '-')) {
                throw new IllegalArgumentException("Tài liệu pháp lý phải là tệp PDF hợp lệ.");
            }
            contentType = "application/pdf"; ext = "pdf";
        } else if (raw.length >= 8 && (raw[0] & 255) == 137 && raw[1]=='P' && raw[2]=='N' && raw[3]=='G') {
            contentType = "image/png"; ext = "png";
        } else if (raw.length >= 3 && (raw[0] & 255)==255 && (raw[1] & 255)==216 && (raw[2] & 255)==255) {
            contentType = "image/jpeg"; ext = "jpg";
        } else {
            throw new IllegalArgumentException("Hình ảnh phải là tệp PNG hoặc JPEG hợp lệ.");
        }
        if (kind == CompanyProfileAsset.Kind.PHOTO && repository.countByOwnerAndKind(owner, kind) >= 8) {
            throw new IllegalArgumentException("Chỉ được lưu tối đa 8 hình ảnh công ty.");
        }
        String supplied = Objects.toString(file.getOriginalFilename(), "tai-lieu").replace('\\', '/');
        String name = supplied.substring(supplied.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank()) name = "tai-lieu." + ext;
        if (name.length() > 160) name = name.substring(0, 160);
        String key = UUID.randomUUID() + "." + ext;
        Path folder = storage.resolve(owner.getId().toString()).normalize();
        Files.createDirectories(folder);
        Path target = folder.resolve(key).normalize();
        if (!target.getParent().equals(folder)) throw new IOException("Đường dẫn lưu tệp không hợp lệ.");
        Files.write(target, raw, StandardOpenOption.CREATE_NEW);
        try {
            CompanyProfileAsset asset = new CompanyProfileAsset();
            asset.setOwner(owner); asset.setKind(kind); asset.setFileKey(key);
            asset.setDisplayName(name); asset.setContentType(contentType); asset.setFileSize(raw.length);
            CompanyProfileAsset created = repository.save(asset);
            if (kind == CompanyProfileAsset.Kind.LOGO) {
                for (CompanyProfileAsset old : forOwner(owner)) {
                    if (old.getKind() == CompanyProfileAsset.Kind.LOGO && !old.getId().equals(created.getId())) remove(owner, old.getId());
                }
            }
            return created;
        } catch (RuntimeException error) {
            Files.deleteIfExists(target);
            throw error;
        }
    }
    public Path path(User owner, CompanyProfileAsset asset) {
        String key = asset.getFileKey();
        if (!key.matches("[0-9a-fA-F-]{36}\\.(jpg|png|pdf)"))
            throw new IllegalArgumentException("Tên tệp không hợp lệ.");
        Path folder = storage.resolve(owner.getId().toString()).normalize();
        Path target = folder.resolve(key).normalize();
        if (!target.getParent().equals(folder)) throw new IllegalArgumentException("Đường dẫn không hợp lệ.");
        return target;
    }
    @Transactional
    public void remove(User owner, Long assetId) {
        CompanyProfileAsset asset = own(owner, assetId);
        Path target = path(owner, asset);
        repository.delete(asset);
        try { Files.deleteIfExists(target); } catch (IOException e) { /* Keep DB consistent; cleanup can run later. */ }
    }
}
