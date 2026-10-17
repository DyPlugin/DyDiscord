package dev.dydiscord.config;

import java.time.ZoneId;
import java.util.Map;

public record AccountSettings(boolean showRoles,ZoneId zone) {
    public static AccountSettings read(Map<String,Object> values){
        String name=Settings.bounded(values,"account.time-zone","UTC",64);
        try{return new AccountSettings(Settings.bool(values,"account.show-integrated-roles",true),ZoneId.of(name));}
        catch(java.time.DateTimeException invalid){throw Settings.invalid("account.time-zone");}
    }
}
