package dev.dydiscord.discord;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.*;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import java.util.*;

/** Discord side of support tickets: one thread per ticket in the tickets channel. Every method blocks on Discord and runs on the plugin I/O worker. */
public final class TicketThreads {
    private final DyDiscord plugin;private final Bot bot;
    public TicketThreads(DyDiscord plugin,Bot bot){this.plugin=plugin;this.bot=bot;}

    /** Thread title from the template; the player name is plain text, at most 100 characters in all. */
    public static String title(String template,String player){String value=template.replace("{player}",player).replaceAll("[\\p{Cntrl}]"," ").strip();if(value.isEmpty())value=player;return value.length()>100?value.substring(0,100):value;}
    /** Opens a thread for a new ticket with its first message and returns the thread ID. Private when possible and wanted. */
    public String open(String player,String text)throws Exception{
        FeatureSettings.Tickets settings=plugin.settings().features().tickets();
        TextChannel channel=bot.channel(settings.channel());Member self=channel.getGuild().getSelfMember();
        boolean privateThread=settings.privateThreads()&&self.hasPermission(channel,Permission.CREATE_PRIVATE_THREADS);
        if(!self.hasPermission(channel,Permission.VIEW_CHANNEL,Permission.MESSAGE_SEND_IN_THREADS)||!privateThread&&!self.hasPermission(channel,Permission.CREATE_PUBLIC_THREADS))throw new IllegalStateException("Ticket channel permissions unavailable");
        var action=channel.createThreadChannel(title(plugin.messages().get(true,bot.activeLanguage(),"ticket-thread-name"),player),privateThread);
        if(privateThread)action=action.setInvitable(false);
        ThreadChannel thread=bot.await(action);
        String role=settings.pingRole();
        var message=new MessageCreateBuilder().setComponents(Cards.text((role.isEmpty()?"":"<@&"+role+">\n")+text,0x55AAFF)).useComponentsV2().setAllowedMentions(List.of());
        if(!role.isEmpty())message.mentionRoles(role);
        bot.await(thread.sendMessage(message.build()));
        return thread.getId();
    }
    private ThreadChannel open(String threadId){var jda=bot.client();if(jda==null||!bot.usable())return null;ThreadChannel thread=jda.getThreadChannelById(threadId);return thread==null||thread.isArchived()||thread.isLocked()?null:thread;}
    /** Adds the player's message to an open ticket thread; false when the thread was closed, archived or deleted. */
    public boolean post(String threadId,String text)throws Exception{
        ThreadChannel thread=open(threadId);if(thread==null)return false;
        bot.await(thread.sendMessage(dev.dydiscord.discord.PanelView.create(Cards.text(text,0x55AAFF))).setAllowedMentions(List.of()));return true;
    }
    /** Posts the closing note and archives the thread; it is locked too when the bot can manage threads. */
    public void close(String threadId,String text)throws Exception{
        ThreadChannel thread=open(threadId);if(thread==null)return;
        bot.await(thread.sendMessage(dev.dydiscord.discord.PanelView.create(Cards.text(text,0xAAAAAA))).setAllowedMentions(List.of()));
        var manager=thread.getManager().setArchived(true);if(thread.getGuild().getSelfMember().hasPermission(thread.getParentChannel(),Permission.MANAGE_THREADS))manager=manager.setLocked(true);
        bot.await(manager);
    }
    /** True for threads of the configured tickets channel. */
    public boolean ticketThread(ThreadChannel thread,Settings settings){String channel=settings.features().tickets().channel();return !channel.isEmpty()&&thread!=null&&thread.getParentChannel()!=null&&channel.equals(thread.getParentChannel().getId());}
}
