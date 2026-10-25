package dev.dydiscord.util;

import org.bukkit.ChatColor;
import java.util.regex.Pattern;

/** Minecraft text formatting: '&' codes everywhere, MiniMessage tags where the server ships Adventure. */
public final class Colors {
    /** A MiniMessage tag such as <green>, <#55ff55>, <gradient:...> or </bold>. */
    private static final Pattern TAG=Pattern.compile("<[a-zA-Z#/!][^<>]*>");
    private static final boolean MINI_MESSAGE=present("net.kyori.adventure.text.minimessage.MiniMessage")&&present("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
    private Colors(){}
    public static boolean miniMessage(){return MINI_MESSAGE;}
    /** Applies MiniMessage tags (Paper, Purpur, Folia) and then '&' codes; on Spigot only '&' codes apply. */
    public static String color(String text){return ChatColor.translateAlternateColorCodes('&',tags(text));}
    /** Applies MiniMessage tags only. Text without tags is never handed to MiniMessage. */
    public static String tags(String text){
        if(text==null)return "";
        if(!MINI_MESSAGE||!TAG.matcher(text).find()&&!text.contains("\\<"))return text;
        try{return MiniMessageBridge.toLegacy(text);}catch(RuntimeException|LinkageError malformed){return text;}
    }
    /** Inserted values are shown as typed: their tags are escaped. Without Adventure the text is returned unchanged. */
    public static String escapeTags(String text){
        if(!MINI_MESSAGE||text==null||text.indexOf('<')<0)return text;
        return text.replace("\\","\\\\").replace("<","\\<");
    }
    private static boolean present(String name){try{Class.forName(name,false,Colors.class.getClassLoader());return true;}catch(ClassNotFoundException|LinkageError missing){return false;}}
}
