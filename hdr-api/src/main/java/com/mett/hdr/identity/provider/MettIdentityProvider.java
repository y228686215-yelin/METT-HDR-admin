package com.mett.hdr.identity.provider;

import com.mett.hdr.common.exception.SystemException;
import org.springframework.stereotype.Component;

@Component
public class MettIdentityProvider implements IdentityProvider {

    @Override
    public UserIdentity authenticate(Credential credential) {
        throw new SystemException("METT identity provider is reserved for future SSO integration.");
    }
}
