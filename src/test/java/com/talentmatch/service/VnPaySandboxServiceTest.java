package com.talentmatch.service;
import com.talentmatch.model.Payment;
import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VnPaySandboxServiceTest {
    private final VnPaySandboxService gateway=new VnPaySandboxService(
        "DEMOV210","sample-test-secret-not-a-real-merchant-key",
        "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
        "https://example.test/payment/vnpay/return");
    @Test void signingRoundtripAndTampering(){
        Payment p=new Payment();p.setTransactionCode("TMTEST20261008");p.setAmount(new BigDecimal("200000"));
        String url=gateway.createPaymentUrl(p,"127.0.0.1");
        assertTrue(url.startsWith("https://sandbox.vnpayment.vn/"));
        var params=new HashMap<String,String>();
        for(String part:url.substring(url.indexOf('?')+1).split("&")){
            String[] a=part.split("=",2);
            params.put(URLDecoder.decode(a[0],StandardCharsets.UTF_8),URLDecoder.decode(a[1],StandardCharsets.UTF_8));
        }
        assertTrue(gateway.verify(params));
        assertTrue(gateway.amountMatches(p,params.get("vnp_Amount")));
        params.put("vnp_Amount","99990000");
        assertFalse(gateway.verify(params));
        assertFalse(gateway.amountMatches(p,params.get("vnp_Amount")));
    }

    @Test void paymentUrlUsesVnPaySortedEncodedHmacInput() throws Exception {
        Payment payment=new Payment();
        payment.setTransactionCode("TM-TEST-01");
        payment.setAmount(new BigDecimal("200000"));
        String url=gateway.createPaymentUrl(payment,"127.0.0.1");
        Map<String,String> params=new TreeMap<>();
        for(String part:url.substring(url.indexOf('?')+1).split("&")){
            String[] pair=part.split("=",2);
            params.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
        }

        StringJoiner signedFields=new StringJoiner("&");
        params.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("vnp_SecureHash"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> signedFields.add(entry.getKey()+"="
                        +URLEncoder.encode(entry.getValue(),StandardCharsets.UTF_8)));
        Mac mac=Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("sample-test-secret-not-a-real-merchant-key"
                .getBytes(StandardCharsets.UTF_8),"HmacSHA512"));
        String expected=HexFormat.of().formatHex(mac.doFinal(
                signedFields.toString().getBytes(StandardCharsets.UTF_8)));

        assertEquals(expected,params.get("vnp_SecureHash"));
        assertTrue(gateway.verify(params));
    }

    @Test void missingMerchantConfigurationDisablesGateway(){
        var off=new VnPaySandboxService("","", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html","https://example.test/return");
        assertFalse(off.configured());
    }
}
