package dev.dydiscord.boost;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.Settings;
import dev.dydiscord.storage.Store;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Worker-side REST/database reads, guarded main-thread console dispatch only. */
public final class BukkitBoostHost implements BoostBridge.Host {
    private final DyDiscord plugin;
    public BukkitBoostHost(DyDiscord plugin){this.plugin=plugin;}
    public Settings settings(){return plugin.settings();}
    public long botGeneration(){return plugin.bot().generation();}
    public boolean ready(){return plugin.ready()&&plugin.bot()!=null&&plugin.bot().usable()&&plugin.bot().applied()!=null&&settings().guild().equals(plugin.bot().applied().guild());}
    public BoostBridge.Source link(String discord)throws Exception{var snapshot=plugin.store().rewardSnapshot(discord);return new BoostBridge.Source(snapshot.link().orElse(null),snapshot.generation());}
    public long linkGeneration(){return plugin.store().linkGeneration();}
    public long linkGeneration(String discord){return plugin.store().rewardGeneration(discord);}
    public List<Store.Link> linkedAfter(String cursor,int limit)throws Exception{return plugin.store().linksPage(cursor,limit);}
    public BoostBridge.Membership freshMembership(String discord)throws Exception{
        if(!ready())throw new IllegalStateException("Boost connection unavailable");
        var bot=plugin.bot();long session=bot.generation();var guild=bot.guild();
        try{var member=bot.await(guild.retrieveMemberById(discord).useCache(false).setCheck(()->ready()&&bot.generation()==session));
            if(!ready()||bot.generation()!=session)throw new IllegalStateException("Boost session expired");
            return new BoostBridge.Membership(true,member.getUser().isBot(),member.getTimeBoosted()==null?null:member.getTimeBoosted().toInstant());
        }catch(ErrorResponseException failure){if(failure.getErrorResponse()==ErrorResponse.UNKNOWN_MEMBER&&ready()&&bot.generation()==session)return new BoostBridge.Membership(false,false,null);throw failure;}
    }
    public CompletableFuture<Boolean> dispatch(String command,BooleanSupplier guard){
        CompletableFuture<Boolean> result=new CompletableFuture<>();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        plugin.onMain(()->{if(result.isDone()||System.nanoTime()>=deadline||!guard.getAsBoolean()){result.complete(false);return;}
            try{result.complete(plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),command));}catch(Exception failure){result.completeExceptionally(failure);}});
        return result.orTimeout(2,TimeUnit.SECONDS);
    }
    public void warning(String message){plugin.getLogger().warning(message);}
}
