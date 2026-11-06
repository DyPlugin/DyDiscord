package dev.dydiscord.roles;

import dev.dydiscord.TestSupport;
import dev.dydiscord.config.*;
import dev.dydiscord.storage.*;
import net.luckperms.api.cacheddata.*;
import net.luckperms.api.model.data.*;
import net.luckperms.api.model.group.*;
import net.luckperms.api.model.user.*;
import net.luckperms.api.node.NodeBuilderRegistry;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.util.Tristate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiscordGroupsTest {
    /** The LuckPerms entry interface is stubbed in tests; the writer only needs these three accessors. */
    public interface Api {NodeBuilderRegistry getNodeBuilderRegistry();UserManager getUserManager();GroupManager getGroupManager();}
    @TempDir Path folder;
    static final String VIP_ROLE="823456789012345678",ADMIN_ROLE="823456789012345679",DISCORD="723456789012345678";
    Store links;FeatureStore grants;Settings settings;UUID player=UUID.randomUUID();
    final Map<UUID,Set<String>> luckPerms=new HashMap<>();final Set<String> roles=new HashSet<>();final List<String> warnings=new ArrayList<>();
    DiscordGroups.Writer.Check check=DiscordGroups.Writer.Check.OK;Set<String> memberRoles;boolean inGuild=true;DiscordGroups engine;

    final DiscordGroups.Writer writer=new DiscordGroups.Writer(){
        public boolean available(){return true;}
        public boolean add(UUID id,String group){return luckPerms.computeIfAbsent(id,k->new HashSet<>()).add(group);}
        public boolean remove(UUID id,String group){return luckPerms.computeIfAbsent(id,k->new HashSet<>()).remove(group);}
        public Check check(String group){return check;}
    };
    @BeforeEach void setup()throws Exception{
        links=new Store(folder.resolve("data.db"));grants=new FeatureStore(folder.resolve("features.db"));
        settings=settings(true,List.of("admin"));
        links.link(player,DISCORD,"Alex",Instant.EPOCH);
        engine=new DiscordGroups(()->settings,links,grants,new DiscordGroups.Members(){public boolean available(){return true;}public Set<String> roles(String id){return inGuild?roles:null;}},writer,List::of,new TestSupport.Time(),warnings::add,e->{throw new AssertionError(e);});
    }
    @AfterEach void close()throws Exception{engine.close();grants.close();links.close();}
    Settings settings(boolean enabled,List<String> staff)throws Exception{
        var bot=new LinkedHashMap<String,Object>();bot.put("discord-groups",Map.of("enabled",enabled,"rules",List.of(Map.of("role-id",VIP_ROLE,"group","vip"),Map.of("role-id",ADMIN_ROLE,"group","admin")),"staff-groups",staff));
        return Settings.read(Map.of(),bot,null);
    }
    @Test void discordRolesGrantGroupsAndOnlyOwnGrantsAreRemoved()throws Exception{
        roles.add(VIP_ROLE);engine.reconcile(player);
        assertEquals(Set.of("vip"),luckPerms.get(player));assertEquals(Set.of("vip"),grants.grants(player));
        luckPerms.get(player).add("builder");roles.clear();engine.reconcile(player);
        assertEquals(Set.of("builder"),luckPerms.get(player),"the group given by hand stays");assertTrue(grants.grants(player).isEmpty());
    }
    @Test void aGroupThePlayerAlreadyHadIsNeverRecordedOrRemoved()throws Exception{
        luckPerms.put(player,new HashSet<>(Set.of("vip")));roles.add(VIP_ROLE);engine.reconcile(player);assertTrue(grants.grants(player).isEmpty());
        roles.clear();engine.reconcile(player);assertEquals(Set.of("vip"),luckPerms.get(player));
    }
    @Test void staffGroupsNeedExplicitTrustAndWildcardGroupsAreRefused()throws Exception{
        settings=settings(true,List.of());roles.add(ADMIN_ROLE);engine.reconcile(player);
        assertNull(luckPerms.get(player));assertEquals(List.of("staff:admin"),warnings);
        settings=settings(true,List.of("admin"));engine.reconcile(player);assertEquals(Set.of("admin"),luckPerms.get(player));
        luckPerms.clear();grants.revoke(player,"admin");roles.clear();roles.add(VIP_ROLE);check=DiscordGroups.Writer.Check.WILDCARD;engine.reconcile(player);
        assertNull(luckPerms.get(player));assertTrue(warnings.contains("wildcard:vip"));
        check=DiscordGroups.Writer.Check.MISSING;engine.reconcile(player);assertTrue(warnings.contains("missing:vip"));
    }
    @Test void unlinkingOrLeavingTheGuildRemovesGrantedGroups()throws Exception{
        roles.add(VIP_ROLE);engine.reconcile(player);inGuild=false;engine.reconcile(player);assertTrue(luckPerms.get(player).isEmpty());
        inGuild=true;engine.reconcile(player);links.unlink(links.byUuid(player).orElseThrow());engine.reconcile(player);assertTrue(luckPerms.get(player).isEmpty());assertTrue(grants.grants(player).isEmpty());
    }
    @Test void disabledFeatureChangesNothingAndQueuesNothing()throws Exception{
        settings=settings(false,List.of());roles.add(VIP_ROLE);engine.reconcile(player);engine.enqueue(player);
        assertNull(luckPerms.get(player));assertEquals(0,engine.queued());
    }
    @Test void periodicRecheckQueuesOnlinePlayersAndProcessesAFewPerTick()throws Exception{
        List<UUID> online=new ArrayList<>();for(int i=0;i<6;i++)online.add(UUID.randomUUID());
        var periodic=new DiscordGroups(()->settings,links,grants,new DiscordGroups.Members(){public boolean available(){return true;}public Set<String> roles(String id){return Set.of();}},writer,()->online,new TestSupport.Time(),warnings::add,e->{throw new AssertionError(e);});
        try(periodic){periodic.tick();assertEquals(6-DiscordGroups.PER_TICK,periodic.queued());periodic.tick();assertEquals(0,periodic.queued());}
    }

    @SuppressWarnings("unchecked")
    @Test void luckPermsWriterAddsInheritanceNodesAndReportsWildcardGroups()throws Exception{
        Api api=mock(Api.class);NodeBuilderRegistry registry=mock(NodeBuilderRegistry.class);InheritanceNode.Builder builder=mock(InheritanceNode.Builder.class);InheritanceNode node=mock(InheritanceNode.class);
        when(api.getNodeBuilderRegistry()).thenReturn(registry);when(registry.forInheritance()).thenReturn(builder);when(builder.group("vip")).thenReturn(builder);when(builder.build()).thenReturn(node);
        UserManager users=mock(UserManager.class);User user=mock(User.class);NodeMap data=mock(NodeMap.class);when(api.getUserManager()).thenReturn(users);when(user.data()).thenReturn(data);
        when(data.add(node)).thenReturn(DataMutateResult.SUCCESS,DataMutateResult.FAIL_ALREADY_HAS);when(data.remove(node)).thenReturn(DataMutateResult.SUCCESS);
        when(users.modifyUser(eq(player),any())).thenAnswer(call->{((Consumer<Object>)call.getArgument(1)).accept(user);return CompletableFuture.completedFuture(null);});
        LuckPermsWriter writer=new LuckPermsWriter(api);
        assertTrue(writer.add(player,"vip"));assertFalse(writer.add(player,"vip"),"already held");assertTrue(writer.remove(player,"vip"));
        GroupManager groups=mock(GroupManager.class);when(api.getGroupManager()).thenReturn(groups);
        assertEquals(DiscordGroups.Writer.Check.MISSING,writer.check("vip"));
        Group group=mock(Group.class);CachedDataManager cached=mock(CachedDataManager.class);CachedPermissionData permissions=mock(CachedPermissionData.class);
        when(groups.getGroup("vip")).thenReturn(group);when(group.getCachedData()).thenReturn(cached);when(cached.getPermissionData()).thenReturn(permissions);
        when(permissions.checkPermission("*")).thenReturn(Tristate.UNDEFINED);assertEquals(DiscordGroups.Writer.Check.OK,writer.check("vip"));
        when(permissions.checkPermission("*")).thenReturn(Tristate.TRUE);assertEquals(DiscordGroups.Writer.Check.WILDCARD,writer.check("vip"));
        assertFalse(new LuckPermsWriter((Object)null).available());
    }
}
