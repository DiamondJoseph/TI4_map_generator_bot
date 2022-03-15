package ti4.commands.units;

import ti4.helpers.Constants;
import ti4.map.Tile;

public class AddUnitDamage extends AddRemoveUnits {
    @Override
    protected void unitAction(Tile tile, int count, String planetName, String unitID) {
        tile.addUnitDamage(planetName, unitID, count);
    }

    @Override
    public String getActionID() {
        return Constants.ADD_UNIT_DAMAGE;
    }

    @Override
    protected String getActionDescription() {
        return "Add unit damage to map";
    }


}
