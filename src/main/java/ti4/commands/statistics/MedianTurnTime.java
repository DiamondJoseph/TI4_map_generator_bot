package ti4.commands.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class MedianTurnTime extends StatisticsSubcommandData {

    public MedianTurnTime() {
        super(Constants.MEDIAN_TURN_TIME, "Median turn time accross all games for all players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.TOP_LIMIT, "How many players to show (Default = 50)").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IGNORE_ENDED_GAMES, "True to exclude ended games from the calculation (default = false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String text = getAverageTurnTimeText(event);
        MessageHelper.sendMessageToThread(event.getChannel(), "Average Turn Time", text);
    }

    private String getAverageTurnTimeText(SlashCommandInteractionEvent event) {
        HashMap<String, Map> maps = MapManager.getInstance().getMapList();

        HashMap<String, Set<Long>> playerAverageTurnTimes = new HashMap<>();

        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        Predicate<Map> endedGamesFilter = ignoreEndedGames ? m -> !m.isHasEnded() : m -> true;

        for (Map map : maps.values().stream().filter(endedGamesFilter).toList()) {
            for (Player player : map.getPlayers().values()) {
                Entry<Integer, Long> playerTurnTime = java.util.Map.entry(player.getNumberTurns(), player.getTotalTurnTime());
                if (playerTurnTime.getKey() == 0) continue;
                Long averageTurnTime = playerTurnTime.getValue() / playerTurnTime.getKey();
                playerAverageTurnTimes.compute(player.getUserID(), (key, value) -> {
                    if (value == null) value = new java.util.HashSet<Long>();
                    value.add(averageTurnTime);
                    return value;
                });
            }
        }

        HashMap<String, Long> playerMedianTurnTimes = playerAverageTurnTimes.entrySet().stream().map(e -> java.util.Map.entry(e.getKey(), Helper.median(e.getValue().stream().sorted().toList()))).collect(Collectors.toMap(Entry::getKey, Entry::getValue, (oldEntry, newEntry) -> oldEntry, HashMap::new));
        StringBuilder sb = new StringBuilder();

        sb.append("## __**Median Turn Time:**__\n");
        
        int index = 1;
        Comparator<Entry<String, Long>> comparator = (o1, o2) -> {
            return o1.getValue().compareTo(o2.getValue());
        };

        int topLimit = event.getOption(Constants.TOP_LIMIT, 50, OptionMapping::getAsInt);
        for (Entry<String, Long> userMedianTurnTime : playerMedianTurnTimes.entrySet().stream().filter(o -> o.getValue() != 0).sorted(comparator).limit(topLimit).collect(Collectors.toList())) {
            User user = MapGenerator.jda.getUserById(userMedianTurnTime.getKey());
            long totalMillis = userMedianTurnTime.getValue();

            if (user == null || totalMillis == 0) continue;
            
            sb.append("`").append(Helper.leftpad(String.valueOf(index), 3)).append(". ");
            sb.append(Helper.getTimeRepresentationToSeconds(userMedianTurnTime.getValue()));
            sb.append("` ").append(user.getEffectiveName());
            sb.append("\n");
            index++;     
        }

        return sb.toString();
    }
}
