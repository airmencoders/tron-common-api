package mil.tron.commonapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import mil.tron.commonapi.entity.Privilege;
import mil.tron.commonapi.entity.scratch.ScratchStorageAppUserPriv;
import mil.tron.commonapi.entity.scratch.ScratchStorageUser;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.*;

/**
 * Represents a registered scratch space app that includes:
 *   - its UUID
 *   - its app name
 *   - its users and their privileges
 *
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScratchStorageAppRegistryDto {

    /**
     * Private inner class that will represent a ScratchUserPriv entity
     * with its userPrivePairId field representing the particular ScratchUserPriv UUID in the db
     * so that a user/priv for a given app can easily be manipulated
     */
    @Builder
    @Getter
    @Setter
    private static class PrivilegeIdPair {

        @Getter
        @Setter
        private UUID userPrivPairId;

        @Getter
        @Setter
        private Privilege priv;
    }

    /**
     * Private inner class that represents a set of ScratchUserPriv's but reduced
     * by user's email for better representation to the client UI (see setter method below for reducer method)
     */
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    private static class UserWithPrivs {

        @Getter
        @Setter
        private UUID userId;

        @Getter
        @Setter
        private String emailAddress;

        @Getter
        @Setter
        private Set<PrivilegeIdPair> privs;
    }

    /**
     * The registered scratch space app's unique ID
     */
    @Getter
    @Setter
    private UUID id;

    /**
     * The String name of the app
     */
    @Getter
    @Setter
    @NotBlank
    @NotNull
    private String appName;

    @Getter
    private Set<UserWithPrivs> userPrivs;

    /**
     * Sets the userAppPrivs field by taking a Set of ScratchStorageAppUserPriv pairs
     * and reducing them by user email address into a set of UserWithPrivs types
     * @param privs
     */
    public void setUserPrivs(Set<ScratchStorageAppUserPriv> privs) {

        Map<ScratchStorageUser, Set<PrivilegeIdPair>> privHash = new HashMap<>();

        // build out a map keyed by ScratchUser with their list of privileges
        for (ScratchStorageAppUserPriv priv : privs) {
            if (privHash.containsKey(priv.getUser())) {
                Set<PrivilegeIdPair> privList = privHash.get(priv.getUser());
                privList.add(PrivilegeIdPair.builder()
                        .userPrivPairId(priv.getId())
                        .priv(priv.getPrivilege())
                        .build());
                privHash.put(priv.getUser(), privList);
            }
            else {
                Set<PrivilegeIdPair> newPrivList = new HashSet<>();
                newPrivList.add(PrivilegeIdPair.builder()
                        .userPrivPairId(priv.getId())
                        .priv(priv.getPrivilege())
                        .build());
                privHash.put(priv.getUser(), newPrivList);
            }
        }

        // reduce the map to a UserWithPriv Set
        Set<Map.Entry<ScratchStorageUser, Set<PrivilegeIdPair>>> privSet = privHash.entrySet();
        Set<UserWithPrivs> userPrivsSet = new HashSet<>();
        for (Map.Entry<ScratchStorageUser, Set<PrivilegeIdPair>> pair : privSet) {
            userPrivsSet.add(UserWithPrivs
                    .builder()
                    .userId(pair.getKey().getId())
                    .emailAddress(pair.getKey().getEmail())
                    .privs(pair.getValue())
                    .build());
        }

        this.userPrivs = userPrivsSet;
    }
}
