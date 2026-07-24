package com.mett.hdr.payment.provider;

import com.mett.hdr.common.exception.BadRequestException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.code().toUpperCase(Locale.ROOT),
                Function.identity()
        ));
    }

    public PaymentProvider require(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new BadRequestException("Payment provider is required.");
        }
        PaymentProvider provider = providers.get(providerCode.trim().toUpperCase(Locale.ROOT));
        if (provider == null) {
            throw new BadRequestException("Unsupported payment provider.");
        }
        return provider;
    }
}
