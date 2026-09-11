package com.sinognss.cloud.vantix.common.user;

import com.sinognss.cloud.base.dto.UserCacheDTO;
import com.sinognss.cloud.base.filter.UserHolder;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserHolderBridgeTest {
    private final UserHolderBridge bridge = new UserHolderBridge();

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void emptyHolderMustNotBecomeGlobal() {
        BusinessException exception = assertThrows(BusinessException.class, bridge::getUserScope);
        assertEquals(ErrorCode.AUTHENTICATION_REQUIRED, exception.getVantixErrorCode());
    }

    @Test
    void scopeShapesAreClassifiedFailClosed() {
        assertEquals(UserScope.Type.GLOBAL, new UserScope(null, null).type());
        assertEquals(UserScope.Type.COMPANY, new UserScope(null, 10L).type());
        assertEquals(UserScope.Type.PERSONAL, new UserScope(88L, 10L).type());
        UserScope unsupported = new UserScope(88L, null);
        assertEquals(UserScope.Type.UNSUPPORTED, unsupported.type());
        assertFalse(unsupported.isGlobal());
        assertFalse(unsupported.canAccessCompany(10L));
    }

    @Test
    void globalDataTypeIsExplicitlyProvidedByNonEmptyBaseUser() {
        UserCacheDTO user = user(88L, 10L, 4);
        UserHolder.setUser(user);

        UserScope scope = bridge.getUserScope();

        assertTrue(scope.isGlobal());
        assertTrue(scope.canAccessCompany(10L));
    }

    @Test
    void companyAndPersonalDataTypesRemainScoped() {
        UserHolder.setUser(user(88L, 10L, 2));
        UserScope companyScope = bridge.getUserScope();
        assertEquals(UserScope.Type.COMPANY, companyScope.type());
        assertTrue(companyScope.canAccessCompany(10L));
        assertFalse(companyScope.canAccessCompany(11L));

        UserHolder.setUser(user(88L, 10L, 3));
        UserScope personalScope = bridge.getUserScope();
        assertEquals(UserScope.Type.PERSONAL, personalScope.type());
        assertTrue(personalScope.canAccessCompany(10L));
    }

    @Test
    void userWithoutCompanyIsRejectedInsteadOfBeingGlobal() {
        UserHolder.setUser(user(88L, null, 3));

        BusinessException exception = assertThrows(BusinessException.class, bridge::getUserScope);

        assertEquals(ErrorCode.UNSUPPORTED_USER_SCOPE, exception.getVantixErrorCode());
    }

    private UserCacheDTO user(Long userId, Long companyId, int dataType) {
        UserCacheDTO user = new UserCacheDTO();
        user.setUserId(userId);
        user.setCompanyId(companyId);
        user.setUserNickname("tester");
        user.setDataType(dataType);
        return user;
    }
}
