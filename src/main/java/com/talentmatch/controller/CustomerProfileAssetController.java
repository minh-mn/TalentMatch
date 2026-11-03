package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.service.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer/profile/assets")
public class CustomerProfileAssetController {
    private final UserService users;
    private final CompanyProfileAssetService service;
    public CustomerProfileAssetController(UserService users, CompanyProfileAssetService service) {
        this.users = users; this.service = service;
    }
    private User current(Authentication authentication) {
        User user = users.byEmail(authentication.getName());
        if (user.getRole() != Role.CUSTOMER) throw new IllegalArgumentException("Tài khoản không phải doanh nghiệp.");
        return user;
    }
    @PostMapping
    public String upload(Authentication auth, @RequestParam String kind, @RequestParam MultipartFile file, RedirectAttributes flash) {
        try {
            CompanyProfileAsset.Kind type = CompanyProfileAsset.Kind.valueOf(kind.toUpperCase(java.util.Locale.ROOT));
            service.store(current(auth), type, file);
            flash.addFlashAttribute("success", "Đã tải tệp lên hồ sơ công ty.");
        } catch (Exception error) { flash.addFlashAttribute("error", error.getMessage()); }
        return "redirect:/customer/profile";
    }
    @PostMapping("/{id}/delete")
    public String remove(@PathVariable Long id, Authentication auth, RedirectAttributes flash) {
        try { service.remove(current(auth), id); flash.addFlashAttribute("success", "Đã xóa tệp khỏi hồ sơ."); }
        catch (Exception error) { flash.addFlashAttribute("error", error.getMessage()); }
        return "redirect:/customer/profile";
    }
    @GetMapping("/{id}/file")
    @ResponseBody
    public ResponseEntity<Resource> file(@PathVariable Long id, Authentication auth) {
        try {
            User owner = current(auth);
            CompanyProfileAsset asset = service.own(owner, id);
            java.nio.file.Path path = service.path(owner, asset);
            Resource file = new UrlResource(path.toUri());
            if (!file.exists() || !file.isReadable()) return ResponseEntity.notFound().build();
            String disposition = asset.getKind() == CompanyProfileAsset.Kind.DOCUMENT ? "attachment" : "inline";
            String safeName = asset.getKind() == CompanyProfileAsset.Kind.DOCUMENT ? "tai-lieu-" + id + ".pdf" : "hinh-anh-" + id + "." + (asset.getContentType().equals("image/png") ? "png" : "jpg");
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + safeName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore()).body(file);
        } catch (Exception e) { return ResponseEntity.notFound().build(); }
    }
}
