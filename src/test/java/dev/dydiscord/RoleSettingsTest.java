package dev.dydiscord;

import dev.dydiscord.config.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoleSettingsTest {
    static final String A="123456789012345678", B="223456789012345678";
    private Settings read(Map<String,Object> roles){return Settings.read(Map.of(),Map.of("roles",roles),null);}
    @Test void shorterCodeDefaultPreservesConfiguredLengths(){assertEquals(8,Settings.read(Map.of(),Map.of(),null).codeLength());assertEquals(12,Settings.read(Map.of("linking",Map.of("code-length",12)),Map.of(),null).codeLength());}
    @Test void defaultsAreOptIn(){var r=read(Map.of()).roleSync();assertFalse(r.enabled());assertTrue(r.memberEvents());assertEquals(300,r.recheckSeconds());assertEquals("all",r.selection());assertTrue(r.managedIds().isEmpty());}
    @Test void mappedRolesAreImmutableAndAggregated(){var r=read(Map.of("linked-role-ids",List.of(A),"groups",List.of(Map.of("group","vip","role-ids",List.of(B),"priority",10)),"context",Map.of("server","survival"))).roleSync();assertEquals(Set.of(A,B),r.managedIds());assertEquals(Set.of(B),r.groupRoleIds());assertThrows(UnsupportedOperationException.class,()->r.rules().clear());assertThrows(UnsupportedOperationException.class,()->r.rules().get(0).roleIds().clear());assertThrows(UnsupportedOperationException.class,()->r.context().clear());}
    @Test void conflictsAreRejectedWhileDisabled(){assertThrows(IllegalArgumentException.class,()->Settings.read(Map.of(),Map.of("roles",Map.of("linked-role-ids",List.of(A)),"console",Map.of("commands",Map.of("allowed-role-ids",List.of(A)))),null));assertThrows(IllegalArgumentException.class,()->Settings.read(Map.of(),Map.of("guild-id",A,"roles",Map.of("linked-role-ids",List.of(A))),null));}
    @Test void invalidInputIsRejected(){for(var roles:List.<Map<String,Object>>of(Map.of("linked-role-ids",List.of(123L)),Map.of("selection","random"),Map.of("recheck-seconds",29),Map.of("recheck-seconds",3601),Map.of("groups",List.of(Map.of("group","vip","role-ids",List.of(A),"priority",1),Map.of("group","vip","role-ids",List.of(B),"priority",2))),Map.of("context",Map.of("server",12))))assertThrows(IllegalArgumentException.class,()->read(roles));}
    @Test void effectiveMemberIntentRequiresRestart(){var disabled=read(Map.of());var disabledNoEvents=read(Map.of("member-events",false));assertFalse(disabled.needsRestart(disabledNoEvents));var enabled=read(Map.of("enabled",true));assertTrue(disabled.needsRestart(enabled));assertTrue(enabled.needsRestart(read(Map.of("enabled",true,"member-events",false))));}
    @Test void managedSetIncludesRepeatedIdsOnlyOnce(){var r=read(Map.of("linked-role-ids",List.of(A,A),"groups",List.of(Map.of("group","vip","role-ids",List.of(A,B,B),"priority",0)))).roleSync();assertEquals(Set.of(A,B),r.managedIds());}
    @Test void malformedSectionsAndExcessiveCollectionsAreRejected(){for(var r:List.<Map<String,Object>>of(Map.of("groups","vip"),Map.of("context",List.of("server")),Map.of("linked-role-ids",Collections.nCopies(101,A)),Map.of("groups",List.of(Map.of("group","bad group","role-ids",List.of(A),"priority",0)))))assertThrows(IllegalArgumentException.class,()->read(r));}
}

