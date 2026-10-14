package dev.dydiscord.roles;

import dev.dydiscord.config.Settings;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Fresh REST observations; an exception is unknown, never an authoritative absence. */
public interface RoleTransport {
    record Member(boolean present,boolean bot,Set<String> roles,Map<String,RolePolicy.Role> catalog,int botPosition,long botPermissions,String botTopRoleId){
        public Member(boolean present,boolean bot,Set<String> roles,Map<String,RolePolicy.Role> catalog,int botPosition,long botPermissions){this(present,bot,roles,catalog,botPosition,botPermissions,"");}
        public Member{roles=Set.copyOf(roles);catalog=Map.copyOf(catalog);Objects.requireNonNull(botTopRoleId);}
    }
    boolean available();long session();String guild();
    Member inspect(String guild,String discord)throws Exception;
    void change(String guild,String discord,String role,boolean add,BooleanSupplier current)throws Exception;
    void validate(Settings settings)throws Exception;
}
