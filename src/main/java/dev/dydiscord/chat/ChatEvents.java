package dev.dydiscord.chat;

import dev.dydiscord.DyDiscord;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.RegisteredListener;
import java.util.*;

/** One chat API per server: Paper final chat event, otherwise Bukkit legacy event. */
public final class ChatEvents implements Listener {
    private final DyDiscord plugin;private final Set<Event> seen=Collections.newSetFromMap(new IdentityHashMap<>());private final Deque<Event> order=new ArrayDeque<>();
    private volatile boolean paper,adapterFailed;private boolean warned,conflictWarned;private volatile HandlerList paperHandlers,synchronousPaperHandlers;
    public ChatEvents(DyDiscord plugin){this.plugin=plugin;}
    @SuppressWarnings("unchecked") public void register(){
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        try{Class<?> type=Class.forName("io.papermc.paper.event.player.AsyncChatEvent");paperHandlers=(HandlerList)type.getMethod("getHandlerList").invoke(null);Class<?> sync=Class.forName("io.papermc.paper.event.player.ChatEvent");synchronousPaperHandlers=(HandlerList)sync.getMethod("getHandlerList").invoke(null);plugin.getServer().getPluginManager().registerEvent((Class<? extends Event>)type,this,EventPriority.MONITOR,(listener,event)->paper(event),plugin,true);paper=true;}
        catch(ClassNotFoundException missing){paper=false;}
        catch(ReflectiveOperationException|LinkageError failure){paper=true;adapterFailed=true;plugin.warn("setup-chat-adapter");}
    }
    private synchronized boolean first(Event event){if(!seen.add(event))return false;order.addLast(event);if(order.size()>512)seen.remove(order.removeFirst());return true;}
    private List<String> blockers(){
        List<RegisteredListener> listeners=new ArrayList<>(List.of(AsyncPlayerChatEvent.getHandlerList().getRegisteredListeners()));listeners.addAll(List.of(PlayerChatEvent.getHandlerList().getRegisteredListeners()));if(paperHandlers!=null)listeners.addAll(List.of(paperHandlers.getRegisteredListeners()));
        if(synchronousPaperHandlers!=null)listeners.addAll(List.of(synchronousPaperHandlers.getRegisteredListeners()));
        return listeners.stream().filter(listener->listener.getPlugin()!=plugin).map(listener->{String name=ChatText.plain(listener.getPlugin().getName(),64);return name.isBlank()?"?":name;}).distinct().sorted().toList();
    }
    private boolean compatible(){boolean safe=blockers().isEmpty();if(safe)conflictWarned=false;else if(!conflictWarned){conflictWarned=true;plugin.warn("setup-chat-conflict");}return safe;}
    /** Read-only handler snapshots: no player state or network requests. */
    public String problem(String language){
        if(adapterFailed)return plugin.messages().get(false,language,"chat-compatibility-adapter");
        List<String> names=blockers();if(names.isEmpty())return "";
        String shown=String.join(", ",names.stream().limit(8).toList())+(names.size()>8?" …":"");
        return plugin.messages().render(false,language,"chat-compatibility-blocked",Map.of("plugins",shown));
    }
    public String adapter(){return paper?"Paper AsyncChatEvent":"Bukkit AsyncPlayerChatEvent";}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void legacy(AsyncPlayerChatEvent event){if(paper||!first(event)||!plugin.ready()||!plugin.settings().chat().toDiscord())return;ChatBridge.Context context=plugin.chat().capture();plugin.onMain(()->{
        if(!plugin.chat().current(context))return;Player player=event.getPlayer();Set<UUID> recipients=new HashSet<>();event.getRecipients().forEach(p->recipients.add(p.getUniqueId()));if(compatible()&&ChatBridge.publicChat(event.isCancelled(),!plugin.chat().visible(player),ChatBridge.muted(player),recipients,publicPlayers()))plugin.chat().minecraft(player,event.getMessage(),context);
    });}
    private void paper(Event event){if(adapterFailed||!first(event)||!plugin.ready()||!plugin.settings().chat().toDiscord())return;ChatBridge.Context context=plugin.chat().capture();plugin.onMain(()->{try{
        if(!plugin.chat().current(context))return;
        Player player=(Player)Ranks.call(event,"getPlayer");Set<?> viewers=(Set<?>)Ranks.call(event,"viewers");Set<UUID> recipients=new HashSet<>();for(Object viewer:viewers)if(viewer instanceof Player p)recipients.add(p.getUniqueId());
        boolean cancelled=event instanceof Cancellable c&&c.isCancelled();if(!compatible()||!ChatBridge.publicChat(cancelled,!plugin.chat().visible(player),ChatBridge.muted(player),recipients,publicPlayers()))return;
        Object component=Ranks.call(event,"message");Class<?> serializer=Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");Object plain=serializer.getMethod("plainText").invoke(null);plugin.chat().minecraft(player,(String)Ranks.call(plain,"serialize",component),context);
    }catch(java.lang.reflect.InvocationTargetException failure){if(failure.getCause() instanceof LinkageError)adapterFailed=true;if(!warned){warned=true;plugin.warn("setup-chat-adapter");}}
    catch(ReflectiveOperationException|LinkageError failure){adapterFailed=true;if(!warned){warned=true;plugin.warn("setup-chat-adapter");}}
    catch(RuntimeException failure){if(!warned){warned=true;plugin.warn("setup-chat-adapter");}}});}
    private Set<UUID> publicPlayers(){Set<UUID> players=new HashSet<>();for(Player player:plugin.getServer().getOnlinePlayers())if(!ChatBridge.hidden(player))players.add(player.getUniqueId());return players;}
    private void later(Event event,java.util.function.Consumer<ChatBridge.Context> action){if(!plugin.ready()||!first(event))return;ChatBridge.Context context=plugin.chat().capture();plugin.onMain(()->{if(plugin.chat().current(context))action.accept(context);});}
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent event){if(plugin.ready())plugin.chat().load(event.getPlayer().getUniqueId());if(plugin.ready()&&plugin.chat().visible(event.getPlayer())&&event.getJoinMessage()!=null&&!event.getJoinMessage().isBlank())later(event,context->{if(event.getJoinMessage()!=null&&!event.getJoinMessage().isBlank())plugin.chat().event("join",event.getPlayer(),"",context);});}
    @EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent event){plugin.chat().leave(event.getPlayer().getUniqueId());if(plugin.ready()&&plugin.chat().visible(event.getPlayer())&&event.getQuitMessage()!=null&&!event.getQuitMessage().isBlank())later(event,context->{if(event.getQuitMessage()!=null&&!event.getQuitMessage().isBlank())plugin.chat().event("quit",event.getPlayer(),"",context);});}
    @EventHandler(priority=EventPriority.MONITOR) public void death(PlayerDeathEvent event){if(!plugin.ready()||!plugin.chat().publicDeath(event.getEntity(),event.getDeathMessage())||!Boolean.TRUE.equals(event.getEntity().getWorld().getGameRuleValue(GameRule.SHOW_DEATH_MESSAGES)))return;later(event,context->{String message=event.getDeathMessage();if(!(event instanceof Cancellable c&&c.isCancelled())&&Boolean.TRUE.equals(event.getEntity().getWorld().getGameRuleValue(GameRule.SHOW_DEATH_MESSAGES))&&message!=null&&!message.isBlank()&&plugin.chat().publicDeath(event.getEntity(),message))plugin.chat().event("death",event.getEntity(),message,context);});}
}
