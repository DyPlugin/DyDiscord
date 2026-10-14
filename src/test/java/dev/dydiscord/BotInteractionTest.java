package dev.dydiscord;

import dev.dydiscord.config.*;
import dev.dydiscord.discord.*;
import dev.dydiscord.storage.Store;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import java.nio.file.Path;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BotInteractionTest {
    @Test void disabledPanelRejectsButtonsButSlashStillOpensPrivateModal()throws Exception{
        var off=Settings.read(Map.of(),Map.of("guild-id",settings.guild(),"panel",Map.of("enabled",false,"channel-id",settings.panelChannel())),null);when(host.settings()).thenReturn(off);
        var button=button("link","423456789012345678");var reply=mock(ReplyCallbackAction.class);when(button.reply(anyString())).thenReturn(reply);when(reply.setEphemeral(true)).thenReturn(reply);when(reply.setAllowedMentions(any())).thenReturn(reply);
        listener.onButtonInteraction(button);verify(button,never()).replyModal(any());
        var slash=mock(net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent.class);Guild guild=mock(Guild.class);User user=mock(User.class);when(slash.isFromGuild()).thenReturn(true);when(slash.getGuild()).thenReturn(guild);when(guild.getId()).thenReturn(settings.guild());when(slash.getMember()).thenReturn(mock(Member.class));when(slash.getUser()).thenReturn(user);when(user.getId()).thenReturn("523456789012345678");when(slash.getName()).thenReturn("link");when(slash.replyModal(any())).thenReturn(mock(ModalCallbackAction.class));
        listener.onSlashCommandInteraction(slash);verify(slash).replyModal(any());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void reconnectDoesNotUpsertAlreadyCorrectOwnedSlashCommands(boolean changedDescription)throws Exception{
        Guild guild=mock(Guild.class);doReturn(guild).when(bot).guild();when(guild.getId()).thenReturn(settings.guild());
        var retrieval=mock(net.dv8tion.jda.api.requests.RestAction.class);doReturn(retrieval).when(guild).retrieveCommands();List<net.dv8tion.jda.api.interactions.commands.Command> existing=new ArrayList<>();Set<String> owned=new HashSet<>();
        for(String function:List.of("help","link","account","unlink","status","server")){var command=mock(net.dv8tion.jda.api.interactions.commands.Command.class);String id="72345678901234567"+existing.size();String description=host.messages().get(true,settings.botLanguage(),"slash-"+function);owned.add(id);when(command.getId()).thenReturn(id);when(command.getName()).thenReturn(dev.dydiscord.command.Words.display(function,settings.botLanguage()));when(command.getDescription()).thenReturn(description);when(command.getType()).thenReturn(net.dv8tion.jda.api.interactions.commands.Command.Type.SLASH);existing.add(command);}
        doReturn(existing).when(bot).await(retrieval);when(host.store().commands(settings.guild())).thenReturn(owned);
        if(changedDescription)when(existing.get(0).getDescription()).thenReturn("Old description");
        var action=mock(net.dv8tion.jda.api.requests.restaction.CommandCreateAction.class);when(guild.upsertCommand(any(net.dv8tion.jda.api.interactions.commands.build.CommandData.class))).thenReturn(action);when(action.setCheck(any())).thenReturn(action);doReturn(existing.get(0),existing.get(1),existing.get(2),existing.get(3),existing.get(4),existing.get(5)).when(bot).await(action);
        var method=Bot.class.getDeclaredMethod("register",long.class);method.setAccessible(true);method.invoke(bot,0L);
        verify(guild,times(changedDescription?1:0)).upsertCommand(any(net.dv8tion.jda.api.interactions.commands.build.CommandData.class));
    }
    @Test void uncachedMemberUpdateSignalsFreshBoostReconciliation(){
        var bridge=mock(dev.dydiscord.boost.BoostBridge.class);when(host.boosts()).thenReturn(bridge);
        var event=mock(net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent.class);Guild guild=mock(Guild.class);User user=mock(User.class);
        when(event.getGuild()).thenReturn(guild);when(guild.getId()).thenReturn(settings.guild());when(event.getUser()).thenReturn(user);when(user.getId()).thenReturn("423456789012345678");
        listener.onGuildMemberUpdate(event);verify(bridge).signal("423456789012345678");
    }
    @TempDir Path folder;
    DyDiscord host; Bot bot; ListenerAdapter listener; Settings settings;
    @BeforeEach void setup()throws Exception{
        Configuration c=TestSupport.config(folder);c.change("bot.yml","guild-id","123456789012345678");c.change("bot.yml","panel.channel-id","223456789012345678");settings=c.active().settings();
        host=mock(DyDiscord.class);Store store=mock(Store.class);when(store.panel()).thenReturn(Optional.of(new Store.Panel(settings.guild(),settings.panelChannel(),"323456789012345678")));
        when(host.store()).thenReturn(store);when(host.settings()).thenReturn(settings);when(host.messages()).thenReturn(c.active().messages());when(host.clock()).thenReturn(new TestSupport.Time());
        bot=spy(new Bot(host));doReturn(true).when(bot).alive(0);doReturn(true).when(bot).usable();var field=Bot.class.getDeclaredField("applied");field.setAccessible(true);field.set(bot,settings);
        var type=Class.forName("dev.dydiscord.discord.Bot$Events");var ctor=type.getDeclaredConstructor(Bot.class,long.class);ctor.setAccessible(true);listener=(ListenerAdapter)ctor.newInstance(bot,0L);
    }
    ButtonInteractionEvent button(String action,String actor){
        ButtonInteractionEvent event=mock(ButtonInteractionEvent.class);Guild guild=mock(Guild.class);User user=mock(User.class);MessageChannelUnion channel=mock(MessageChannelUnion.class);
        when(event.getComponentId()).thenReturn("dd:panel:"+action);when(event.isFromGuild()).thenReturn(true);when(event.getGuild()).thenReturn(guild);when(guild.getId()).thenReturn(settings.guild());when(event.getMember()).thenReturn(mock(Member.class));
        when(event.getUser()).thenReturn(user);when(user.getId()).thenReturn(actor);when(event.getMessageId()).thenReturn("323456789012345678");when(event.getChannel()).thenReturn(channel);when(channel.getId()).thenReturn(settings.panelChannel());
        ReplyCallbackAction reply=mock(ReplyCallbackAction.class,RETURNS_SELF);when(event.reply(anyString())).thenReturn(reply);when(event.replyModal(any(Modal.class))).thenReturn(mock(ModalCallbackAction.class));return event;
    }
    @Test void repeatedPanelRequestsAreLimitedBeforeModalOrAsyncWork(){
        ButtonInteractionEvent first=button("link","423456789012345678"),second=button("link","423456789012345678");listener.onButtonInteraction(first);listener.onButtonInteraction(second);
        verify(first).replyModal(any());verify(second,never()).replyModal(any());verify(second).reply(bot.text("rate-limit",Map.of()));verify(host,never()).submit(any());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"account","unlink"})
    void sessionLostDuringMemberLookupCannotRefreshAccountOrCreateConfirmation(String action)throws Exception{
        try(Store store=new Store(folder.resolve("delayed-account.db"))){
            UUID uuid=UUID.randomUUID();String actor="423456789012345678";Store.Link original=store.link(uuid,actor,"Alice","OriginalName",host.clock().instant());
            var linking=new dev.dydiscord.linking.Linking(store,host.clock(),new java.security.SecureRandom());when(host.store()).thenReturn(store);when(host.linking()).thenReturn(linking);
            var guild=mock(Guild.class,RETURNS_DEEP_STUBS);doReturn(guild).when(bot).guild();var member=mock(Member.class,RETURNS_DEEP_STUBS);when(member.getUser().getName()).thenReturn("NewName");
            doAnswer(call->{doReturn(false).when(bot).alive(0);return member;}).when(bot).await(any());
            doAnswer(call->{try{return java.util.concurrent.CompletableFuture.completedFuture(((java.util.concurrent.Callable<?>)call.getArgument(0)).call());}catch(Exception failure){return java.util.concurrent.CompletableFuture.failedFuture(failure);}}).when(host).submit(any());
            var event=button(action,actor);var hook=mock(net.dv8tion.jda.api.interactions.InteractionHook.class);var edit=mock(net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction.class,RETURNS_SELF);when(hook.editOriginalEmbeds(any(MessageEmbed[].class))).thenReturn(edit);
            var deferred=mock(ReplyCallbackAction.class,RETURNS_SELF);when(event.deferReply(true)).thenReturn(deferred);doAnswer(call->{((java.util.function.Consumer<net.dv8tion.jda.api.interactions.InteractionHook>)call.getArgument(0)).accept(hook);return null;}).when(deferred).queue(any(),any());
            listener.onButtonInteraction(event);
            assertEquals(original,store.byUuid(uuid).orElseThrow());var response=ArgumentCaptor.forClass(MessageEmbed[].class);verify(hook).editOriginalEmbeds(response.capture());assertEquals("This confirmation is invalid or expired.",response.getValue()[0].getDescription());verify(edit).setComponents(List.of());verify(host,never()).error(any());
        }
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"channel","message","current","slash","disabled","slash-disabled"})
    void modalRetainsOpeningPanelContextAndAllowsCurrentPanel(String change)throws Exception{
        String actor="423456789012345678";var form=ArgumentCaptor.forClass(Modal.class);
        if(change.startsWith("slash")){var opening=mock(net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent.class,RETURNS_DEEP_STUBS);when(opening.isFromGuild()).thenReturn(true);when(opening.getGuild().getId()).thenReturn(settings.guild());when(opening.getUser().getId()).thenReturn(actor);when(opening.getName()).thenReturn("link");listener.onSlashCommandInteraction(opening);verify(opening).replyModal(form.capture());}
        else {var opening=button("link",actor);listener.onButtonInteraction(opening);verify(opening).replyModal(form.capture());}
        if(change.equals("channel")){var c=TestSupport.config(folder.resolve("new-policy"));c.change("bot.yml","guild-id",settings.guild());c.change("bot.yml","panel.channel-id","723456789012345678");when(host.settings()).thenReturn(c.active().settings());}
        if(change.equals("message")){var field=PanelService.class.getDeclaredField("current");field.setAccessible(true);field.set(bot.panel(),new Store.Panel(settings.guild(),settings.panelChannel(),"823456789012345678"));}
        if(change.equals("disabled")||change.equals("slash-disabled"))when(host.settings()).thenReturn(Settings.read(Map.of(),Map.of("guild-id",settings.guild(),"panel",Map.of("enabled",false,"channel-id",settings.panelChannel())),null));
        try(Store store=new Store(folder.resolve("panel-context.db"))){
            var linking=new dev.dydiscord.linking.Linking(store,host.clock(),new java.security.SecureRandom());when(host.linking()).thenReturn(linking);when(host.store()).thenReturn(store);UUID uuid=UUID.randomUUID();String code=linking.issue(uuid,"Alice",1,host.settings(),true).code();
            when(host.sessionValid(any())).thenReturn(true);when(host.validOwner(any())).thenReturn(true);doReturn(mock(Guild.class,RETURNS_DEEP_STUBS)).when(bot).guild();doReturn(mock(Member.class)).when(bot).await(any());
            doAnswer(call->{try{return java.util.concurrent.CompletableFuture.completedFuture(((java.util.concurrent.Callable<?>)call.getArgument(0)).call());}catch(Exception failure){return java.util.concurrent.CompletableFuture.failedFuture(failure);}}).when(host).submit(any());
            var modal=mock(ModalInteractionEvent.class,RETURNS_DEEP_STUBS);when(modal.getModalId()).thenReturn(form.getValue().getId());when(modal.isFromGuild()).thenReturn(true);when(modal.getGuild().getId()).thenReturn(settings.guild());when(modal.getUser().getId()).thenReturn(actor);when(modal.getUser().getName()).thenReturn("DiscordAlice");when(modal.getValue("code").getAsString()).thenReturn(code);
            var hook=mock(net.dv8tion.jda.api.interactions.InteractionHook.class);var edit=mock(net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction.class,RETURNS_SELF);when(hook.editOriginalEmbeds(any(MessageEmbed[].class))).thenReturn(edit);var deferred=modal.deferReply(true);doAnswer(call->{((java.util.function.Consumer<net.dv8tion.jda.api.interactions.InteractionHook>)call.getArgument(0)).accept(hook);return null;}).when(deferred).queue(any(),any());
            listener.onModalInteraction(modal);boolean rejected=Set.of("channel","message","disabled").contains(change);assertEquals(rejected,store.byUuid(uuid).isEmpty());assertEquals(rejected,linking.inspect(code).isPresent());if(rejected)verify(host,never()).notifyLinked(any());else verify(host).notifyLinked(any());verify(host,never()).error(any());
        }
    }
    @Test void interactionLimitIsPerActionAndUserAndExpires(){
        listener.onButtonInteraction(button("link","423456789012345678"));
        var unrelated=button("unknown","423456789012345678");listener.onButtonInteraction(unrelated);verify(unrelated,never()).reply(anyString());
        var other=button("link","523456789012345678");listener.onButtonInteraction(other);verify(other).replyModal(any());
        ((TestSupport.Time)host.clock()).advance(1);var later=button("link","423456789012345678");listener.onButtonInteraction(later);verify(later).replyModal(any());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"permission","minecraft-session","bot-session","allowed"})
    void modalRechecksOwnerAndSessionsAtCommitAndKeepsAuthorizedSuccess(String scenario)throws Exception{
        boolean revoked=!scenario.equals("allowed");
        var opening=button("link","423456789012345678");listener.onButtonInteraction(opening);var form=ArgumentCaptor.forClass(Modal.class);verify(opening).replyModal(form.capture());
        try(Store store=new Store(folder.resolve("redemption.db"))){
            var linking=new dev.dydiscord.linking.Linking(store,host.clock(),new java.security.SecureRandom());UUID uuid=UUID.randomUUID();String code=linking.issue(uuid,"Alice",1,settings,true).code();when(host.linking()).thenReturn(linking);when(host.store()).thenReturn(store);
            when(host.sessionValid(any())).thenReturn(true);var ownerChecks=new java.util.concurrent.atomic.AtomicInteger();doAnswer(call->{if(ownerChecks.incrementAndGet()==2){if(scenario.equals("permission"))return false;if(scenario.equals("minecraft-session"))when(host.sessionValid(any())).thenReturn(false);if(scenario.equals("bot-session"))doReturn(false).when(bot).alive(0);}return true;}).when(host).validOwner(any());
            Guild guild=mock(Guild.class,RETURNS_DEEP_STUBS);doReturn(guild).when(bot).guild();doReturn(mock(Member.class)).when(bot).await(any());
            doAnswer(call->{try{return java.util.concurrent.CompletableFuture.completedFuture(((java.util.concurrent.Callable<?>)call.getArgument(0)).call());}catch(Exception failure){return java.util.concurrent.CompletableFuture.failedFuture(failure);}}).when(host).submit(any());
            ModalInteractionEvent modal=mock(ModalInteractionEvent.class,RETURNS_DEEP_STUBS);when(modal.getModalId()).thenReturn(form.getValue().getId());when(modal.isFromGuild()).thenReturn(true);when(modal.getGuild().getId()).thenReturn(settings.guild());when(modal.getUser().getId()).thenReturn("423456789012345678");when(modal.getUser().getName()).thenReturn("DiscordAlice");when(modal.getValue("code").getAsString()).thenReturn(code);
            var hook=mock(net.dv8tion.jda.api.interactions.InteractionHook.class);var edit=mock(net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction.class,RETURNS_SELF);when(hook.editOriginalEmbeds(any(MessageEmbed[].class))).thenReturn(edit);var deferred=modal.deferReply(true);doAnswer(call->{((java.util.function.Consumer<net.dv8tion.jda.api.interactions.InteractionHook>)call.getArgument(0)).accept(hook);return null;}).when(deferred).queue(any(),any());
            listener.onModalInteraction(modal);verify(host,times(2)).validOwner(any());assertEquals(revoked,store.byUuid(uuid).isEmpty());assertEquals(revoked,linking.inspect(code).isPresent());if(revoked)verify(host,never()).notifyLinked(any());else verify(host).notifyLinked(any());verify(host,never()).error(any());var response=ArgumentCaptor.forClass(MessageEmbed[].class);verify(hook).editOriginalEmbeds(response.capture());assertEquals(bot.text(revoked?"invalid-code":"linked",Map.of("name","Alice","discord","DiscordAlice")),response.getValue()[0].getDescription());assertTrue(store.writable());
        }
    }
    @Test void modalIngressLimitRejectsBeforeDeferAndDatabaseWork()throws Exception{
        var opening=button("link","423456789012345678");listener.onButtonInteraction(opening);var form=ArgumentCaptor.forClass(Modal.class);verify(opening).replyModal(form.capture());
        var field=Bot.class.getDeclaredField("modalLimit");field.setAccessible(true);assertTrue(((IngressLimit)field.get(bot)).admit("423456789012345678"));
        var event=mock(ModalInteractionEvent.class,RETURNS_DEEP_STUBS);when(event.getModalId()).thenReturn(form.getValue().getId());when(event.isFromGuild()).thenReturn(true);when(event.getGuild().getId()).thenReturn(settings.guild());when(event.getUser().getId()).thenReturn("423456789012345678");listener.onModalInteraction(event);
        verify(event).reply(bot.text("rate-limit",Map.of()));verify(event,never()).deferReply(anyBoolean());verify(host,never()).submit(any());
    }
    @Test void slashAndPanelLinkShareTheSameAdmissionLimit(){
        var event=mock(net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent.class,RETURNS_DEEP_STUBS);when(event.isFromGuild()).thenReturn(true);when(event.getGuild().getId()).thenReturn(settings.guild());when(event.getUser().getId()).thenReturn("423456789012345678");when(event.getName()).thenReturn("link");listener.onSlashCommandInteraction(event);verify(event).replyModal(any());
        var panel=button("link","423456789012345678");listener.onButtonInteraction(panel);verify(panel,never()).replyModal(any());verify(panel).reply(bot.text("rate-limit",Map.of()));
    }
    @Test void sharedPanelOpensModalAsFirstResponseForAnyHumanMember(){
        ButtonInteractionEvent event=button("link","423456789012345678");listener.onButtonInteraction(event);
        ArgumentCaptor<Modal> form=ArgumentCaptor.forClass(Modal.class);verify(event).replyModal(form.capture());assertTrue(form.getValue().getId().startsWith("dd:form:"));
        verify(event,never()).deferReply(anyBoolean());verify(event,never()).reply(anyString());verify(host,never()).submit(any());
    }
    @Test void stalePanelCannotOpenModal(){
        ButtonInteractionEvent event=button("link","423456789012345678");when(event.getMessageId()).thenReturn("old-message");listener.onButtonInteraction(event);
        verify(event).reply(bot.text("panel-missing",Map.of()));verify(event,never()).replyModal(any());verify(host,never()).submit(any());
    }
    @Test void wrongGuildCannotReadOrMutateAccount(){
        ButtonInteractionEvent event=button("account","423456789012345678");when(event.getGuild().getId()).thenReturn("other-guild");listener.onButtonInteraction(event);
        verify(event).reply(bot.text("no-permission",Map.of()));verify(event,never()).deferReply(anyBoolean());verify(host,never()).submit(any());
    }
    @Test void forgedOrExpiredModalGetsPrivateGenericFailure(){
        ModalInteractionEvent event=mock(ModalInteractionEvent.class);User user=mock(User.class);when(event.getUser()).thenReturn(user);when(user.getId()).thenReturn("423456789012345678");when(event.getModalId()).thenReturn("dd:form:unknown");
        ReplyCallbackAction reply=mock(ReplyCallbackAction.class);when(event.reply(anyString())).thenReturn(reply);when(reply.setEphemeral(true)).thenReturn(reply);
        listener.onModalInteraction(event);verify(event).reply(bot.text("invalid-code",Map.of()));verify(reply).setEphemeral(true);verify(event,never()).deferReply(anyBoolean());verify(host,never()).submit(any());
    }
}
