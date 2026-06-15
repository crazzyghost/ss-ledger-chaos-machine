package com.softspark.chaos.account.service;

import com.softspark.chaos.account.dto.CreateVirtualAccountRequest;
import com.softspark.chaos.account.dto.VirtualAccountResponse;
import com.softspark.chaos.account.enumeration.AccountOwnershipType;
import com.softspark.chaos.account.enumeration.AccountStatus;
import com.softspark.chaos.account.enumeration.Channel;
import com.softspark.chaos.account.enumeration.CreatedVia;
import com.softspark.chaos.account.enumeration.OrganizationStatus;
import com.softspark.chaos.account.model.Organization;
import com.softspark.chaos.account.model.VirtualAccount;
import com.softspark.chaos.account.repository.OrganizationRepository;
import com.softspark.chaos.account.repository.VirtualAccountRepository;
import com.softspark.chaos.base.Ids;
import com.softspark.chaos.base.PageResponse;
import com.softspark.chaos.exception.BadRequestException;
import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing virtual accounts.
 * <p>
 * Provides operations for creating and listing virtual accounts, including organization linking
 * and optional Kafka announcement.
 */
@Service
public class VirtualAccountService {

    private static final Logger log = LoggerFactory.getLogger(VirtualAccountService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final VirtualAccountRepository virtualAccountRepository;
    private final OrganizationRepository organizationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public VirtualAccountService(
            VirtualAccountRepository virtualAccountRepository,
            OrganizationRepository organizationRepository,
            ApplicationEventPublisher eventPublisher) {
        this.virtualAccountRepository = virtualAccountRepository;
        this.organizationRepository = organizationRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a new virtual account.
     *
     * @param request the creation request
     * @return the created virtual account
     * @throws BadRequestException if validation fails
     * @throws ConflictException   if the VA ID already exists
     */
    @Transactional
    public VirtualAccountResponse createVirtualAccount(CreateVirtualAccountRequest request) {
        log.info("Creating virtual account: {}", request.name());

        // Parse ownership type
        AccountOwnershipType ownershipType;
        try {
            ownershipType = AccountOwnershipType.valueOf(request.ownershipType());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid ownership type: " + request.ownershipType(), null);
        }

        // Generate or validate VA ID
        String vaId = request.vaId();
        if (vaId == null || vaId.isBlank()) {
            vaId = Ids.generate();
        } else {
            if (virtualAccountRepository.existsById(vaId)) {
                throw new ConflictException("Virtual account already exists: " + vaId);
            }
        }

        // Validate organization requirements
        String organizationId = request.organizationId();
        if (ownershipType == AccountOwnershipType.ORGANIZATION) {
            if (organizationId == null || organizationId.isBlank()) {
                throw new BadRequestException(
                        "Organization ID is required for ORGANIZATION ownership type",
                        null);
            }

            // Create or link organization
            if (!organizationRepository.existsById(organizationId)) {
                var organization = new Organization();
                organization.setOrganizationId(organizationId);
                organization.setName(request.organizationName() != null && !request.organizationName().isBlank()
                        ? request.organizationName()
                        : "Organization " + organizationId);
                organization.setStatus(OrganizationStatus.ACTIVE);
                organizationRepository.save(organization);
                log.info("Created new organization: {}", organizationId);
            }
        }

        // Parse optional enums
        Channel channel = null;
        if (request.channel() != null && !request.channel().isBlank()) {
            try {
                channel = Channel.valueOf(request.channel());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid channel: " + request.channel(), null);
            }
        }

        AccountStatus status = AccountStatus.ACTIVE;
        if (request.status() != null && !request.status().isBlank()) {
            try {
                status = AccountStatus.valueOf(request.status());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status: " + request.status(), null);
            }
        }

        // Create virtual account
        var va = new VirtualAccount();
        va.setVaId(vaId);
        va.setName(request.name());
        va.setOwnershipType(ownershipType);
        va.setOrganizationId(organizationId);
        va.setCurrency(request.currency());
        va.setStatus(status);
        va.setChannel(channel);
        va.setAccountRole(null); // Set by bootstrap for SYSTEM accounts
        va.setCreatedVia(CreatedVia.API);
        var saved = virtualAccountRepository.save(va);

        log.info("Created virtual account: {} ({})", saved.getVaId(), saved.getName());

        // Publish announcement event if requested
        if (Boolean.TRUE.equals(request.announce())) {
            eventPublisher.publishEvent(new VirtualAccountCreatedEvent(saved));
            log.info("Published VA creation event for: {}", saved.getVaId());
        }

        return mapToResponse(saved);
    }

    /**
     * Retrieves a virtual account by ID.
     *
     * @param vaId the virtual account ID
     * @return the virtual account
     * @throws NotFoundException if the virtual account is not found
     */
    @Transactional(readOnly = true)
    public VirtualAccountResponse getVirtualAccount(String vaId) {
        log.debug("Fetching virtual account: {}", vaId);
        var va = virtualAccountRepository.findById(vaId)
                .orElseThrow(() -> new NotFoundException("Virtual account not found: " + vaId));
        return mapToResponse(va);
    }

    /**
     * Lists virtual accounts with pagination and optional filtering.
     *
     * @param page           the page number (0-indexed)
     * @param perPage        the number of items per page
     * @param ownershipType  optional ownership type filter
     * @param organizationId optional organization ID filter
     * @param currency       optional currency filter
     * @param status         optional status filter
     * @param search         optional search term for name/ID
     * @return a paginated list of virtual accounts
     */
    @Transactional(readOnly = true)
    public PageResponse<VirtualAccountResponse> listVirtualAccounts(
            Integer page,
            Integer perPage,
            String ownershipType,
            String organizationId,
            String currency,
            String status,
            String search) {
        log.debug("Listing virtual accounts");

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = perPage != null && perPage > 0
                ? Math.min(perPage, MAX_PAGE_SIZE)
                : DEFAULT_PAGE_SIZE;
        Pageable pageable = PageRequest.of(pageNum, pageSize);

        Page<VirtualAccount> vaPage;

        // Apply filters
        if (search != null && !search.isBlank()) {
            vaPage = virtualAccountRepository.searchByNameOrId(search, pageable);
        } else if (ownershipType != null && !ownershipType.isBlank()) {
            try {
                var ownership = AccountOwnershipType.valueOf(ownershipType);
                vaPage = virtualAccountRepository.findByOwnershipType(ownership, pageable);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid ownership type: " + ownershipType, null);
            }
        } else if (organizationId != null && !organizationId.isBlank()) {
            vaPage = virtualAccountRepository.findByOrganizationId(organizationId, pageable);
        } else if (currency != null && !currency.isBlank()) {
            vaPage = virtualAccountRepository.findByCurrency(currency, pageable);
        } else if (status != null && !status.isBlank()) {
            try {
                var accountStatus = AccountStatus.valueOf(status);
                vaPage = virtualAccountRepository.findByStatus(accountStatus, pageable);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status: " + status, null);
            }
        } else {
            vaPage = virtualAccountRepository.findAll(pageable);
        }

        var items = vaPage.getContent().stream()
                .map(this::mapToResponse)
                .toList();

        return new PageResponse<>(items, pageNum, pageSize, vaPage.getTotalElements());
    }

    private VirtualAccountResponse mapToResponse(VirtualAccount va) {
        return new VirtualAccountResponse(
                va.getVaId(),
                va.getName(),
                va.getOwnershipType(),
                va.getOrganizationId(),
                va.getCurrency(),
                va.getStatus(),
                va.getChannel(),
                va.getAccountRole(),
                va.getCreatedVia(),
                va.getCreatedAt(),
                va.getUpdatedAt()
        );
    }

    /**
     * Event published after a virtual account is created with announce=true.
     *
     * @param virtualAccount the created virtual account
     */
    public record VirtualAccountCreatedEvent(VirtualAccount virtualAccount) {
    }
}
