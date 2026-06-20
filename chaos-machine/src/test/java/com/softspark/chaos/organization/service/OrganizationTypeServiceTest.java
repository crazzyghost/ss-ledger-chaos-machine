package com.softspark.chaos.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.softspark.chaos.exception.ConflictException;
import com.softspark.chaos.exception.NotFoundException;
import com.softspark.chaos.organization.dto.CreateOrganizationTypeRequest;
import com.softspark.chaos.organization.dto.UpdateOrganizationTypeRequest;
import com.softspark.chaos.organization.model.OrganizationType;
import com.softspark.chaos.organization.repository.OrganizationTypeRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link OrganizationTypeService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationTypeService")
class OrganizationTypeServiceTest {

  @Mock private OrganizationTypeRepository organizationTypeRepository;

  @InjectMocks private OrganizationTypeService service;

  // ── Helpers ────────────────────────────────────────────────────────────────

  private OrganizationType buildType(String id, String name) {
    var organizationType = new OrganizationType();
    organizationType.setOrganizationTypeId(id);
    organizationType.setName(name);
    return organizationType;
  }

  // ── createOrganizationType ───────────────────────────────────────────────────

  @Nested
  @DisplayName("createOrganizationType")
  class CreateOrganizationTypeTests {

    @Test
    @DisplayName("assigns a UUID id and persists the name")
    void createAssignsId() {
      var req = new CreateOrganizationTypeRequest("Business");
      when(organizationTypeRepository.findByNameIgnoreCase("Business"))
          .thenReturn(Optional.empty());
      when(organizationTypeRepository.save(any()))
          .thenAnswer(invocation -> invocation.getArgument(0));

      var response = service.createOrganizationType(req);

      ArgumentCaptor<OrganizationType> captor = ArgumentCaptor.forClass(OrganizationType.class);
      org.mockito.Mockito.verify(organizationTypeRepository).save(captor.capture());
      var persisted = captor.getValue();

      assertThat(persisted.getOrganizationTypeId()).isNotBlank();
      assertThat(java.util.UUID.fromString(persisted.getOrganizationTypeId())).isNotNull();
      assertThat(persisted.getName()).isEqualTo("Business");
      assertThat(response.name()).isEqualTo("Business");
      assertThat(response.organizationTypeId()).isEqualTo(persisted.getOrganizationTypeId());
    }

    @Test
    @DisplayName("duplicate name (case-insensitive) throws ConflictException")
    void duplicateNameThrowsConflict() {
      var req = new CreateOrganizationTypeRequest("business");
      when(organizationTypeRepository.findByNameIgnoreCase("business"))
          .thenReturn(Optional.of(buildType("ot-1", "Business")));

      assertThatThrownBy(() -> service.createOrganizationType(req))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining("business");
    }
  }

  // ── getOrganizationType ──────────────────────────────────────────────────────

  @Nested
  @DisplayName("getOrganizationType")
  class GetOrganizationTypeTests {

    @Test
    @DisplayName("throws NotFoundException when the type does not exist")
    void throwsNotFoundWhenMissing() {
      when(organizationTypeRepository.findById(anyString())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getOrganizationType("missing"))
          .isInstanceOf(NotFoundException.class)
          .hasMessageContaining("missing");
    }
  }

  // ── updateOrganizationType ────────────────────────────────────────────────────

  @Nested
  @DisplayName("updateOrganizationType")
  class UpdateOrganizationTypeTests {

    @Test
    @DisplayName("throws NotFoundException when the type does not exist")
    void throwsNotFoundWhenMissing() {
      when(organizationTypeRepository.findById(anyString())).thenReturn(Optional.empty());

      var req = new UpdateOrganizationTypeRequest("Business");
      assertThatThrownBy(() -> service.updateOrganizationType("missing", req))
          .isInstanceOf(NotFoundException.class)
          .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("renames the type successfully")
    void renamesType() {
      var existing = buildType("ot-1", "Business");
      when(organizationTypeRepository.findById("ot-1")).thenReturn(Optional.of(existing));
      when(organizationTypeRepository.findByNameIgnoreCase("Merchant"))
          .thenReturn(Optional.empty());
      when(organizationTypeRepository.save(any()))
          .thenAnswer(invocation -> invocation.getArgument(0));

      var req = new UpdateOrganizationTypeRequest("Merchant");
      var response = service.updateOrganizationType("ot-1", req);

      assertThat(response.name()).isEqualTo("Merchant");
    }

    @Test
    @DisplayName("renaming to a name owned by another type throws ConflictException")
    void conflictingNameThrows() {
      var existing = buildType("ot-1", "Business");
      var other = buildType("ot-2", "Merchant");
      when(organizationTypeRepository.findById("ot-1")).thenReturn(Optional.of(existing));
      when(organizationTypeRepository.findByNameIgnoreCase("Merchant"))
          .thenReturn(Optional.of(other));

      var req = new UpdateOrganizationTypeRequest("Merchant");
      assertThatThrownBy(() -> service.updateOrganizationType("ot-1", req))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining("Merchant");
    }

    @Test
    @DisplayName("keeping the same name (case-insensitive) does not trigger a uniqueness check")
    void sameNameNoConflict() {
      var existing = buildType("ot-1", "Business");
      when(organizationTypeRepository.findById("ot-1")).thenReturn(Optional.of(existing));
      when(organizationTypeRepository.save(any()))
          .thenAnswer(invocation -> invocation.getArgument(0));

      var req = new UpdateOrganizationTypeRequest("business");
      var response = service.updateOrganizationType("ot-1", req);

      assertThat(response.name()).isEqualTo("business");
      org.mockito.Mockito.verify(organizationTypeRepository, org.mockito.Mockito.never())
          .findByNameIgnoreCase(anyString());
    }
  }
}
