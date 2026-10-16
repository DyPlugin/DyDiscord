package dev.dydiscord.giveaway;

import dev.dydiscord.DyDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import java.util.*;
import java.util.function.BooleanSupplier;

/** REST membership is authoritative; transient errors propagate and postpone the lottery. */
final class JdaGiveawayHost implements GiveawayHost {
    private final DyDiscord plugin;
    JdaGiveawayHost(DyDiscord plugin){this.plugin=plugin;}
    @Override public Context context(){
        var s=plugin.settings();var bot=plugin.bot();
        boolean connected=plugin.ready()&&bot!=null&&bot.usable()&&bot.applied()!=null&&bot.applied().guild().equals(s.guild());
        return new Context(connected,bot==null?-1:bot.generation(),s.guild(),s.giveaways());
    }
    @Override public Member member(String guild,String actor)throws Exception{
        Guild target=plugin.bot().guild();if(target==null||!target.getId().equals(guild))throw new IllegalStateException("Giveaway guild unavailable");
        try{
            var member=plugin.bot().await(target.retrieveMemberById(actor).useCache(false));
            return new Member(true,member.getUser().isBot(),member.getEffectiveName(),member.getRoles().stream().map(Role::getId).collect(java.util.stream.Collectors.toSet()));
        }catch(ErrorResponseException e){if(e.getErrorResponse()==ErrorResponse.UNKNOWN_MEMBER)return new Member(false,false,"",Set.of());throw e;}
    }
    @Override public boolean linked(String actor)throws Exception{return plugin.store().byDiscord(actor).isPresent();}
    @Override public String publish(GiveawayStore.Giveaway g,BooleanSupplier current)throws Exception{
        guard(g,current);plugin.bot().validateChannel(g.channel(),false);guard(g,current);
        return plugin.bot().await(plugin.bot().channel(g.channel()).sendMessageEmbeds(view(g,List.of())).setComponents(ActionRow.of(button(g,false))).setAllowedMentions(List.of()).setCheck(current)).getId();
    }
    @Override public void update(GiveawayStore.Giveaway g,List<GiveawayStore.Winner> winners,BooleanSupplier current)throws Exception{
        guard(g,current);plugin.bot().validateChannel(g.channel(),false);
        Message message=plugin.bot().await(plugin.bot().channel(g.channel()).retrieveMessageById(g.message()).setCheck(current));guard(g,current);
        if(!message.getAuthor().getId().equals(plugin.bot().guild().getSelfMember().getId()))throw new IllegalStateException("Giveaway message ownership mismatch");
        plugin.bot().await(message.editMessageEmbeds(view(g,winners)).setComponents(ActionRow.of(button(g,true))).setAllowedMentions(List.of()).setCheck(current));
    }
    private void guard(GiveawayStore.Giveaway g,BooleanSupplier current){var s=plugin.settings();if(!current.getAsBoolean()||!g.guild().equals(s.guild())||!g.channel().equals(s.giveaways().channel())||g.channel().equals(s.consoleChannel())||g.channel().equals(s.panelChannel()))throw new IllegalStateException("Giveaway policy changed");}
    private net.dv8tion.jda.api.entities.MessageEmbed view(GiveawayStore.Giveaway g,List<GiveawayStore.Winner> winners){
        String key=g.state().equals("DRAWN")?"giveaway-drawn":g.state().equals("CANCELLED")?"giveaway-cancelled":"giveaway-open";
        String names=winners.isEmpty()?text("giveaway-no-winners",Map.of()):String.join(", ",winners.stream().map(GiveawayStore.Winner::name).toList());
        String marker="DDTIME"+UUID.randomUUID().toString().replace("-","");
        String body=text(key,Map.of("prize",g.prize(),"winners",g.state().equals("DRAWN")?names:Integer.toString(g.winnerCount()),"ends",marker)).replace(marker,"<t:"+Math.max(0,Math.floorDiv(g.endsAt(),1000))+":R>");
        return new EmbedBuilder().setTitle(text("giveaway-title",Map.of())).setColor(g.state().equals("CANCELLED")?0xAAAAAA:0x5865F2).setDescription(body).build();
    }
    private Button button(GiveawayStore.Giveaway g,boolean disabled){return Button.primary("dd:giveaway:"+g.id(),text("giveaway-enter",Map.of())).withDisabled(disabled);}
    String text(String key,Map<String,String> values){return plugin.messages().render(true,plugin.bot().activeLanguage(),key,values);}
    @Override public void warning(){plugin.getLogger().warning("Giveaway operation postponed or unavailable. Check Discord connectivity, channel permissions and giveaway data; values withheld.");}
}
