package com.mett.hdr.identity.provider;

public interface IdentityProvider {

    UserIdentity authenticate(Credential credential);
}
