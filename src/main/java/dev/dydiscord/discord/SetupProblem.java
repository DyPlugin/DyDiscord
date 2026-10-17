package dev.dydiscord.discord;

/** Fixed diagnostic keys, never remote response bodies, tokens or arbitrary exception text. */
public final class SetupProblem extends IllegalStateException {
    public enum Code { CHANNEL_MISSING, BOT_UNAVAILABLE, CHANNEL_INVISIBLE, CHANNEL_PERMISSIONS, CONSOLE_PRIVATE, ACCESS_DENIED, RATE_LIMITED, RESTART_REQUIRED }
    private final Code code;
    public SetupProblem(Code code){super("Discord setup: "+code.name().toLowerCase(java.util.Locale.ROOT));this.code=code;}
    public String key(){return "setup-"+code.name().toLowerCase(java.util.Locale.ROOT).replace('_','-');}
}
