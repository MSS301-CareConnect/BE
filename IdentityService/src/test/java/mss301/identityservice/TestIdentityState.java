package mss301.identityservice;
import mss301.identityservice.config.RedisKeys;
import mss301.identityservice.service.IdentityStateService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import java.time.*;
import java.util.*;
@TestConfiguration
public class TestIdentityState {
    @Bean @Primary
    @ConditionalOnProperty(name="identity.testing.real-state", havingValue="false", matchIfMissing=true)
    IdentityStateService memoryState() { return new Memory(); }
    static class Memory implements IdentityStateService {
        final Map<String,UUID> refresh=new HashMap<>();
        final Map<String,Instant> blacklist=new HashMap<>();
        final Map<String,Map<String,String>> otp=new HashMap<>();
        final Set<String> cooldown=new HashSet<>();
        String key(String purpose,String email) { return purpose+":"+email; }
        public synchronized void storeRefresh(String jti,UUID id,Duration ttl) { refresh.put(jti,id); }
        public synchronized boolean consumeRefresh(String jti,UUID id) { return id.equals(refresh.remove(jti)); }
        public synchronized void blacklist(String jti,Duration ttl) { blacklist.put(jti,Instant.now().plus(ttl)); }
        public synchronized boolean isBlacklisted(String jti) { return blacklist.containsKey(jti)&&blacklist.get(jti).isAfter(Instant.now()); }
        public synchronized boolean beginOtp(String purpose,String email,Map<String,String> data) {
            String key=key(purpose,email); if(!cooldown.add(key))return false; otp.put(key,new HashMap<>(data)); return true;
        }
        public synchronized Map<String,String> getOtp(String purpose,String email) { return new HashMap<>(otp.getOrDefault(key(purpose,email),Map.of())); }
        public synchronized boolean consumeOtp(String purpose,String email,String hash) {
            var data=otp.get(key(purpose,email)); if(data==null||!hash.equals(data.get(RedisKeys.OTP_HASH_FIELD)))return false;
            otp.remove(key(purpose,email)); return true;
        }
        public synchronized int failOtp(String purpose,String email,String hash) {
            var data=otp.get(key(purpose,email)); if(data==null||!hash.equals(data.get(RedisKeys.OTP_HASH_FIELD)))return -1;
            int n=Integer.parseInt(data.get(RedisKeys.OTP_ATTEMPTS_FIELD))+1; data.put(RedisKeys.OTP_ATTEMPTS_FIELD,Integer.toString(n)); if(n>=5)data.remove(RedisKeys.OTP_HASH_FIELD); return n;
        }
        public synchronized void clearOtp(String purpose,String email) { otp.remove(key(purpose,email)); cooldown.remove(key(purpose,email)); }
    }
}
