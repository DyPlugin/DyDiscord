package dev.dydiscord.roles;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.Settings;
import dev.dydiscord.discord.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdaRoleTransportTest {
    @Test void freshRolePrivilegesOverrideStaleCacheAndNeverReachGrantEndpoint()throws Exception{
        String g="123456789012345678",d="223456789012345678",r="323456789012345678",b="423456789012345678";
        DyDiscord plugin=mock(DyDiscord.class);Bot bot=mock(Bot.class);Guild guild=mock(Guild.class);DiscordAccess access=mock(DiscordAccess.class);
        Settings settings=Settings.read(Map.of(),Map.of("guild-id",g,"roles",Map.of("linked-role-ids",List.of(r))),null);
        when(plugin.bot()).thenReturn(bot);when(plugin.settings()).thenReturn(settings);when(bot.applied()).thenReturn(settings);when(bot.usable()).thenReturn(true);when(bot.guild()).thenReturn(guild);when(bot.generation()).thenReturn(1L);when(guild.getId()).thenReturn(g);
        SelfMember self=mock(SelfMember.class);Member target=mock(Member.class);Role botRole=mock(Role.class);User human=mock(User.class);
        when(self.getId()).thenReturn(b);when(self.getRoles()).thenReturn(List.of(botRole));when(botRole.getId()).thenReturn(b);when(guild.getSelfMember()).thenReturn(self);when(target.getUser()).thenReturn(human);when(target.getRoles()).thenReturn(List.of());
        @SuppressWarnings("unchecked") CacheRestAction<Member> selfAction=mock(CacheRestAction.class),targetAction=mock(CacheRestAction.class);
        when(guild.retrieveMemberById(b)).thenReturn(selfAction);when(guild.retrieveMemberById(d)).thenReturn(targetAction);when(selfAction.useCache(false)).thenReturn(selfAction);when(targetAction.useCache(false)).thenReturn(targetAction);
        when(bot.await(selfAction)).thenReturn(self);when(bot.await(targetAction)).thenReturn(target);
        var json=new ObjectMapper().readTree("[{\"id\":\""+g+"\",\"position\":0,\"permissions\":\"0\",\"managed\":false},{\"id\":\""+b+"\",\"position\":10,\"permissions\":\"268435456\",\"managed\":true},{\"id\":\""+r+"\",\"position\":1,\"permissions\":\"8\",\"managed\":false}]");
        when(access.roles(settings)).thenReturn(json);JdaRoleTransport transport=new JdaRoleTransport(plugin,access);
        assertThrows(RolePolicy.Blocked.class,()->transport.change(g,d,r,true,()->true));verify(guild,never()).addRoleToMember(any(UserSnowflake.class),any(Role.class));
        assertThrows(RolePolicy.Blocked.class,()->transport.change(g,d,r,false,()->true));verify(guild,never()).removeRoleFromMember(any(UserSnowflake.class),any(Role.class));
    }
}
