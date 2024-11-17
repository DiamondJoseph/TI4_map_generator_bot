package ti4.model;

import java.awt.Color;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.Emojis;

public class LegendaryPlanetModel extends PlanetModel {

    private String legendaryAbilityName;
    private String legendaryAbilityText;
    private String legendaryAbilityFlavourText;

    @JsonIgnore
    public MessageEmbed getLegendaryEmbed() {
        EmbedBuilder eb = new EmbedBuilder();

        eb.setTitle(String.format("{}__{}__", Emojis.LegendaryPlanet, legendaryAbilityFlavourText));
        eb.setColor(Color.black);

        eb.setDescription(legendaryAbilityFlavourText);
        if (getStickerOrEmojiURL() != null) eb.setThumbnail(getStickerOrEmojiURL());
        return eb.build();
    }


    protected EmbedBuilder getRepresentationBuilder(boolean includeAliases) {
        var eb = super.getRepresentationBuilder(includeAliases);
        if (!StringUtils.isBlank(legendaryAbilityName)) {
            eb.addField(Emojis.LegendaryPlanet + legendaryAbilityName, legendaryAbilityText, false);
        }
        if (!StringUtils.isBlank(legendaryAbilityFlavourText)) {
            eb.addField("", legendaryAbilityFlavourText, false);
        }
        return eb;
    }

}
