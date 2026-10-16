package dev.dydiscord.giveaway;

import dev.dydiscord.config.GiveawaySettings;
import java.util.*;
import java.security.SecureRandom;

/** The service boundary keeps JDA/Bukkit out of lottery and persistence tests. */
public interface GiveawayHost {
    record Context(boolean connected,long session,String guild,GiveawaySettings settings){}
    record Member(boolean present,boolean bot,String name,Set<String> roles){public Member{roles=Set.copyOf(roles);}}
    Context context();
    Member member(String guild,String discord)throws Exception;
    boolean linked(String discord)throws Exception;
    String publish(GiveawayStore.Giveaway giveaway,java.util.function.BooleanSupplier current)throws Exception;
    void update(GiveawayStore.Giveaway giveaway,List<GiveawayStore.Winner> winners,java.util.function.BooleanSupplier current)throws Exception;
    void warning();
    static <T> List<T> shuffled(List<T> entrants,SecureRandom random){List<T> result=new ArrayList<>(entrants);for(int i=result.size()-1;i>0;i--){int j=random.nextInt(i+1);Collections.swap(result,i,j);}return result;}
}
