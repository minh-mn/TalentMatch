package com.talentmatch.service;
import com.talentmatch.model.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** VNPay payment URL and verified server-to-server IPN. Do not settle from browser return URL. */
@Service
public class VnPaySandboxService {
    private final String terminal,secret,baseUrl,returnUrl;
    public VnPaySandboxService(@Value("${vnpay.tmn-code:}") String terminal,
        @Value("${vnpay.hash-secret:}") String secret,
        @Value("${vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String baseUrl,
        @Value("${vnpay.return-url:http://localhost:8080/payment/vnpay/return}") String returnUrl){
        this.terminal=terminal.trim();this.secret=secret.trim();this.baseUrl=baseUrl;this.returnUrl=returnUrl;
    }
    public boolean configured(){return !terminal.isBlank()&&!secret.isBlank();}
    public String createPaymentUrl(Payment payment,String ip){
        if(!configured())throw new IllegalStateException("Chưa cấu hình VNPAY_TMN_CODE/VNPAY_HASH_SECRET.");
        String now=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String expiry=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusMinutes(15).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Map<String,String> p=new TreeMap<>();
        p.put("vnp_Version","2.1.0");p.put("vnp_Command","pay");p.put("vnp_TmnCode",terminal);
        p.put("vnp_Amount",payment.getAmount().longValueExact()*100L+"");
        p.put("vnp_CurrCode","VND");p.put("vnp_TxnRef",payment.getTransactionCode());
        p.put("vnp_OrderInfo","Thanh toan TalentMatch "+payment.getTransactionCode());
        p.put("vnp_OrderType","other");p.put("vnp_Locale","vn");
        p.put("vnp_ReturnUrl",returnUrl);p.put("vnp_IpAddr",ip==null?"127.0.0.1":ip);
        p.put("vnp_CreateDate",now);p.put("vnp_ExpireDate",expiry);
        String query=canonical(p);
        return baseUrl+"?"+query+"&vnp_SecureHash="+hmac(query);
    }
    private String encode(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    private String canonical(Map<String,String> fields){
        StringJoiner j=new StringJoiner("&");
        fields.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            if(e.getValue()!=null&&!e.getValue().isBlank()&&!e.getKey().equals("vnp_SecureHash")&&!e.getKey().equals("vnp_SecureHashType"))
                j.add(encode(e.getKey())+"="+encode(e.getValue()));
        });
        return j.toString();
    }
    private String hmac(String content){
        try{Mac mac=Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception e){throw new IllegalStateException("Lỗi chữ ký VNPay.",e);}
    }
    public boolean verify(Map<String,String> params){
        if(!configured()||!terminal.equals(params.get("vnp_TmnCode")))return false;
        String provided=params.get("vnp_SecureHash");
        if(provided==null||!provided.matches("[a-fA-F0-9]{128}"))return false;
        Map<String,String> cleaned=new TreeMap<>();
        params.forEach((key,value)->{if(key.startsWith("vnp_")&&!key.equals("vnp_SecureHash")&&!key.equals("vnp_SecureHashType"))cleaned.put(key,value);});
        return MessageDigest.isEqual(hmac(canonical(cleaned)).getBytes(StandardCharsets.US_ASCII),provided.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }
    public boolean amountMatches(Payment payment,String amount){
        try{return Long.parseLong(amount)==Math.multiplyExact(payment.getAmount().longValueExact(),100L);}
        catch(Exception e){return false;}
    }
}
