package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SendTG extends PlayerSubcommandData {
	public SendTG() {
		super(Constants.SEND_TG, "Sent TG to player/faction");
		addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Trade goods count").setRequired(true));
		addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which you send TG")
				.setAutoComplete(true).setRequired(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {

		Map activeMap = getActiveMap();
		Player player = activeMap.getPlayer(getUser().getId());
		player = Helper.getGamePlayer(activeMap, player, event, null);
		if (player == null) {
			sendMessage("Player could not be found");
			return;
		}
		Player player_ = Helper.getPlayer(activeMap, player, event);
		if (player_ == null) {
			sendMessage("Player to send TG/Commodities could not be found");
			return;
		}

		OptionMapping optionTG = event.getOption(Constants.TG);
		if (optionTG != null) {
			int sendTG = optionTG.getAsInt();
			int tg = player.getTg();
			sendTG = Math.min(sendTG, tg);
			tg -= sendTG;
			player.setTg(tg);

			int targetTG = player_.getTg();
			targetTG += sendTG;
			player_.setTg(targetTG);

			String message = Helper.getPlayerRepresentation(event, player) + " sent " + sendTG + Emojis.tg
					+ " trade goods to " + Helper.getPlayerRepresentation(event, player_);
			sendMessage(message);
			if (activeMap.isFoWMode()) {
				String fail = "Could not notify recieving player.";
				String success = "The other player has been notified";
				MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, event.getChannel(), message, fail,
						success);

				// Add extra message for transaction visibility
				for (Player viewPlayer : activeMap.getPlayers().values()) {
					boolean senderVisible = FoWHelper.canSeeStatsOfPlayer(activeMap, player, viewPlayer);
					boolean recieverVisible = FoWHelper.canSeeStatsOfPlayer(activeMap, player_, viewPlayer);
					if (senderVisible && recieverVisible) {
						MessageHelper.sendPrivateMessageToPlayer(viewPlayer, activeMap, message);
					} else if (senderVisible) {
						String tempMessage = Helper.getPlayerRepresentation(event, player) + " sent " + sendTG + Emojis.tg
								+ " trade goods to " + "???";
						MessageHelper.sendPrivateMessageToPlayer(viewPlayer, activeMap, tempMessage);
					} else if (recieverVisible) {
						String tempMessage = "???" + " sent " + sendTG + Emojis.tg
								+ " trade goods to " + Helper.getPlayerRepresentation(event, player_);
						MessageHelper.sendPrivateMessageToPlayer(viewPlayer, activeMap, tempMessage);
					}

				}
			}
		}
	}
}
