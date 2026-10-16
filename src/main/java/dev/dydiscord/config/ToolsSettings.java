package dev.dydiscord.config;
import java.util.*;
/** Optional owner-written notices, bounded and hot-reloadable. */
public record ToolsSettings(String channel,boolean startup,Map<String,String> templates,boolean scheduled,List<Schedule> schedules,String updateAudience,boolean updateBeta) {
 public record Schedule(String template,String target,int seconds) {}
 public ToolsSettings {templates=Map.copyOf(templates);schedules=List.copyOf(schedules);}
 public static ToolsSettings read(Map<String,Object> c,Map<String,Object> b){
  String root="community-tools";
  if(!(Settings.get(b,root,Map.of()) instanceof Map<?,?>))throw Settings.invalid(root);
  String channel=Settings.id(b,root+".channel-id");
  if(!channel.isEmpty()&&(channel.equals(Settings.id(b,"console.channel-id"))||channel.equals(Settings.id(b,"panel.channel-id"))))throw Settings.invalid(root+".channel-id");
  Object raw=Settings.get(b,root+".templates",Map.of("maintenance","Maintenance will begin shortly.","restart","The server will restart shortly."));
  if(!(raw instanceof Map<?,?> map)||map.size()>20)throw Settings.invalid(root+".templates");
  Map<String,String> templates=new LinkedHashMap<>();
  for(var entry:map.entrySet()){
   if(!(entry.getKey() instanceof String id)||!id.matches("[a-z][a-z0-9-]{0,31}")||!(entry.getValue() instanceof String text)||text.isBlank()||text.length()>800||unsafe(text))throw Settings.invalid(root+".templates");
   templates.put(id,text);
  }
  raw=Settings.get(b,root+".announcements.schedules",List.of());
  if(!(raw instanceof List<?> list)||list.size()>20)throw Settings.invalid(root+".announcements.schedules");
  List<Schedule> schedules=new ArrayList<>();
  for(Object value:list){if(!(value instanceof Map<?,?> item))throw Settings.invalid(root+".announcements.schedules");Map<String,Object> fields=new HashMap<>();item.forEach((k,v)->fields.put(String.valueOf(k),v));
   String template=Settings.bounded(fields,"template","",32);if(!templates.containsKey(template))throw Settings.invalid(root+".announcements.schedules.template");
   schedules.add(new Schedule(template,Settings.choice(fields,"target","both",Set.of("both","minecraft","discord")),Settings.number(fields,"every-seconds",300,60,86400)));
  }
  return new ToolsSettings(channel,Settings.bool(b,root+".startup-notice",false),templates,Settings.bool(b,root+".announcements.enabled",false),schedules,Settings.choice(c,"updates.notify","both",Set.of("console","players","both")),Settings.bool(c,"updates.include-beta",false));
 }
 private static boolean unsafe(String s){return s.codePoints().anyMatch(cp->Character.isISOControl(cp)||Character.getType(cp)==Character.FORMAT||cp==0x2028||cp==0x2029);}
}
