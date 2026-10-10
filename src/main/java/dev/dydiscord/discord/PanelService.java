package dev.dydiscord.discord;

import dev.dydiscord.storage.Store;
import java.util.*;

/** Creation is serialized and a durable pending marker prevents duplicates after ambiguous sends. */
public final class PanelService {
    public interface Transport {
        boolean exists(Store.Panel panel)throws Exception; // false means confirmed 404 only
        void update(Store.Panel panel)throws Exception;
        void disable(Store.Panel panel)throws Exception;
        List<String> recover(String channel)throws Exception;
        String create(String channel)throws Exception;
    }
    private final Store store;private volatile Store.Panel current;
    public PanelService(Store store)throws Exception{this.store=store;current=store.panel().orElse(null);}
    public Store.Panel current(){return current;}
    public String url(String guild,String channel){Store.Panel p=current;return channel.isEmpty()?"":"https://discord.com/channels/"+guild+"/"+channel+(p!=null&&p.guild().equals(guild)&&p.channel().equals(channel)&&!p.message().equals("pending")?"/"+p.message():"");}
    public synchronized Store.Panel refresh(String guild,String channel,Transport remote)throws Exception {
        if(channel.isEmpty())throw new IllegalStateException("panel.channel-id is missing");
        Store.Panel old=store.panel().orElse(null);
        if(old!=null&&!old.channel().equals(channel)){remote.disable(old);old=null;}
        if(old!=null&&!old.guild().equals(guild))throw new IllegalStateException("Panel belongs to a different guild; repair required");
        if(old!=null&&!old.message().equals("pending")&&remote.exists(old)){remote.update(old);current=old;return old;}
        List<String> candidates=remote.recover(channel);
        if(candidates.size()>1)throw new IllegalStateException("Multiple panel markers; manual repair required");
        if(candidates.size()==1){Store.Panel found=new Store.Panel(guild,channel,candidates.get(0));remote.update(found);store.panel(found);current=found;return found;}
        if(old!=null&&old.message().equals("pending"))throw new IllegalStateException("Ambiguous panel send; manual repair required");
        Store.Panel pending=new Store.Panel(guild,channel,"pending");store.panel(pending);current=pending;
        String id=remote.create(channel);Store.Panel sent=new Store.Panel(guild,channel,id);store.panel(sent);current=sent;return sent;
    }
}
