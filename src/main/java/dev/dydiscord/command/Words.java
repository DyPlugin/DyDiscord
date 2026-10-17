package dev.dydiscord.command;
import java.util.*;
public final class Words {
    private static final Map<String,String> TR=new LinkedHashMap<>();private static final Map<String,String> ALIASES=new HashMap<>();
    static {
        String[][] pairs={{"help","yardım"},{"link","eşle"},{"status","durum"},{"unlink","kaldır"},{"confirm","onayla"},{"cancel","iptal"},{"language","dil"},{"invite","davet"},{"doctor","kontrol"},{"reload","yenile"},{"console","konsol"},{"channel","kanal"},{"send","gönder"},{"refresh","yenile"},{"commands","komutlar"},{"role","rol"},{"add","ekle"},{"remove","çıkar"},{"list","liste"},{"enable","aç"},{"disable","kapat"},{"restart","yenidenbaşlat"},{"administrator","yönetici"},{"account","hesap"},{"setinvite","ayarla"},{"admin","yönetim"}};
        for(String[] p:pairs){TR.put(p[0],p[1]);ALIASES.put(plain(p[0]),p[0]);ALIASES.put(plain(p[1]),p[0]);}
        String[][] communityPairs={{"players","oyuncular"},{"links","bağlantılar"},{"ip","ip"},{"chat","sohbet"},{"discord-minecraft","discord-minecraft"},{"minecraft-discord","minecraft-discord"},{"prefix","önek"},{"guild","sunucu"},{"token","token"},{"inbound","gelen"},{"outbound","giden"},{"notify","bildirim"},{"pause","duraklat"},{"resume","devam"},{"both","ikisi"},{"announce","duyuru"},{"announcements","duyurular"},{"set","düzenle"},{"maintenance","bakım"},{"restartnotice","yenidenbaşlatmabildirimi"},{"report","rapor"},{"config","ayarlar"},{"validate","doğrula"},{"translations","çeviriler"},{"setup","kurulum"},{"rewards","ödüller"},{"giveaway","çekiliş"},{"start","başlat"},{"end","bitir"}}; for(String[] p:communityPairs){TR.put(p[0],p[1]);ALIASES.put(plain(p[0]),p[0]);ALIASES.put(plain(p[1]),p[0]);} ALIASES.put("adres","ip"); ALIASES.put("lang","language");
        TR.put("server","sunucu");ALIASES.put("server","server");ALIASES.put("sunucu","server");
        String[][] rolePairs={{"roles","roller"},{"linked","eşlenen"},{"groups","gruplar"},{"preview","önizle"},{"sync","eşitle"},{"all","tümü"},{"cleanup","temizle"}};
        for(String[] p:rolePairs){TR.put(p[0],p[1]);ALIASES.put(plain(p[0]),p[0]);ALIASES.put(plain(p[1]),p[0]);}
        ALIASES.put("ayir","unlink");
        TR.put("admin","admin");TR.put("both","tümü");
        TR.put("on","aç");TR.put("off","kapat");
    }
    public static String plain(String v){return v.toLowerCase(Locale.ROOT).replace('ı','i').replace('ş','s').replace('ç','c').replace('ğ','g').replace('ö','o').replace('ü','u');}
    public static String canonical(String word){return ALIASES.getOrDefault(plain(word),word.toLowerCase(Locale.ROOT));}
    public static boolean matches(String expected,String actual){if(expected.equals("both"))return Set.of("both","ikisi","tumu").contains(plain(actual));if(expected.equals("refresh"))return Set.of("refresh","yenile").contains(plain(actual));if(expected.equals("reload"))return Set.of("reload","yenile").contains(plain(actual));if(expected.equals("on"))return Set.of("on","ac").contains(plain(actual));if(expected.equals("off"))return Set.of("off","kapat").contains(plain(actual));if(expected.equals("guild"))return Set.of("guild","sunucu").contains(plain(actual));return expected.equals(canonical(actual));}
    public static String display(String word,String lang){return lang.equals("tr")?TR.getOrDefault(word,word):word;}
    public static String displayPath(String path,String lang){return Arrays.stream(path.split(" ")).map(s->s.startsWith("<")?(lang.equals("tr")?switch(s){case "<reason>"->"<gerekçe>";case "<url>"->"<bağlantı>";case "<group>"->"<grup>";case "<priority>"->"<öncelik>";case "<template>"->"<şablon>";case "<text>"->"<metin>";case "<minutes>"->"<dakika>";case "<seconds>"->"<saniye>";case "<winners>"->"<kazanan>";case "<prize>"->"<ödül>";case "<inbound|outbound|both>"->"<gelen|giden|ikisi>";case "<on|off>"->"<aç|kapat>";default->s;}:s):display(s,lang)).reduce((a,b)->a+" "+b).orElse("");}
}
