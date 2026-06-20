package com.softspark.chaos.account.service;

import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.model.VirtualAccount;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.base.Ids;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.flow.model.v1.OrganizationOnboardedEventData;
import com.softspark.chaos.flow.model.v1.OrganizationVaUpdatedEventData;
import com.softspark.chaos.kafka.ChaosEventPublisher;
import com.softspark.chaos.kafka.EventEnvelopeBuilder;
import com.softspark.chaos.kafka.EventMetadataBuilder;
import com.softspark.chaos.kafka.TopicCatalog;
import com.softspark.chaos.organization.model.Organization;
import com.softspark.chaos.organization.repository.OrganizationRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Service for announcing virtual account events to Kafka.
 *
 * <p>Publishes {@code organization.onboarded} and {@code organization.va.updated} events after
 * successful transaction commits to ensure consistency between the local registry and the ledger.
 *
 * <p>The {@code metadata.tenant_id} field defaults to the value of {@code chaos.default-tenant-id}
 * (configurable via environment variable {@code CHAOS_DEFAULT_TENANT_ID}).
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
  private final String defaultTenantId;

  public VirtualAccountAnnouncer(
      ChaosEventPublisher eventPublisher,
      TopicCatalog topicCatalog,
      VirtualAccountRepository virtualAccountRepository,
      OrganizationRepository organizationRepository,
      @Value("${chaos.default-tenant-id:org_system}") String defaultTenantId) {
    this.eventPublisher = eventPublisher;
    this.topicCatalog = topicCatalog;
    this.virtualAccountRepository = virtualAccountRepository;
    this.organizationRepository = organizationRepository;
    this.defaultTenantId = defaultTenantId;
  }

  /**
   * Listens for virtual account creation events and publishes to Kafka after commit.
   *
   * <p>Only calls {@link #publishOrganizationOnboarded(Organization)} when the event signals that
   * the organization was newly created ({@code event.newOrganization() == true}). The VA-updated
   * event is always published regardless.
   *
   * @param event the virtual account created event
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onVirtualAccountCreated(VirtualAccountService.VirtualAccountCreatedEvent event) {
    var va = event.virtualAccount();
    log.info("Publishing virtual account announcement for: {}", va.getVaId());

    if (va.getOwnershipType() != AccountOwnershipType.ORGANIZATION) {
      log.debug("Skipping announcement for SYSTEM VA: {}", va.getVaId());
      return;
    }

    if (va.getOrganizationId() == null || va.getOrganizationId().isBlank()) {
      log.warn("Cannot announce ORGANIZATION VA {} without organization ID", va.getVaId());
      return;
    }

    if (event.newOrganization()) {
      var organization = organizationRepository.findById(va.getOrganizationId()).orElse(null);
      if (organization != null) {
        publishOrganizationOnboarded(organization);
      }
    }

    publishVaUpdated(va);
  }

  /**
   * Announces a virtual account to Kafka.
   *
   * <p>Always publishes both {@code organization.onboarded} and {@code organization.va.updated}
   * since this method is intended for manual re-announcements where history is not tracked.
   *
   * @param vaId the virtual account ID to announce
   * @throws NotFoundException if the virtual account is not found
   */
  public void announceVirtualAccount(String vaId) {
    log.info("Announcing virtual account: {}", vaId);

    var va =
        virtualAccountRepository
            .findById(vaId)
            .orElseThrow(() -> new NotFoundException("Virtual account not found: " + vaId));

    if (va.getOwnershipType() == AccountOwnershipType.ORGANIZATION) {
      if (va.getOrganizationId() == null || va.getOrganizationId().isBlank()) {
        log.warn("Cannot announce ORGANIZATION VA {} without organization ID", vaId);
        return;
      }

      var organization = organizationRepository.findById(va.getOrganizationId()).orElse(null);
      if (organization != null) {
        publishOrganizationOnboarded(organization);
      }

      publishVaUpdated(va);
    } else {
      log.debug("Skipping announcement for SYSTEM VA: {}", vaId);
    }
  }

  private void publishOrganizationOnboarded(Organization organization) {
    String eventId = Ids.generate();
    String idempotencyKey = "organization-onboarded:" + organization.getOrganizationId();

    var eventData =
        new OrganizationOnboardedEventData(
            organization.getOrganizationId(),
            organization.getName(),
            new OrganizationOnboardedEventData.OrganizationType(
                organization.getTypeName() != null ? organization.getTypeName() : "MERCHANT",
                organization.getTypeName() != null ? organization.getTypeName() : "Merchant"),
            new OrganizationOnboardedEventData.Country(
                organization.getCountryIsoCode() != null ? organization.getCountryIsoCode() : "GHA",
                organization.getCountryName() != null ? organization.getCountryName() : "Ghana",
                organization.getCountryIsoCode() != null ? organization.getCountryIsoCode() : "GHA",
                organization.getCountryStatus(),
                organization.getCountryModifiedDate()),
            organization.getPrimaryCurrencyId() != null || organization.getPrimaryCurrencyCode() != null
                ? new OrganizationOnboardedEventData.Currency(
                    organization.getPrimaryCurrencyId(), organization.getPrimaryCurrencyCode())
                : null,
            null,
            List.of(),
            organization.getStatus().name());

    var metadata =
        EventMetadataBuilder.builder()
            .correlationId(eventId)
            .idempotencyKey(idempotencyKey)
            .tenantId(defaultTenantId)
            .build();

    var envelope =
        EventEnvelopeBuilder.builder()
            .eventId(eventId)
            .eventType(topicCatalog.getOrganizationOnboarded())
            .timestamp(Instant.now())
            .source(SOURCE)
            .version(VERSION)
            .data(eventData)
            .metadata(metadata)
            .build();

    try {
      var result =
          eventPublisher.publish(
              topicCatalog.getOrganizationOnboarded(), organization.getOrganizationId(), envelope);
      log.info(
          "Published organization.onboarded event for org {} at offset {}",
          organization.getOrganizationId(),
          result.offset());
    } catch (Exception e) {
      log.error(
          "Failed to publish organization.onboarded event for org {}: {}",
          organization.getOrganizationId(),
          e.getMessage(),
          e);
    }
  }

  private void publishVaUpdated(VirtualAccount va) {
    String eventId = Ids.generate();
    String idempotencyKey = "organization-va-updated:" + va.getVaId();

    var eventData =
        new OrganizationVaUpdatedEventData(
            va.getVaId(),
            va.getStatus().name(),
            new OrganizationVaUpdatedEventData.CurrencyInfo(va.getCurrency()),
            new OrganizationVaUpdatedEventData.AccountType(
                va.getChannel() != null ? va.getChannel().name() : "MOBILE_MONEY"));

    var metadata =
        EventMetadataBuilder.builder()
            .correlationId(eventId)
            .idempotencyKey(idempotencyKey)
            .tenantId(defaultTenantId)
            .build();

    var envelope =
        EventEnvelopeBuilder.builder()
            .eventId(eventId)
            .eventType(topicCatalog.getOrganizationVaUpdated())
            .timestamp(Instant.now())
            .source(SOURCE)
            .version(VERSION)
            .data(eventData)
            .metadata(metadata)
            .build();

    try {
      var result =
          eventPublisher.publish(topicCatalog.getOrganizationVaUpdated(), va.getVaId(), envelope);
      log.info(
          "Published organization.va.updated event for VA {} at offset {}",
          va.getVaId(),
          result.offset());
    } catch (Exception e) {
      log.error(
          "Failed to publish organization.va.updated event for VA {}: {}",
          va.getVaId(),
          e.getMessage(),
          e);
    }
  }
}
