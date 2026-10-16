package dev.dydiscord.reward;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.LinkRewardSettings;
import dev.dydiscord.storage.Store;
import org.bukkit.Bukkit;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Database snapshots stay on workers; the immediate main-thread guard reads atomic generations. */
public final class BukkitLinkRewardHost implements LinkRewardService.Host {
    private final DyDiscord plugin;
    public BukkitLinkRewardHost(DyDiscord plugin){this.plugin=plugin;}
    public LinkRewardSettings settings(){return plugin.settings().linkRewards();}
    public boolean mainThread(){return Bukkit.isPrimaryThread();}
    public boolean ready(){return plugin.ready()&&plugin.bot()!=null&&plugin.bot().usable()&&plugin.bot().applied()!=null&&plugin.settings().guild().equals(plugin.bot().applied().guild());}
    public long session(){return plugin.bot()==null?-1:plugin.bot().generation();}
    public Store.LinkSnapshot snapshot(String discord)throws Exception{return plugin.store().rewardSnapshot(discord);}
    public long linkGeneration(String discord){return plugin.store().rewardGeneration(discord);}
    public boolean firstRewardEligible(Store.Link link)throws Exception{return plugin.store().firstRewardEligible(link);}
    public CompletableFuture<Boolean> dispatch(String command,BooleanSupplier guard){
        CompletableFuture<Boolean> result=new CompletableFuture<>();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        plugin.onMain(()->{
            if(result.isDone()||System.nanoTime()>=deadline||!guard.getAsBoolean()){result.complete(false);return;}
            try{result.complete(plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),command));}
            catch(Exception error){result.completeExceptionally(error);}
        });return result.orTimeout(2,TimeUnit.SECONDS);
    }
    public void warning(String safeMessage){plugin.getLogger().warning(safeMessage);}
}
