package goblinlabour.entity;

import goblinlabour.entity.ai.CrewGoal;
import goblinlabour.ring.RingCrew;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla walking, plus one rule for ring crews: ground in the player's way (see {@link CrewGoal#inWay}) costs extra,
 * so paths go round the player and behind its back instead of across the lane it looks or walks along. Not blocked:
 * a goblin that stands in the way still finds a path out.
 */
public class GoblinNodeEvaluator extends WalkNodeEvaluator {
    /** The path type marking ground in the player's way; its malus is set on crew goblins only. */
    public static final PathType IN_WAY = PathType.DAMAGE_CAUTIOUS;
    public static final float IN_WAY_MALUS = 12.0f;

    @Override
    public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        PathType type = super.getPathTypeOfMob(context, x, y, z, mob);
        if (type != PathType.WALKABLE || !(mob instanceof GoblinEntity goblin)) return type;
        RingCrew.Session crew = goblin.crew();
        if (crew == null) return type;
        return CrewGoal.inWay(crew.owner(), new Vec3(x + 0.5, y, z + 0.5), goblin.crewAvoidsLook()) ? IN_WAY : type;
    }
}
