package dev.dydiscord.console;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.Settings;
import dev.dydiscord.util.PluginThreads;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.time.*;

public final class ConsoleBridge implements AutoCloseable {
    private enum DispatchResult { DISPATCHED, NOT_FOUND, REJECTED }
    private final DyDiscord plugin;private final LogBuffer buffer=new LogBuffer();private final AtomicLong policy=new AtomicLong();
    private final AtomicInteger pending=new AtomicInteger();private final AtomicBoolean flushPending=new AtomicBoolean();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(PluginThreads.factory("DyDiscord-relay"));
    private final Map<String,Instant> users=new HashMap<>();private final Deque<Instant> channelTimes=new ArrayDeque<>();private volatile boolean closed;
    private AbstractAppender appender;private org.apache.logging.log4j.core.Logger root;
    private volatile long nextFlushNanos;private int failures;private Instant lastFailureLog=Instant.EPOCH;
    public ConsoleBridge(DyDiscord plugin){this.plugin=plugin;}
    public LogBuffer buffer(){return buffer;}
    public boolean attached(){return appender!=null&&appender.isStarted();}
    public void attach(){
        try {
            LoggerContext context=(LoggerContext)LogManager.getContext(false);root=context.getRootLogger();
            appender=new AbstractAppender("DyDiscordRelay",null,null,true,org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY){
                @Override public void append(LogEvent event){String name=event.getLoggerName()==null?"":event.getLoggerName();if(closed||!plugin.ready()||!plugin.settings().consoleEnabled()||!event.getLevel().isMoreSpecificThan(Level.INFO)||name.contains("DyDiscord")||name.startsWith("dev.dydiscord")||name.startsWith("net.dv8tion")||name.startsWith("okhttp")||event.getThreadName().startsWith("DyDiscord"))return;
                    String text="["+event.getLevel()+"] "+event.getMessage().getFormattedMessage();buffer.offer(text);if(event.getThrown()!=null){java.io.StringWriter writer=new java.io.StringWriter();event.getThrown().printStackTrace(new java.io.PrintWriter(writer));buffer.offer(writer.toString());}
                }
            };appender.start();root.addAppender(appender);
        }catch(LinkageError|ClassCastException e){plugin.getLogger().warning("Log relay unavailable: unsupported logging backend.");}
        timer.scheduleWithFixedDelay(this::flush,2,2,TimeUnit.SECONDS);
    }
    public void invalidate(){synchronized(buffer){policy.incrementAndGet();buffer.clear();}synchronized(users){users.clear();channelTimes.clear();}}
    private boolean relayCurrent(long version,long session,Settings captured){return !closed&&plugin.ready()&&version==policy.get()&&plugin.bot().alive(session)&&plugin.bot().usable()&&captured.equals(plugin.settings())&&captured.consoleEnabled();}
    private void flush(){
        if(closed||buffer.size()==0||System.nanoTime()<nextFlushNanos||!plugin.ready()||!plugin.settings().consoleEnabled()||!plugin.bot().usable()||!flushPending.compareAndSet(false,true))return;
        long version=policy.get(),session=plugin.bot().generation();Settings captured=plugin.settings();
        plugin.submit(()->{
            if(!relayCurrent(version,session,captured))return null;plugin.bot().validateChannel(captured.consoleChannel(),true);TextChannel target=plugin.bot().channel(captured.consoleChannel());String text;
            synchronized(buffer){if(!relayCurrent(version,session,captured))return null;text=buffer.drain();}
            if(!text.isEmpty())plugin.bot().await(target.sendMessage(text).setAllowedMentions(List.of()).setCheck(()->relayCurrent(version,session,captured)));
            return null;
        }).whenComplete((v,e)->{flushPending.set(false);if(closed||version!=policy.get()||!plugin.bot().alive(session))return;if(e!=null){failures=Math.min(failures+1,5);nextFlushNanos=System.nanoTime()+TimeUnit.SECONDS.toNanos(Math.min(60,2L<<failures))+ThreadLocalRandom.current().nextLong(1_000_000_000L);Instant now=plugin.clock().instant();if(now.isAfter(lastFailureLog.plusSeconds(60))){lastFailureLog=now;plugin.error(e);}}else{failures=0;nextFlushNanos=0;}});
    }
    private boolean rate(String user){synchronized(users){Instant now=plugin.clock().instant();users.values().removeIf(t->!now.isBefore(t.plusSeconds(120)));while(!channelTimes.isEmpty()&&!now.isBefore(channelTimes.peekFirst().plusSeconds(60)))channelTimes.removeFirst();if(users.size()>=4096&&!users.containsKey(user)||users.containsKey(user)&&now.isBefore(users.get(user).plusSeconds(2))||channelTimes.size()>=20)return false;users.put(user,now);channelTimes.addLast(now);return true;}}
    private boolean access(Member member,TextChannel channel,Settings settings){return member.hasPermission(channel,Permission.VIEW_CHANNEL)&&ConsolePolicy.authorized(member.hasPermission(Permission.ADMINISTRATOR),member.getRoles().stream().map(Role::getId).collect(java.util.stream.Collectors.toSet()),settings);}
    public void receive(MessageReceivedEvent event,long session){
        if(event.getMessage().getType()!=MessageType.DEFAULT)return;
        Settings captured=plugin.settings();String raw=event.getMessage().getContentRaw();
        if(raw.startsWith("dy!"))return;
        ConsolePolicy.Source source=new ConsolePolicy.Source(event.getGuild().getId(),event.getChannel().getId(),!event.getAuthor().isBot(),event.isWebhookMessage(),event.getChannel().getType().isThread(),event.getMessage().isEdited(),!event.getMessage().getAttachments().isEmpty());
        if(!ConsolePolicy.source(source,captured)||!rate(event.getAuthor().getId()))return;
        String command;try{command=ConsolePolicy.command(raw);}catch(IllegalArgumentException e){return;}
        if(pending.incrementAndGet()>50){pending.decrementAndGet();return;}
        long version=policy.get();String user=event.getAuthor().getId(),message=event.getMessageId();Instant created=event.getMessage().getTimeCreated().toInstant();
        plugin.submit(()->{
            if(closed||version!=policy.get()||!plugin.bot().alive(session)||Duration.between(created,plugin.clock().instant()).abs().getSeconds()>30)throw new IllegalStateException("Expired console request");
            plugin.bot().validateChannel(captured.consoleChannel(),true);TextChannel channel=plugin.bot().channel(captured.consoleChannel());Member member=plugin.bot().await(channel.getGuild().retrieveMemberById(user).useCache(false));
            String authority=plugin.bot().consoleAuthority(member,captured);if(authority.isEmpty())throw new IllegalStateException("Console access denied");
            if(!plugin.store().accept(message,plugin.clock().instant()))return false;
            String root=command.split(" ",2)[0];plugin.audit().write(user,"console-accepted guild="+captured.guild()+" channel="+captured.consoleChannel(),message,"root="+buffer.sanitize(root)+" args=omitted authority="+authority);
            // Re-fetch membership after durable acceptance, immediately before dispatch scheduling.
            Member fresh=plugin.bot().await(channel.getGuild().retrieveMemberById(user).useCache(false));TextChannel freshChannel=plugin.bot().channel(captured.consoleChannel());
            if(plugin.bot().consoleAuthority(fresh,captured).isEmpty())throw new IllegalStateException("Console access changed");
            DispatchResult dispatched=plugin.mainValue(()->{
                Settings now=plugin.settings();if(closed||version!=policy.get()||!plugin.bot().alive(session)||!now.equals(captured)||!now.consoleEnabled()||!now.commandsEnabled()||!plugin.bot().content()||Duration.between(created,plugin.clock().instant()).abs().getSeconds()>30)return DispatchResult.REJECTED;
                return plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),command)?DispatchResult.DISPATCHED:DispatchResult.NOT_FOUND;
            });
            String result=switch(dispatched){case DISPATCHED->"dispatched";case NOT_FOUND->"not-found";case REJECTED->"rejected";};plugin.store().result(message,result);plugin.audit().write(user,"console-result",message,result);return true;
        }).whenComplete((v,e)->{pending.decrementAndGet();if(e!=null&&!closed&&version==policy.get()&&plugin.bot().alive(session))plugin.error(e);});
    }
    @Override public void close(){closed=true;invalidate();timer.shutdownNow();if(root!=null&&appender!=null){root.removeAppender(appender);appender.stop();}}
}
