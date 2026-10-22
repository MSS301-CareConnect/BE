package mss301.carerecipientservice.mapper;

import mss301.carerecipientservice.client.dto.IdentityUserResponse;
import mss301.carerecipientservice.dto.request.CareRecipientCreateRequest;
import mss301.carerecipientservice.dto.request.CareRecipientUpdateRequest;
import mss301.carerecipientservice.dto.request.EmergencyContactRequest;
import mss301.carerecipientservice.dto.request.ManagerUpdateRequest;
import mss301.carerecipientservice.dto.request.MedicationRequest;
import mss301.carerecipientservice.dto.response.CareRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.RecipientManagerResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.EmergencyContact;
import mss301.carerecipientservice.entity.Medication;
import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RecipientStatus;
import mss301.carerecipientservice.enums.RelationshipType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test cho các mapping không hiển nhiên: nhiều nguồn trùng tên, profile null, PATCH bỏ qua null, PUT ghi đè null,
 * trường server quản lý, danh sách con tạo cùng hồ sơ. Dùng thẳng code MapStruct sinh ra (không cần Spring).
 */
class CareRecipientMapperTests {

    private final MedicationMapper medicationMapper = new MedicationMapperImpl();
    private final EmergencyContactMapper emergencyContactMapper = new EmergencyContactMapperImpl();
    private final CareRecipientMapper careRecipientMapper = new CareRecipientMapperImpl(medicationMapper, emergencyContactMapper);
    private final RecipientManagerMapper recipientManagerMapper = new RecipientManagerMapperImpl();

    @Test
    void caregiverDetailOnlyCarriesSnapshot() {
        RecipientSnapshotResponse snapshot = RecipientSnapshotResponse.builder()
                .recipientId(UUID.randomUUID())
                .fullName("Ong B")
                .build();
        CareRecipientDetailResponse response = careRecipientMapper.toCaregiverDetailResponse(snapshot);
        assertThat(response.getCareSnapshot()).isSameAs(snapshot);
        assertThat(response.getProfile()).isNull();
        assertThat(response.getMyAccessLevel()).isNull();
        assertThat(response.getMedications()).isNull();
        assertThat(response.getEmergencyContacts()).isNull();
    }

    @Test
    void managerResponseWithoutProfileOnlyHasLocalData() {
        RecipientManager manager = manager(RecipientAccessLevel.OWNER, true);
        RecipientManagerResponse response = recipientManagerMapper.toResponse(manager, null);
        assertThat(response.getId()).isEqualTo(manager.getId());
        assertThat(response.getUserId()).isEqualTo(manager.getUserId());
        assertThat(response.isPrimary()).isTrue();
        assertThat(response.isProfileAvailable()).isFalse();
        assertThat(response.getFullName()).isNull();
        assertThat(response.getEmail()).isNull();
        assertThat(response.getUserStatus()).isNull();
    }

    @Test
    void managerResponseTakesIdFromManagerAndStatusFromProfile() {
        RecipientManager manager = manager(RecipientAccessLevel.EDITOR, false);
        IdentityUserResponse profile = IdentityUserResponse.builder()
                .id(manager.getUserId())
                .email("a@example.test")
                .fullName("A")
                .phoneNumber("0901234567")
                .status("ACTIVE")
                .roles(Set.of("CUSTOMER"))
                .build();
        RecipientManagerResponse response = recipientManagerMapper.toResponse(manager, profile);
        assertThat(response.getId()).isEqualTo(manager.getId()).isNotEqualTo(profile.getId());
        assertThat(response.getUserStatus()).isEqualTo("ACTIVE");
        assertThat(response.getFullName()).isEqualTo("A");
        assertThat(response.getPhoneNumber()).isEqualTo("0901234567");
        assertThat(response.isProfileAvailable()).isTrue();
    }

    @Test
    void managerPatchIgnoresNullFields() {
        RecipientManager manager = manager(RecipientAccessLevel.VIEWER, false);
        recipientManagerMapper.update(ManagerUpdateRequest.builder().accessLevel(RecipientAccessLevel.EDITOR).build(), manager);
        assertThat(manager.getAccessLevel()).isEqualTo(RecipientAccessLevel.EDITOR);
        assertThat(manager.getRelationshipType()).isEqualTo(RelationshipType.CHILD);
    }

    @Test
    void createSetsActorButNeverServerManagedFields() {
        UUID actorId = UUID.randomUUID();
        CareRecipient recipient = careRecipientMapper.toCareRecipient(
                CareRecipientCreateRequest.builder().fullName("Ong A").relationshipType(RelationshipType.CHILD).build(), actorId);
        assertThat(recipient.getFullName()).isEqualTo("Ong A");
        assertThat(recipient.getCreatedBy()).isEqualTo(actorId);
        assertThat(recipient.getUpdatedBy()).isEqualTo(actorId);
        assertThat(recipient.getStatus()).isNull();
        assertThat(recipient.getId()).isNull();
        assertThat(recipient.getVersion()).isZero();
    }

    @Test
    void putOverwritesNullButKeepsServerManagedFields() {
        UUID creator = UUID.randomUUID();
        UUID editor = UUID.randomUUID();
        CareRecipient recipient = recipient(creator);
        recipient.setAllergies("Penicillin");
        recipient.setVersion(3);
        careRecipientMapper.update(CareRecipientUpdateRequest.builder().fullName("Ten moi").version(99L).build(), editor, recipient);
        assertThat(recipient.getFullName()).isEqualTo("Ten moi");
        assertThat(recipient.getAllergies()).isNull();
        assertThat(recipient.getUpdatedBy()).isEqualTo(editor);
        assertThat(recipient.getCreatedBy()).isEqualTo(creator);
        assertThat(recipient.getStatus()).isEqualTo(RecipientStatus.ACTIVE);
        assertThat(recipient.getVersion()).isEqualTo(3);
    }

    @Test
    void summaryAndSnapshotTakeAmbiguousFieldsFromRecipient() {
        CareRecipient recipient = recipient(UUID.randomUUID());
        RecipientManager membership = manager(RecipientAccessLevel.VIEWER, false);
        CareRecipientSummaryResponse summary = careRecipientMapper.toSummaryResponse(recipient, membership);
        assertThat(summary.getId()).isEqualTo(recipient.getId());
        assertThat(summary.getMyAccessLevel()).isEqualTo(RecipientAccessLevel.VIEWER);
        assertThat(summary.getPrimaryManager()).isFalse();
        assertThat(summary.getAge()).isNotNull();

        EmergencyContact contact = new EmergencyContact();
        contact.setFullName("Con gai");
        contact.setPhoneNumber("0909999999");
        contact.setPrimary(true);
        Instant snapshotAt = Instant.now();
        RecipientSnapshotResponse snapshot = careRecipientMapper.toSnapshotResponse(recipient, List.of(), contact, snapshotAt);
        assertThat(snapshot.getRecipientId()).isEqualTo(recipient.getId());
        assertThat(snapshot.getFullName()).isEqualTo("Ong B");
        assertThat(snapshot.getPhoneNumber()).isEqualTo("0901111111");
        assertThat(snapshot.getPrimaryEmergencyContact().getFullName()).isEqualTo("Con gai");
        assertThat(snapshot.getSnapshotAt()).isEqualTo(snapshotAt);
    }

    @Test
    void childListsAreCreatedWithParentAndDefaults() {
        CareRecipient recipient = recipient(UUID.randomUUID());
        UUID actorId = UUID.randomUUID();
        assertThat(emergencyContactMapper.toEmergencyContactList(null, recipient)).isEmpty();
        assertThat(medicationMapper.toMedicationList(null, recipient, actorId)).isEmpty();

        List<EmergencyContact> contacts = emergencyContactMapper.toEmergencyContactList(List.of(
                EmergencyContactRequest.builder().fullName("A").relationship("Con").phoneNumber("0900000001").primary(true).build(),
                EmergencyContactRequest.builder().fullName("B").relationship("Con").phoneNumber("0900000002").build()), recipient);
        assertThat(contacts).extracting(EmergencyContact::getRecipient).containsOnly(recipient);
        assertThat(contacts).extracting(EmergencyContact::isPrimary).containsExactly(true, false);
        assertThat(contacts).extracting(EmergencyContact::getPriority).containsExactly(0, 0);

        List<Medication> medications = medicationMapper.toMedicationList(List.of(
                MedicationRequest.builder().medicationName("Insulin").startDate(LocalDate.of(2026, 1, 1)).build(),
                MedicationRequest.builder().medicationName("X").startDate(LocalDate.of(2026, 1, 1)).active(false).build()),
                recipient, actorId);
        assertThat(medications).extracting(Medication::getRecipient).containsOnly(recipient);
        assertThat(medications).extracting(Medication::getUpdatedBy).containsOnly(actorId);
        assertThat(medications).extracting(Medication::isActive).containsExactly(true, false);
        assertThat(medications).extracting(Medication::getId).containsOnlyNulls();
    }

    @Test
    void childPutOverwritesButKeepsFlagsHandledByService() {
        EmergencyContact contact = new EmergencyContact();
        contact.setPrimary(true);
        contact.setPriority(5);
        emergencyContactMapper.update(
                EmergencyContactRequest.builder().fullName("C").relationship("Con").phoneNumber("0900000003").build(), contact);
        assertThat(contact.getPriority()).isZero();
        assertThat(contact.isPrimary()).isTrue();

        Medication medication = new Medication();
        medication.setActive(false);
        medication.setDosage("5mg");
        UUID editor = UUID.randomUUID();
        medicationMapper.update(MedicationRequest.builder().medicationName("Y").startDate(LocalDate.of(2026, 2, 1)).build(),
                editor, medication);
        assertThat(medication.getDosage()).isNull();
        assertThat(medication.isActive()).isFalse();
        assertThat(medication.getUpdatedBy()).isEqualTo(editor);
    }

    private CareRecipient recipient(UUID createdBy) {
        CareRecipient recipient = new CareRecipient();
        recipient.setId(UUID.randomUUID());
        recipient.setFullName("Ong B");
        recipient.setPhoneNumber("0901111111");
        recipient.setDateOfBirth(LocalDate.of(1950, 1, 1));
        recipient.setStatus(RecipientStatus.ACTIVE);
        recipient.setCreatedBy(createdBy);
        recipient.setCreatedAt(Instant.now());
        return recipient;
    }

    private RecipientManager manager(RecipientAccessLevel accessLevel, boolean primary) {
        return RecipientManager.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .relationshipType(RelationshipType.CHILD)
                .accessLevel(accessLevel)
                .primary(primary)
                .createdAt(Instant.now())
                .build();
    }
}
