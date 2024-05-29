package ti4.commands.explore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import software.amazon.awssdk.utils.StringUtils;

import org.jetbrains.annotations.NotNull;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.tokens.AddToken;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.PlanetModel;

public abstract class ExploreSubcommandData extends SubcommandData {

    private Game game;
    private User user;
    protected final OptionData typeOption = new OptionData(OptionType.STRING, Constants.TRAIT, "Cultural, Industrial, Hazardous, or Frontier.").setAutoComplete(true);
    protected final OptionData idOption = new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Explore card id sent between (). Can include multiple comma-separated ids.");

    public String getActionID() {
        return getName();
    }

    public ExploreSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Game getActiveGame() {
        return game;
    }

    public User getUser() {
        return user;
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        user = event.getUser();
        game = GameManager.getInstance().getUserActiveGame(user.getId());
    }

    /**
     * @deprecated should use {@link ExploreModel#getRepresentationEmbed()} instead
     */
    @Deprecated
    public static String displayExplore(String cardID) {
        ExploreModel model = Mapper.getExplore(cardID);
        StringBuilder sb = new StringBuilder();
        if (model != null) {
            sb.append("(").append(cardID).append(") ").append(model.getName()).append(" - ").append(model.getText());
        } else {
            sb.append("Invalid ID ").append(cardID);
        }
        return sb.toString();
    }

    protected Tile getTile(SlashCommandInteractionEvent event, String tileID, Game game) {
        if (game.isTileDuplicated(tileID)) {
            MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
            return null;
        }
        Tile tile = game.getTile(AliasHandler.resolveTile(tileID));
        if (tile == null) {
            tile = game.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.replyToMessage(event, "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return null;
        }
        return tile;
    }

    public static void resolveExplore(GenericInteractionCreateEvent event, String cardID, Tile tile, String planetID,
        String messageText, Player player, Game game) {
        if (player == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Player could not be found");
            return;
        }
        if (game == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Game could not be found");
            return;
        }

        cardID = cardID.replace("extra1", "");
        cardID = cardID.replace("extra2", "");
        ExploreModel exploreModel = Mapper.getExplore(cardID);
        if (exploreModel == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                "ExploreModel could not be found: " + cardID);
            return;
        }

        MessageEmbed exploreEmbed = exploreModel.getRepresentationEmbed();
        MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), messageText, exploreEmbed);

        String message = null;

        if (game != null && !game.isFoWMode() && (event.getChannel() != game.getActionsChannel())) {
            if (planetID != null) {
                MessageHelper.sendMessageToChannel(game.getActionsChannel(),
                    player.getFactionEmoji() + " found a " + exploreModel.getName() + " on "
                        + Helper.getPlanetRepresentation(planetID, game));
            } else {
                MessageHelper.sendMessageToChannel(game.getActionsChannel(),
                    player.getFactionEmoji() + " found a " + exploreModel.getName());
            }
        }

        if (tile == null) {
            tile = game.getTileFromPlanet(planetID);
        }

        // Generic Resolution Handling
        switch (exploreModel.getResolution().toLowerCase()) {
            case Constants.FRAGMENT -> {
                player.addFragment(cardID);
                game.purgeExplore(cardID);
            }
            case Constants.ATTACH -> {
                String attachment = exploreModel.getAttachmentId().orElse("");
                String attachmentFilename = Mapper.getAttachmentImagePath(attachment);
                if (attachmentFilename == null || tile == null || planetID == null) {
                    message = "Invalid attachment, tile, or planet";
                } else {
                    PlanetModel planetInfo = Mapper.getPlanet(planetID);
                    if (Optional.ofNullable(planetInfo).isPresent()) {
                        if (Optional.ofNullable(planetInfo.getTechSpecialties()).orElse(new ArrayList<>()).size() > 0
                            || ButtonHelper.doesPlanetHaveAttachmentTechSkip(tile, planetID)) {
                            if ((attachment.equals(Constants.WARFARE) ||
                                attachment.equals(Constants.PROPULSION) ||
                                attachment.equals(Constants.CYBERNETIC) ||
                                attachment.equals(Constants.BIOTIC) ||
                                attachment.equals(Constants.WEAPON))) {
                                String attachmentID = Mapper.getAttachmentImagePath(attachment + "stat");
                                if (attachmentID != null) {
                                    attachmentFilename = attachmentID;
                                }
                            }
                        }
                    }

                    if (attachment.equals(Constants.DMZ)) {
                        String dmzLargeFilename = Mapper.getTokenID(Constants.DMZ_LARGE);
                        tile.addToken(dmzLargeFilename, planetID);
                        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
                        UnitHolder planetUnitHolder = unitHolders.get(planetID);
                        UnitHolder spaceUnitHolder = unitHolders.get(Constants.SPACE);
                        if (planetUnitHolder != null && spaceUnitHolder != null) {
                            Map<UnitKey, Integer> units = new HashMap<>(planetUnitHolder.getUnits());
                            for (Player player_ : game.getPlayers().values()) {
                                String color = player_.getColor();
                                planetUnitHolder.removeAllUnits(color);
                            }
                            Map<UnitKey, Integer> spaceUnits = spaceUnitHolder.getUnits();
                            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                                UnitKey key = unitEntry.getKey();
                                if (Set.of(UnitType.Fighter, UnitType.Infantry, UnitType.Mech)
                                    .contains(key.getUnitType())) {
                                    Integer count = spaceUnits.get(key);
                                    if (count == null) {
                                        count = unitEntry.getValue();
                                    } else {
                                        count += unitEntry.getValue();
                                    }
                                    spaceUnits.put(key, count);
                                }
                            }
                        }
                    }
                    tile.addToken(attachmentFilename, planetID);
                    game.purgeExplore(cardID);
                    message = "Attachment `" + attachment + "` added to planet";
                    if (player.getLeaderIDs().contains("solcommander") && !player.hasLeaderUnlocked("solcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, game, "sol", event);
                    }
                    if (player.getLeaderIDs().contains("xxchacommander")
                        && !player.hasLeaderUnlocked("xxchacommander")) {
                        ButtonHelper.commanderUnlockCheck(player, game, "xxcha", event);
                    }
                }
            }
            case Constants.TOKEN -> {
                String token = exploreModel.getAttachmentId().orElse("");
                String tokenFilename = Mapper.getTokenID(token);
                if (tokenFilename == null || tile == null) {
                    message = "Invalid token or tile";
                } else {
                    if ("ionalpha".equalsIgnoreCase(token)) {
                        message = "Use buttons to decide to place either an alpha or a beta Ion Storm";
                        List<Button> buttonIon = new ArrayList<>();
                        buttonIon.add(Button.success("addIonStorm_beta_" + tile.getPosition(), "Place a beta")
                            .withEmoji(Emoji.fromFormatted(Emojis.CreussBeta)));
                        buttonIon.add(Button.secondary("addIonStorm_alpha_" + tile.getPosition(), "Place an alpha")
                            .withEmoji(Emoji.fromFormatted(Emojis.CreussAlpha)));
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttonIon);
                    } else {
                        tile.addToken(tokenFilename, Constants.SPACE);
                        message = "Token `" + token + "` added to map";
                    }

                    if (Constants.MIRAGE.equalsIgnoreCase(token)) {
                        Helper.addMirageToTile(tile);
                        game.clearPlanetsCache();
                        message = "Mirage added to map, added to your stats, readied, and explored!";
                    }
                    game.purgeExplore(cardID);
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        message = "Card has been discarded. Resolve effects manually.";

        // Specific Explore Handling
        switch (cardID) {
            case "crf1", "crf2", "crf3", "crf4", "crf5", "crf6", "crf7", "crf8", "crf9" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getFactionEmojiOrColor() + " gained " + Emojis.CFrag);
            }
            case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getFactionEmojiOrColor() + " gained " + Emojis.HFrag);
            }
            case "irf1", "irf2", "irf3", "irf4", "irf5" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getFactionEmojiOrColor() + " gained " + Emojis.IFrag);
            }
            case "urf1", "urf2", "urf3" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getFactionEmojiOrColor() + " gained " + Emojis.UFrag);
            }
            case "ed1", "ed2" -> {
                message = "Card has been added to play area.";
                player.addRelic(Constants.ENIGMATIC_DEVICE);
                game.purgeExplore(cardID);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            }
            case "lc1", "lc2" -> {
                boolean hasSchemingAbility = player.hasAbility("scheming");
                message = hasSchemingAbility
                    ? "Drew 3 action cards (Scheming) - please discard an action card from your hand"
                    : "Drew 2 action cards";
                int count = hasSchemingAbility ? 3 : 2;
                if (player.hasAbility("autonetic_memory")) {
                    ButtonHelperAbilities.autoneticMemoryStep1(game, player, count);
                    message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
                } else {
                    for (int i = 0; i < count; i++) {
                        game.drawActionCard(player.getUserID());
                    }

                    if (game.isFoWMode()) {
                        FoWHelper.pingAllPlayersWithFullStats(game, event, player, "Drew 2 AC");
                    }
                    ACInfo.sendActionCardInfo(game, player, event);
                }

                if (hasSchemingAbility) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                        player.getRepresentation(true, true) + " use buttons to discard",
                        ACInfo.getDiscardActionCardButtons(game, player, false));
                }
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
                ButtonHelper.checkACLimit(game, event, player);
            }
            case "dv1", "dv2" -> {
                message = "Drew Secret Objective";
                game.drawSecretObjective(player.getUserID());
                if (game.isFoWMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, "Drew SO");
                }
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    message = message + ". Drew a second SO due to plausible deniability";
                }
                SOInfo.sendSecretObjectiveInfo(game, player, event);
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
            }
            case "dw" -> {
                message = "Drew Relic";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
                DrawRelic.drawRelicAndNotify(player, event, game);
            }
            case "ms1", "ms2" -> {
                message = "Replenished Commodities (" + player.getCommodities() + "->" + player.getCommoditiesTotal()
                    + "). Reminder that this is optional, and that you can instead convert your existing comms.";
                player.setCommodities(player.getCommoditiesTotal());
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
                ButtonHelper.resolveMinisterOfCommerceCheck(game, player, event);
                ButtonHelperAgents.cabalAgentInitiation(game, player);
                if (player.hasAbility("military_industrial_complex")
                    && ButtonHelperAbilities.getBuyableAxisOrders(player, game).size() > 1) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                        ButtonHelperAbilities.getBuyableAxisOrders(player, game));
                }
                if (player.getLeaderIDs().contains("mykomentoricommander")
                    && !player.hasLeaderUnlocked("mykomentoricommander")) {
                    ButtonHelper.commanderUnlockCheck(player, game, "mykomentori", event);
                }
            }
            case Constants.MIRAGE -> {
                String mirageID = Constants.MIRAGE;
                PlanetModel planetValue = Mapper.getPlanet(mirageID);
                if (Optional.ofNullable(planetValue).isEmpty()) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid planet: " + mirageID);
                    return;
                }
                new PlanetAdd().doAction(player, mirageID, game);
                new PlanetRefresh().doAction(player, mirageID, game);
                String exploreID = game.drawExplore(Constants.CULTURAL);
                if (exploreID == null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Planet cannot be explored: " + mirageID + "\n> The Cultural deck may be empty");
                    return;
                }
                if (((game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID())))
                    || game.getCurrentPhase().contains("agenda")) && player.hasAbility("scavenge")
                    && event != null) {
                    String fac = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), fac + " gained 1tg from Scavenge ("
                        + player.getTg() + "->" + (player.getTg() + 1)
                        + "). Reminder you do not legally have this tg prior to exploring, and you could potentially deploy a mech before doing it to dodge pillage.");
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                    ButtonHelperAbilities.pillageCheck(player, game);
                }

                if (((game.getActivePlayerID() != null && !("".equalsIgnoreCase(game.getActivePlayerID())))
                    || game.getCurrentPhase().contains("agenda")) && player.hasUnit("saar_mech")
                    && event != null && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
                    List<Button> saarButton = new ArrayList<>();
                    saarButton.add(Button.success("saarMechRes_" + "mirage",
                        "Pay 1tg for mech on " + Helper.getPlanetRepresentation("mirage", game)));
                    saarButton.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentation(true, true)
                            + " you can pay 1tg to place a mech here. Do not do this prior to exploring. It is an after, while exploring is a when",
                        saarButton);
                }

                if (ButtonHelper.isPlayerElected(game, player, "minister_exploration") && event != null) {
                    String fac = player.getFactionEmoji();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        fac + " gained one " + Emojis.tg + " from Minister of Exploration (" + player.getTg()
                            + "->" + (player.getTg() + 1) + "). You do have this tg prior to exploring.");
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAbilities.pillageCheck(player, game);
                    ButtonHelperAgents.resolveArtunoCheck(player, game, 1);

                }

                String exploredMessage = player.getRepresentation() + " explored " + Emojis.Cultural +
                    "Planet " + Helper.getPlanetRepresentationPlusEmoji(mirageID) + " *(tile " + tile.getPosition()
                    + ")*:";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
                resolveExplore(event, exploreID, tile, mirageID, exploredMessage, player, game);
            }
            case "fb1", "fb2", "fb3", "fb4" -> {
                message = "Resolve using the buttons";
                Button getACButton = Button.success("comm_for_AC", "Spend 1 TG/Comm For An AC")
                    .withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
                Button getCommButton = Button.primary("gain_1_comms", "Gain 1 Commodity")
                    .withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getACButton, getCommButton);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "aw1", "aw2", "aw3", "aw4" -> {
                if (player.getCommodities() > 0) {
                    message = "Resolve explore using the buttons";
                    Button convert2CommButton = Button.success("convert_2_comms", "Convert 2 Commodities Into TG")
                        .withEmoji(Emoji.fromFormatted(Emojis.Wash));
                    Button get2CommButton = Button.primary("gain_2_comms", "Gain 2 Commodities")
                        .withEmoji(Emoji.fromFormatted(Emojis.comm));
                    List<Button> buttons = List.of(convert2CommButton, get2CommButton);
                    MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message,
                        buttons);
                } else {
                    String message2 = "Gained 2 Commodities automatically due to having no comms to convert";
                    player.setCommodities(player.getCommodities() + 2);
                    if (player.hasAbility("military_industrial_complex")
                        && ButtonHelperAbilities.getBuyableAxisOrders(player, game).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(),
                            player.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                            ButtonHelperAbilities.getBuyableAxisOrders(player, game));
                    }
                    if (player.getLeaderIDs().contains("mykomentoricommander")
                        && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, game, "mykomentori", event);
                    }
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getFactionEmoji() + " " + message2);
                }
            }
            case "mo1", "mo2", "mo3" -> {
                if (tile != null && planetID != null) {
                    new AddUnits().unitParsing(event, player.getColor(), tile, "inf " + planetID, game);
                }
                message = player.getFactionEmoji() + Emojis.getColorEmojiWithName(player.getColor()) + Emojis.infantry
                    + " automatically added to " + Helper.getPlanetRepresentationPlusEmoji(planetID)
                    + ", however this placement *is* optional.";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
            }
            case "darkvisions" -> {
                List<Button> discardButtons = new ArrayList<>();
                String type = "industrial";
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, type);
                discardButtons.add(
                    Button.success("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                type = "hazardous";
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, type);
                discardButtons
                    .add(Button.danger("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                type = "cultural";
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, type);
                discardButtons.add(
                    Button.primary("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                type = "frontier";
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, type);
                discardButtons.add(
                    Button.secondary("discardExploreTop_" + type, "Discard Top " + StringUtils.capitalize(type)));
                discardButtons.add(Button.danger("deleteButtons", "Done Resolving"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentation()
                        + " you can use the buttons to discard the top of the explore decks if you choose",
                    discardButtons);
                List<Button> buttonsAll = new ArrayList<>();
                for (String planet : player.getPlanetsAllianceMode()) {
                    UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
                    if (unitHolder == null) {
                        continue;
                    }
                    Planet planetReal = (Planet) unitHolder;
                    List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, planetReal, player);
                    if (buttons != null && !buttons.isEmpty()) {
                        buttonsAll.addAll(buttons);
                    }
                }
                String msg = "Click button to explore a planet after resolving any discards";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    msg, buttonsAll);

                MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(),
                    "Use this button to shuffle explore decks once youre done with the rest",
                    Button.danger("shuffleExplores", "Shuffle Explore Decks"));

            }
            case "lf1", "lf2", "lf3", "lf4" -> {
                message = "Resolve using the buttons";
                // TODO: Button resolves using planet ID at end of label - add planetID to buttonId and use that instead
                Button getMechButton = Button.success("comm_for_mech", "Spend 1 TG/Comm For A Mech On " + planetID).withEmoji(Emoji.fromFormatted(Emojis.mech));
                Button getCommButton3 = Button.primary("gain_1_comms", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
                List<Button> buttons = List.of(getMechButton, getCommButton3);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "kel1", "kel2", "ent", "minent", "majent" -> {
                switch (cardID.toLowerCase()) {
                    case "minent" -> {
                        player.setTg(player.getTg() + 1);
                        message = "Gained 1" + Emojis.getTGorNomadCoinEmoji(game) + " (" + (player.getTg() - 1)
                            + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, game);
                        ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                    }
                    case "ent" -> {
                        player.setTg(player.getTg() + 2);
                        message = "Gained 2" + Emojis.getTGorNomadCoinEmoji(game) + " (" + (player.getTg() - 2)
                            + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, game);
                        ButtonHelperAgents.resolveArtunoCheck(player, game, 2);
                    }
                    case "majent" -> {
                        player.setTg(player.getTg() + 3);
                        message = "Gained 3" + Emojis.getTGorNomadCoinEmoji(game) + " (" + (player.getTg() - 3)
                            + " -> **" + player.getTg() + "**) ";
                        ButtonHelperAbilities.pillageCheck(player, game);
                        ButtonHelperAgents.resolveArtunoCheck(player, game, 3);
                    }
                    default -> message = "";
                }
                if (player.getLeaderIDs().contains("hacancommander") && !player.hasLeaderUnlocked("hacancommander")) {
                    ButtonHelper.commanderUnlockCheck(player, game, "hacan", event);
                }
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String trueIdentity = player.getRepresentation(true, true);
                message += "\n" + trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                    + ". Use buttons to gain CCs";
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "exp1", "exp2", "exp3" -> {
                message = "Resolve explore using the buttons.";
                Button ReadyPlanet = Button.success("planet_ready", "Remove Inf Or Have Mech To Ready " + planetID);
                Button Decline = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(ReadyPlanet, Decline);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "frln1", "frln2", "frln3" -> {
                message = "Resolve explore using the buttons.";
                Button gainTG = Button.success("freelancersBuild_" + planetID, "Build 1 Unit");
                Button Decline2 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainTG, Decline2);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "cm1", "cm2", "cm3" -> {
                message = "Resolve explore using the buttons.";
                Button gainTG = Button.success("gain_1_tg", "Gain 1tg By Removing 1 Inf Or Having Mech On " + planetID)
                    .withEmoji(Emoji.fromFormatted(Emojis.tg));
                Button Decline2 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainTG, Decline2);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "vfs1", "vfs2", "vfs3" -> {
                message = "Resolve explore using the buttons.";
                Button gainCC = Button.success("gain_CC", "Gain 1CC By Removing 1 Inf Or Having Mech On " + planetID);
                Button Decline3 = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(gainCC, Decline3);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "warforgeruins" -> {
                message = "Resolve explore using the buttons.";
                Button ruinsInf = Button.success("ruins_" + planetID + "_2inf",
                    "Remove Inf Or Have Mech To Place 2 Infantry on " + Mapper.getPlanet(planetID).getName());
                Button ruinsMech = Button.success("ruins_" + planetID + "_mech",
                    "Remove Inf Or Have Mech To Place Mech on " + Mapper.getPlanet(planetID).getName());
                Button Decline = Button.danger("decline_explore", "Decline Explore");
                List<Button> buttons = List.of(ruinsInf, ruinsMech, Decline);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "seedyspaceport" -> {
                List<Button> buttons = new ArrayList<>();
                message = "Resolve explore using the buttons.";
                for (Leader leader : player.getLeaders()) {
                    if (leader.isExhausted() && leader.getId().contains("agent")) {
                        buttons.add(Button.success("seedySpace_" + leader.getId() + "_" + planetID,
                            "Remove Inf Or Have Mech To Refresh " + Mapper.getLeader(leader.getId()).getName()));
                    }
                }
                buttons.add(Button.primary("seedySpace_AC_" + planetID, "Remove Inf Or Have Mech Draw AC "));
                buttons.add(Button.danger("decline_explore", "Decline Explore"));

                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
            }
            case "hiddenlaboratory" -> {
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                    "# Exploring frontier in this system due to finding the hidden laboratory industrial explore.");
                AddToken.addToken(event, tile, Constants.FRONTIER, game);
                new ExpFrontier().expFront(event, tile, game, player);
            }
            case "ancientshipyard" -> {
                List<String> colors = tile.getUnitHolders().get("space").getUnitColorsOnHolder();
                if (colors.isEmpty() || colors.contains(player.getColorID())) {
                    new AddUnits().unitParsing(event, player.getColor(), tile, "cruiser", game);
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                        "Cruiser added to the system automatically.");
                } else {
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                        "Someone else's ships were in the system, no cruiser added");
                }

            }
            case "forgottentradestation" -> {
                int tgGain = tile.getUnitHolders().size() - 1;
                int oldTg = player.getTg();
                player.setTg(oldTg + tgGain);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    ButtonHelper.getIdentOrColor(player, game) + " gained " + tgGain
                        + "tg due to the forgotten trade station (" + oldTg + "->" + player.getTg() + ")");
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, game, tgGain);
            }
            case "starchartcultural", "starchartindustrial", "starcharthazardous", "starchartfrontier" -> {
                game.purgeExplore(cardID);
                player.addRelic(cardID);
                message = "Card has been added to play area.\nAdded as a relic (not actually a relic)";
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), message);
            }
        }

        if (player.hasAbility("fortune_seekers") && game.getStoredValue("fortuneSeekers").isEmpty()) {
            List<Button> gainComm = new ArrayList<>();
            gainComm.add(Button.success("gain_1_comms", "Gain 1 Comm").withEmoji(Emoji.fromFormatted(Emojis.comm)));
            gainComm.add(Button.danger("deleteButtons", "Decline"));
            StringBuilder sb = new StringBuilder();
            sb.append(player.getFactionEmoji()).append(" can use their **Fortune Seekers** ability\n");
            sb.append(player.getRepresentation(true, true)).append(
                " After resolving the explore, you can use this button to get your commodity from your fortune seekers ability");
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), sb.toString(), gainComm);
            game.setStoredValue("fortuneSeekers", "Used");
        }

        if (player.getLeaderIDs().contains("kollecccommander") && !player.hasLeaderUnlocked("kollecccommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "kollecc", event);
        }
        if (player.getPlanets().contains(planetID)) {
            ButtonHelperAbilities.offerOrladinPlunderButtons(player, game, planetID);
        }
        if (player.getLeaderIDs().contains("bentorcommander") && !player.hasLeaderUnlocked("bentorcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "bentor", event);
        }

        if (player.hasAbility("awaken") && !game.getAllPlanetsWithSleeperTokens().contains(planetID)
            && player.getPlanets().contains(planetID)) {
            Button placeSleeper = Button.success("putSleeperOnPlanet_" + planetID, "Put Sleeper on " + planetID)
                .withEmoji(Emoji.fromFormatted(Emojis.Sleeper));
            Button decline = Button.danger("deleteButtons", "Decline To Put a Sleeper Down");
            List<Button> buttons = List.of(placeSleeper, decline);
            MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message, buttons);
        }
    }
}
