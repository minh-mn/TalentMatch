package com.talentmatch.controller;
import com.talentmatch.model.*;
import com.talentmatch.service.*;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
@Controller @RequestMapping("/projects")
public class DeliveryController {
    private final DeliveryService delivery;private final ProjectService projects;private final UserService users;
    public DeliveryController(DeliveryService delivery,ProjectService projects,UserService users){this.delivery=delivery;this.projects=projects;this.users=users;}
    @PostMapping("/{projectId}/deliveries")
    public String submit(@PathVariable Long projectId,@RequestParam(required=false) Long milestoneId,
                         @RequestParam(required=false) String externalUrl,@RequestParam(required=false) String note,
                         @RequestParam(required=false,name="file") MultipartFile file,Authentication auth,RedirectAttributes ra){
        try{delivery.submit(projects.get(projectId),milestoneId,users.byEmail(auth.getName()),externalUrl,note,file);
            ra.addFlashAttribute("success","Đã bàn giao sản phẩm. Client có thể xem và nghiệm thu.");}
        catch(Exception ex){ra.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/projects/"+projectId;
    }
    @GetMapping("/deliveries/{deliveryId}/download")
    @ResponseBody public ResponseEntity<UrlResource> download(@PathVariable Long deliveryId,Authentication auth)throws java.io.IOException{
        ProjectDelivery d=delivery.getOwnedFile(deliveryId,users.byEmail(auth.getName()));
        Path file=delivery.file(d);
        if(!Files.isRegularFile(file))return ResponseEntity.notFound().build();
        UrlResource resource=new UrlResource(file.toUri());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(Files.size(file))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.getFileName(),StandardCharsets.UTF_8).build().toString())
            .body(resource);
    }
}
