package com.mett.hdr.payment.service;

import com.mett.hdr.common.exception.BadRequestException;
import com.mett.hdr.common.exception.NotFoundException;
import com.mett.hdr.membership.entity.MembershipPlan;
import com.mett.hdr.membership.repository.MembershipPlanRepository;
import com.mett.hdr.payment.dto.MembershipOfferResponse;
import com.mett.hdr.payment.entity.MembershipPlanOffer;
import com.mett.hdr.payment.repository.MembershipPlanOfferRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MembershipOfferQueryService {

    private final MembershipPlanOfferRepository offerRepository;
    private final MembershipPlanRepository planRepository;

    public MembershipOfferQueryService(
            MembershipPlanOfferRepository offerRepository,
            MembershipPlanRepository planRepository
    ) {
        this.offerRepository = offerRepository;
        this.planRepository = planRepository;
    }

    public List<MembershipOfferResponse> available(String audienceType) {
        String audience = normalizeAudience(audienceType);
        return offerRepository.findAvailable(now(), audience).stream()
                .map(this::toResponse)
                .toList();
    }

    public MembershipPlanOffer requireAvailable(String globalOfferId) {
        if (globalOfferId == null || globalOfferId.isBlank()) {
            throw new BadRequestException("Global offer ID is required.");
        }
        MembershipPlanOffer offer = offerRepository.findByGlobalId(globalOfferId)
                .filter(value -> value.availableAt(now()))
                .orElseThrow(() -> new NotFoundException("Membership offer not found."));
        MembershipPlan plan = planRepository.findById(offer.membershipPlanId())
                .filter(value -> "ACTIVE".equals(value.status()))
                .orElseThrow(() -> new NotFoundException("Membership offer not found."));
        validatePurchasable(offer, plan);
        return offer;
    }

    public MembershipOfferResponse toResponse(MembershipPlanOffer offer) {
        MembershipPlan plan = planRepository.findById(offer.membershipPlanId()).orElseThrow();
        validatePurchasable(offer, plan);
        return new MembershipOfferResponse(
                offer.globalOfferId(),
                offer.offerCode(),
                offer.version(),
                plan.planCode(),
                plan.version(),
                plan.tier(),
                plan.audienceType(),
                offer.amountMinor(),
                offer.currency(),
                offer.membershipDurationMonths()
        );
    }

    private void validatePurchasable(
            MembershipPlanOffer offer,
            MembershipPlan plan
    ) {
        if (!Set.of("PLUS", "PRO").contains(plan.tier())) {
            throw new BadRequestException("Offer plan is not purchasable.");
        }
        if (offer.amountMinor() <= 0) {
            throw new BadRequestException("Active offer amount must be positive.");
        }
        if (!offer.currency().matches("[A-Z]{3}")) {
            throw new BadRequestException("Offer currency is invalid.");
        }
    }

    private String normalizeAudience(String audienceType) {
        if (audienceType == null || audienceType.isBlank()) {
            return null;
        }
        String value = audienceType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PERSONAL", "TEAM").contains(value)) {
            throw new BadRequestException("Invalid offer audience type.");
        }
        return value;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.ofHours(8));
    }
}
