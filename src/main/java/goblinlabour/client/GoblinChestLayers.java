package goblinlabour.client;

import goblinlabour.GoblinLabour;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;

import java.util.EnumSet;
import java.util.Set;

/**
 * Model layers of the Goblin Chest: vanilla's chest boxes (same parts, same texture layout, so vanilla's
 * {@code ChestModel} opens the lid) plus a jaw. Teeth stand on the rim of the box and hang from the underside of the
 * lid, a tongue lies on the floor. Closed, all of it sits inside the other half and cannot be seen; opened, the lid
 * swings back and its teeth point forward. Texture: {@code tools/MakeChestTextures.java}.
 */
public final class GoblinChestLayers {
    public static final ModelLayerLocation SINGLE = new ModelLayerLocation(GoblinLabour.id("goblin_chest"), "main");
    public static final ModelLayerLocation LEFT = new ModelLayerLocation(GoblinLabour.id("goblin_chest"), "left");
    public static final ModelLayerLocation RIGHT = new ModelLayerLocation(GoblinLabour.id("goblin_chest"), "right");

    /** Teeth texture offsets (see MakeChestTextures) and the tongue's. */
    private static final int UPPER_TOOTH_U = 0, UPPER_FANG_U = 4, LOWER_TOOTH_U = 8, LOWER_FANG_U = 12, TEETH_V = 43;
    private static final int TONGUE_U = 0, TONGUE_V = 48;
    /** z of the front row of teeth: one pixel inside the front wall (the box ends at z 15). */
    private static final float FRONT_ROW = 13.5f;

    private GoblinChestLayers() {
    }

    public static LayerDefinition single() {
        return create(1, 15, true, true, EnumSet.allOf(Direction.class), 7, 2, 5, 6);
    }

    /** The half whose open side (the seam) faces east, like vanilla's right half. */
    public static LayerDefinition right() {
        return create(1, 16, true, false, Util.allOfEnumExcept(Direction.EAST), 15, 1, 12, 4);
    }

    /** The half whose open side (the seam) faces west. */
    public static LayerDefinition left() {
        return create(0, 15, false, true, Util.allOfEnumExcept(Direction.WEST), 0, 1, 0, 4);
    }

    private static LayerDefinition create(float minX, float maxX, boolean teethAtMinX, boolean teethAtMaxX, Set<Direction> faces,
                                          float lockX, float lockWidth, float tongueX, float tongueWidth) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float width = maxX - minX;

        CubeListBuilder bottom = CubeListBuilder.create().texOffs(0, 19).addBox(minX, 0, 1, width, 10, 14, faces);
        // lower jaw: teeth on the rim of the box (y 10 up), inside the lid while it is closed
        int i = 0;
        for (float x = minX + 1; x <= maxX - 2; x += 2, i++) {
            boolean fang = i % 3 == 1;
            bottom.texOffs(fang ? LOWER_FANG_U : LOWER_TOOTH_U, TEETH_V).addBox(x, 10, FRONT_ROW, 1, fang ? 3 : 2, 1);
        }
        for (float z = 3; z <= 11; z += 4) {
            if (teethAtMinX) bottom.texOffs(LOWER_TOOTH_U, TEETH_V).addBox(minX + 0.5f, 10, z, 1, 2, 1);
            if (teethAtMaxX) bottom.texOffs(LOWER_TOOTH_U, TEETH_V).addBox(maxX - 1.5f, 10, z, 1, 2, 1);
        }
        bottom.texOffs(TONGUE_U, TONGUE_V).addBox(tongueX, 9.5f, 5, tongueWidth, 1, 7);

        CubeListBuilder lid = CubeListBuilder.create().texOffs(0, 0).addBox(minX, 0, 0, width, 5, 14, faces);
        // upper jaw, in lid space (y 0 is the underside, z 0 the hinge): teeth between the lower ones
        i = 0;
        for (float x = minX + 2; x <= maxX - 2; x += 2, i++) {
            boolean fang = i % 3 == 0;
            lid.texOffs(fang ? UPPER_FANG_U : UPPER_TOOTH_U, TEETH_V).addBox(x, fang ? -3 : -2, FRONT_ROW - 1, 1, fang ? 3 : 2, 1);
        }
        for (float z = 2; z <= 10; z += 4) {
            if (teethAtMinX) lid.texOffs(UPPER_TOOTH_U, TEETH_V).addBox(minX + 0.5f, -2, z, 1, 2, 1);
            if (teethAtMaxX) lid.texOffs(UPPER_TOOTH_U, TEETH_V).addBox(maxX - 1.5f, -2, z, 1, 2, 1);
        }

        root.addOrReplaceChild("bottom", bottom, PartPose.ZERO);
        root.addOrReplaceChild("lid", lid, PartPose.offset(0, 9, 1));
        root.addOrReplaceChild("lock", CubeListBuilder.create().texOffs(0, 0).addBox(lockX, -2, 14, lockWidth, 4, 1, faces),
                PartPose.offset(0, 9, 1));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
