package dev.dydiscord;

import dev.dydiscord.command.Definition;
import dev.dydiscord.config.Configuration;
import dev.dydiscord.roles.*;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleCommandsTest {
    @TempDir Path folder;
    DyDiscord plugin;RoleEngine engine;CommandSender sender;Configuration configuration;RoleCommands commands;
    @BeforeEach void setup()throws Exception{
        configuration=TestSupport.config(folder);plugin=mock(DyDiscord.class);engine=mock(RoleEngine.class);sender=mock(CommandSender.class);
        when(plugin.configuration()).thenReturn(configuration);when(plugin.settings()).thenAnswer(i->configuration.active().settings());when(plugin.language(sender)).thenReturn("en");when(plugin.actor(sender)).thenReturn("local:Console");when(plugin.clock()).thenReturn(new TestSupport.Time());when(sender.hasPermission(anyString())).thenReturn(true);
        when(plugin.mainValue(any())).thenAnswer(i->((Callable<?>)i.getArgument(0)).call());
        when(engine.work(any())).thenAnswer(i->{try{return CompletableFuture.completedFuture(((Callable<?>)i.getArgument(0)).call());}catch(Exception e){return CompletableFuture.failedFuture(e);}});
        commands=new RoleCommands(plugin,engine);
    }
    private void execute(String key,String... args){Definition d=Definition.ALL.stream().filter(x->x.key().equals(key)).findFirst().orElseGet(()->new Definition("internal role service",key.equals("roles-groups-remove")?"dydiscord.admin.roles.groups.remove":"dydiscord.admin.roles.linked.add",false,args.length,false,key));commands.execute(sender,d,args);}
    @Test void removalPreservesOtherRolesAndRulePriority()throws Exception{
        configuration.change("bot.yml","roles.groups",List.of(Map.of("group","vip","role-ids",List.of("123456789012345678","223456789012345678"),"priority",7)));
        execute("roles-groups-remove","vip","123456789012345678");
        var rule=configuration.active().settings().roleSync().rules().get(0);assertEquals(Set.of("223456789012345678"),rule.roleIds());assertEquals(7,rule.priority());verify(engine).refresh();
    }
    @Test void permissionRevokedDuringValidationPreventsConfigurationWrite()throws Exception{
        String original=java.nio.file.Files.readString(folder.resolve("bot.yml"));doAnswer(i->{when(sender.hasPermission(anyString())).thenReturn(false);return null;}).when(engine).validateSettings(any());
        execute("roles-linked-add","123456789012345678");assertEquals(original,java.nio.file.Files.readString(folder.resolve("bot.yml")));assertTrue(configuration.active().settings().roleSync().linkedRoles().isEmpty());verify(plugin).message(sender,"no-permission",Map.of());verify(engine,never()).refresh();
    }
    @Test void deniedSyncDoesNotEnqueueWork(){when(sender.hasPermission(anyString())).thenReturn(false);execute("roles-sync","00000000-0000-0000-0000-000000000001");verify(engine,never()).enqueue(any());verify(plugin).message(sender,"no-permission",Map.of());}
    @Test void malformedIdentifierCannotChangeSettings()throws Exception{execute("roles-linked-add","not-an-id");assertTrue(configuration.active().settings().roleSync().managedIds().isEmpty());verify(engine,never()).validateSettings(any());}
    private String cleanupToken()throws Exception{
        when(engine.cleanupPreview()).thenReturn(new RoleEngine.CleanupPreview(3,Set.of("123456789012345678")));execute("roles-cleanup-preview");
        return mockingDetails(plugin).getInvocations().stream().filter(i->i.getMethod().getName().equals("message")&&i.getArgument(1).equals("roles-cleanup-confirmation")).map(i->((Map<?,?>)i.getArgument(2)).get("token").toString()).findFirst().orElseThrow();
    }
    @Test void cleanupTokenCannotReplay()throws Exception{String token=cleanupToken();execute("roles-cleanup-confirm",token);execute("roles-cleanup-confirm",token);verify(engine,times(1)).startCleanup();verify(plugin).message(sender,"roles-invalid-confirmation",Map.of());}
    @Test void changedSettingsInvalidateCleanupToken()throws Exception{String token=cleanupToken();configuration.change("bot.yml","roles.member-events",false);execute("roles-cleanup-confirm",token);verify(engine,never()).startCleanup();verify(plugin).message(sender,"roles-invalid-confirmation",Map.of());}
    @Test void expiredCleanupTokenDoesNotStartDeletion()throws Exception{String token=cleanupToken();((TestSupport.Time)plugin.clock()).advance(31);execute("roles-cleanup-confirm",token);verify(engine,never()).startCleanup();}
    private String summary(){return mockingDetails(plugin).getInvocations().stream().filter(i->i.getMethod().getName().equals("message")&&i.getArgument(1).equals("roles-summary")).reduce((a,b)->b).map(i->((Map<?,?>)i.getArgument(2)).get("status").toString()).orElseThrow();}
    @Test void pendingBulkJobsAreNotReportedCompleted()throws Exception{configuration.change("bot.yml","roles.enabled",true);execute("roles-sync-all");when(engine.counts()).thenReturn(Map.of("PENDING",1L));execute("roles-status");assertFalse(summary().contains("completed"));assertTrue(summary().contains("retrying"));}
    @Test void failedBulkJobsRequireAttention()throws Exception{configuration.change("bot.yml","roles.enabled",true);execute("roles-sync-all");when(engine.counts()).thenReturn(Map.of("FAILED",1L));execute("roles-status");assertFalse(summary().contains("completed"));assertTrue(summary().contains("needs attention"));}
    @Test void criticalRoleFailureHasAnActionableExplanation()throws Exception{when(engine.counts()).thenReturn(Map.of());when(engine.failure()).thenReturn("critical-role");execute("roles-status");assertTrue(summary().contains("privileged or console role"));assertFalse(summary().contains("critical-role"));}
    @Test void missingMemberEventsExplainsPeriodicFallback()throws Exception{configuration.change("bot.yml","roles.enabled",true);dev.dydiscord.discord.Bot bot=mock(dev.dydiscord.discord.Bot.class);when(plugin.bot()).thenReturn(bot);when(bot.roleMemberEvents()).thenReturn(false);when(engine.counts()).thenReturn(Map.of());execute("roles-status");assertTrue(summary().contains("member events unavailable"));assertTrue(summary().contains("periodic reconciliation remains active"));}
    @Test void unknownAccountCannotReceiveStartedAcknowledgement()throws Exception{
        configuration.change("bot.yml","roles.enabled",true);
        try(dev.dydiscord.storage.Store store=new dev.dydiscord.storage.Store(folder.resolve("unknown-role-account.db"));
            RoleEngine actual=new RoleEngine(store,()->configuration.active().settings(),mock(GroupProvider.class),mock(RoleTransport.class),plugin.clock(),e->{throw new AssertionError(e);})){
            RoleCommands actualCommands=new RoleCommands(plugin,actual);Definition d=Definition.ALL.stream().filter(x->x.key().equals("roles-sync")).findFirst().orElseThrow();
            actualCommands.execute(sender,d,new String[]{"00000000-0000-0000-0000-000000000001"});actual.work(()->null).get(5,TimeUnit.SECONDS);
            verify(plugin,never()).message(sender,"roles-started",Map.of());verify(plugin).message(sender,"usage",Map.of());
        }
    }
    @Test void successfulCleanupIsReportedCompleted()throws Exception{String token=cleanupToken();execute("roles-cleanup-confirm",token);when(engine.counts()).thenReturn(Map.of("SUCCESS",3L));execute("roles-status");assertTrue(summary().contains("completed"));assertFalse(summary().contains("not started"));}
}
