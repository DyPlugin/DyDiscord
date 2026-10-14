package dev.dydiscord.roles;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoleHierarchyTest {
    private static final String G="123456789012345678",B="223456789012345678",NEW="323456789012345678",OLDER="123456789012345679";
    private RoleTransport.Member member(String role){return new RoleTransport.Member(true,false,Set.of(),Map.of(role,new RolePolicy.Role(role,G,1,0,false)),1,1L<<28,B);}
    @Test void newerRoleAtEqualRawPositionIsBelowOlderBotRole()throws Exception{assertDoesNotThrow(()->RolePolicy.validate(member(NEW),Set.of(NEW),Set.of(),G));}
    @Test void olderRoleAtEqualRawPositionRemainsAboveBotAndBlocked(){assertThrows(RolePolicy.Blocked.class,()->RolePolicy.validate(member(OLDER),Set.of(OLDER),Set.of(),G));}
    @Test void equalPositionWithoutKnownBotRoleIdFailsClosed(){var member=new RoleTransport.Member(true,false,Set.of(),Map.of(NEW,new RolePolicy.Role(NEW,G,1,0,false)),1,1L<<28);assertThrows(RolePolicy.Blocked.class,()->RolePolicy.validate(member,Set.of(NEW),Set.of(),G));}
}
