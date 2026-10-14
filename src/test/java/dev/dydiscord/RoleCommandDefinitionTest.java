package dev.dydiscord;

import dev.dydiscord.command.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoleCommandDefinitionTest {
    private Definition match(String text){return Definition.ALL.stream().filter(d->d.match(text.split(" "))).findFirst().orElseThrow();}
    @Test void roleLeavesHaveSeparatePermissionsAndExactArguments(){var definitions=Definition.ALL.stream().filter(d->d.key().startsWith("roles-")).toList();assertEquals(12,definitions.size());assertEquals(12,definitions.stream().map(Definition::permission).distinct().count());for(var d:definitions){String[] args=(d.path()+" x".repeat(d.arguments())).split(" ");assertTrue(d.match(args),d.key());assertFalse(d.match(Arrays.copyOf(args,args.length+1)),d.key());}assertEquals("roles-sync-all",match("admin roles sync all").key());assertEquals("roles-sync",match("admin roles sync 00000000-0000-0000-0000-000000000000").key());}
    @Test void turkishAndAsciiAliasesUseSameLeaves(){assertEquals("roles-groups-add",match("admin roller gruplar ekle vip 123456789012345678 10").key());assertEquals("roles-cleanup-confirm",match("yonetim roller temizle onayla token").key());assertEquals(List.of("durum"),Definition.tabs(new String[]{"admin","roller",""},"tr",false,p->p.equals("dydiscord.admin.roles.status")));assertTrue(Definition.tabs(new String[]{"admin","roles",""},"en",false,p->false).isEmpty());}
}
