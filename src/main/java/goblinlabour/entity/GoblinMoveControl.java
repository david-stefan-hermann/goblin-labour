package goblinlabour.entity;

import goblinlabour.GoblinLabour;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Vanilla's move control, except for a goblin sneaking down through a scaffold column. The column block at its feet has
 * a collision shape, so vanilla jumps there and switches to JUMPING, which keeps walking on the old heading and ignores
 * new wanted positions until the mob lands. In a column it lands at the foot: the goblin walked out of its column on
 * the way down and fell. While sneaking in scaffold the goblin only steers towards the wanted position; the climber
 * decides about jumping.
 */
public class GoblinMoveControl extends MoveControl<GoblinEntity> {
    public GoblinMoveControl(GoblinEntity goblin) {
        super(goblin);
    }

    private boolean sneakingInScaffold() {
        return mob.isShiftKeyDown() && mob.level().getBlockState(mob.blockPosition()).is(GoblinLabour.GOBLIN_SCAFFOLD);
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speed) {
        super.setWantedPosition(x, y, z, speed);
        if (sneakingInScaffold()) operation = Operation.MOVE_TO;
    }

    @Override
    public void tick() {
        if ((operation != Operation.MOVE_TO && operation != Operation.JUMPING) || !sneakingInScaffold()) {
            super.tick();
            return;
        }
        boolean steer = operation == Operation.MOVE_TO;
        operation = Operation.WAIT;
        double dx = wantedX - mob.getX(), dy = wantedY - mob.getY(), dz = wantedZ - mob.getZ();
        if (!steer || dx * dx + dy * dy + dz * dz < MIN_SPEED_SQR) {
            mob.setZza(0.0f);
            return;
        }
        float yaw = (float) Mth.atan2(dz, dx) * Mth.RAD_TO_DEG - 90.0f;
        mob.setYRot(rotlerp(mob.getYRot(), yaw, MAX_TURN));
        mob.setSpeed((float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
    }
}
