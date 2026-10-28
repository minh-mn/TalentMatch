package com.talentmatch.controller;

import com.talentmatch.model.*;
import com.talentmatch.repository.UserRepository;
import com.talentmatch.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Controller
@RequestMapping("/freelancer/profile")
public class FreelancerProfileMediaController {
 private final UserService users;
 private final UserRepository repository;
 private final Path storage;
 public FreelancerProfileMediaController(UserService users, UserRepository repository,
        @Value("${talentmatch.profile.storage:uploads/freelancer-profile}") String root) {
   this.users=users;this.repository=repository;this.storage=Paths.get(root).toAbsolutePath().normalize();
 }
 @PostMapping("/upload")
 public String upload(Authentication auth,@RequestParam("type") String type,@RequestParam("file") MultipartFile file,RedirectAttributes ra){
   try {
     User u=users.byEmail(auth.getName());
     if (u.getRole()!=Role.FREELANCER) throw new IllegalArgumentException("Chỉ Freelancer được sửa hồ sơ.");
     if (!Set.of("avatar","cover","cv").contains(type)) throw new IllegalArgumentException("Loại tài liệu không hợp lệ.");
     long limit="cv".equals(type)?10*1024*1024:5*1024*1024;
     if(file==null||file.isEmpty()||file.getSize()>limit) throw new IllegalArgumentException("File trống hoặc quá dung lượng cho phép.");
     byte[] bytes=file.getBytes();
     boolean pdf=bytes.length>=5 && bytes[0]=='%' && bytes[1]=='P' && bytes[2]=='D' && bytes[3]=='F' && bytes[4]=='-';
     boolean png=bytes.length>=8 && (bytes[0]&255)==137 && bytes[1]=='P' && bytes[2]=='N' && bytes[3]=='G';
     boolean jpg=bytes.length>=3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255;
     if("cv".equals(type)?!pdf:!(png||jpg)) throw new IllegalArgumentException("Chỉ nhận ảnh PNG/JPEG hoặc CV PDF tương ứng.");
     String extension="cv".equals(type)?"pdf":(png?"png":"jpg");
     Path dir=storage.resolve(String.valueOf(u.getId())).resolve(type);Files.createDirectories(dir);
     String fileName=UUID.randomUUID()+"."+extension;
     Files.write(dir.resolve(fileName),bytes,StandardOpenOption.CREATE_NEW);
     if("cv".equals(type))u.setCvFile(fileName);
     else if("avatar".equals(type))u.setAvatar("/freelancer/profile/media/avatar");
     else u.setProfileCover(fileName);
     // Avatar file needs persistent key; use separate filename field by deriving via profileCover? Store avatar media filename in dedicated field.
     if("avatar".equals(type))u.setProfileAvatarFile(fileName);
     repository.save(u);
     ra.addFlashAttribute("success","Đã tải lên " + ("cv".equals(type)?"CV":("cover".equals(type)?"ảnh bìa":"ảnh đại diện")) + " thành công.");
   } catch(Exception e){ra.addFlashAttribute("error",e.getMessage());}
   return "redirect:/freelancer/profile";
 }
 @GetMapping("/media/{type}")
 @ResponseBody
 public ResponseEntity<Resource> read(Authentication auth,@PathVariable String type){
   User u=users.byEmail(auth.getName());
   String file="cv".equals(type)?u.getCvFile():("cover".equals(type)?u.getProfileCover():("avatar".equals(type)?u.getProfileAvatarFile():null));
   if(file==null||!file.matches("[a-f0-9\\-]{36}\\.(png|jpg|pdf)"))return ResponseEntity.notFound().build();
   try {
     Path path=storage.resolve(String.valueOf(u.getId())).resolve(type).resolve(file).normalize();
     if(!path.startsWith(storage))return ResponseEntity.notFound().build();
     Resource data=new UrlResource(path.toUri());if(!data.exists())return ResponseEntity.notFound().build();
     String contentType=file.endsWith("pdf")?"application/pdf":(file.endsWith("png")?"image/png":"image/jpeg");
     return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
       .header(HttpHeaders.CONTENT_DISPOSITION,("cv".equals(type)?"attachment":"inline")+"; filename=\""+file+"\"")
       .body(data);
   }catch(Exception e){return ResponseEntity.notFound().build();}
 }
}
