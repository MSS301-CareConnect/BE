package mss301.providerservice.service;

import mss301.providerservice.client.IdentityClient;
import mss301.providerservice.client.dto.IdentityUserResponse;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.enums.RoleCode;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.service.Impl.IdentityAccountServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class IdentityAccountServiceTests {
    IdentityClient client = mock(IdentityClient.class);
    IdentityAccountService service = new IdentityAccountServiceImpl(client);

    @Test
    void requiresActiveAccountWithExpectedRole() {
        UUID id = UUID.randomUUID();
        IdentityUserResponse user = IdentityUserResponse.builder()
                .id(id).status("ACTIVE").roles(Set.of("CAREGIVER")).build();
        when(client.get(id)).thenReturn(APIResponse.success("ok", user));

        assertEquals(id, service.requireActiveRole(id, RoleCode.CAREGIVER).getId());
    }

    @Test
    void rejectsWrongRole() {
        UUID id = UUID.randomUUID();
        IdentityUserResponse user = IdentityUserResponse.builder()
                .id(id).status("ACTIVE").roles(Set.of("CUSTOMER")).build();
        when(client.get(id)).thenReturn(APIResponse.success("ok", user));

        AppException error = assertThrows(AppException.class,
                () -> service.requireActiveRole(id, RoleCode.CAREGIVER));
        assertEquals(ErrorCode.IDENTITY_USER_INVALID, error.getErrorCode());
    }
}
