package dev.dydiscord;
import dev.dydiscord.command.*;
import dev.dydiscord.config.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.permissions.*;
import org.bukkit.plugin.*;
import org.mockito.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CommandTest {
    @ParameterizedTest @ValueSource(strings={"en","tr"})
    void bothCommandRootsOpenTheSameHelpAndEveryEntryUsesDyDiscord(String language)throws Exception{
        DyDiscord plugin=mock(DyDiscord.class);when(plugin.ready()).thenReturn(true);when(plugin.language(any())).thenReturn(language);when(plugin.messages()).thenReturn(TestSupport.config(folder).active().messages());
        List<List<String>> outputs=new ArrayList<>();
        for(String root:List.of("discord","dydiscord")){
            Player player=mock(Player.class);when(player.hasPermission(anyString())).thenReturn(true);new Router(plugin,false).onCommand(player,mock(Command.class),root,new String[]{"help"});
            ArgumentCaptor<String> lines=ArgumentCaptor.forClass(String.class);verify(player,atLeastOnce()).sendMessage(lines.capture());outputs.add(lines.getAllValues());
            assertTrue(lines.getAllValues().stream().allMatch(line->line.startsWith("§6/dydiscord")));assertFalse(String.join("\n",lines.getAllValues()).contains("/discord"));
        }
        assertEquals(outputs.get(0),outputs.get(1));
    }
    @ParameterizedTest @ValueSource(strings={"kaldır","kaldir","ayır","ayir"})
    void naturalUnlinkCommandAndLegacyAliasesShareLeafPermission(String word){
        Definition unlink=Definition.ALL.stream().filter(d->d.path().equals("unlink")).findFirst().orElseThrow();assertTrue(unlink.match(new String[]{word}));
        DyDiscord plugin=mock(DyDiscord.class);when(plugin.ready()).thenReturn(true);Player player=mock(Player.class);
        new Router(plugin,false).onCommand(player,mock(Command.class),"discord",new String[]{word});verify(plugin).message(eq(player),eq("no-permission"),any());verify(plugin,never()).execute(any(),any(),any());
        when(player.hasPermission("dydiscord.unlink")).thenReturn(true);new Router(plugin,false).onCommand(player,mock(Command.class),"discord",new String[]{word});verify(plugin).execute(eq(player),eq(unlink),eq(new String[0]));
    }
    @Test void turkishManagementKeepsAdminRootAndLegacyAliases(){
        assertEquals(List.of("kaldır"),Definition.tabs(new String[]{""},"tr",true,p->p.equals("dydiscord.unlink")));
        assertEquals(List.of("admin"),Definition.tabs(new String[]{""},"tr",true,p->p.equals("dydiscord.admin.reload")));
        assertEquals(List.of("yenile"),Definition.tabs(new String[]{"yonetim",""},"tr",true,p->p.equals("dydiscord.admin.reload")));
        assertEquals("unlink",Words.canonical("ayır"));assertEquals("unlink",Words.canonical("kaldır"));assertEquals("admin",Words.canonical("yonetim"));
    }
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path folder;
    static Stream<Definition> definitions(){return Definition.ALL.stream();}
    static String[] arguments(Definition d){List<String> list=new ArrayList<>(List.of(d.words()));for(int i=0;i<d.arguments();i++)list.add("value");return list.toArray(String[]::new);}
    @ParameterizedTest @MethodSource("definitions")void allEnglishAndTurkishPathsHaveStrictArity(Definition d){String[] en=arguments(d);assertTrue(d.match(en));String[] tr=en.clone();for(int i=0;i<d.words().length;i++)tr[i]=Words.display(d.words()[i],"tr");assertTrue(d.match(tr),d.path());for(int i=0;i<d.words().length;i++)tr[i]=Words.plain(tr[i]);assertTrue(d.match(tr));if(en.length>0)assertFalse(d.match(Arrays.copyOf(en,en.length-1)));if(!d.remainder())assertFalse(d.match(Arrays.copyOf(en,en.length+1)));}
    @ParameterizedTest @MethodSource("definitions")void directUnauthorizedCallsCannotReachExecutor(Definition d){DyDiscord plugin=mock(DyDiscord.class);when(plugin.ready()).thenReturn(true);Player player=mock(Player.class);Router router=new Router(plugin,false);router.onCommand(player,mock(Command.class),"discord",arguments(d));verify(plugin,never()).execute(any(),any(),any());verify(plugin).message(eq(player),eq("no-permission"),any());}
    @Test void noAdminOrDataInPlayerTab(){List<String> tab=Definition.tabs(new String[]{""},"en",true,p->p.startsWith("dydiscord.")&&!p.startsWith("dydiscord.admin"));assertFalse(tab.contains("admin"));assertFalse(tab.contains("setinvite"));assertTrue(tab.contains("link"));assertEquals(List.of(),Definition.tabs(new String[]{"admin",""},"en",true,p->false));}
    @Test void singleAdminLeafIsEnoughAndNoInviteAliasLeaks(){List<String> tab=Definition.tabs(new String[]{""},"en",true,p->p.equals("dydiscord.admin.reload"));assertTrue(tab.contains("admin"));assertFalse(tab.contains("setinvite"));assertEquals(List.of("yenile"),Definition.tabs(new String[]{"admin",""},"tr",true,p->p.equals("dydiscord.admin.reload")));}
    @Test void grammarNormalizationDoesNotTouchValues(){Definition d=Definition.ALL.stream().filter(v->v.path().equals("admin invite")).findFirst().orElseThrow();String url="https://discord.gg/AbCD-12";String[] args={"admin","davet",url};assertTrue(d.match(args));assertEquals(url,args[2]);assertEquals("UUID-Şçı",Words.display("UUID-Şçı","tr"));}
    @Test void consoleCannotRunOwnPlayerCommands(){DyDiscord plugin=mock(DyDiscord.class);when(plugin.ready()).thenReturn(true);ConsoleCommandSender sender=mock(ConsoleCommandSender.class);when(sender.hasPermission(anyString())).thenReturn(true);new Router(plugin,false).onCommand(sender,mock(Command.class),"dydiscord",new String[]{"link"});verify(plugin).message(eq(sender),eq("player-only"),any());verify(plugin,never()).execute(any(),any(),any());}
    @Test void shortcutsUseSamePermissionAndRouter(){DyDiscord plugin=mock(DyDiscord.class);when(plugin.ready()).thenReturn(true);Player sender=mock(Player.class);new Router(plugin,true).onCommand(sender,mock(Command.class),"eşle",new String[]{});verify(plugin).message(eq(sender),eq("no-permission"),any());}
    @ParameterizedTest @ValueSource(strings={"en","tr"})void consoleHelpListsAllPermittedPlayerCommands(String language)throws Exception{
        DyDiscord plugin=mock(DyDiscord.class);when(plugin.ready()).thenReturn(true);when(plugin.language(any())).thenReturn(language);when(plugin.messages()).thenReturn(TestSupport.config(folder).active().messages());
        ConsoleCommandSender sender=mock(ConsoleCommandSender.class);when(sender.hasPermission(anyString())).thenReturn(true);
        new Router(plugin,false).onCommand(sender,mock(Command.class),"dydiscord",new String[]{"help"});
        ArgumentCaptor<String> lines=ArgumentCaptor.forClass(String.class);verify(sender,atLeastOnce()).sendMessage(lines.capture());String text=String.join("\n",lines.getAllValues());
        for(String word:List.of("link","status","unlink","language","confirm","cancel"))assertTrue(text.contains("/dydiscord "+Words.display(word,language)),word);
        assertTrue(text.contains("/dydiscord §7-"));assertTrue(text.contains(plugin.messages().get(false,language,"help-player-only")));
    }
    @Test void helpStillHidesDeniedPlayerAndAdminLeaves()throws Exception{
        DyDiscord plugin=mock(DyDiscord.class);when(plugin.ready()).thenReturn(true);when(plugin.language(any())).thenReturn("en");when(plugin.messages()).thenReturn(TestSupport.config(folder).active().messages());
        Player sender=mock(Player.class);when(sender.hasPermission("dydiscord.help")).thenReturn(true);
        new Router(plugin,false).onCommand(sender,mock(Command.class),"discord",new String[]{"help"});
        verify(plugin).helpHeader(sender);verify(sender,never()).sendMessage(anyString());
    }
    @Test void pluginGroupsAndBukkitExplicitDeny()throws Exception{
        Map<String,Object> descriptor;try(var in=getClass().getResourceAsStream("/plugin.yml")){descriptor=Configuration.parse(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        @SuppressWarnings("unchecked") Map<String,Map<String,Object>> declarations=(Map<String,Map<String,Object>>)descriptor.get("permissions");
        for(var e:declarations.entrySet())if(!Set.of("dydiscord.player","dydiscord.admin").contains(e.getKey()))assertEquals(false,e.getValue().get("default"));
        Server server=mock(Server.class);SimplePluginManager manager=new SimplePluginManager(server,mock(SimpleCommandMap.class));Plugin owner=mock(Plugin.class);when(owner.isEnabled()).thenReturn(true);
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class)){bukkit.when(Bukkit::getServer).thenReturn(server);bukkit.when(Bukkit::getPluginManager).thenReturn(manager);when(server.getPluginManager()).thenReturn(manager);
            List<Permission> permissions=Permission.loadPermissions(declarations,"error",PermissionDefault.FALSE);for(Permission p:permissions)manager.addPermission(p);
            ServerOperator operator=mock(ServerOperator.class);when(operator.isOp()).thenReturn(true);PermissibleBase base=new PermissibleBase(operator);base.recalculatePermissions();assertTrue(base.hasPermission("dydiscord.admin.reload"));PermissionAttachment attachment=base.addAttachment(owner);attachment.setPermission("dydiscord.admin.reload",false);assertFalse(base.hasPermission("dydiscord.admin.reload"));
            attachment.setPermission("dydiscord.player",false);assertFalse(base.hasPermission("dydiscord.link"));attachment.setPermission("dydiscord.link",true);assertTrue(base.hasPermission("dydiscord.link"));base.clearPermissions();
        }
    }
}
