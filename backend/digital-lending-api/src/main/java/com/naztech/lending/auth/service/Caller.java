package com.naztech.lending.auth.service;

/** Who is asking, as far as the audit trail and device binding are concerned. */
public record Caller(String ipAddress, String userAgent, String deviceId) {

    public static Caller unknown() {
        return new Caller(null, null, null);
    }
}
