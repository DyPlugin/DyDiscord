package dev.dydiscord.discord;

import dev.dydiscord.*;
import dev.dydiscord.config.*;
import dev.dydiscord.command.Words;
import dev.dydiscord.storage.Store;
import dev.dydiscord.linking.Linking;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.*;
import net.dv8tion.jda.api.events.interaction.command.*;
import net.dv8tion.jda.api.events.interaction.component.*;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.*;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.*;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.*;
import net.dv8tion.jda.api.modals.Modal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.time.*;

public final class Bot {
    public enum State { DISABLED, STARTING, CONNECTED, DEGRADED, STOPPING, FAILED }
    private record Form(String actor,long generation,Instant expires,Store.Panel source) {}
    private final DyDiscord plugin;private final PanelService panel;private final AtomicLong generation=new AtomicLong();private final Object lifecycle=new Object();
    private final Map<String,Form> forms=new ConcurrentHashMap<>();
    private volatile JDA jda;private volatile State state=State.DISABLED;private volatile Settings applied;private volatile String activeLanguage="";private volatile boolean content,members;
    public boolean roleMemberEvents(){return members&&usable();}
    private boolean intentsReady(){return (!applied.prefixEnabled()&&!applied.commandsEnabled()&&!applied.chat().toMinecraft()||content)&&(!applied.memberIntentRequested()||members);}
    private volatile Instant lastWarning=Instant.EPOCH;
    private volatile Instant nextInviteAttempt=Instant.EPOCH;private volatile String inviteGuild="";
    private final DiscordAccess access=new DiscordAccess();
    private final IngressLimit prefixLimit,interactionLimit,modalLimit;
    private final StaffNotices staff;
    public StaffNotices staff(){return staff;}
    JDA client(){return jda;}
    public Bot(DyDiscord plugin)throws Exception{this.plugin=plugin;staff=new StaffNotices(plugin,this);panel=new PanelService(plugin.store());prefixLimit=new IngressLimit(plugin.clock());interactionLimit=new IngressLimit(plugin.clock());modalLimit=new IngressLimit(plugin.clock());}
    public State state(){return state;}public long generation(){return generation.get();}public Settings applied(){return applied;}public boolean usable(){return jda!=null&&jda.getStatus()==JDA.Status.CONNECTED&&!activeLanguage.isEmpty()&&(state==State.CONNECTED||state==State.DEGRADED);}public PanelService panel(){return panel;}public boolean content(){return content;}
    public boolean alive(long g){return generation.get()==g&&plugin.ready()&&jda!=null;}
    public void start(){synchronized(lifecycle){startLocked();}}
    public void connectIfConfigured(){Settings s=plugin.settings();if(s.botEnabled()&&jda==null&&!s.token().isBlank()&&!s.guild().isEmpty())start();}
    public boolean restartRequired(){return jda!=null&&applied!=null&&applied.needsRestart(plugin.settings());}
    private synchronized void startLocked(){
        if(!plugin.ready())return;
        Settings s=plugin.settings();if(!s.botEnabled()){state=State.DISABLED;plugin.getLogger().info("Discord bot is disabled. Configure bot.yml to connect.");return;}
        if(s.token().isBlank()||s.guild().isEmpty()){state=State.FAILED;plugin.warn("setup-bot-unavailable");return;}
        if(jda!=null)return;applied=s;activeLanguage="";state=State.STARTING;long g=generation.incrementAndGet();
        content=s.prefixEnabled()||s.commandsEnabled()||s.chat().toMinecraft();members=s.memberIntentRequested();connect(s,g,content);
    }
    private void connect(Settings s,long g,boolean withContent){
        try{
            EnumSet<GatewayIntent> intents=EnumSet.of(GatewayIntent.GUILD_MESSAGES);if(withContent)intents.add(GatewayIntent.MESSAGE_CONTENT);if(members)intents.add(GatewayIntent.GUILD_MEMBERS);if(s.moderation().banFromDiscord())intents.add(GatewayIntent.GUILD_MODERATION);
            jda=JDABuilder.createLight(s.token(),intents).setEnableShutdownHook(false).setAutoReconnect(true).setRequestTimeoutRetry(false)
                .setHttpClientBuilder(new okhttp3.OkHttpClient.Builder().connectTimeout(4,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS))
                .addEventListeners(new Events(g)).build();
            plugin.console().buffer().token(s.token());
        }catch(Exception e){jda=null;state=State.FAILED;plugin.warn("setup-connection-failed");plugin.error(e);}
    }
    public void stop(){
      synchronized(lifecycle){
        final JDA old;final long stopped;
        synchronized(this){
            if(plugin.roles()!=null)plugin.roles().refresh();if(plugin.boosts()!=null)plugin.boosts().refresh();if(plugin.chat()!=null)plugin.chat().invalidate();if(plugin.linking()!=null)plugin.linking().invalidate();if(plugin.console()!=null)plugin.console().invalidate();
            stopped=generation.incrementAndGet();state=State.STOPPING;forms.clear();prefixLimit.clear();interactionLimit.clear();modalLimit.clear();activeLanguage="";old=jda;jda=null;
        }
        if(old!=null){old.removeEventListener(old.getRegisteredListeners().toArray());old.shutdownNow();try{if(!old.awaitShutdown(4,TimeUnit.SECONDS))plugin.getLogger().warning(dev.dydiscord.language.ConsoleDiagnostics.text(plugin,"error-discord-shutdown",java.util.Map.of()));}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        synchronized(this){if(generation()==stopped)state=State.DISABLED;}
      }
    }
    public void restart(){synchronized(lifecycle){stop();start();}}
    public <T> T await(RestAction<T> action)throws Exception{try{return action.timeout(10,TimeUnit.SECONDS).submit().get(12,TimeUnit.SECONDS);}catch(ExecutionException e){if(e.getCause() instanceof Exception cause)throw cause;throw e;}}
    public Guild guild(){return jda==null||applied==null?null:jda.getGuildById(applied.guild());}
    public TextChannel channel(String id)throws Exception{
        if(id.isEmpty())throw new SetupProblem(SetupProblem.Code.CHANNEL_MISSING);
        if(!usable())throw new SetupProblem(SetupProblem.Code.BOT_UNAVAILABLE);
        // Use REST rather than a stale permission cache for security decisions.
        TextChannel channel=guild()==null?null:guild().getTextChannelById(id);if(channel==null)throw new SetupProblem(SetupProblem.Code.CHANNEL_INVISIBLE);return channel;
    }
    public void validateChannel(String id,boolean console)throws Exception{
        validateChannel(id,console,plugin.settings());
    }
    private void validateChannel(String id,boolean console,Settings policy)throws Exception{
        TextChannel channel=channel(id);Member self=await(channel.getGuild().retrieveMemberById(jda.getSelfUser().getId()).useCache(false));
        DiscordAccess.Channel current=access.channel(applied,id);Set<String> roles=self.getRoles().stream().map(Role::getId).collect(java.util.stream.Collectors.toSet());long required=console?Permission.getRaw(Permission.VIEW_CHANNEL,Permission.MESSAGE_SEND):Permission.getRaw(Permission.VIEW_CHANNEL,Permission.MESSAGE_SEND,Permission.MESSAGE_EMBED_LINKS,Permission.MESSAGE_HISTORY);
        if((current.permissions(self.getId(),roles)&required)!=required)throw new SetupProblem(SetupProblem.Code.CHANNEL_PERMISSIONS);

    }
    public static boolean privateChannel(TextChannel channel,Settings settings){
        Guild guild=channel.getGuild();Role everyone=guild.getPublicRole();long view=Permission.VIEW_CHANNEL.getRawValue();
        long base=everyone.getPermissionsRaw();PermissionOverride publicOverride=channel.getPermissionOverride(everyone);
        if(publicOverride!=null)base=(base&~publicOverride.getDeniedRaw())|publicOverride.getAllowedRaw();if((base&view)!=0)return false;
        for(Role role:guild.getRoles()){
            if(role.isPublicRole()||role.isManaged()&&role.getId().equals(guild.getSelfMember().getRoles().stream().filter(Role::isManaged).map(Role::getId).findFirst().orElse("")))continue;
            if(role.hasPermission(Permission.ADMINISTRATOR))continue;long effective=(everyone.getPermissionsRaw()|role.getPermissionsRaw());
            if(publicOverride!=null)effective=(effective&~publicOverride.getDeniedRaw())|publicOverride.getAllowedRaw();PermissionOverride own=channel.getPermissionOverride(role);if(own!=null)effective=(effective&~own.getDeniedRaw())|own.getAllowedRaw();
            if((effective&view)!=0&&!settings.roles().contains(role.getId()))return false;
        }
        // Individual allow overrides cannot be proven private without checking every member; fail closed.
        return channel.getMemberPermissionOverrides().stream().noneMatch(o->(o.getAllowedRaw()&view)!=0&&!o.getId().equals(guild.getSelfMember().getId()));
    }
    public void validateRole(String id)throws Exception{if(!usable()||!access.role(applied,id))throw new IllegalArgumentException("Role unavailable");}
    public String consoleAuthority(Member member,Settings policy)throws Exception{DiscordAccess.Channel current=access.channel(applied,policy.consoleChannel());Set<String> roles=member.getRoles().stream().map(Role::getId).collect(java.util.stream.Collectors.toSet());if((current.permissions(member.getId(),roles)&Permission.VIEW_CHANNEL.getRawValue())==0)return "";if(policy.administrator()&&(current.owner().equals(member.getId())||current.administrator(roles)))return "administrator";return roles.stream().filter(policy.roles()::contains).sorted().findFirst().map(id->"role:"+id).orElse("");}
    public String roleName(String id){Guild guild=guild();Role r=guild==null?null:guild.getRoleById(id);return r==null?"(unavailable)":r.getName();}
    public void validateChanges(Settings before,Settings next)throws Exception{
        if(plugin.roles()!=null)plugin.roles().validateSettings(next);
        boolean chatChanged=!before.chat().channel().equals(next.chat().channel())||!before.chat().toDiscord()&&next.chat().toDiscord()||!before.chat().toMinecraft()&&next.chat().toMinecraft()||!before.chat().join()&&next.chat().join()||!before.chat().quit()&&next.chat().quit()||!before.chat().death()&&next.chat().death();
        boolean sensitive=chatChanged||!before.panelChannel().equals(next.panelChannel())||!before.consoleChannel().equals(next.consoleChannel())||!before.roles().equals(next.roles())||before.administrator()!=next.administrator()||!before.consoleEnabled()&&next.consoleEnabled()||!before.commandsEnabled()&&next.commandsEnabled();
        // Offline setup is staged locally. Every panel/relay/console operation still
        // validates live access before sending or dispatching; no connection is started here.
        if(sensitive&&!usable())return;
        if(sensitive){
            if(!next.guild().equals(applied.guild()))throw new SetupProblem(SetupProblem.Code.RESTART_REQUIRED);
            if(chatChanged&&!next.chat().channel().isEmpty())validateChannel(next.chat().channel(),false);
            if(!before.panelChannel().equals(next.panelChannel())&&!next.panelChannel().isEmpty())validateChannel(next.panelChannel(),false);
            boolean consolePolicy=!before.consoleChannel().equals(next.consoleChannel())||!before.roles().equals(next.roles())||before.administrator()!=next.administrator()||!before.consoleEnabled()&&next.consoleEnabled()||!before.commandsEnabled()&&next.commandsEnabled();
            if(consolePolicy&&next.consoleEnabled()&&!next.consoleChannel().isEmpty())validateChannel(next.consoleChannel(),true,next);
            if(!before.roles().equals(next.roles()))for(String role:next.roles())validateRole(role);
        }
    }
    public List<String> diagnostics(boolean doctor,String language){Settings s=plugin.settings();List<String> lines=new ArrayList<>();boolean tr=language.equals("tr");
        lines.add("DyDiscord "+plugin.getDescription().getVersion()+" | "+(tr?"Bot: ":"Bot: ")+plugin.messages().get(false,language,"state-"+state.name().toLowerCase(Locale.ROOT))+" | "+(tr?"Hesap kayıtları: ":"Data: ")+(plugin.store().writable()?(tr?"kullanılabilir":"healthy"):(tr?"yalnızca okunabiliyor":"read-only")));
        String source=tr?switch(s.tokenSource()){case "environment"->"ortam değişkeni";case "file"->"bot.yml dosyası";default->"ayarlanmamış";}:s.tokenSource();
        lines.add((tr?"Bot anahtarının kaynağı: ":"Token source: ")+source+(tr?" (gizli) | Kullanılan dil: ":" (hidden) | Applied language: ")+(activeLanguage.isEmpty()?(tr?"henüz uygulanmadı":"pending"):plugin.messages().get(false,language,"language-name-"+activeLanguage))+(tr?" | Seçilen dil: ":" | Requested language: ")+plugin.messages().get(false,language,"language-name-"+s.botLanguage()));
        lines.add("Panel: "+(!s.panelEnabled()?(tr?"kapalı":"disabled"):panel.current()==null?(tr?"henüz gönderilmedi":"missing"):panel.current().message().equals("pending")?(tr?"yenilenmesi gerekiyor":"repair required"):(tr?"kayıtlı":"recorded"))+(tr?" | Konsol kayıtları: ":" | Console relay: ")+toggle(s.consoleEnabled(),tr)+(tr?" | Discord'dan komut çalıştırma: ":" | Commands: ")+toggle(s.commandsEnabled(),tr)+" | Message Content: "+toggle(content,tr));
        if(!plugin.console().attached())lines.add(tr?"Sunucu kayıtları Discord'a aktarılamıyor; kayıt altyapısına erişilemiyor.":"Log relay: logging backend unavailable.");
        if(applied!=null&&applied.needsRestart(s))lines.add(tr?"Bot yeniden başlatılmalı.":"Bot restart required.");
        String chatProblem=s.chat().toDiscord()&&plugin.chatEvents()!=null?plugin.chatEvents().problem(language):"";
        lines.add((tr?"Sohbet: ":"Chat: ")+(chatProblem.isEmpty()?toggle(s.chat().toDiscord(),tr):(tr?"duraklatıldı":"paused"))+" MC → DC | "+toggle(s.chat().toMinecraft(),tr)+" DC → MC"+(plugin.chatEvents()==null?"":" | "+plugin.chatEvents().adapter()));
        if(!chatProblem.isEmpty())lines.add(chatProblem);
        if(doctor){if(s.token().isEmpty())lines.add((tr?"Doldurulması gerekiyor: ":"Missing: ")+"bot.yml token");if(s.guild().isEmpty())lines.add((tr?"Doldurulması gerekiyor: ":"Missing: ")+"bot.yml guild-id");if(s.panelEnabled()&&s.panelChannel().isEmpty())lines.add((tr?"Doldurulması gerekiyor: ":"Missing: ")+"bot.yml panel.channel-id");if(!content&&(s.prefixEnabled()||s.commandsEnabled()||s.chat().toMinecraft()))lines.add(tr?"Developer Portal'da Message Content iznini açın. Bu izin olmadan yalnızca / komutları ve hesap paneli kullanılabilir.":"Enable Message Content intent or use slash/panel only.");
            if(usable()){for(String id:List.of(s.panelEnabled()?s.panelChannel():"",s.consoleChannel(),s.chat().channel()))if(!id.isEmpty())try{validateChannel(id,id.equals(s.consoleChannel()));lines.add((tr?"Kanal ":"Channel ")+id+(tr?": doğrulandı":": validated"));}catch(Exception e){lines.add((tr?"Kanal ":"Channel ")+id+(tr?": erişilemiyor, izin eksik veya görünürlük güvenli değil":": unavailable, missing permissions or unsafe visibility"));}}
        }
        if(s.roleSync().enabled()){boolean available=plugin.roles()!=null&&plugin.roles().failure().isEmpty()&&usable();lines.add((tr?"Rol eşitlemesi: ":"Role synchronization: ")+(available?(tr?"kullanılabilir":"available"):(tr?"bekliyor veya kurulum sorunu var":"waiting or setup issue")));}
        if(s.linkRewards().enabled())lines.add((tr?"İlk eşleme ödülleri: ":"First-link rewards: ")+(plugin.linkRewards()!=null&&plugin.linkRewards().healthy()?(tr?"kayıt sistemi kullanılabilir":"receipt store available"):(tr?"kayıt sistemi kullanılamıyor":"receipt store unavailable")));
        if(s.boostRewards().enabled()){
            boolean healthy=plugin.boosts()!=null&&plugin.boosts().healthy();int pending=plugin.boosts()==null?0:plugin.boosts().pending();
            lines.add(tr?"Boost ödülleri: "+(healthy?"kullanılabilir":"kayıt sorunu")+" | Bekleyen: "+pending:"Boost rewards: "+(healthy?"available":"receipt store unavailable")+" | Pending: "+pending);
            if(!members)lines.add(tr?"Server Members intent kullanılamıyor; boost değişimleri aralıklı kontrol ediliyor. Developer Portal ayarını kontrol edin.":"Server Members intent unavailable; boost changes use periodic checks. Check Developer Portal.");
        }return lines;
    }
    private static String toggle(boolean enabled,boolean tr){return tr?(enabled?"açık":"kapalı"):Boolean.toString(enabled);}
    private boolean informationCurrent(long session,Settings settings,String channel){return alive(session)&&usable()&&plugin.settings()==settings&&settings.guild().equals(applied.guild())&&settings.community().allows("server",channel);}
    private MessageEmbed information(String action,long session,Settings settings,String channel)throws Exception{
        if(!informationCurrent(session,settings,channel))throw new IllegalStateException("Information request expired");
        MessageEmbed card=switch(action){
            case "server"->{var snapshot=plugin.mainValue(()->ServerInfo.capture(plugin));yield ServerInfo.render(plugin.messages(),activeLanguage(),snapshot,settings.community());}
            case "players"->{var snapshot=plugin.mainValue(()->ServerInfo.capturePlayers(plugin));yield CommunityInfo.players(plugin.messages(),activeLanguage(),snapshot);}
            case "ip"->CommunityInfo.addresses(plugin.messages(),activeLanguage(),settings.community());
            case "links"->CommunityInfo.links(plugin.messages(),activeLanguage(),settings.community());
            default->throw new IllegalArgumentException("Unknown information command");
        };
        if(!informationCurrent(session,settings,channel))throw new IllegalStateException("Information request expired");return card;
    }
    public int chatSlowmodeSeconds(){Guild guild=guild();TextChannel target=guild==null?null:guild.getTextChannelById(plugin.settings().chat().channel());return target==null?0:Math.max(0,target.getSlowmode());}
    public String activeLanguage(){return activeLanguage.isEmpty()?plugin.settings().botLanguage():activeLanguage;}
    /** Called on the plugin I/O worker. Reuses the persisted invite, never creates per player. */
    public String invitation(){
        Settings captured=plugin.settings();if(!captured.invite().isEmpty()||!usable())return captured.invite();
        long session=generation();Guild guild=guild();if(guild==null||!guild.getId().equals(captured.guild()))return "";
        Instant now=plugin.clock().instant();if(inviteGuild.equals(captured.guild())&&now.isBefore(nextInviteAttempt))return "";inviteGuild=captured.guild();nextInviteAttempt=now.plusSeconds(60);
        TextChannel target=captured.panelChannel().isEmpty()?null:guild.getTextChannelById(captured.panelChannel());
        if(target==null||target.getId().equals(captured.consoleChannel())||!guild.getSelfMember().hasPermission(target,Permission.VIEW_CHANNEL,Permission.CREATE_INSTANT_INVITE))
            target=guild.getTextChannels().stream().filter(c->!c.getId().equals(captured.consoleChannel())&&guild.getSelfMember().hasPermission(c,Permission.VIEW_CHANNEL,Permission.CREATE_INSTANT_INVITE)).findFirst().orElse(null);
        if(target==null){plugin.getLogger().warning(plugin.messages().get(false,captured.botLanguage(),"invite-auto-unavailable"));return "";}
        try{
            Invite invite=await(target.createInvite().setMaxAge(0).setMaxUses(0).setUnique(false).setCheck(()->alive(session)&&plugin.settings().invite().isEmpty()&&plugin.settings().guild().equals(captured.guild())));
            String url=invite.getUrl();Settings.validateInvite(url);
            if(!alive(session)||!plugin.settings().guild().equals(captured.guild()))return "";
            if(plugin.settings().invite().isEmpty())plugin.configuration().inviteIfMissing(captured.guild(),url,next->{if(!alive(session)||!next.guild().equals(captured.guild()))throw new IllegalStateException("Expired invitation setup");validateChanges(plugin.settings(),next);});
            return plugin.settings().invite();
        }catch(Exception failure){plugin.error(failure);plugin.getLogger().warning(plugin.messages().get(false,captured.botLanguage(),"invite-auto-unavailable"));return "";}
    }
    public void sendChat(dev.dydiscord.chat.ChatBridge.Outgoing message,java.util.function.BooleanSupplier current)throws Exception{
        TextChannel target=channel(plugin.settings().chat().channel());Member self=target.getGuild().getSelfMember();if(!self.hasPermission(target,Permission.VIEW_CHANNEL,Permission.MESSAGE_SEND)||message.color()!=null&&!self.hasPermission(target,Permission.MESSAGE_EMBED_LINKS))throw new IllegalStateException("Chat channel unavailable");
        var action=message.color()==null?target.sendMessage(message.text()):target.sendMessageEmbeds(new EmbedBuilder().setDescription(message.text()).setColor(message.color()).build());
        action.setAllowedMentions(List.of()).mentionRepliedUser(false).setCheck(current::getAsBoolean).timeout(2,TimeUnit.SECONDS).submit().get(3,TimeUnit.SECONDS);
    }
    public void serverShutdownNotice(boolean stopping){
        Settings settings=plugin.settings();if(!stopping||settings.consoleChannel().isEmpty()||!usable())return;
        long session=generation(),deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);var worker=Executors.newSingleThreadExecutor(dev.dydiscord.util.PluginThreads.factory("DyDiscord-shutdown-notice"));
        try{boolean sent=dev.dydiscord.chat.ShutdownNotice.send(true,true,()->CompletableFuture.runAsync(()->{
            try{validateChannel(settings.consoleChannel(),true);if(System.nanoTime()>=deadline||generation()!=session)throw new TimeoutException();
                TextChannel target=channel(settings.consoleChannel());if(!target.getGuild().getSelfMember().hasPermission(target,Permission.MESSAGE_EMBED_LINKS))throw new IllegalStateException("Shutdown embed permission unavailable");
                target.sendMessage(PanelView.create(Cards.text(text("server-stopping",Map.of()),0xFFAA00))).setAllowedMentions(List.of()).mentionRepliedUser(false).setCheck(()->System.nanoTime()<deadline&&generation()==session).timeout(1,TimeUnit.SECONDS).submit().get(1500,TimeUnit.MILLISECONDS);
            }catch(Exception failure){throw new CompletionException(failure);}
        },worker),2000);if(!sent)plugin.getLogger().info("Shutdown notice could not be confirmed before its deadline; server shutdown continues.");}finally{worker.shutdownNow();}
    }
    public String text(String key,Map<String,String> values){String lang=activeLanguage.isEmpty()?plugin.settings().botLanguage():activeLanguage;return plugin.messages().render(true,lang,key,values);}
    private MessageEmbed embed(String key,Map<String,String> values){return new EmbedBuilder().setColor(0xFF55FF).setDescription(text(key,values)).build();}
    private net.dv8tion.jda.api.components.container.Container panelCard(){String lang=activeLanguage.isEmpty()?plugin.settings().botLanguage():activeLanguage;String image=plugin.settings().panelImage().isEmpty()?guild().getIconUrl():plugin.settings().panelImage();return PanelView.card(plugin.messages(),lang,plugin.settings().serverName(),image);}
    public void refreshPanel()throws Exception{if(!plugin.settings().panelEnabled())throw new Linking.Rejected("panel-disabled");if(!usable())throw new IllegalStateException("Bot unavailable");long g=generation();Settings s=plugin.settings();validateChannel(s.panelChannel(),false);panel.refresh(applied.guild(),s.panelChannel(),transport(g));}
    public void reconcilePanelMode(){
        if(!usable()||applied==null||!applied.guild().equals(plugin.settings().guild()))return;
        try{if(panel.current()!=null&&!panel.current().guild().equals(applied.guild()))throw new IllegalStateException("Panel guild mismatch");if(!plugin.settings().panelEnabled()){if(panel.current()!=null)panel.disable(transport(generation()));}else if(!plugin.settings().panelChannel().isEmpty())refreshPanel();}
        catch(Exception failure){plugin.error(failure);plugin.getLogger().warning(dev.dydiscord.language.ConsoleDiagnostics.text(plugin,"error-panel-pending",java.util.Map.of()));}
    }
    public String commandPrompt(String action){if(!Set.of("link","account","unlink").contains(action))throw new IllegalArgumentException();return text("commands-prompt",Map.of("command","/"+Words.display(action,activeLanguage())));}
    private String accountHelp(){return text(plugin.settings().panelEnabled()?"help":"commands-help",Map.of());}
    private String completeHelp(){String help=accountHelp()+"\n\n"+text("help-server",Map.of())+"\n"+text("help-community",Map.of());return help.length()>2000?help.substring(0,1999)+"…":help;}
    private PanelService.Transport transport(long g){return new PanelService.Transport(){
        private void check(){if(!alive(g))throw new IllegalStateException("Expired bot session");}
        private TextChannel ch(String id)throws Exception{check();return channel(id);}
        public boolean exists(Store.Panel p)throws Exception{try{Message m=await(ch(p.channel()).retrieveMessageById(p.message()).setCheck(()->alive(g)));if(!m.getAuthor().getId().equals(jda.getSelfUser().getId()))throw new IllegalStateException("Panel ownership mismatch");return true;}catch(ErrorResponseException e){if(e.getErrorResponse()==ErrorResponse.UNKNOWN_MESSAGE)return false;throw e;}}
        public void update(Store.Panel p)throws Exception{await(ch(p.channel()).editMessageById(p.message(),PanelView.update(panelCard())).setAllowedMentions(List.of()).setCheck(()->alive(g)));}
        public void disable(Store.Panel p)throws Exception{if(p.message().equals("pending"))throw new IllegalStateException("Pending old panel requires repair");try{Message old=await(ch(p.channel()).retrieveMessageById(p.message()).setCheck(()->alive(g)));if(!old.getAuthor().getId().equals(jda.getSelfUser().getId()))throw new IllegalStateException("Panel ownership mismatch");await(old.editMessageComponents(old.getComponentTree().asDisabled()).useComponentsV2(old.isUsingComponentsV2()).setAllowedMentions(List.of()).setCheck(()->alive(g)));}catch(ErrorResponseException e){if(e.getErrorResponse()!=ErrorResponse.UNKNOWN_MESSAGE)throw e;}}
        public List<String> recover(String channel)throws Exception{return await(ch(channel).getHistory().retrievePast(100).setCheck(()->alive(g))).stream().filter(m->PanelView.recognizable(m,jda.getSelfUser().getId())).map(Message::getId).toList();}
        public String create(String channel)throws Exception{return await(ch(channel).sendMessage(PanelView.create(panelCard())).setAllowedMentions(List.of()).setCheck(()->alive(g))).getId();}
    };}
    private void register(long g)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
        Guild guild=guild();if(guild==null)throw new IllegalStateException("Configured guild is unavailable");Set<String> owned=plugin.store().commands(guild.getId());List<Command> existing=awaitRegistration(guild.retrieveCommands(),g,deadline);Set<String> newIds=new HashSet<>();String lang=applied.botLanguage();
        for(String function:List.of("help","link","account","unlink","status","server","players","ip","links")){
            if(!alive(g))throw new IllegalStateException("Expired bot session");
            String name=Words.display(function,lang);if(existing.stream().anyMatch(c->c.getName().equals(name)&&!owned.contains(c.getId())))throw new IllegalStateException("Unmanaged slash name collision");
            String description=plugin.messages().get(true,lang,"slash-"+function);
            Command previous=existing.stream().filter(c->c.getName().equals(name)).findFirst().orElse(null);
            if(previous!=null&&previous.getType()==Command.Type.SLASH&&description.equals(previous.getDescription())&&previous.getOptions().isEmpty()){newIds.add(previous.getId());continue;}
            Command command=awaitRegistration(guild.upsertCommand(Commands.slash(name,description)),g,deadline);if(!alive(g))throw new IllegalStateException("Expired bot session");plugin.store().command(guild.getId(),command.getId());newIds.add(command.getId());
        }
        for(Command command:existing)if(owned.contains(command.getId())&&!newIds.contains(command.getId())){awaitRegistration(guild.deleteCommandById(command.getId()),g,deadline);if(!alive(g))throw new IllegalStateException("Expired bot session");plugin.store().forgetCommand(command.getId());}
        synchronized(this){if(!alive(g))throw new IllegalStateException("Expired bot session");activeLanguage=lang;}
    }
    /** Startup registration shares one bounded budget, including JDA's rate-limit wait. */
    private <T> T awaitRegistration(RestAction<T> action,long session,long deadline)throws Exception{
        long remaining=deadline-System.nanoTime();if(remaining<=0)throw new TimeoutException("Command registration deadline exceeded");
        if(!alive(session))throw new IllegalStateException("Expired bot session");
        var pending=action.setCheck(()->alive(session)&&System.nanoTime()<deadline).timeout(remaining,TimeUnit.NANOSECONDS).submit();
        try{return pending.get(remaining+TimeUnit.SECONDS.toNanos(2),TimeUnit.NANOSECONDS);}
        catch(ExecutionException failure){if(failure.getCause() instanceof Exception cause)throw cause;throw failure;}
        catch(TimeoutException|InterruptedException failure){pending.cancel(true);if(failure instanceof InterruptedException)Thread.currentThread().interrupt();throw failure;}
    }
    private boolean context(Interaction interaction,long g){return alive(g)&&usable()&&interaction.isFromGuild()&&interaction.getGuild()!=null&&interaction.getGuild().getId().equals(applied.guild())&&interaction.getMember()!=null&&!interaction.getUser().isBot();}
    private static Throwable failureCause(Throwable failure){while((failure instanceof CompletionException||failure instanceof ExecutionException)&&failure.getCause()!=null)failure=failure.getCause();return failure;}
    private boolean codeOwner(Linking.Code code,long g){if(!alive(g)||!plugin.sessionValid(code))return false;try{return plugin.validOwner(code)&&alive(g)&&plugin.sessionValid(code);}catch(Exception failure){plugin.error(failure);return false;}}
    private boolean formCurrent(Form form){return form.source==null||plugin.settings().panelEnabled()&&form.source.equals(panel.current())&&form.source.guild().equals(applied.guild())&&form.source.channel().equals(plugin.settings().panelChannel());}
    private Modal form(String actor,long g,Store.Panel source){forms.entrySet().removeIf(e->e.getValue().actor.equals(actor)||!plugin.clock().instant().isBefore(e.getValue().expires));if(forms.size()>=4096)throw new IllegalStateException("Modal capacity reached");String id=UUID.randomUUID().toString();forms.put(id,new Form(actor,g,plugin.clock().instant().plusSeconds(300),source));return Modal.create("dd:form:"+id,text("modal-title",Map.of())).addComponents(Label.of(text("code-label",Map.of()),TextInput.create("code",TextInputStyle.SHORT).setMinLength(6).setMaxLength(16).setRequired(true).build())).build();}
    private void reply(InteractionHook hook,String key,Map<String,String> values){
        var card=net.dv8tion.jda.api.components.container.Container.of(net.dv8tion.jda.api.components.textdisplay.TextDisplay.of(text(key,values))).withAccentColor(0x5865F2);
        hook.editOriginal(PanelView.update(card)).timeout(10,TimeUnit.SECONDS).queue(v->{},plugin::error);
    }
    private void account(InteractionHook hook,String actor,boolean unlink,long g){plugin.submit(()->{
        if(!alive(g))throw new Linking.Rejected("confirmation-invalid");Guild guild=guild();Member member=await(guild.retrieveMemberById(actor).useCache(false));if(!alive(g)||!usable())throw new Linking.Rejected("confirmation-invalid");Optional<Store.Link> record=plugin.store().byDiscord(actor);
        if(record.isEmpty()){reply(hook,"not-linked",Map.of());return null;}Store.Link link=record.get();String username=member.getUser().getName();
        if(!link.discordName().equals(username)&&plugin.store().writable())try{link=plugin.store().updateDiscordName(link,username).orElseThrow(()->new Linking.Rejected("confirmation-invalid"));}catch(java.sql.SQLException cacheFailure){plugin.error(cacheFailure);}
        if(!unlink){
            Set<String> managed=plugin.settings().roleSync().managedIds();List<String> roles=member.getRoles().stream().filter(role->managed.contains(role.getId())).map(Role::getName).toList();
            String body=AccountView.details(plugin.messages(),activeLanguage(),link,username,plugin.settings().accounts(),roles);
            var card=net.dv8tion.jda.api.components.container.Container.of(net.dv8tion.jda.api.components.textdisplay.TextDisplay.of(body)).withAccentColor(0x5865F2);
            hook.editOriginal(PanelView.update(card)).timeout(10,TimeUnit.SECONDS).queue(v->{},plugin::error);
        }
        else {String token=plugin.linking().confirm("dc:"+actor,link,"discord-owner","",plugin.settings().confirmationTimeout());hook.editOriginal(PanelView.update(AccountView.confirmation(plugin.messages(),activeLanguage(),token,plugin.settings().confirmationTimeout()))).timeout(10,TimeUnit.SECONDS).queue(v->{},plugin::error);}
        return null;
    }).exceptionally(e->{Throwable cause=failureCause(e);reply(hook,cause instanceof Linking.Rejected rejected?rejected.key:"failed",cause instanceof Linking.Rejected?Map.of():Map.of("event",plugin.error(cause)));return null;});}
    private final class Events extends ListenerAdapter {
        private final long g;Events(long g){this.g=g;}
        @Override public void onReady(ReadyEvent event){if(generation()!=g)return;plugin.submit(()->{if(!alive(g))return null;try{register(g);synchronized(Bot.this){if(!alive(g))return null;state=intentsReady()?State.CONNECTED:State.DEGRADED;plugin.getLogger().info("Discord bot connected to "+dev.dydiscord.language.Messages.safeMinecraft(guild().getName())+".");}if(plugin.roles()!=null)plugin.roles().refresh();if(plugin.boosts()!=null)plugin.boosts().refresh();if(plugin.giveaways()!=null)plugin.giveaways().refresh();reconcilePanelMode();invitation();}catch(Exception e){degraded(e);}return null;});}
        @Override public void onGuildMemberJoin(GuildMemberJoinEvent event){if(!alive(g)||!usable()||!event.getGuild().getId().equals(applied.guild()))return;if(plugin.roles()!=null)plugin.roles().enqueueMember(event.getUser().getId());if(plugin.boosts()!=null)plugin.boosts().signal(event.getUser().getId());}
        @Override public void onGuildMemberRemove(GuildMemberRemoveEvent event){if(alive(g)&&usable()&&event.getGuild().getId().equals(applied.guild())&&plugin.boosts()!=null)plugin.boosts().signal(event.getUser().getId());}
        @Override public void onGuildMemberUpdate(GuildMemberUpdateEvent event){if(alive(g)&&usable()&&event.getGuild().getId().equals(applied.guild())&&plugin.boosts()!=null)plugin.boosts().signal(event.getUser().getId());}
        @Override public void onGuildMemberUpdateBoostTime(GuildMemberUpdateBoostTimeEvent event){if(alive(g)&&usable()&&event.getGuild().getId().equals(applied.guild())&&plugin.boosts()!=null)plugin.boosts().signal(event.getUser().getId());}
        @Override public void onGuildBan(GuildBanEvent event){if(alive(g)&&usable()&&event.getGuild().getId().equals(applied.guild())&&plugin.accountSync()!=null)plugin.accountSync().fromDiscord(event.getUser().getId(),true);}
        @Override public void onGuildUnban(GuildUnbanEvent event){if(alive(g)&&usable()&&event.getGuild().getId().equals(applied.guild())&&plugin.accountSync()!=null)plugin.accountSync().fromDiscord(event.getUser().getId(),false);}
        @Override public void onRoleUpdatePermissions(RoleUpdatePermissionsEvent event){if(alive(g)&&usable()&&event.getGuild().getId().equals(applied.guild())&&plugin.roles()!=null)plugin.roles().requestSweep();}
        private void degraded(Exception failure){synchronized(Bot.this){if(!alive(g))return;state=State.DEGRADED;plugin.warn("setup-connection-failed");plugin.error(failure);}}
        @Override public void onSessionDisconnect(SessionDisconnectEvent event){synchronized(Bot.this){if(!alive(g))return;state=State.DEGRADED;if(plugin.roles()!=null)plugin.roles().refresh();if(plugin.boosts()!=null)plugin.boosts().refresh();plugin.linking().invalidate();plugin.console().invalidate();if(plugin.chat()!=null)plugin.chat().invalidate();Instant now=plugin.clock().instant();if(now.isAfter(lastWarning.plusSeconds(60))){lastWarning=now;plugin.getLogger().warning(dev.dydiscord.language.ConsoleDiagnostics.text(plugin,"error-discord-disconnected",java.util.Map.of()));}}}
        private void recovered(){synchronized(Bot.this){if(!alive(g))return;state=intentsReady()&&!activeLanguage.isEmpty()?State.CONNECTED:State.DEGRADED;}if(plugin.roles()!=null)plugin.roles().refresh();if(plugin.boosts()!=null)plugin.boosts().refresh();if(plugin.giveaways()!=null)plugin.giveaways().refresh();if(usable())plugin.submit(()->{if(!alive(g))return null;reconcilePanelMode();return null;});}
        @Override public void onSessionResume(SessionResumeEvent event){recovered();}
        @Override public void onSessionRecreate(SessionRecreateEvent event){recovered();}
        @Override public void onShutdown(ShutdownEvent event){synchronized(Bot.this){if(!alive(g))return;if(event.getCode()==4014&&(content||members)){plugin.submit(()->{synchronized(Bot.this){if(!alive(g))return null;jda=null;content=false;members=false;state=State.STARTING;long fallback=generation.incrementAndGet();forms.clear();prefixLimit.clear();interactionLimit.clear();modalLimit.clear();if(plugin.linking()!=null)plugin.linking().invalidate();if(plugin.console()!=null)plugin.console().invalidate();if(plugin.chat()!=null)plugin.chat().invalidate();plugin.warn("setup-intents-denied");connect(applied,fallback,false);}return null;});}else{jda=null;state=State.FAILED;plugin.warn("setup-connection-failed");}}}
        @Override public void onSlashCommandInteraction(SlashCommandInteractionEvent event){
            if(!context(event,g)){event.reply(PanelView.create(Cards.text(text("no-permission",Map.of())))).setEphemeral(true).queue(v->{},plugin::error);return;}
            String action=Words.canonical(event.getName());Settings policy=plugin.settings();if(Set.of("server","players","ip","links").contains(action)&&!policy.community().allows(action,event.getChannel().getId())){event.reply(PanelView.create(Cards.text(text("information-channel-only",Map.of())))).setEphemeral(true).setAllowedMentions(List.of()).timeout(2,TimeUnit.SECONDS).queue(v->{},plugin::error);return;}if(!interactionLimit.admit(event.getUser().getId()+":"+action)){event.reply(PanelView.create(Cards.text(text("rate-limit",Map.of())))).setEphemeral(true).setAllowedMentions(List.of()).timeout(2,TimeUnit.SECONDS).queue(v->{},plugin::error);return;}switch(action){
                case "link"->event.replyModal(form(event.getUser().getId(),g,null)).queue(v->{},plugin::error);
                case "account","unlink"->event.deferReply(true).queue(h->account(h,event.getUser().getId(),action.equals("unlink"),g),plugin::error);
                case "help"->event.reply(PanelView.create(Cards.of(HelpView.card(plugin.messages(),activeLanguage(),plugin.settings().panelEnabled())))).setEphemeral(true).setAllowedMentions(List.of()).queue(v->{},plugin::error);
                case "server","players","ip","links"->{String channel=event.getChannel().getId();event.deferReply(true).queue(h->plugin.submit(()->{if(informationCurrent(g,policy,channel))h.editOriginal(PanelView.update(Cards.of(information(action,g,policy,channel)))).setAllowedMentions(List.of()).setCheck(()->informationCurrent(g,policy,channel)).timeout(10,TimeUnit.SECONDS).queue(v->{},plugin::error);return null;}).exceptionally(e->{plugin.error(e);return null;}),plugin::error);}
                case "status"->event.reply(PanelView.create(Cards.of(embed("status",Map.of("version",plugin.getDescription().getVersion(),"state",text("state-"+state.name().toLowerCase(Locale.ROOT),Map.of())))))).setEphemeral(true).queue(v->{},plugin::error);
                default->event.reply(PanelView.create(Cards.text(text("no-permission",Map.of())))).setEphemeral(true).queue(v->{},plugin::error);
            }
        }
        @Override public void onButtonInteraction(ButtonInteractionEvent event){
            if(!event.getComponentId().startsWith("dd:"))return;if(!context(event,g)){event.reply(PanelView.create(Cards.text(text("no-permission",Map.of())))).setEphemeral(true).queue(v->{},plugin::error);return;}
            if(event.getComponentId().startsWith("dd:giveaway:")){if(plugin.giveaways()==null){event.replyComponents(dev.dydiscord.giveaway.GiveawayView.reply((key,values)->text(key,values),"giveaway-unavailable")).useComponentsV2().setEphemeral(true).setAllowedMentions(List.of()).queue(v->{},plugin::error);return;}if(!interactionLimit.admit(event.getUser().getId()+":giveaway")){event.replyComponents(dev.dydiscord.giveaway.GiveawayView.reply((key,values)->text(key,values),"rate-limit")).useComponentsV2().setEphemeral(true).setAllowedMentions(List.of()).queue(v->{},plugin::error);return;}plugin.giveaways().button(event,g);return;}
            String id=event.getComponentId(),actor=event.getUser().getId();Store.Panel p=panel.current();
            String actionKey=id.startsWith("dd:panel:")?id.substring(9):id.startsWith("dd:confirm:")?"confirm":id.startsWith("dd:cancel:")?"cancel":"unknown";
            if(!interactionLimit.admit(actor+":"+actionKey)){event.reply(PanelView.create(Cards.text(text("rate-limit",Map.of())))).setEphemeral(true).setAllowedMentions(List.of()).timeout(2,TimeUnit.SECONDS).queue(v->{},plugin::error);return;}
            if(id.startsWith("dd:panel:")){
                if(!plugin.settings().panelEnabled()){event.reply(PanelView.create(Cards.text(text("panel-disabled",Map.of())))).setEphemeral(true).setAllowedMentions(List.of()).queue(v->{},plugin::error);return;}
                if(p==null||!p.message().equals(event.getMessageId())||!p.channel().equals(event.getChannel().getId())||!p.channel().equals(plugin.settings().panelChannel())){event.reply(PanelView.create(Cards.text(text("panel-missing",Map.of())))).setEphemeral(true).queue(v->{},plugin::error);return;}
                String action=id.substring(9);if(action.equals("link"))event.replyModal(form(actor,g,p)).queue(v->{},plugin::error);
                else if(Set.of("account","unlink").contains(action))event.deferReply(true).queue(h->account(h,actor,action.equals("unlink"),g),plugin::error);
                return;
            }
            if(id.startsWith("dd:confirm:")||id.startsWith("dd:cancel:")){boolean cancel=id.startsWith("dd:cancel:");String token=id.substring(cancel?10:11);event.deferEdit().queue(h->plugin.submit(()->{
                if(!alive(g)||plugin.linking().pending(token,"dc:"+actor).isEmpty())throw new Linking.Rejected("confirmation-invalid");await(guild().retrieveMemberById(actor).useCache(false));
                if(cancel){plugin.linking().cancel("dc:"+actor);reply(h,"cancelled",Map.of());}else{plugin.linking().remove(token,"dc:"+actor,permission->alive(g),c->{});reply(h,"unlinked",Map.of());}return null;
            }).exceptionally(e->{Throwable cause=failureCause(e);reply(h,cause instanceof Linking.Rejected r?r.key:"failed",cause instanceof Linking.Rejected?Map.of():Map.of("event",plugin.error(cause)));return null;}),plugin::error);}
        }
        @Override public void onModalInteraction(ModalInteractionEvent event){
            if(!event.getModalId().startsWith("dd:form:"))return;Form form=forms.remove(event.getModalId().substring(8));String actor=event.getUser().getId();
            if(!context(event,g)||form==null||!form.actor.equals(actor)||form.generation!=g||!plugin.clock().instant().isBefore(form.expires)||!formCurrent(form)){event.reply(PanelView.create(Cards.text(text("invalid-code",Map.of())))).setEphemeral(true).queue(v->{},plugin::error);return;}
            if(!modalLimit.admit(actor)){event.reply(PanelView.create(Cards.text(text("rate-limit",Map.of())))).setEphemeral(true).setAllowedMentions(List.of()).timeout(2,TimeUnit.SECONDS).queue(v->{},plugin::error);return;}
            String code=event.getValue("code")==null?"":event.getValue("code").getAsString();event.deferReply(true).queue(h->plugin.submit(()->{
                await(guild().retrieveMemberById(actor).useCache(false));Optional<Linking.Code> pending=plugin.linking().inspect(code);
                Store.Link saved=plugin.linking().redeem(code,actor,event.getUser().getName(),plugin.settings(),alive(g),c->pending.isPresent()&&c.equals(pending.get())&&formCurrent(form)&&codeOwner(c,g));reply(h,"linked",Map.of("name",saved.name(),"discord",saved.discordName()));plugin.notifyLinked(saved);return null;
            }).exceptionally(e->{Throwable cause=failureCause(e);reply(h,cause instanceof Linking.Rejected r?r.key:"data-unavailable",cause instanceof Linking.Rejected r?r.values:Map.of());if(!(cause instanceof Linking.Rejected))plugin.error(cause);return null;}),plugin::error);
        }
        @Override public void onMessageReceived(MessageReceivedEvent event){
            if(!alive(g)||!usable()||!content||!event.isFromGuild()||!event.getGuild().getId().equals(applied.guild())||event.getAuthor().isBot()||event.isWebhookMessage())return;
            Settings s=plugin.settings();if(event.getChannel().getId().equals(s.consoleChannel())){plugin.console().receive(event,g);return;}
            if(event.getChannel().getId().equals(s.chat().channel())&&plugin.chat()!=null)plugin.chat().receive(event,g);
            if(!s.prefixEnabled())return;String input=event.getMessage().getContentRaw();if(!input.startsWith(s.botPrefix())||input.length()>128)return;
            String word=input.substring(s.botPrefix().length());if(word.contains(" "))return;String action=Words.canonical(word);
            if(!Set.of("link","account","unlink","help","status","server","players","ip","links").contains(action))return;
            if(!s.community().allows(action,event.getChannel().getId())||!prefixLimit.admit(event.getAuthor().getId()))return;
            if(Set.of("server","players","ip","links").contains(action)){String channel=event.getChannel().getId();plugin.submit(()->{if(informationCurrent(g,s,channel))event.getChannel().sendMessage(PanelView.create(Cards.of(information(action,g,s,channel)))).setAllowedMentions(List.of()).mentionRepliedUser(false).setCheck(()->informationCurrent(g,s,channel)).timeout(10,TimeUnit.SECONDS).queue(v->{},plugin::error);return null;}).exceptionally(e->{plugin.error(e);return null;});return;}
            String message=switch(action){case "help"->completeHelp();case "status"->text("status",Map.of("version",plugin.getDescription().getVersion(),"state",text("state-"+state.name().toLowerCase(Locale.ROOT),Map.of())));default->{if(!s.panelEnabled())yield commandPrompt(action);String url=panel.url(applied.guild(),s.panelChannel());yield url.isEmpty()?text("panel-missing",Map.of()):text("private-prompt",Map.of("url",url));}};
            MessageEmbed card=action.equals("help")?HelpView.card(plugin.messages(),activeLanguage(),s.panelEnabled()):new EmbedBuilder().setColor(0x5865F2).setDescription(message).build();
            event.getChannel().sendMessage(PanelView.create(Cards.of(card))).setAllowedMentions(List.of()).mentionRepliedUser(false).setCheck(()->alive(g)&&usable()&&s.equals(plugin.settings())).timeout(10,TimeUnit.SECONDS).queue(v->{},plugin::error);
        }
    }
}
