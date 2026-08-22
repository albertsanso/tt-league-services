package org.cttelsamicsterrassa.data.api.rest.config.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {

    private final ConcurrentHashMap<String, Date> blacklist = new ConcurrentHashMap<>();

    public void blacklistToken(String token, Date expiry) {
        if (token == null || expiry == null) {
            return;
        }
        blacklist.put(token, expiry);
    }

    public boolean isBlacklisted(String token) {
        if (token == null) {
            return false;
        }
        Date expiry = blacklist.get(token);
        if (expiry == null) {
            return false;
        }
        if (expiry.before(new Date())) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        Date now = new Date();
        Iterator<Map.Entry<String, Date>> it = blacklist.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Date> entry = it.next();
            if (entry.getValue().before(now)) {
                it.remove();
            }
        }
    }
}

