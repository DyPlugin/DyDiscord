package dev.dydiscord;

import dev.dydiscord.command.*;
import dev.dydiscord.config.*;
import dev.dydiscord.language.*;
import dev.dydiscord.storage.*;
import dev.dydiscord.linking.*;
import dev.dydiscord.discord.*;
import dev.dydiscord.console.*;
import dev.dydiscord.util.PluginThreads;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.*;
import java.time.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class DyDiscord extends JavaPlugin implements Listener {
    private final Clock clock=Clock.systemUTC();
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),PluginThreads.factory("DyDiscord-io"),new ThreadPoolExecutor.AbortPolicy());
    private final Map<UUID,String> preferences=new ConcurrentHashMap<>();
    private final Map<UUID,Long> sessions=new ConcurrentHashMap<>();
    private Configuration configuration;private Store store;private Linking linking;private Audit audit;private Bot bot;private ConsoleBridge console;private dev.dydiscord.update.UpdateChecker updates;private dev.dydiscord.chat.ChatBridge chat;private dev.dydiscord.chat.ChatEvents chatEvents;
    private volatile boolean ready,closing;private long sessionCounter;
    private dev.dydiscord.roles.RoleEngine roles;private dev.dydiscord.roles.RoleCommands roleCommands;
    private dev.dydiscord.boost.BoostBridge boosts;private dev.dydiscord.boost.BoostStore boostStore;
    private final Set<String> warnedTranslations=ConcurrentHashMap.newKeySet();
    private void translationWarnings(){long count=messages().missing().stream().filter(warnedTranslations::add).count();if(count>0)getLogger().warning(count+" missing message keys use bundled English defaults (values withheld).");}
    @Override public void onEnable(){
        configuration=new Configuration(getDataFolder().toPath());Router root=new Router(this,false);
        Objects.requireNonNull(getCommand("dydiscord")).setExecutor(root);getCommand("dydiscord").setTabCompleter(root);
        Router shortcut=new Router(this,true);Objects.requireNonNull(getCommand("dylink")).setExecutor(shortcut);getCommand("dylink").setTabCompleter(shortcut);
        PluginCommand discord=getServer().getPluginCommand("discord");if(discord!=null&&discord.getPlugin()!=this)getLogger().warning("/discord is owned by another plugin. Use /dydiscord or /dydiscord:dydiscord.");
        getServer().getPluginManager().registerEvents(this,this);
        for(Player p:getServer().getOnlinePlayers())sessions.put(p.getUniqueId(),++sessionCounter);
        submit(()->{
            configuration.load();translationWarnings();store=new Store(getDataFolder().toPath().resolve("data/dydiscord.db"));preferences.putAll(store.preferences());
            audit=new Audit(getDataFolder().toPath().resolve("logs"),clock);linking=new Linking(store,clock,new SecureRandom());bot=new Bot(this);console=new ConsoleBridge(this);chat=new dev.dydiscord.chat.ChatBridge(this);chatEvents=new dev.dydiscord.chat.ChatEvents(this);
            var groups=mainValue(()->new dev.dydiscord.roles.LuckPermsGroups(this));
            roles=new dev.dydiscord.roles.RoleEngine(store,this::settings,groups,new dev.dydiscord.roles.JdaRoleTransport(this),clock,e->error(e));roleCommands=new dev.dydiscord.roles.RoleCommands(this,roles);
            roles.validateSettings(settings());
            try{boostStore=new dev.dydiscord.boost.BoostStore(getDataFolder().toPath().resolve("data/boosts.db"));boosts=new dev.dydiscord.boost.BoostBridge(new dev.dydiscord.boost.BukkitBoostHost(this),boostStore,clock);store.linkListener(boosts::signal);}catch(Exception failure){error(failure);getLogger().warning("Boost rewards unavailable; existing reward data was preserved. Other features remain available.");}
            mainValue(()->{ready=true;getLogger().info("DyDiscord v"+getDescription().getVersion()+" enabled!");console.attach();chatEvents.register();chat.start();return null;});
            roles.start();if(boosts!=null)boosts.start();updates=new dev.dydiscord.update.UpdateChecker(this);updates.refresh();bot.start();return null;
        }).exceptionally(e->{error(e);getLogger().severe("Initialization failed. Existing data was preserved; fix configuration or data and restart the server.");return null;});
        getServer().getScheduler().runTaskTimer(this,()->{if(ready&&!closing)submit(()->{linking.cleanup();return null;});},200,200);
    }
    @Override public void onDisable(){
        if(boosts!=null)boosts.close();
        closing=true;ready=false;if(roleCommands!=null)roleCommands.close();if(roles!=null)roles.close();if(chat!=null)chat.close();if(bot!=null&&configuration.active()!=null)bot.serverShutdownNotice(dev.dydiscord.chat.ShutdownNotice.stopping(getServer()));if(linking!=null)linking.invalidate();if(console!=null)console.close();
        if(updates!=null)updates.close();if(bot!=null)bot.stop();io.shutdown();try{if(!io.awaitTermination(5,TimeUnit.SECONDS))io.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();io.shutdownNow();}
        if(boostStore!=null)try{boostStore.close();}catch(Exception e){error(e);}if(store!=null)try{store.close();}catch(Exception e){error(e);}getLogger().info("DyDiscord disabled!");
    }
    @EventHandler public void join(PlayerJoinEvent event){Player p=event.getPlayer();sessions.put(p.getUniqueId(),++sessionCounter);UUID uuid=p.getUniqueId();String name=p.getName();if(updates!=null)updates.notify(p);if(roles!=null)roles.enqueue(uuid);if(ready)submit(()->{store.updateName(uuid,name);return null;}).exceptionally(e->{error(e);return null;});}
    @EventHandler public void providerEnabled(PluginEnableEvent event){if(roles!=null&&event.getPlugin().getName().equalsIgnoreCase("LuckPerms"))roles.provider(new dev.dydiscord.roles.LuckPermsGroups(this));}
    @EventHandler public void providerDisabled(PluginDisableEvent event){if(roles!=null&&event.getPlugin().getName().equalsIgnoreCase("LuckPerms"))roles.provider(new dev.dydiscord.roles.GroupProvider(){public boolean available(){return false;}public Result groups(UUID uuid,Map<String,String> context){return Result.unknown("provider-unavailable");}});}
    @EventHandler public void leave(PlayerQuitEvent event){UUID uuid=event.getPlayer().getUniqueId();sessions.remove(uuid);if(updates!=null)updates.leave(uuid);if(linking!=null)submit(()->{linking.leave(uuid);return null;});}
    public void cancel(CommandSender sender){String actor=actor(sender);submit(()->{linking.cancel(actor);message(sender,"cancelled",Map.of());return null;}).exceptionally(e->{failed(sender,e);return null;});}
    public boolean ready(){return ready&&!closing;}public Settings settings(){return configuration.active().settings();}public Messages messages(){return configuration.active().messages();}
    public Configuration configuration(){return configuration;}
    public dev.dydiscord.roles.RoleEngine roles(){return roles;}
    public dev.dydiscord.boost.BoostBridge boosts(){return boosts;}
    public dev.dydiscord.chat.ChatBridge chat(){return chat;}public dev.dydiscord.chat.ChatEvents chatEvents(){return chatEvents;}
    public Store store(){return store;}public Linking linking(){return linking;}public Audit audit(){return audit;}public Bot bot(){return bot;}public ConsoleBridge console(){return console;}public Clock clock(){return clock;}
    public String actor(CommandSender sender){return sender instanceof Player p?"mc:"+p.getUniqueId():"local:"+sender.getName();}
    public String language(CommandSender sender){return Messages.language(settings().language(),sender instanceof Player p?preferences.get(p.getUniqueId()):"auto",sender instanceof Player p?p.getLocale():null);}
    public <T> CompletableFuture<T> submit(Callable<T> action){
        CompletableFuture<T> result=new CompletableFuture<>();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        if(closing){result.completeExceptionally(new RejectedExecutionException());return result;}
        try{io.execute(()->{if(closing||System.nanoTime()>deadline){result.completeExceptionally(new TimeoutException());return;}try{result.complete(action.call());}catch(Throwable e){result.completeExceptionally(e);}});}catch(RejectedExecutionException e){result.completeExceptionally(e);}return result;
    }
    public void onMain(Runnable action){if(closing)return;try{getServer().getScheduler().runTask(this,()->{if(!closing)action.run();});}catch(IllegalStateException e){if(!closing)error(e);}}
    public <T> T mainValue(Callable<T> action)throws Exception{
        if(closing)throw new RejectedExecutionException();CompletableFuture<T> future=new CompletableFuture<>();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);onMain(()->{if(future.isDone()||System.nanoTime()>deadline){future.completeExceptionally(new TimeoutException());return;}try{future.complete(action.call());}catch(Exception e){future.completeExceptionally(e);}});try{return future.get(3,TimeUnit.SECONDS);}catch(TimeoutException e){future.cancel(false);throw e;}
    }
    public boolean validOwner(Linking.Code code)throws Exception{return mainValue(()->{Player p=getServer().getPlayer(code.uuid());return p!=null&&p.isOnline()&&sessions.getOrDefault(code.uuid(),-1L)==code.session()&&p.hasPermission("dydiscord.link");});}
    public boolean sessionValid(Linking.Code code){return sessions.getOrDefault(code.uuid(),-1L)==code.session();}
    public void notifyLinked(Store.Link link){if(roles!=null)roles.enqueue(link.uuid());onMain(()->{Player p=getServer().getPlayer(link.uuid());if(p!=null)message(p,"linked",Map.of("name",link.name(),"discord",discordName(link,language(p))));});}
    public void message(CommandSender sender,String key,Map<String,String> values){
        if(!Bukkit.isPrimaryThread()){onMain(()->message(sender,key,values));return;}if(sender instanceof Player p&&!p.isOnline()||closing)return;
        if(configuration.active()==null){sender.sendMessage("DyDiscord is initializing.");return;}
        String prefix=settings().prefix().replaceAll("&([0-9a-fk-orA-FK-OR])","§$1");String text=prefix+" "+messages().render(false,language(sender),key,values);
        if(sender instanceof Player p){TextComponent component=new TextComponent(TextComponent.fromLegacyText(text));
            if(key.equals("invite"))component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,values.get("invite_url")));
            if(key.equals("update-available"))component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,values.get("url")));
            if(key.equals("panel-link"))component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,values.get("url")));
            if(key.equals("link-code"))component.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,values.get("code")));
            if(key.equals("confirm"))component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/dydiscord "+dev.dydiscord.command.Words.display("confirm",language(sender))+" "+values.get("token")));
            p.spigot().sendMessage(component);
        }else sender.sendMessage(ChatColor.stripColor(text));
    }
    public void helpHeader(CommandSender sender){sender.sendMessage("§7§m----------§r "+settings().prefix().replaceAll("&([0-9a-fk-orA-FK-OR])","§$1")+" §e"+messages().get(false,language(sender),"help")+" §7§m----------");}
    public String error(Throwable failure){Throwable e=failure;while((e instanceof CompletionException||e instanceof ExecutionException)&&e.getCause()!=null)e=e.getCause();String detail="";for(Throwable cause=e;cause!=null;cause=cause.getCause()){String m=cause.getMessage();if(m!=null&&m.matches("Invalid (setting|message|file): [a-zA-Z0-9_./:-]+")){detail="; "+m;break;}}String id=UUID.randomUUID().toString().substring(0,8);getLogger().warning("Event "+id+": "+e.getClass().getSimpleName()+detail+" (sensitive values withheld).");return id;}
    private void failed(CommandSender sender,Throwable failure){if(closing)return;Throwable e=failure;while(e.getCause()!=null&&(e instanceof CompletionException||e instanceof ExecutionException))e=e.getCause();if(e instanceof Linking.Rejected r)message(sender,r.key,Map.of());else if(e instanceof IllegalArgumentException)message(sender,"usage",Map.of());else message(sender,"failed",Map.of("event",error(e)));}
    public void execute(CommandSender sender,Definition d,String[] args){
        if(d.key().startsWith("roles-")){roleCommands.execute(sender,d,args);return;}
        UUID uuid=sender instanceof Player p?p.getUniqueId():null;String name=sender.getName();String actor=actor(sender);String effectiveLanguage=language(sender);long session=uuid==null?-1:sessions.getOrDefault(uuid,-1L);
        if(d.key().equals("invite-show")){String invite=settings().invite();message(sender,invite.isEmpty()?"invite-not-set":"invite",invite.isEmpty()?Map.of():Map.of("invite_url",invite));return;}
        submit(()->{
            if(!mainValue(()->sender.hasPermission(d.permission())&&(!(sender instanceof Player p)||p.isOnline()&&sessions.getOrDefault(uuid,-2L)==session)))throw new Linking.Rejected("no-permission");
            switch(d.key()){
                case "link"->{if(sessions.getOrDefault(uuid,-2L)!=session)throw new Linking.Rejected("invalid-code");Linking.Issued issued=linking.issue(uuid,name,session,settings(),bot.usable());message(sender,"link-code",Map.of("code",issued.code(),"minutes",Integer.toString(issued.seconds()/60)));}
                case "status"->{Optional<Store.Link> found=store.byUuid(uuid);if(found.isEmpty())message(sender,"not-linked",Map.of());else account(sender,found.get());}
                case "unlink","admin-unlink"->{Store.Link target;if(d.key().equals("admin-unlink")){if(args[1].length()>256||Arrays.stream(args).anyMatch(s->s.chars().anyMatch(Character::isISOControl)))throw new IllegalArgumentException();target=store.byUuid(UUID.fromString(args[0])).orElseThrow(()->new Linking.Rejected("not-linked"));}else target=store.byUuid(uuid).orElseThrow(()->new Linking.Rejected("not-linked"));String reason=d.key().equals("admin-unlink")?String.join(" ",Arrays.copyOfRange(args,1,args.length)):"";if(reason.length()>256)throw new IllegalArgumentException();String token=linking.confirm(actor,target,d.permission(),reason,settings().confirmationTimeout());message(sender,"confirm",Map.of("token",token,"seconds",Integer.toString(settings().confirmationTimeout())));}
                case "language"->{if(!Set.of("en","tr","auto").contains(args[0]))throw new IllegalArgumentException();store.preference(uuid,args[0]);preferences.put(uuid,args[0]);message(sender,"language-saved",Map.of("language",messages().get(false,effectiveLanguage,"language-name-"+args[0])));if(!settings().language().equals("auto"))message(sender,"language-forced",Map.of("language",messages().get(false,effectiveLanguage,"language-name-"+settings().language())));}
                case "reload"->{try{Configuration.Snapshot preview=configuration.preview();bot.validateChanges(settings(),preview.settings());Settings before=settings();configuration.apply(preview);if(roles!=null)roles.refresh();if(boosts!=null)boosts.refresh();if(chat!=null)chat.invalidate();if(updates!=null)updates.refresh();translationWarnings();linking.invalidate();console.invalidate();if(before.botEnabled()!=settings().botEnabled()){if(settings().botEnabled())bot.restart();else bot.stop();}if(before.panelEnabled()!=settings().panelEnabled())bot.reconcilePanelMode();if(before.needsRestart(settings()))message(sender,"restart-required",Map.of());if(!bot.usable())message(sender,"discord-validation-pending",Map.of());message(sender,"reload-success",Map.of());getLogger().info("Configuration reloaded successfully!");}catch(Exception e){message(sender,"reload-invalid",Map.of());error(e);}}
                case "admin-status","doctor"->{for(String line:bot.diagnostics(d.key().equals("doctor"),effectiveLanguage))message(sender,"status-line",Map.of("status",line));}
                case "bot-restart"->{linking.invalidate();console.invalidate();bot.restart();message(sender,"saved",Map.of());}
                case "panel-send","panel-refresh"->{bot.refreshPanel();message(sender,"saved",Map.of());}
                case "role-list"->{for(String id:settings().roles())message(sender,"status-line",Map.of("status",id+" "+bot.roleName(id)));}
                default->change(sender,actor,d.key(),args);
            }return null;
        }).exceptionally(e->{failed(sender,e);return null;});
    }
    private String discordName(Store.Link link,String language){return link.discordName().isBlank()?messages().get(false,language,"discord-account"):link.discordName();}
    private void account(CommandSender sender,Store.Link link){message(sender,"account",Map.of("name",link.name(),"discord",discordName(link,language(sender))));}
    public void confirm(CommandSender sender,String token){String actor=actor(sender);submit(()->{
        Linking.Confirmation pending=linking.pending(token,actor).orElseThrow(()->new Linking.Rejected("confirmation-invalid"));boolean permitted=mainValue(()->sender.hasPermission(pending.permission())&&(!(sender instanceof Player p)||p.isOnline()));
        boolean success=linking.remove(token,actor,permission->{try{return permitted&&permission.equals(pending.permission())&&mainValue(()->sender.hasPermission(permission)&&(!(sender instanceof Player p)||p.isOnline()));}catch(Exception e){return false;}},confirmation->{if(confirmation.permission().equals("dydiscord.admin.unlink"))try{audit.write(actor,"unlink",confirmation.target().uuid().toString(),"accepted reason="+console.buffer().sanitize(confirmation.reason()));}catch(Exception e){throw new CompletionException(e);}});
        message(sender,success?"unlinked":"confirmation-invalid",Map.of());return null;
    }).exceptionally(e->{failed(sender,e);return null;});}
    private void change(CommandSender sender,String actor,String key,String[] args)throws Exception{
        String file="bot.yml",path;Object value;Settings before=settings();
        switch(key){
            case "invite"->{file="config.yml";path="discord.invite-url";Settings.validateInvite(args[0]);value=args[0];}
            case "global-language"->{file="config.yml";path="lang";if(!Set.of("en","tr","auto").contains(args[0]))throw new IllegalArgumentException();value=args[0];}
            case "bot-enable","bot-disable"->{path="enabled";value=key.equals("bot-enable");if((boolean)value&&(before.token().isBlank()||before.guild().isEmpty()))throw new Linking.Rejected("bot-setup-incomplete");}
            case "panel-enable","panel-disable"->{path="panel.enabled";value=key.equals("panel-enable");}
            case "panel-channel","console-channel"->{if(!Settings.snowflake(args[0]))throw new IllegalArgumentException();bot.validateChannel(args[0],key.equals("console-channel"));path=key.equals("panel-channel")?"panel.channel-id":"console.channel-id";value=args[0];if(key.equals("panel-channel")&&args[0].equals(before.consoleChannel())||key.equals("console-channel")&&args[0].equals(before.panelChannel()))throw new IllegalArgumentException();}
            case "console-enable","console-disable"->{path="console.enabled";value=key.equals("console-enable");if((boolean)value)bot.validateChannel(before.consoleChannel(),true);}
            case "commands-enable","commands-disable"->{path="console.commands.enabled";value=key.equals("commands-enable");if((boolean)value)bot.validateChannel(before.consoleChannel(),true);}
            case "administrator-on","administrator-off"->{path="console.commands.allow-administrator";value=key.equals("administrator-on");}
            case "role-add","role-remove"->{if(!Settings.snowflake(args[0])||!bot.usable())throw new IllegalArgumentException();if(key.equals("role-add"))bot.validateRole(args[0]);Set<String> roles=new TreeSet<>(before.roles());if(key.equals("role-add"))roles.add(args[0]);else roles.remove(args[0]);path="console.commands.allowed-role-ids";value=new ArrayList<>(roles);}
            default->throw new IllegalArgumentException();
        }
        if(key.startsWith("console")||key.startsWith("commands")||key.startsWith("role")||key.startsWith("administrator"))audit.write(actor,"policy-change",path,"accepted");
        configuration.change(file,path,value,candidate->bot.validateChanges(before,candidate));if(roles!=null)roles.refresh();if(boosts!=null)boosts.refresh();if(chat!=null)chat.invalidate();translationWarnings();linking.invalidate();console.invalidate();
        if(before.panelEnabled()!=settings().panelEnabled())bot.reconcilePanelMode();
        if(key.startsWith("bot-")){if(settings().botEnabled())bot.restart();else bot.stop();}
        if(before.needsRestart(settings())&&!key.startsWith("bot-"))message(sender,"restart-required",Map.of());message(sender,"saved",Map.of());
    }
}
