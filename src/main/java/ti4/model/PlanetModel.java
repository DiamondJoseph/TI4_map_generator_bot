package ti4.model;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.sticker.Sticker;
import ti4.AsyncTI4DiscordBot;
import ti4.generator.TileHelper;
import ti4.generator.UnitTokenPosition;
import ti4.helpers.Emojis;
import ti4.helpers.Stickers;
import ti4.model.PlanetTypeModel.PlanetType;
import ti4.model.Source.ComponentSource;
import ti4.model.TechSpecialtyModel.TechSpecialty;

@Data
public class PlanetModel implements ModelInterface, EmbeddableModel {
    private String id;
    private String tileId;
    private String name;
    private String shortName;
    private List<String> aliases;
    private Point positionInTile;
    private int resources;
    private int influence;
    private String factionHomeworld;
    private PlanetTypeModel.PlanetType planetType;
    private List<PlanetTypeModel.PlanetType> planetTypes;
    private String cardImagePath;
    private List<TechSpecialtyModel.TechSpecialty> techSpecialties;
    private String basicAbilityText;
    private String flavourText;
    private UnitTokenPosition unitPositions;
    private int spaceCannonDieCount;
    private int spaceCannonHitsOn;
    private List<String> searchTags = new ArrayList<>();
    private String contrastColor;
    private ComponentSource source;

    @JsonIgnore
    public boolean isValid() {
        return id != null
            && name != null
            && source != null
            && aliases != null
            && aliases.contains(name.toLowerCase().replace(" ", "")); // Alias list must contain the lowercase'd name
    }

    @JsonIgnore
    public String getAlias() {
        return getId();
    }

    @Deprecated
    public PlanetTypeModel.PlanetType getPlanetType() {
        if (planetType != null) {
            return planetType;
        }
        if (!getPlanetTypes().isEmpty()) {
            return getPlanetTypes().getFirst();
        }
        return PlanetTypeModel.PlanetType.NONE;
    }

    public List<PlanetTypeModel.PlanetType> getPlanetTypes() {
        List<PlanetType> types = new ArrayList<>();
        if (planetTypes != null) types.addAll(planetTypes);
        if (planetType != null) types.add(planetType);
        return types;
    }

    @JsonIgnore
    public String getNameNullSafe() {
        return Optional.ofNullable(name).orElse("");
    }

    @JsonIgnore
    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeAliases) {
        return getRepresentationBuilder(includeAliases).build();
    }

    protected EmbedBuilder getRepresentationBuilder(boolean includeAliases) {
                EmbedBuilder eb = new EmbedBuilder();

        StringBuilder sb = new StringBuilder();
        sb.append(getEmoji()).append("__").append(getName()).append("__");
        eb.setTitle(sb.toString());

        if (getPlanetType() != null) {
            switch (getPlanetType()) {
                case HAZARDOUS -> eb.setColor(Color.red);
                case INDUSTRIAL -> eb.setColor(Color.green);
                case CULTURAL -> eb.setColor(Color.blue);
                default -> eb.setColor(Color.white);
            }
        }

        TileModel tile = TileHelper.getTileById(getTileId());
        sb = new StringBuilder();
        sb.append(getInfResEmojis()).append(getPlanetTypeEmoji()).append(getTechSpecialtyEmoji());
        if (tile != null) sb.append("\nSystem: ").append(tile.getName());
        eb.setDescription(sb.toString());
        if (getBasicAbilityText() != null) eb.addField("Ability:", getBasicAbilityText(), false);
        if (getFlavourText() != null) eb.addField("", getFlavourText(), false);

        sb = new StringBuilder();
        sb.append("ID: ").append(getId());
        if (includeAliases) sb.append("\nAliases: ").append(getAliases());
        eb.setFooter(sb.toString());

        if (getStickerOrEmojiURL() != null) eb.setThumbnail(getStickerOrEmojiURL());

        return eb;
    }

    @JsonIgnore
    private String getInfResEmojis() {
        return Emojis.getResourceEmoji(resources) + Emojis.getInfluenceEmoji(influence);
    }

    @JsonIgnore
    private String getPlanetTypeEmoji() {
        return switch (getPlanetType()) {
            case HAZARDOUS -> Emojis.Hazardous;
            case INDUSTRIAL -> Emojis.Industrial;
            case CULTURAL -> Emojis.Cultural;
            default -> "";
        };
    }

    @JsonIgnore
    private String getTechSpecialtyEmoji() {
        if (getTechSpecialties() == null || getTechSpecialties().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (TechSpecialty techSpecialty : getTechSpecialties()) {
            switch (techSpecialty) {
                case BIOTIC -> sb.append(Emojis.BioticTech);
                case CYBERNETIC -> sb.append(Emojis.CyberneticTech);
                case PROPULSION -> sb.append(Emojis.PropulsionTech);
                case WARFARE -> sb.append(Emojis.WarfareTech);
                case UNITSKIP -> sb.append(Emojis.UnitTechSkip);
                case NONUNITSKIP -> sb.append(Emojis.NonUnitTechSkip);
            }
        }
        return sb.toString();
    }

    @JsonIgnore
    private String getTechSpecialtyStringRepresentation() {
        if (getTechSpecialties() == null || getTechSpecialties().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (TechSpecialty techSpecialty : getTechSpecialties()) {
            switch (techSpecialty) {
                case BIOTIC -> sb.append("G");
                case CYBERNETIC -> sb.append("Y");
                case PROPULSION -> sb.append("B");
                case WARFARE -> sb.append("R");
                case UNITSKIP -> sb.append("U");
                case NONUNITSKIP -> sb.append("?");
            }
        }
        return sb.toString();
    }

    @JsonIgnore
    public String getEmoji() {
        return Emojis.getPlanetEmoji(getId());
    }

    @JsonIgnore
    public String getEmojiURL() {
        Emoji emoji = Emoji.fromFormatted(getEmoji());
        if (getEmoji().equals(Emojis.SemLore) && !getId().equals("semlore")) {
            return null;
        }
        if (emoji instanceof CustomEmoji customEmoji) {
            return customEmoji.getImageUrl();
        }
        return null;
    }

    @JsonIgnore
    public String getStickerOrEmojiURL() {
        long ident = Stickers.getPlanetSticker(getId());
        if (ident == -1) {
            return getEmojiURL();
        }
        return AsyncTI4DiscordBot.jda.retrieveSticker(Sticker.fromId(ident)).complete().getIconUrl();
    }

    public boolean search(String searchString) {
        return getName().toLowerCase().contains(searchString)
            || getId().toLowerCase().contains(searchString)
            || getSource().toString().contains(searchString)
            || getSearchTags().contains(searchString);
    }

    @JsonIgnore
    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(" (").append(getResources()).append("/").append(getInfluence());
        if (!getTechSpecialtyStringRepresentation().isBlank()) sb.append(" ").append(getTechSpecialtyStringRepresentation());
        sb.append(")");
        return sb.toString();
    }
}
