package dev.dydiscord;

import dev.dydiscord.config.Configuration;
import dev.dydiscord.discord.Bot;
import dev.dydiscord.discord.PanelView;
import dev.dydiscord.language.Messages;
import dev.dydiscord.storage.Store;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PanelViewTest {
    private static final String BOT="123456789012345678";
    private static final String AVATAR="https://cdn.discordapp.com/avatars/123456789012345678/avatar.png";
    private static final String BRAND_ID="dd:panel:brand";
    private static final List<String> BUTTON_IDS=List.of("dd:panel:link","dd:panel:unlink","dd:panel:account");
    @TempDir Path folder;

    private Messages messages()throws Exception{return TestSupport.config(folder).active().messages();}
    private Container card(String language)throws Exception{return PanelView.card(messages(),language,"Test Server",AVATAR);}
    @Test void missingGuildIconKeepsTextDividerAndAllThreeActions()throws Exception{try(var data=PanelView.create(PanelView.card(messages(),"tr","Test",null))){var children=component(data.toData()).getArray("components");assertEquals(List.of(10,14,1),children.stream((a,i)->a.getObject(i).getInt("type")).toList());assertEquals(3,children.getObject(2).getArray("components").length());}}
    private DataObject component(DataObject message){
        assertEquals(1,message.getArray("components").length(),"The entire panel must be one top-level card");
        DataObject card=message.getArray("components").getObject(0);
        assertEquals(17,card.getInt("type"));assertEquals(PanelView.MARKER,card.getInt("id"));return card;
    }
    private Message message(String author,List<? extends MessageTopLevelComponent> components,List<MessageEmbed> embeds){
        Message message=mock(Message.class);User user=mock(User.class);when(user.getId()).thenReturn(author);when(message.getAuthor()).thenReturn(user);
        MessageComponentTree tree=MessageComponentTree.of(components);when(message.getComponents()).thenReturn(tree.getComponents());when(message.getComponentTree()).thenReturn(tree);when(message.getEmbeds()).thenReturn(embeds);return message;
    }

    @ParameterizedTest @ValueSource(strings={"en","tr"})
    void panelSerializesAsOneLocalizedContainerWithSpacedExplanationsAndThreeActions(String language)throws Exception{
        boolean tr=language.equals("tr");try(var data=PanelView.create(card(language))){
            DataObject payload=data.toData(),container=component(payload);assertEquals(32768,payload.getInt("flags"));assertTrue(data.isUsingComponentsV2());
            assertTrue(data.getContent().isEmpty());assertTrue(data.getEmbeds().isEmpty());assertTrue(payload.optArray("embeds").map(a->a.isEmpty()).orElse(true));
            assertEquals(0,payload.getObject("allowed_mentions").getArray("parse").length());assertEquals(0xFF55FF,container.getInt("accent_color"));
            var children=container.getArray("components");
            assertEquals(List.of(9,14,1),children.stream((a,i)->a.getObject(i).getInt("type")).toList(),"The complete explanation shares one avatar section, with a divider before the three actions");
            var section=children.stream((a,i)->a.getObject(i)).filter(c->c.getInt("type")==9).findFirst().orElseThrow();
            assertEquals(1,section.getArray("components").length());
            String text=section.getArray("components").getObject(0).getString("content");
            assertTrue(text.startsWith("## Test Server - "+(tr?"Hesap Eşle":"Link Your Account")+"\n"));
            assertFalse(text.contains("\n\n\n"),"Separate paragraphs by one empty line without excess padding");
            assertEquals(11,section.getObject("accessory").getInt("type"));assertEquals(AVATAR,section.getObject("accessory").getObject("media").getString("url"));
            assertEquals("Discord",section.getObject("accessory").getString("description"));
            var separator=children.getObject(1);assertTrue(separator.getBoolean("divider"));assertEquals(1,separator.getInt("spacing"));
            var rows=children.stream((a,i)->a.getObject(i)).filter(c->c.getInt("type")==1).toList();assertEquals(1,rows.size());
            var buttons=rows.get(0).getArray("components");assertEquals(3,buttons.length());List<String> labels=tr?List.of("Hesap Eşle","Eşlemeyi Kaldır","Hesabım"):List.of("Link Account","Unlink","My Account");
            List<String> descriptionLabels=tr?List.of("Hesap Eşle","Hesabım","Eşlemeyi Kaldır"):List.of("Link Account","My Account","Unlink");
            String[] paragraphs=text.split("\n\n",-1);assertEquals(5,paragraphs.length,"Introduction, three individual action explanations, and privacy each have their own paragraph");
            for(int i=0;i<3;i++){var button=buttons.getObject(i);assertEquals(2,button.getInt("type"));assertEquals(BUTTON_IDS.get(i),button.getString("custom_id"));assertEquals(labels.get(i),button.getString("label"));assertEquals(List.of(1,4,2).get(i).intValue(),button.getInt("style"));assertFalse(button.getBoolean("disabled",false));assertTrue(paragraphs[i+1].startsWith("**"+descriptionLabels.get(i)+":**"));}
            List<String> texts=data.getComponentTree().findAll(TextDisplay.class).stream().map(TextDisplay::getContent).toList();
            assertEquals(List.of(text),texts);assertTrue(text.contains(tr?"/discord eşle":"/discord link"));assertTrue(text.contains(tr?"sadece siz":"Only you"));
            assertFalse(text.contains("Onayınızla"));assertFalse(text.contains("after confirmation"));
            assertTrue(paragraphs[4].startsWith(tr?"Hesap bilgilerinizi sadece siz":"Only you"));
            assertFalse(payload.toString().contains("DyDiscord"),"Neither the text, actions, nor image description should show the removed branding");
            assertFalse(payload.toString().contains("DyPlugin"));assertFalse(payload.toString().contains("dd:panel:v1"));
        }
    }

    @Test void legacyToV2EditExplicitlyClearsLegacyContentAndEmbeds()throws Exception{
        try(var edit=PanelView.update(card("tr"))){
            DataObject payload=edit.toData();component(payload);assertEquals(32768,payload.getInt("flags"));assertTrue(edit.isUsingComponentsV2());
            assertTrue(payload.hasKey("content"),"Discord requires an explicit clear when replacing a legacy message");
            assertTrue(payload.isNull("content")||payload.getString("content").isEmpty());assertTrue(payload.hasKey("embeds"));assertTrue(payload.getArray("embeds").isEmpty());
            assertEquals(0,payload.getObject("allowed_mentions").getArray("parse").length());
        }
    }

    @Test void recoveryAcceptsOwnedV2OrLegacyPanelsAndRejectsUnrelatedMessages()throws Exception{
        Container valid=card("en");assertTrue(PanelView.recognizable(message(BOT,List.of(valid),List.of()),BOT));
        Container oldV2=Container.of(TextDisplay.of("Account panel"),ActionRow.of(Button.primary("dd:panel:link","Link Account"),Button.secondary("dd:panel:account","My Account"),Button.danger("dd:panel:unlink","Unlink"))).withUniqueId(PanelView.MARKER);
        assertTrue(PanelView.recognizable(message(BOT,List.of(oldV2),List.of()),BOT),"The previously deployed three-button Container must remain recoverable");
        Container brandedV2=Container.of(TextDisplay.of("Account panel"),ActionRow.of(Button.secondary(BRAND_ID,"DyDiscord").asDisabled(),Button.primary("dd:panel:link","Link Account"),Button.secondary("dd:panel:account","My Account"),Button.danger("dd:panel:unlink","Unlink"))).withUniqueId(PanelView.MARKER);
        assertTrue(PanelView.recognizable(message(BOT,List.of(brandedV2),List.of()),BOT),"The previous four-button panel must be recognized for an in-place upgrade");
        assertFalse(PanelView.recognizable(message("223456789012345678",List.of(valid),List.of()),BOT));
        assertFalse(PanelView.recognizable(message(BOT,List.of(valid.withUniqueId(99)),List.of()),BOT));
        Container partial=Container.of(TextDisplay.of("Account panel"),ActionRow.of(Button.primary("dd:panel:link","Link Account"),Button.secondary("dd:panel:account","My Account"))).withUniqueId(PanelView.MARKER);
        assertFalse(PanelView.recognizable(message(BOT,List.of(partial),List.of()),BOT));
        Container unrelated=Container.of(TextDisplay.of("Unrelated message"),ActionRow.of(Button.primary("other:link","Open"))).withUniqueId(PanelView.MARKER);
        assertFalse(PanelView.recognizable(message(BOT,List.of(unrelated),List.of()),BOT));
        Container extra=Container.of(TextDisplay.of("Unrelated message"),ActionRow.of(Button.primary("dd:panel:link","Link Account"),Button.secondary("dd:panel:account","My Account"),Button.danger("dd:panel:unlink","Unlink"),Button.secondary("other:action","Other"))).withUniqueId(PanelView.MARKER);
        assertFalse(PanelView.recognizable(message(BOT,List.of(extra),List.of()),BOT),"A matching marker must not adopt a different component action set");
        MessageEmbed legacy=new EmbedBuilder().setDescription("Legacy account panel").setFooter("DyDiscord • DyPlugin • dd:panel:v1").build();
        assertTrue(PanelView.recognizable(message(BOT,List.of(),List.of(legacy)),BOT));
        assertFalse(PanelView.recognizable(message("223456789012345678",List.of(),List.of(legacy)),BOT));
        assertFalse(PanelView.recognizable(message(BOT,List.of(),List.of(new EmbedBuilder().setDescription("Ordinary bot reply").setFooter("DyDiscord").build())),BOT));
    }

    @Test void disablingAnOldPanelPreservesItsCardAndDisablesAllNestedButtons()throws Exception{
        try(var original=PanelView.create(card("tr"))){
            MessageComponentTree disabled=original.getComponentTree().asDisabled();
            assertEquals(3,disabled.findAll(Button.class).size());assertTrue(disabled.findAll(Button.class).stream().allMatch(Button::isDisabled));
            assertEquals(3,original.getComponentTree().findAll(Button.class).stream().filter(Button::isEnabled).count());
            assertTrue(original.getComponentTree().findAll(Button.class).stream().noneMatch(Button::isDisabled));
            assertEquals(original.getComponentTree().findAll(TextDisplay.class).stream().map(TextDisplay::getContent).toList(),disabled.findAll(TextDisplay.class).stream().map(TextDisplay::getContent).toList());
            assertEquals(AVATAR,disabled.findAll(Thumbnail.class).get(0).getUrl());assertEquals(BUTTON_IDS,disabled.findAll(Button.class).stream().map(Button::getCustomId).toList());
            try(var data=new MessageCreateBuilder().setComponents(disabled.getComponents()).useComponentsV2().build()){
                component(data.toData());assertTrue(PanelView.recognizable(message(BOT,data.getComponents(),List.of()),BOT));
            }
        }
    }

    @Test void serverNameCannotCreateMentionsOrFormattingInsideTheTitle()throws Exception{
        Container card=PanelView.card(messages(),"en","Test @everyone **name** <@123456789012345678>",AVATAR);
        try(var data=PanelView.create(card)){
            String title=data.getComponentTree().findAll(TextDisplay.class).get(0).getContent();
            assertFalse(title.contains("@everyone"));assertFalse(title.contains("<@123456789012345678>"));assertTrue(title.contains("\\*\\*name\\*\\*"));assertTrue(data.getAllowedMentions().isEmpty());
        }
    }

    @ParameterizedTest @ValueSource(strings={"en","tr"})
    void ordinaryPrivateRepliesHaveNoBrandFooter(String language)throws Exception{
        Configuration configuration=TestSupport.config(folder);configuration.change("bot.yml","language",language);
        DyDiscord plugin=mock(DyDiscord.class);Store store=mock(Store.class);when(store.panel()).thenReturn(Optional.empty());when(plugin.store()).thenReturn(store);when(plugin.clock()).thenReturn(new TestSupport.Time());when(plugin.settings()).thenReturn(configuration.active().settings());when(plugin.messages()).thenReturn(configuration.active().messages());
        Bot bot=new Bot(plugin);var embed=Bot.class.getDeclaredMethod("embed",String.class,Map.class);embed.setAccessible(true);
        MessageEmbed reply=(MessageEmbed)embed.invoke(bot,"help",Map.of());assertNull(reply.getFooter());assertEquals(0xFF55FF,reply.getColorRaw());assertNotNull(reply.getDescription());assertFalse(reply.getDescription().contains("DyPlugin"));
    }
}
