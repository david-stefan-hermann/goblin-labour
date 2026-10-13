package goblinlabour.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import goblinlabour.GoblinLabour;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla lets a sneaking player hang still on anything climbable, except inside {@code minecraft:scaffolding}, where
 * sneaking lowers the player. This makes goblin scaffold count as scaffolding for that check. Common, not client-only:
 * the client predicts the movement and the server checks it.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @ModifyExpressionValue(method = "handleOnClimbable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean goblinlabour$sinkThroughGoblinScaffold(boolean inScaffolding) {
        return inScaffolding || ((LivingEntity) (Object) this).getInBlockState().is(GoblinLabour.GOBLIN_SCAFFOLD);
    }
}
