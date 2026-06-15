package com.softspark.chaos.account.service;

import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.model.Organization;
import com.softspark.chaos.account.model.VirtualAccount;
import com.softspark.chaos.account.repository.OrganizationRepository;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.base.Ids;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.flow.model.v1.OrganizationOnboardedEventData;
import com.softspark.chaos.flow.model.v1.OrganizationVaUpdatedEventData;
import com.softspark.chaos.kafka.ChaosEventPublisher;
import com.softspark.chaos.kafka.EventEnvelope;
import com.softspark.chaos.kafka.EventEnvelopeBuilder;
import com.softspark.chaos.kafka.EventMetadata;
import com.softspark.chaos.kafka.EventMetadataBuilder;
import com.softspark.chaos.kafka.TopicCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

/**
 * Service for announcing virtual account events to Kafka.
 * <p>
 * Publishes organization.onboarded and organization.va.updated events after successful
 * transaction commits to ensure consistency between the local registry and the ledger.
 */
@Service
public class VirtualAccountAnnouncer {

    private static final Logger log = LoggerFactory.getLogger(VirtualAccountAnnouncer.class);
    private static final String SOURCE = "organization-service";
    private static final String VERSION = "1.0";

    private final ChaosEventPublisher eventPublisher;
    private final TopicCatalog topicCatalog;
    private final VirtualAccountRepository virtualAccountRepository;
    private final OrganizationRepository organizationRepository;

    public VirtualAccountAnnouncer(
            ChaosEventPublisher eventPublisher,
            TopicCatalog topicCatalog,
            VirtualAccountRepository virtualAccountRepository,
            OrganizationRepository organizationRepository) {
        this.eventPublisher = eventPublisher;
        this.topicCatalog = topicCatalog;
        this.virtualAccountRepository = virtualAccountRepository;
        this.organizationRepository = organizationRepository;
    }

    /**
     * Listens for virtual account creation events and publishes to Kafka after commit.
     * <p>
     * This method is invoked automatically when a {@link VirtualAccountService.VirtualAccountCreatedEvent}
     * is published, but only after the transaction commits successfully.
     *
     * @param event the virtual account created event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVirtualAccountCreated(VirtualAccountService.VirtualAccountCreatedEvent event) {
        var va = event.virtualAccount();
        log.info("Publishing virtual account announcement for: {}", va.getVaId());
        announceVirtualAccount(va.getVaId());
    }

    /**
     * Announces a virtual account to Kafka.
     * <p>
     * Publishes organization.onboarded if the organization is new, and always publishes
     * organization.va.updated for the virtual account.
     *
     * @param vaId the virtual account ID to announce
     * @throws NotFoundException if the virtual account is not found
     */
    public void announceVirtualAccount(String vaId) {
        log.info("Announcing virtual account: {}", vaId);

        var va = virtualAccountRepository.findById(vaId)
                .orElseThrow(() -> new NotFoundException("Virtual account not found: " + vaId));

        // Only announce organization virtual accounts
        if (va.getOwnershipType() == AccountOwnershipType.ORGANIZATION) {
            if (va.getOrganizationId() == null || va.getOrganizationId().isBlank()) {
                log.warn("Cannot announce ORGANIZATION VA {} without organization ID", vaId);
                return;
            }

            var organization = organizationRepository.findById(va.getOrganizationId())
                    .orElse(null);

            if (organization != null) {
                // Check if this is a new organization (created recently)
                // For simplicity, we'll publish onboarded event for all organizations
                // In production, you might track whether onboarded was already sent
                publishOrganizationOnboarded(organization);
            }

            // Always publish VA updated event
            publishVaUpdated(va);
        } else {
            log.debug("Skipping announcement for SYSTEM VA: {}", vaId);
        }
    }

    private void publishOrganizationOnboarded(Organization organization) {
        String eventId = Ids.generate();
        String idempotencyKey = "organization-onboarded:" + organization.getOrganizationId();

        var eventData = new OrganizationOnboardedEventData(
                organization.getOrganizationId(),
                organization.getName(),
                new OrganizationOnboardedEventData.OrganizationType(
                        organization.getTypeName() != null ? organization.getTypeName() : "MERCHANT",
                        organization.getTypeName() != null ? organization.getTypeName() : "Merchant"
                ),
                new OrganizationOnboardedEventData.Country(
                        organization.getCountryIsoCode() != null ? organization.getCountryIsoCode() : "GHA",
                        organization.getCountryName() != null ? organization.getCountryName() : "Ghana",
                        organization.getCountryIsoCode() != null ? organization.getCountryIsoCode() : "GHA"
                ),
                null, // primary contact email not available
                List.of(), // phone numbers not available
                organization.getStatus().name()
        );

        var metadata = EventMetadataBuilder.builder()
                .correlationId(eventId)
                .idempotencyKey(idempotencyKey)
                .tenantId(organization.getOrganizationId())
                .build();

        var envelope = EventEnvelopeBuilder.builder()
                .eventId(eventId)
                .eventType(topicCatalog.getOrganizationOnboarded())
                .timestamp(Instant.now())
                .source(SOURCE)
                .version(VERSION)
                .data(eventData)
                .metadata(metadata)
                .build();

        try {
            var result = eventPublisher.publish(
                    topicCatalog.getOrganizationOnboarded(),
                    organization.getOrganizationId(),
                    envelope
            );
            log.info("Published organization.onboarded event for org {} at offset {}",
                    organization.getOrganizationId(), result.offset());
        } catch (Exception e) {
            log.error("Failed to publish organization.onboarded event for org {}: {}",
                    organization.getOrganizationId(), e.getMessage(), e);
            // Don't throw - the VA is already persisted, and we can retry later
        }
    }

    private void publishVaUpdated(VirtualAccount va) {
        String eventId = Ids.generate();
        String idempotencyKey = "organization-va-updated:" + va.getVaId();

        var eventData = new OrganizationVaUpdatedEventData(
                va.getVaId(),
                va.getStatus().name(),
                new OrganizationVaUpdatedEventData.CurrencyInfo(va.getCurrency()),
                new OrganizationVaUpdatedEventData.AccountType(
                        va.getChannel() != null ? va.getChannel().name() : "MOBILE_MONEY"
                )
        );

        var metadata = EventMetadataBuilder.builder()
                .correlationId(eventId)
                .idempotencyKey(idempotencyKey)
                .tenantId(va.getOrganizationId())
                .build();

        var envelope = EventEnvelopeBuilder.builder()
                .eventId(eventId)
                .eventType(topicCatalog.getOrganizationVaUpdated())
                .timestamp(Instant.now())
                .source(SOURCE)
                .version(VERSION)
                .data(eventData)
                .metadata(metadata)
                .build();

        try {
            var result = eventPublisher.publish(
                    topicCatalog.getOrganizationVaUpdated(),
                    va.getVaId(),
                    envelope
            );
            log.info("Published organization.va.updated event for VA {} at offset {}",
                    va.getVaId(), result.offset());
        } catch (Exception e) {
            log.error("Failed to publish organization.va.updated event for VA {}: {}",
                    va.getVaId(), e.getMessage(), e);
            // Don't throw - the VA is already persisted, and we can retry later
        }
    }
}
