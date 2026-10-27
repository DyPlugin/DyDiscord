package dev.dydiscord.discord;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.ModerationSettings;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Discord side of the 1.2.0 staff tools: cheat-check notices, staff logs, player reports,
 * nickname and ban sync. Every method blocks on Discord and runs on the plugin I/O worker.
 */
public final class StaffNotices {
    /** How a cheat-check notice reached the player. */
    public enum Delivery {DM,CHANNEL,BOTH,FAILED}
    private final DyDiscord plugin;private final Bot bot;
    public StaffNotices(DyDiscord plugin,Bot bot){this.plugin=plugin;this.bot=bot;}

    /** Sends the cheat-check request by DM and/or in the notice channel; a closed DM falls back to the channel. */
    public Delivery cheatCheck(String discordId,String player,String staff){
        ModerationSettings settings=plugin.settings().moderation();var jda=bot.client();if(jda==null||!bot.usable())return Delivery.FAILED;
        String body=bot.text("cheatcheck-dm",Map.of("player",player,"staff",staff));
        if(!settings.voiceChannel().isEmpty())body+="\n"+bot.text("cheatcheck-voice",Map.of())+" <#"+settings.voiceChannel()+">";
        boolean direct=false,posted=false;
        if(settings.directMessage())try{var channel=bot.await(jda.openPrivateChannelById(discordId));bot.await(channel.sendMessage(body).setAllowedMentions(List.of()));direct=true;}catch(Exception closed){direct=false;}
        if((settings.channelNotice()||!direct)&&!settings.noticeChannel().isEmpty())try{
            TextChannel channel=bot.channel(settings.noticeChannel());
            if(channel.getGuild().getSelfMember().hasPermission(channel,Permission.VIEW_CHANNEL,Permission.MESSAGE_SEND)){bot.await(channel.sendMessage("<@"+discordId+"> "+body).setAllowedMentions(List.of()).mentionUsers(discordId));posted=true;}
        }catch(Exception failure){plugin.error(failure);}
        return direct&&posted?Delivery.BOTH:direct?Delivery.DM:posted?Delivery.CHANNEL:Delivery.FAILED;
    }
    /** Posts a staff log card; the cheat-check log falls back to the plugin-logs channel. Silently skipped without a channel or permission. */
    public void log(String channelId,String text,int color){
        if(channelId.isEmpty()||!bot.usable())return;
        try{TextChannel channel=bot.channel(channelId);
            if(!channel.getGuild().getSelfMember().hasPermission(channel,Permission.VIEW_CHANNEL,Permission.MESSAGE_SEND,Permission.MESSAGE_EMBED_LINKS))return;
            channel.sendMessage(PanelView.create(Cards.text(text,color))).setAllowedMentions(List.of()).queue(v->{},plugin::error);
        }catch(Exception failure){plugin.error(failure);}
    }
    public String checkLogChannel(){ModerationSettings settings=plugin.settings().moderation();return settings.checkLogChannel().isEmpty()?plugin.settings().integrations().logChannel():settings.checkLogChannel();}
    /** Posts a player report, mentioning only the configured role. */
    public boolean report(String text){
        ModerationSettings settings=plugin.settings().moderation();if(settings.reportChannel().isEmpty()||!bot.usable())return false;
        try{TextChannel channel=bot.channel(settings.reportChannel());
            if(!channel.getGuild().getSelfMember().hasPermission(channel,Permission.VIEW_CHANNEL,Permission.MESSAGE_SEND))return false;
            String role=settings.reportRole();String body=(role.isEmpty()?"":"<@&"+role+">\n")+text;
            var message=new MessageCreateBuilder().setComponents(Cards.text(body,0xFF5555)).useComponentsV2().setAllowedMentions(List.of());
            if(!role.isEmpty())message.mentionRoles(role);
            bot.await(channel.sendMessage(message.build()));return true;
        }catch(Exception failure){plugin.error(failure);return false;}
    }
    /** Gives a linked member their Minecraft name; members the bot cannot manage, such as the owner, are skipped. */
    public void nickname(String discordId,String nickname)throws Exception{
        Guild guild=bot.guild();if(guild==null||!bot.usable())return;Member self=guild.getSelfMember();if(!self.hasPermission(Permission.NICKNAME_MANAGE))return;
        Member member;try{member=bot.await(guild.retrieveMemberById(discordId).useCache(false));}catch(Exception left){return;}
        if(member.isOwner()||!self.canInteract(member)||nickname.equals(member.getNickname()))return;
        bot.await(guild.modifyNickname(member,nickname).reason("DyDiscord nickname sync"));
    }
    /** Bans a linked member on Discord after a Minecraft ban; needs Ban Members. */
    public void ban(String discordId,String reason)throws Exception{
        Guild guild=bot.guild();if(guild==null||!bot.usable()||!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS))return;
        bot.await(guild.ban(UserSnowflake.fromId(discordId),0,TimeUnit.SECONDS).reason(reason.length()>512?reason.substring(0,512):reason));
    }
}
