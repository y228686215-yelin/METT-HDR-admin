package com.mett.hdr.identity.verification;

public interface VerificationProvider {

    boolean verify(String target, String purpose, String verificationCode);
}
