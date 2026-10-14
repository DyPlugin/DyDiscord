package dev.dydiscord.roles;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.command.Definition;
import dev.dydiscord.config.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Role commands share the bounded role worker; Bukkit access returns to the main thread. */
public final class RoleCommands {
    private record Confirmation(String actor,Settings settings,long epoch,Instant expiry) {}
    private static final class Denied extends RuntimeException {}
    private static final class InvalidConfirmation extends RuntimeException {}
    private final DyDiscord plugin;
    private final RoleEngine engine;
    private final SecureRandom random=new SecureRandom();
    private final Map<String,Confirmation> confirmations=new LinkedHashMap<>();
    private volatile boolean closed;
    private boolean bulkStarted;

    public RoleCommands(DyDiscord plugin,RoleEngine engine){this.plugin=plugin;this.engine=engine;}
    public void execute(CommandSender sender,Definition definition,String[] args){
        String actor=plugin.actor(sender),language=plugin.language(sender);
        String[] arguments=args.clone();
        engine.work(()->{
            if(arguments.length!=definition.arguments())throw new IllegalArgumentException();
            require(sender,definition);
            Settings before=plugin.settings();
            switch(definition.key()){
                case "roles-status"->status(sender,before,language);
                case "roles-linked-list"->line(sender,before.roleSync().linkedRoles().isEmpty()?text(language,"No linked-account roles.","Eşlenen hesaplar için rol ayarlanmadı."):join(before.roleSync().linkedRoles()));
                case "roles-groups-list"->{
                    if(before.roleSync().rules().isEmpty())line(sender,text(language,"No group role rules.","Grup ve rol kuralı ayarlanmadı."));
                    for(RoleSettings.Rule rule:before.roleSync().rules())line(sender,rule.group()+": "+join(rule.roleIds())+"; "+text(language,"priority ","öncelik ")+rule.priority());
                }
                case "roles-preview"->{
                    UUID uuid=uuid(arguments[0]);RolePolicy.Plan plan=engine.preview(uuid);
                    plugin.message(sender,"roles-preview-result",Map.of("add",join(plan.add()),"remove",join(plan.remove()),"unknown",text(language,plan.unknown()?"yes":"no",plan.unknown()?"var":"yok")));
                }
                case "roles-sync"->{
                    UUID uuid=uuid(arguments[0]);require(sender,definition);
                    if(!plugin.settings().roleSync().enabled())plugin.message(sender,"roles-disabled",Map.of());
                    else{engine.sync(uuid);plugin.message(sender,"roles-started",Map.of());}
                }
                case "roles-sync-all"->{
                    require(sender,definition);
                    if(!plugin.settings().roleSync().enabled())plugin.message(sender,"roles-disabled",Map.of());
                    else{engine.syncAll();bulkStarted=true;plugin.message(sender,"roles-started",Map.of());}
                }
                case "roles-cleanup-preview"->cleanupPreview(sender,definition,actor,before,language);
                case "roles-cleanup-confirm"->cleanupConfirm(sender,definition,actor,arguments[0]);
                case "roles-linked-add","roles-linked-remove","roles-groups-add","roles-groups-remove"->change(sender,definition,before,arguments);
                default->throw new IllegalArgumentException();
            }
            return null;
        }).exceptionally(failure->{failed(sender,failure);return null;});
    }
    private void require(CommandSender sender,Definition definition)throws Exception{
        if(closed||!plugin.mainValue(()->sender.hasPermission(definition.permission())&&(!(sender instanceof Player p)||p.isOnline())))throw new Denied();
    }
    private void status(CommandSender sender,Settings settings,String language)throws Exception{
        Map<String,Long> counts=engine.counts();long success=count(counts,"SUCCESS")+count(counts,"ABSENT"),pending=count(counts,"QUEUED")+count(counts,"PENDING"),failed=count(counts,"FAILED");
        String enabled=text(language,settings.roleSync().enabled()?"enabled":"disabled",settings.roleSync().enabled()?"açık":"kapalı");
        boolean providerAvailable=engine.providerAvailable();
        String provider=text(language,providerAvailable?"LuckPerms available":"LuckPerms unavailable",providerAvailable?"LuckPerms kullanılabilir":"LuckPerms kullanılamıyor");
        String batch=failed>0?text(language,"needs attention","kontrol gerekiyor")
            :engine.scanning()?text(language,"scanning","taranıyor")
            :pending>0?text(language,"retrying","yeniden deneniyor")
            :bulkStarted?text(language,"completed","tamamlandı"):text(language,"not started","başlatılmadı");
        String summary=enabled+"; "+provider+"; "+text(language,"batch ","toplu eşitleme ")+batch+"; "+text(language,"success ","başarılı ")+success+", "+text(language,"pending ","bekleyen ")+pending+", "+text(language,"failed ","başarısız ")+failed;
        String reason=engine.failure();
        if(reason!=null&&!reason.isEmpty())summary+="; "+diagnostic(language,reason);
        if(settings.roleSync().enabled()&&settings.roleSync().memberEvents()&&plugin.bot()!=null&&!plugin.bot().roleMemberEvents())
            summary+="; "+text(language,"member events unavailable; periodic reconciliation remains active","üye olayları alınamıyor; dönemsel eşitleme devam ediyor");
        plugin.message(sender,"roles-summary",Map.of("status",summary));
    }
    private static String diagnostic(String language,String reason){
        return switch(reason){
            case "critical-role","console-role-conflict"->text(language,"A privileged or console role is blocked; choose an ordinary role.","Ayrıcalıklı veya konsol rolü engellendi; normal bir rol seçin.");
            case "role-hierarchy"->text(language,"Move the bot above the affected roles.","Botu ilgili rollerin üstüne taşıyın.");
            case "manage-roles"->text(language,"Give the bot Manage Roles permission.","Bota Rolleri Yönet izni verin.");
            case "unavailable-role"->text(language,"Check that role IDs belong to this guild and are not managed roles.","Rol ID'lerinin bu sunucuya ait olduğunu ve yönetilen rol olmadığını kontrol edin.");
            case "bot-account"->text(language,"Bot accounts cannot receive linked-account roles.","Bot hesaplarına eşlenen hesap rolleri verilmez.");
            default->text(language,"Check the Discord connection and role settings; work remains pending.","Discord bağlantısını ve rol ayarlarını kontrol edin; işler bekliyor.");
        };
    }
    private static long count(Map<String,Long> counts,String key){return counts.getOrDefault(key,0L);}
    private void change(CommandSender sender,Definition definition,Settings before,String[] args)throws Exception{
        String key=definition.key(),path;Object value;
        if(key.startsWith("roles-linked-")){
            requireId(args[0]);Set<String> ids=new TreeSet<>(before.roleSync().linkedRoles());
            if(key.endsWith("add"))ids.add(args[0]);else ids.remove(args[0]);
            path="roles.linked-role-ids";value=new ArrayList<>(ids);
        }else{
            if(!args[0].matches("[a-z0-9_.-]{1,64}"))throw new IllegalArgumentException();requireId(args[1]);
            List<Map<String,Object>> rules=new ArrayList<>();boolean found=false;
            int priority=key.endsWith("add")?Integer.parseInt(args[2]):0;
            for(RoleSettings.Rule rule:before.roleSync().rules()){
                Set<String> ids=new TreeSet<>(rule.roleIds());int currentPriority=rule.priority();
                if(rule.group().equals(args[0])){found=true;if(key.endsWith("add")){ids.add(args[1]);currentPriority=priority;}else ids.remove(args[1]);}
                if(!ids.isEmpty())rules.add(rule(rule.group(),ids,currentPriority));
            }
            if(!found&&key.endsWith("add"))rules.add(rule(args[0],Set.of(args[1]),priority));
            path="roles.groups";value=rules;
        }
        require(sender,definition);
        if(plugin.settings()!=before)throw new IllegalStateException("Settings changed");
        plugin.configuration().change("bot.yml",path,value,candidate->{
            engine.validateSettings(candidate);
            require(sender,definition);
            if(plugin.settings()!=before)throw new IllegalStateException("Settings changed");
        });
        engine.refresh();plugin.message(sender,"roles-saved",Map.of());
    }
    private static Map<String,Object> rule(String group,Set<String> ids,int priority){return Map.of("group",group,"role-ids",new ArrayList<>(new TreeSet<>(ids)),"priority",priority);}
    private void cleanupPreview(CommandSender sender,Definition definition,String actor,Settings settings,String language)throws Exception{
        long epoch=engine.epoch();RoleEngine.CleanupPreview preview=engine.cleanupPreview();
        require(sender,definition);
        if(plugin.settings()!=settings||engine.epoch()!=epoch)throw new IllegalStateException("Role policy changed");
        line(sender,text(language,"Cleanup candidates: ","Temizlenecek hesaplar: ")+preview.accounts()+"; "+text(language,"roles: ","roller: ")+join(preview.roles()));
        Instant now=plugin.clock().instant();
        synchronized(confirmations){
            confirmations.entrySet().removeIf(e->!now.isBefore(e.getValue().expiry())||e.getValue().actor().equals(actor));
            if(confirmations.size()>=128)throw new RejectedExecutionException();
            byte[] bytes=new byte[18];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            confirmations.put(token,new Confirmation(actor,settings,epoch,now.plusSeconds(30)));
            plugin.message(sender,"roles-cleanup-confirmation",Map.of("token",token,"seconds","30"));
        }
    }
    private void cleanupConfirm(CommandSender sender,Definition definition,String actor,String token)throws Exception{
        Confirmation pending;
        synchronized(confirmations){pending=confirmations.get(token);if(pending==null||!pending.actor().equals(actor))throw new InvalidConfirmation();confirmations.remove(token);}
        require(sender,definition);
        if(plugin.settings().roleSync().enabled()||plugin.settings()!=pending.settings()||engine.epoch()!=pending.epoch()||!plugin.clock().instant().isBefore(pending.expiry()))throw new InvalidConfirmation();
        require(sender,definition);
        if(plugin.settings()!=pending.settings()||engine.epoch()!=pending.epoch()||!plugin.clock().instant().isBefore(pending.expiry()))throw new InvalidConfirmation();
        engine.startCleanup();bulkStarted=true;plugin.message(sender,"roles-started",Map.of());
    }
    private void failed(CommandSender sender,Throwable failure){
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()){
            if(cause instanceof Denied){plugin.message(sender,"no-permission",Map.of());return;}
            if(cause instanceof InvalidConfirmation){plugin.message(sender,"roles-invalid-confirmation",Map.of());return;}
        }
        Throwable cause=failure;while(cause.getCause()!=null&&(cause instanceof CompletionException||cause instanceof ExecutionException))cause=cause.getCause();
        if(cause instanceof IllegalArgumentException)plugin.message(sender,"usage",Map.of());
        else{plugin.error(failure);plugin.message(sender,"roles-unavailable",Map.of());}
    }
    private void line(CommandSender sender,String status){plugin.message(sender,"status-line",Map.of("status",status));}
    private static String text(String language,String en,String tr){return language.equals("tr")?tr:en;}
    private static String join(Set<String> values){return String.join(", ",new TreeSet<>(values));}
    private static void requireId(String id){if(!Settings.snowflake(id))throw new IllegalArgumentException();}
    private static UUID uuid(String value){UUID uuid=UUID.fromString(value);if(!uuid.toString().equalsIgnoreCase(value))throw new IllegalArgumentException();return uuid;}
    public void close(){closed=true;synchronized(confirmations){confirmations.clear();}}
}
