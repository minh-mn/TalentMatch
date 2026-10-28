package com.talentmatch.service;
import com.talentmatch.model.*;
import com.talentmatch.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service
public class NotificationService {
    private final NotificationRepository repository;
    public NotificationService(NotificationRepository repository){this.repository=repository;}
    public void send(User recipient,String title,String message,String url){
        if(recipient==null)return;
        Notification n=new Notification();n.setRecipient(recipient);
        n.setTitle(title.substring(0,Math.min(180,title.length())));
        n.setMessage(message.substring(0,Math.min(1000,message.length())));
        n.setTargetUrl(url!=null&&url.startsWith("/")&&!url.startsWith("//")?url:"/notifications");
        repository.save(n);
    }
    public List<Notification> inbox(User user){return repository.findTop100ByRecipientOrderByCreatedAtDesc(user);}
    public long unread(User user){return repository.countByRecipientAndReadFalse(user);}
    @Transactional public String read(Long id,User user){
        Notification n=repository.findById(id).orElseThrow(()->new IllegalArgumentException("Không tìm thấy thông báo."));
        if(!n.getRecipient().getId().equals(user.getId()))throw new IllegalArgumentException("Bạn không sở hữu thông báo.");
        n.setRead(true);repository.save(n);return n.getTargetUrl();
    }
}
