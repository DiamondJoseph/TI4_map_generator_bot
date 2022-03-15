package ti4.commands.units;

import ti4.helpers.Constants;
import ti4.map.Tile;

public class RemoveUnitDamage extends AddRemoveUnits {
    @Override
    protected void unitAction(Tile tile, int count, String planetName, String unitID) {
        tile.removeUnitDamage(planetName, unitID, count);
    }

    @Override
    public String getActionID() {
        return Constants.REMOVE_UNIT_DAMAGE;
    }

    @Override
    protected String getActionDescription() {
        return "Remove unit damage from map";
    }
}
