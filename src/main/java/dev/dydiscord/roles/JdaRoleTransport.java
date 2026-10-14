package dev.dydiscord.roles;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.Settings;
import dev.dydiscord.discord.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Fresh role catalog and uncached members; only individual Discord role endpoints are used. */
public final class JdaRoleTransport implements RoleTransport {
    private final DyDiscord plugin;private final DiscordAccess access;
    public JdaRoleTransport(DyDiscord plugin){this(plugin,new DiscordAccess());}
    JdaRoleTransport(DyDiscord plugin,DiscordAccess access){this.plugin=plugin;this.access=access;}
    public boolean available(){return plugin.bot()!=null&&plugin.bot().usable();}
    public long session(){return plugin.bot()==null?-1:plugin.bot().generation();}
    public String guild(){return plugin.bot()==null||plugin.bot().applied()==null?"":plugin.bot().applied().guild();}
    private Guild require(String guild){if(!available()||!guild.equals(guild()))throw new IllegalStateException("Discord role connection unavailable");Guild result=plugin.bot().guild();if(result==null)throw new IllegalStateException("Guild unavailable");return result;}
    public Member inspect(String guild,String discord)throws Exception{
        Bot bot=plugin.bot();long session=session();Guild server=require(guild);Settings applied=bot.applied();
        Map<String,RolePolicy.Role> catalog=new HashMap<>();
        for(var role:access.roles(applied)){
            String id=role.path("id").asText();if(!Settings.snowflake(id)||!role.has("position")||!role.has("permissions")||!role.has("managed"))throw new IllegalStateException("Malformed role catalog");
            catalog.put(id,new RolePolicy.Role(id,guild,role.path("position").asInt(),Long.parseUnsignedLong(role.path("permissions").asText()),role.path("managed").asBoolean()));
        }
        net.dv8tion.jda.api.entities.Member self=bot.await(server.retrieveMemberById(server.getSelfMember().getId()).useCache(false));
        Set<String> botIds=new HashSet<>();for(Role role:self.getRoles())botIds.add(role.getId());botIds.add(guild);int position=0;long permissions=0;String highest=guild;
        for(String id:botIds){var role=catalog.get(id);if(role==null)throw new IllegalStateException("Bot role unavailable");if(role.position()>position||role.position()==position&&Long.compareUnsigned(Long.parseUnsignedLong(id),Long.parseUnsignedLong(highest))<0){position=role.position();highest=id;}permissions|=role.permissions();}
        try{
            net.dv8tion.jda.api.entities.Member member=discord.equals(self.getId())?self:bot.await(server.retrieveMemberById(discord).useCache(false));
            if(!available()||session()!=session)throw new IllegalStateException("Role session expired");
            Set<String> ids=new HashSet<>();for(Role role:member.getRoles())ids.add(role.getId());return new Member(true,member.getUser().isBot(),ids,catalog,position,permissions,highest);
        }catch(ErrorResponseException failure){if(failure.getErrorResponse()==ErrorResponse.UNKNOWN_MEMBER&&available()&&session()==session)return new Member(false,false,Set.of(),catalog,position,permissions,highest);throw failure;}
    }
    public void validate(Settings settings)throws Exception{Guild server=require(settings.guild());RolePolicy.validate(inspect(settings.guild(),server.getSelfMember().getId()),settings.roleSync().managedIds(),settings.roles(),settings.guild());}
    public void change(String guild,String discord,String id,boolean add,BooleanSupplier current)throws Exception{
        if(!current.getAsBoolean())return;Bot bot=plugin.bot();long session=session();var observed=inspect(guild,discord);
        RolePolicy.validate(observed,Set.of(id),plugin.settings().roles(),guild);if(!observed.present())throw new IllegalStateException("Member absent");
        if(observed.roles().contains(id)==add||!current.getAsBoolean())return;
        Guild server=require(guild);Role role=server.getRoleById(id);if(role==null)throw new IllegalStateException("Role cache incomplete");
        var action=add?server.addRoleToMember(UserSnowflake.fromId(discord),role):server.removeRoleFromMember(UserSnowflake.fromId(discord),role);
        bot.await(action.setCheck(()->current.getAsBoolean()&&available()&&session()==session&&!role.isManaged()&&role.getGuild().getId().equals(guild)&&(role.getPermissionsRaw()&RolePolicy.CRITICAL)==0&&!plugin.settings().roles().contains(id)&&server.getSelfMember().canInteract(role)));
    }
}
