package dev.dydiscord.config;
import java.util.*;
/** Update notification preferences; announcements have been retired. */
public record ToolsSettings(String updateAudience,boolean updateBeta) {
 public static ToolsSettings read(Map<String,Object> c,Map<String,Object> b){
  return new ToolsSettings(Settings.choice(c,"updates.notify","both",Set.of("console","players","both")),Settings.bool(c,"updates.include-beta",false));
 }
}
