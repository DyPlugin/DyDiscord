package dev.dydiscord.roles;

import dev.dydiscord.config.RoleSettings;
import java.util.*;

/** Pure, one-way policy. Prefixes, Discord rank positions and primary-group labels are not inputs. */
public final class RolePolicy {
    public record Role(String id,String guild,int position,long permissions,boolean managed){}
    public record Plan(Set<String> add,Set<String> remove,boolean unknown){
        public Plan{add=Set.copyOf(add);remove=Set.copyOf(remove);}
    }
    public static final class Blocked extends Exception{public Blocked(String reason){super(reason);}}
    private static final long MANAGE_ROLES=1L<<28;
    // Administration, moderation and management privileges are unsuitable for automatic grants.
    public static final long CRITICAL=(1L<<1)|(1L<<2)|(1L<<3)|(1L<<4)|(1L<<5)|(1L<<13)|(1L<<22)|(1L<<23)|(1L<<24)|(1L<<27)|(1L<<28)|(1L<<29)|(1L<<30)|(1L<<33)|(1L<<34)|(1L<<40)|(1L<<51);
    public static void validate(RoleTransport.Member member,Set<String> ids,Set<String> console,String guild)throws Blocked{
        if(!ids.isEmpty()&&(member.botPermissions()&MANAGE_ROLES)==0&&(member.botPermissions()&(1L<<3))==0)throw new Blocked("manage-roles");
        for(String id:ids){Role role=member.catalog().get(id);
            if(role==null||!role.guild().equals(guild)||id.equals(guild)||role.managed())throw new Blocked("unavailable-role");
            boolean below=role.position()<member.botPosition()||role.position()==member.botPosition()&&dev.dydiscord.config.Settings.snowflake(member.botTopRoleId())&&Long.compareUnsigned(Long.parseUnsignedLong(id),Long.parseUnsignedLong(member.botTopRoleId()))>0;
            if(!below)throw new Blocked("role-hierarchy");
            if((role.permissions()&CRITICAL)!=0||console.contains(id))throw new Blocked("critical-role");
        }
    }
    public static Plan plan(RoleSettings settings,boolean linked,GroupProvider.Result groups,Set<String> actual,Set<String> history){
        Set<String> desired=new HashSet<>();boolean unknown=linked&&!settings.rules().isEmpty()&&!groups.known();
        if(linked){
            desired.addAll(settings.linkedRoles());
            if(unknown){for(String id:settings.groupRoleIds())if(actual.contains(id))desired.add(id);}
            else{
                int best=Integer.MIN_VALUE;for(var rule:settings.rules())if(groups.groups().contains(rule.group()))best=Math.max(best,rule.priority());
                for(var rule:settings.rules())if(groups.groups().contains(rule.group())&&(!settings.selection().equals("highest")||rule.priority()==best))desired.addAll(rule.roleIds());
            }
        }
        Set<String> add=new HashSet<>(desired);add.removeAll(actual);
        Set<String> remove=new HashSet<>(actual);remove.retainAll(history);remove.removeAll(desired);
        return new Plan(add,remove,unknown);
    }
    private RolePolicy(){}
}
