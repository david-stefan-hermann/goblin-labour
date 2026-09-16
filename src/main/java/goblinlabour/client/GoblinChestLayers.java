package goblinlabour.client;

import goblinlabour.GoblinLabour;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Model layers of the Goblin Chest, generated from {@code art/goblin_chest_single.bbmodel} and
 * {@code art/goblin_chest_double.bbmodel} by {@code tools/make_chest_model.py} - do not edit by hand, edit the
 * Blockbench models and run the script again.
 *
 * <p>The parts are named {@code bottom}, {@code lid} and {@code lock} like vanilla's, so vanilla's
 * {@code ChestModel} opens the lid. Each of them holds a single {@code body} child that carries a half turn around
 * z: Blockbench's Modded Entity space is the model space turned that way, and its box UV follows the turn, so the
 * cubes go in turned (x and y negated) and the {@code body} stands them upright again while the UV strips stay where
 * Blockbench shows them. Cube rotations are conjugated by the same turn, hence the flipped x and y angles.
 *
 * <p>{@link #DOUBLE} is the whole double chest, drawn by the left half alone; see {@link GoblinChestRenderer}.
 */
public final class GoblinChestLayers {
    public static final ModelLayerLocation SINGLE = new ModelLayerLocation(GoblinLabour.id("goblin_chest"), "single");
    public static final ModelLayerLocation DOUBLE = new ModelLayerLocation(GoblinLabour.id("goblin_chest"), "double");

    /** Half turn around z, see the class comment. */
    private static final float TURN = (float) Math.PI;

    private GoblinChestLayers() {
    }

    private static float deg(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    public static LayerDefinition single() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        singleBottom(root);
        singleLid(root);
        singleLock(root);
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void singleBottom(PartDefinition root) {
        CubeListBuilder body = CubeListBuilder.create()
                .texOffs(40, 0).addBox(-8.0F, -1.0F, -7.0F, 16.0F, 1.0F, 14.0F) // iron_plinth
                .texOffs(0, 39).addBox(-7.0F, -9.0F, 4.0F, 14.0F, 8.0F, 2.0F) // steel_wall_front
                .texOffs(60, 59).addBox(-7.0F, -9.0F, -6.0F, 14.0F, 8.0F, 1.0F) // steel_wall_back
                .texOffs(0, 0).addBox(5.0F, -9.0F, -4.0F, 2.0F, 8.0F, 8.0F) // steel_wall_left
                .texOffs(20, 0).addBox(-7.0F, -9.0F, -4.0F, 2.0F, 8.0F, 8.0F) // steel_wall_right
                .texOffs(68, 28).addBox(-5.0F, -2.0F, -5.0F, 10.0F, 1.0F, 9.0F) // mouth_floor
                .texOffs(0, 85).addBox(-8.0F, -10.0F, 4.0F, 16.0F, 1.0F, 3.0F) // iron_rim_front
                .texOffs(48, 93).addBox(-8.0F, -10.0F, -7.0F, 16.0F, 1.0F, 2.0F) // iron_rim_back
                .texOffs(84, 39).addBox(5.0F, -10.0F, -5.0F, 3.0F, 1.0F, 9.0F) // iron_rim_left
                .texOffs(0, 49).addBox(-8.0F, -10.0F, -5.0F, 3.0F, 1.0F, 9.0F) // iron_rim_right
                .texOffs(40, 59).addBox(5.0F, -9.0F, -7.0F, 3.0F, 8.0F, 2.0F) // iron_post_-1_-1
                .texOffs(20, 69).addBox(5.0F, -9.0F, -5.0F, 3.0F, 8.0F, 1.0F) // iron_postin_-1
                .texOffs(104, 97).addBox(5.0F, -6.0F, -8.0F, 2.0F, 2.0F, 1.0F) // stud_front_-1_-1
                .texOffs(104, 89).addBox(8.0F, -6.0F, -6.0F, 1.0F, 2.0F, 2.0F) // stud_end_-1_-1
                .texOffs(0, 28).addBox(5.0F, -9.0F, 4.0F, 3.0F, 8.0F, 3.0F) // iron_post_-1_1
                .texOffs(110, 97).addBox(5.0F, -6.0F, 7.0F, 2.0F, 2.0F, 1.0F) // stud_front_-1_1
                .texOffs(110, 89).addBox(8.0F, -6.0F, 4.0F, 1.0F, 2.0F, 2.0F) // stud_end_-1_1
                .texOffs(50, 59).addBox(-8.0F, -9.0F, -7.0F, 3.0F, 8.0F, 2.0F) // iron_post_1_-1
                .texOffs(28, 69).addBox(-8.0F, -9.0F, -5.0F, 3.0F, 8.0F, 1.0F) // iron_postin_1
                .texOffs(116, 97).addBox(-7.0F, -6.0F, -8.0F, 2.0F, 2.0F, 1.0F) // stud_front_1_-1
                .texOffs(116, 89).addBox(-9.0F, -6.0F, -6.0F, 1.0F, 2.0F, 2.0F) // stud_end_1_-1
                .texOffs(12, 28).addBox(-8.0F, -9.0F, 4.0F, 3.0F, 8.0F, 3.0F) // iron_post_1_1
                .texOffs(122, 97).addBox(-7.0F, -6.0F, 7.0F, 2.0F, 2.0F, 1.0F) // stud_front_1_1
                .texOffs(122, 89).addBox(-9.0F, -6.0F, 4.0F, 1.0F, 2.0F, 2.0F) // stud_end_1_1
                .texOffs(0, 100).addBox(-1.0F, -7.0F, 6.5F, 2.0F, 2.0F, 1.0F) // iron_drop_stem
                .texOffs(76, 85).addBox(5.0F, -11.5F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lf0
                .texOffs(120, 103).addBox(5.5F, -12.5F, 4.5F, 1.0F, 1.0F, 1.0F) // bonelt_tooth_lf0_tip
                .texOffs(84, 85).addBox(1.0F, -11.5F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lf1
                .texOffs(36, 93).addBox(1.5F, -14.0F, 4.5F, 1.0F, 2.5F, 1.0F) // bonelt_tooth_lf1_tip
                .texOffs(92, 85).addBox(-3.0F, -11.5F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lf2
                .texOffs(40, 93).addBox(-2.5F, -14.0F, 4.5F, 1.0F, 2.5F, 1.0F) // bonelt_tooth_lf2_tip
                .texOffs(100, 85).addBox(-7.0F, -11.5F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lf3
                .texOffs(124, 103).addBox(-6.5F, -12.5F, 4.5F, 1.0F, 1.0F, 1.0F) // bonelt_tooth_lf3_tip
                .texOffs(108, 85).addBox(5.0F, -11.5F, -1.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_ll
                .texOffs(0, 105).addBox(5.5F, -12.5F, -0.5F, 1.0F, 1.0F, 1.0F) // bonelt_tooth_ll_tip
                .texOffs(116, 85).addBox(-7.0F, -11.5F, -1.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lr
                .texOffs(4, 105).addBox(-6.5F, -12.5F, -0.5F, 1.0F, 1.0F, 1.0F); // bonelt_tooth_lr_tip
        PartDefinition part = root.addOrReplaceChild("bottom", CubeListBuilder.create(),
                PartPose.offset(8.0F, 0.0F, 8.0F));
        PartDefinition turned = part.addOrReplaceChild("body", body,
                PartPose.rotation(0.0F, 0.0F, TURN));
        CubeListBuilder turn0 = CubeListBuilder.create()
                .texOffs(0, 97).addBox(-6.0F, -0.707F, -1.0F, 12.0F, 1.414F, 1.0F); // steel_bev_plinth_front
        turned.addOrReplaceChild("turn0", turn0, PartPose.offsetAndRotation(
                0.0F, -1.5F, 6.5F, deg(45), 0.0F, 0.0F));
        CubeListBuilder turn1 = CubeListBuilder.create()
                .texOffs(26, 97).addBox(-6.0F, -0.707F, 0.0F, 12.0F, 1.414F, 1.0F); // steel_bev_plinth_back
        turned.addOrReplaceChild("turn1", turn1, PartPose.offsetAndRotation(
                0.0F, -1.5F, -6.5F, deg(-45), 0.0F, 0.0F));
        CubeListBuilder turn2 = CubeListBuilder.create()
                .texOffs(52, 97).addBox(-6.0F, -0.707F, -1.0F, 12.0F, 1.414F, 1.0F); // steel_bev_rim_front
        turned.addOrReplaceChild("turn2", turn2, PartPose.offsetAndRotation(
                0.0F, -8.5F, 6.5F, deg(-45), 0.0F, 0.0F));
        CubeListBuilder turn3 = CubeListBuilder.create()
                .texOffs(78, 97).addBox(-6.0F, -0.707F, 0.0F, 12.0F, 1.414F, 1.0F); // steel_bev_rim_back
        turned.addOrReplaceChild("turn3", turn3, PartPose.offsetAndRotation(
                0.0F, -8.5F, -6.5F, deg(45), 0.0F, 0.0F));
        CubeListBuilder turn4 = CubeListBuilder.create()
                .texOffs(100, 0).addBox(0.0F, -0.707F, -5.0F, 1.0F, 1.414F, 10.0F); // steel_bev_plinth_end_r
        turned.addOrReplaceChild("turn4", turn4, PartPose.offsetAndRotation(
                -7.5F, -1.5F, 0.0F, 0.0F, 0.0F, deg(45)));
        CubeListBuilder turn5 = CubeListBuilder.create()
                .texOffs(0, 16).addBox(-1.0F, -0.707F, -5.0F, 1.0F, 1.414F, 10.0F); // steel_bev_plinth_end_l
        turned.addOrReplaceChild("turn5", turn5, PartPose.offsetAndRotation(
                7.5F, -1.5F, 0.0F, 0.0F, 0.0F, deg(-45)));
        CubeListBuilder turn6 = CubeListBuilder.create()
                .texOffs(22, 16).addBox(0.0F, -0.707F, -5.0F, 1.0F, 1.414F, 10.0F); // steel_bev_rim_end_r
        turned.addOrReplaceChild("turn6", turn6, PartPose.offsetAndRotation(
                -7.5F, -8.5F, 0.0F, 0.0F, 0.0F, deg(-45)));
        CubeListBuilder turn7 = CubeListBuilder.create()
                .texOffs(44, 16).addBox(-1.0F, -0.707F, -5.0F, 1.0F, 1.414F, 10.0F); // steel_bev_rim_end_l
        turned.addOrReplaceChild("turn7", turn7, PartPose.offsetAndRotation(
                7.5F, -8.5F, 0.0F, 0.0F, 0.0F, deg(45)));
        CubeListBuilder turn8 = CubeListBuilder.create()
                .texOffs(6, 100).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F); // iron_drop_gem
        turned.addOrReplaceChild("turn8", turn8, PartPose.offsetAndRotation(
                0.0F, -4.0F, 7.0F, 0.0F, 0.0F, deg(45)));
    }

    private static void singleLid(PartDefinition root) {
        CubeListBuilder body = CubeListBuilder.create()
                .texOffs(0, 93).addBox(7.0F, -3.0F, 14.0F, 2.0F, 3.0F, 1.0F) // iron_lidedge_l_front
                .texOffs(64, 103).addBox(7.0F, -3.414F, 14.0F, 2.0F, 0.414F, 1.0F) // iron_lidedge_l_frontfill
                .texOffs(90, 59).addBox(7.0F, -7.0F, 3.0F, 2.0F, 1.0F, 8.0F) // iron_lidedge_l_top
                .texOffs(100, 103).addBox(7.0F, -7.0F, 11.0F, 2.0F, 1.0F, 0.414F) // iron_lidedge_l_topfill_f
                .texOffs(105, 103).addBox(7.0F, -7.0F, 2.586F, 2.0F, 1.0F, 0.414F) // iron_lidedge_l_topfill_b
                .texOffs(6, 93).addBox(7.0F, -3.0F, -1.0F, 2.0F, 3.0F, 1.0F) // iron_lidedge_l_back
                .texOffs(70, 103).addBox(7.0F, -3.414F, -1.0F, 2.0F, 0.414F, 1.0F) // iron_lidedge_l_backfill
                .texOffs(12, 93).addBox(-9.0F, -3.0F, 14.0F, 2.0F, 3.0F, 1.0F) // iron_lidedge_r_front
                .texOffs(76, 103).addBox(-9.0F, -3.414F, 14.0F, 2.0F, 0.414F, 1.0F) // iron_lidedge_r_frontfill
                .texOffs(0, 69).addBox(-9.0F, -7.0F, 3.0F, 2.0F, 1.0F, 8.0F) // iron_lidedge_r_top
                .texOffs(110, 103).addBox(-9.0F, -7.0F, 11.0F, 2.0F, 1.0F, 0.414F) // iron_lidedge_r_topfill_f
                .texOffs(115, 103).addBox(-9.0F, -7.0F, 2.586F, 2.0F, 1.0F, 0.414F) // iron_lidedge_r_topfill_b
                .texOffs(18, 93).addBox(-9.0F, -3.0F, -1.0F, 2.0F, 3.0F, 1.0F) // iron_lidedge_r_back
                .texOffs(82, 103).addBox(-9.0F, -3.414F, -1.0F, 2.0F, 0.414F, 1.0F) // iron_lidedge_r_backfill
                .texOffs(38, 85).addBox(-8.0F, -1.0F, 11.0F, 16.0F, 1.0F, 3.0F) // iron_lid_trim_front
                .texOffs(0, 89).addBox(8.0F, -1.0F, 11.0F, 1.0F, 1.0F, 3.0F) // iron_lidbottom_edge_front_l
                .texOffs(8, 89).addBox(-9.0F, -1.0F, 11.0F, 1.0F, 1.0F, 3.0F) // iron_lidbottom_edge_front_r
                .texOffs(84, 93).addBox(-8.0F, -1.0F, 0.0F, 16.0F, 1.0F, 2.0F) // iron_lid_trim_back
                .texOffs(12, 100).addBox(8.0F, -1.0F, 0.0F, 1.0F, 1.0F, 2.0F) // iron_lidbottom_edge_back_l
                .texOffs(18, 100).addBox(-9.0F, -1.0F, 0.0F, 1.0F, 1.0F, 2.0F) // iron_lidbottom_edge_back_r
                .texOffs(32, 78).addBox(-9.0F, -3.0F, 11.0F, 18.0F, 2.0F, 3.0F) // steel_lid_front
                .texOffs(74, 78).addBox(-9.0F, -3.0F, 0.0F, 18.0F, 2.0F, 2.0F) // steel_lid_back
                .texOffs(72, 49).addBox(8.0F, -1.0F, 2.0F, 1.0F, 1.0F, 9.0F) // iron_lidbottom_edge_end_l
                .texOffs(24, 49).addBox(5.0F, -1.0F, 2.0F, 3.0F, 1.0F, 9.0F) // iron_lid_trim_end_l
                .texOffs(66, 16).addBox(5.0F, -3.0F, 2.0F, 4.0F, 2.0F, 9.0F) // steel_lid_cap1_l
                .texOffs(32, 39).addBox(5.0F, -4.0F, 2.0F, 4.0F, 1.0F, 9.0F) // steel_lid_cap2_l
                .texOffs(92, 49).addBox(7.0F, -6.0F, 3.0F, 2.0F, 2.0F, 8.0F) // steel_lid_cap3_l
                .texOffs(0, 59).addBox(-9.0F, -1.0F, 2.0F, 1.0F, 1.0F, 9.0F) // iron_lidbottom_edge_end_r
                .texOffs(48, 49).addBox(-8.0F, -1.0F, 2.0F, 3.0F, 1.0F, 9.0F) // iron_lid_trim_end_r
                .texOffs(92, 16).addBox(-9.0F, -3.0F, 2.0F, 4.0F, 2.0F, 9.0F) // steel_lid_cap1_r
                .texOffs(58, 39).addBox(-9.0F, -4.0F, 2.0F, 4.0F, 1.0F, 9.0F) // steel_lid_cap2_r
                .texOffs(20, 59).addBox(-9.0F, -6.0F, 3.0F, 2.0F, 2.0F, 8.0F) // steel_lid_cap3_r
                .texOffs(24, 28).addBox(-7.0F, -6.0F, 3.0F, 14.0F, 2.0F, 8.0F) // steel_lid_top
                .texOffs(16, 89).addBox(3.0F, 0.0F, 11.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_uf0
                .texOffs(24, 100).addBox(3.5F, 1.5F, 11.5F, 1.0F, 2.0F, 1.0F) // bonelt_tooth_uf0_tip
                .texOffs(24, 89).addBox(-1.0F, 0.0F, 11.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_uf1
                .texOffs(44, 93).addBox(-0.5F, 1.5F, 11.5F, 1.0F, 2.5F, 1.0F) // bonelt_tooth_uf1_tip
                .texOffs(32, 89).addBox(-5.0F, 0.0F, 11.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_uf2
                .texOffs(28, 100).addBox(-4.5F, 1.5F, 11.5F, 1.0F, 2.0F, 1.0F) // bonelt_tooth_uf2_tip
                .texOffs(40, 89).addBox(5.0F, 0.0F, 8.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_ul1
                .texOffs(32, 100).addBox(5.5F, 1.5F, 8.5F, 1.0F, 1.5F, 1.0F) // bonelt_tooth_ul1_tip
                .texOffs(48, 89).addBox(5.0F, 0.0F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_ul-3
                .texOffs(36, 100).addBox(5.5F, 1.5F, 4.5F, 1.0F, 1.5F, 1.0F) // bonelt_tooth_ul-3_tip
                .texOffs(56, 89).addBox(-7.0F, 0.0F, 8.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_ur1
                .texOffs(40, 100).addBox(-6.5F, 1.5F, 8.5F, 1.0F, 1.5F, 1.0F) // bonelt_tooth_ur1_tip
                .texOffs(64, 89).addBox(-7.0F, 0.0F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_ur-3
                .texOffs(44, 100).addBox(-6.5F, 1.5F, 4.5F, 1.0F, 1.5F, 1.0F) // bonelt_tooth_ur-3_tip
                .texOffs(24, 93).addBox(9.0F, -3.0F, 6.0F, 1.0F, 2.0F, 2.0F) // horn_l_root
                .texOffs(30, 93).addBox(-10.0F, -3.0F, 6.0F, 1.0F, 2.0F, 2.0F); // horn_r_root
        PartDefinition part = root.addOrReplaceChild("lid", CubeListBuilder.create(),
                PartPose.offset(8.0F, 10.0F, 1.0F));
        PartDefinition turned = part.addOrReplaceChild("body", body,
                PartPose.rotation(0.0F, 0.0F, TURN));
        CubeListBuilder turn0 = CubeListBuilder.create()
                .texOffs(8, 78).addBox(7.0F, -2.536F, 0.0F, 2.0F, 5.072F, 1.0F) // iron_lidedge_l_bev_front
                .texOffs(20, 78).addBox(-9.0F, -2.536F, 0.0F, 2.0F, 5.072F, 1.0F) // iron_lidedge_r_bev_front
                .texOffs(100, 69).addBox(7.0F, -2.121F, -2.0F, 1.98F, 4.242F, 2.0F) // steel_lid_bevend_front_l
                .texOffs(116, 69).addBox(-8.98F, -2.121F, -2.0F, 1.98F, 4.242F, 2.0F) // steel_lid_bevend_front_r
                .texOffs(36, 69).addBox(-7.0F, -2.121F, -2.0F, 14.0F, 4.242F, 2.0F); // steel_lid_bev_front
        turned.addOrReplaceChild("turn0", turn0, PartPose.offsetAndRotation(
                0.0F, -4.5F, 12.5F, deg(45), 0.0F, 0.0F));
        CubeListBuilder turn1 = CubeListBuilder.create()
                .texOffs(14, 78).addBox(7.0F, -2.536F, -1.0F, 2.0F, 5.072F, 1.0F) // iron_lidedge_l_bev_back
                .texOffs(26, 78).addBox(-9.0F, -2.536F, -1.0F, 2.0F, 5.072F, 1.0F) // iron_lidedge_r_bev_back
                .texOffs(108, 69).addBox(7.0F, -2.121F, 0.0F, 1.98F, 4.242F, 2.0F) // steel_lid_bevend_back_l
                .texOffs(0, 78).addBox(-8.98F, -2.121F, 0.0F, 1.98F, 4.242F, 2.0F) // steel_lid_bevend_back_r
                .texOffs(68, 69).addBox(-7.0F, -2.121F, 0.0F, 14.0F, 4.242F, 2.0F); // steel_lid_bev_back
        turned.addOrReplaceChild("turn1", turn1, PartPose.offsetAndRotation(
                0.0F, -4.5F, 1.5F, deg(-45), 0.0F, 0.0F));
        CubeListBuilder turn2 = CubeListBuilder.create()
                .texOffs(72, 89).addBox(-1.0F, -2.0F, -0.95F, 2.0F, 2.0F, 1.9F); // horn_l_1
        turned.addOrReplaceChild("turn2", turn2, PartPose.offsetAndRotation(
                9.5F, -2.0F, 7.0F, 0.0F, 0.0F, deg(55)));
        CubeListBuilder turn3 = CubeListBuilder.create()
                .texOffs(80, 89).addBox(-1.0F, -2.0F, -0.9F, 2.0F, 2.0F, 1.8F); // horn_l_2
        turned.addOrReplaceChild("turn3", turn3, PartPose.offsetAndRotation(
                10.7287F, -2.8604F, 7.0F, 0.0F, 0.0F, deg(35)));
        CubeListBuilder turn4 = CubeListBuilder.create()
                .texOffs(48, 100).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F); // horntip_l_3
        turned.addOrReplaceChild("turn4", turn4, PartPose.offsetAndRotation(
                11.5891F, -4.0891F, 7.0F, 0.0F, 0.0F, deg(12)));
        CubeListBuilder turn5 = CubeListBuilder.create()
                .texOffs(52, 100).addBox(-0.5F, -1.5F, -0.45F, 1.0F, 1.5F, 0.9F); // horntip_l_4
        turned.addOrReplaceChild("turn5", turn5, PartPose.offsetAndRotation(
                11.901F, -5.5563F, 7.0F, 0.0F, 0.0F, deg(-15)));
        CubeListBuilder turn6 = CubeListBuilder.create()
                .texOffs(88, 89).addBox(-1.0F, -2.0F, -0.95F, 2.0F, 2.0F, 1.9F); // horn_r_1
        turned.addOrReplaceChild("turn6", turn6, PartPose.offsetAndRotation(
                -9.5F, -2.0F, 7.0F, 0.0F, 0.0F, deg(-55)));
        CubeListBuilder turn7 = CubeListBuilder.create()
                .texOffs(96, 89).addBox(-1.0F, -2.0F, -0.9F, 2.0F, 2.0F, 1.8F); // horn_r_2
        turned.addOrReplaceChild("turn7", turn7, PartPose.offsetAndRotation(
                -10.7287F, -2.8604F, 7.0F, 0.0F, 0.0F, deg(-35)));
        CubeListBuilder turn8 = CubeListBuilder.create()
                .texOffs(56, 100).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F); // horntip_r_3
        turned.addOrReplaceChild("turn8", turn8, PartPose.offsetAndRotation(
                -11.5891F, -4.0891F, 7.0F, 0.0F, 0.0F, deg(-12)));
        CubeListBuilder turn9 = CubeListBuilder.create()
                .texOffs(60, 100).addBox(-0.5F, -1.5F, -0.45F, 1.0F, 1.5F, 0.9F); // horntip_r_4
        turned.addOrReplaceChild("turn9", turn9, PartPose.offsetAndRotation(
                -11.901F, -5.5563F, 7.0F, 0.0F, 0.0F, deg(15)));
    }

    private static void singleLock(PartDefinition root) {
        CubeListBuilder body = CubeListBuilder.create()
                .texOffs(14, 103).addBox(-2.0F, 2.0F, 13.5F, 4.0F, 1.0F, 1.0F) // iron_plate_row0
                .texOffs(64, 100).addBox(-3.0F, 1.0F, 13.5F, 6.0F, 1.0F, 1.0F) // iron_plate_row1
                .texOffs(78, 100).addBox(-3.0F, 0.0F, 13.5F, 6.0F, 1.0F, 1.0F) // iron_plate_row2
                .texOffs(92, 100).addBox(-3.0F, -1.0F, 13.5F, 6.0F, 1.0F, 1.0F) // iron_plate_row3
                .texOffs(106, 100).addBox(-3.0F, -2.0F, 13.5F, 6.0F, 1.0F, 1.0F) // iron_plate_row4
                .texOffs(0, 103).addBox(-3.0F, -3.0F, 13.5F, 6.0F, 1.0F, 1.0F) // iron_plate_row5
                .texOffs(24, 103).addBox(-2.0F, -4.0F, 13.5F, 4.0F, 1.0F, 1.0F) // iron_plate_row6
                .texOffs(88, 103).addBox(-1.0F, 1.0F, 14.0F, 2.0F, 1.0F, 1.0F) // eye_row0
                .texOffs(34, 103).addBox(-2.0F, 0.0F, 14.0F, 4.0F, 1.0F, 1.0F) // eye_row1
                .texOffs(44, 103).addBox(-2.0F, -1.0F, 14.0F, 4.0F, 1.0F, 1.0F) // eye_row2
                .texOffs(54, 103).addBox(-2.0F, -2.0F, 14.0F, 4.0F, 1.0F, 1.0F) // eye_row3
                .texOffs(94, 103).addBox(-1.0F, -3.0F, 14.0F, 2.0F, 1.0F, 1.0F); // eye_row4
        PartDefinition part = root.addOrReplaceChild("lock", CubeListBuilder.create(),
                PartPose.offset(8.0F, 10.0F, 1.0F));
        PartDefinition turned = part.addOrReplaceChild("body", body,
                PartPose.rotation(0.0F, 0.0F, TURN));
    }

    public static LayerDefinition doubleChest() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        doubleBottom(root);
        doubleLid(root);
        doubleLock(root);
        return LayerDefinition.create(mesh, 128, 256);
    }

    private static void doubleBottom(PartDefinition root) {
        CubeListBuilder body = CubeListBuilder.create()
                .texOffs(0, 16).addBox(-16.0F, -1.0F, -7.0F, 32.0F, 1.0F, 14.0F) // iron_plinth
                .texOffs(0, 74).addBox(-15.0F, -9.0F, 4.0F, 30.0F, 8.0F, 2.0F) // steel_wall_front
                .texOffs(0, 104).addBox(-15.0F, -9.0F, -6.0F, 30.0F, 8.0F, 1.0F) // steel_wall_back
                .texOffs(0, 0).addBox(13.0F, -9.0F, -4.0F, 2.0F, 8.0F, 8.0F) // steel_wall_left
                .texOffs(20, 0).addBox(-15.0F, -9.0F, -4.0F, 2.0F, 8.0F, 8.0F) // steel_wall_right
                .texOffs(0, 64).addBox(-13.0F, -2.0F, -5.0F, 26.0F, 1.0F, 9.0F) // mouth_floor
                .texOffs(0, 155).addBox(-16.0F, -10.0F, 4.0F, 32.0F, 1.0F, 3.0F) // iron_rim_front
                .texOffs(0, 171).addBox(-16.0F, -10.0F, -7.0F, 32.0F, 1.0F, 2.0F) // iron_rim_back
                .texOffs(0, 84).addBox(13.0F, -10.0F, -5.0F, 3.0F, 1.0F, 9.0F) // iron_rim_left
                .texOffs(24, 84).addBox(-16.0F, -10.0F, -5.0F, 3.0F, 1.0F, 9.0F) // iron_rim_right
                .texOffs(60, 94).addBox(13.0F, -9.0F, -7.0F, 3.0F, 8.0F, 2.0F) // iron_post_-1_-1
                .texOffs(90, 113).addBox(13.0F, -9.0F, -5.0F, 3.0F, 8.0F, 1.0F) // iron_postin_-1
                .texOffs(58, 180).addBox(13.0F, -6.0F, -8.0F, 2.0F, 2.0F, 1.0F) // stud_front_-1_-1
                .texOffs(8, 167).addBox(16.0F, -6.0F, -6.0F, 1.0F, 2.0F, 2.0F) // stud_end_-1_-1
                .texOffs(0, 43).addBox(13.0F, -9.0F, 4.0F, 3.0F, 8.0F, 3.0F) // iron_post_-1_1
                .texOffs(64, 180).addBox(13.0F, -6.0F, 7.0F, 2.0F, 2.0F, 1.0F) // stud_front_-1_1
                .texOffs(14, 167).addBox(16.0F, -6.0F, 4.0F, 1.0F, 2.0F, 2.0F) // stud_end_-1_1
                .texOffs(70, 94).addBox(-16.0F, -9.0F, -7.0F, 3.0F, 8.0F, 2.0F) // iron_post_1_-1
                .texOffs(98, 113).addBox(-16.0F, -9.0F, -5.0F, 3.0F, 8.0F, 1.0F) // iron_postin_1
                .texOffs(70, 180).addBox(-15.0F, -6.0F, -8.0F, 2.0F, 2.0F, 1.0F) // stud_front_1_-1
                .texOffs(20, 167).addBox(-17.0F, -6.0F, -6.0F, 1.0F, 2.0F, 2.0F) // stud_end_1_-1
                .texOffs(12, 43).addBox(-16.0F, -9.0F, 4.0F, 3.0F, 8.0F, 3.0F) // iron_post_1_1
                .texOffs(76, 180).addBox(-15.0F, -6.0F, 7.0F, 2.0F, 2.0F, 1.0F) // stud_front_1_1
                .texOffs(26, 167).addBox(-17.0F, -6.0F, 4.0F, 1.0F, 2.0F, 2.0F) // stud_end_1_1
                .texOffs(24, 43).addBox(8.0F, -10.0F, 7.0F, 1.0F, 10.0F, 1.0F) // iron_bandL_front_0
                .texOffs(28, 43).addBox(6.0F, -10.0F, 7.0F, 1.0F, 10.0F, 1.0F) // iron_bandL_front_1
                .texOffs(106, 113).addBox(6.0F, -9.0F, 6.0F, 3.0F, 8.0F, 1.0F) // iron_bandfillL_front
                .texOffs(56, 43).addBox(7.0F, -10.0F, 7.0F, 1.0F, 10.0F, 0.5F) // band_midL_front
                .texOffs(44, 188).addBox(9.0F, -3.0F, 7.0F, 1.0F, 1.0F, 1.0F) // iron_notchL_front_2_a
                .texOffs(48, 188).addBox(5.0F, -3.0F, 7.0F, 1.0F, 1.0F, 1.0F) // iron_notchL_front_2_b
                .texOffs(52, 188).addBox(9.0F, -6.0F, 7.0F, 1.0F, 1.0F, 1.0F) // iron_notchL_front_5_a
                .texOffs(56, 188).addBox(5.0F, -6.0F, 7.0F, 1.0F, 1.0F, 1.0F) // iron_notchL_front_5_b
                .texOffs(32, 43).addBox(8.0F, -10.0F, -8.0F, 1.0F, 10.0F, 1.0F) // iron_bandL_back_0
                .texOffs(36, 43).addBox(6.0F, -10.0F, -8.0F, 1.0F, 10.0F, 1.0F) // iron_bandL_back_1
                .texOffs(114, 113).addBox(6.0F, -9.0F, -7.0F, 3.0F, 8.0F, 1.0F) // iron_bandfillL_back
                .texOffs(59, 43).addBox(7.0F, -10.0F, -7.5F, 1.0F, 10.0F, 0.5F) // band_midL_back
                .texOffs(68, 188).addBox(9.0F, -3.0F, -8.0F, 1.0F, 1.0F, 1.0F) // iron_notchL_back_2_a
                .texOffs(72, 188).addBox(5.0F, -3.0F, -8.0F, 1.0F, 1.0F, 1.0F) // iron_notchL_back_2_b
                .texOffs(76, 188).addBox(9.0F, -6.0F, -8.0F, 1.0F, 1.0F, 1.0F) // iron_notchL_back_5_a
                .texOffs(80, 188).addBox(5.0F, -6.0F, -8.0F, 1.0F, 1.0F, 1.0F) // iron_notchL_back_5_b
                .texOffs(40, 43).addBox(-7.0F, -10.0F, 7.0F, 1.0F, 10.0F, 1.0F) // iron_bandR_front_0
                .texOffs(44, 43).addBox(-9.0F, -10.0F, 7.0F, 1.0F, 10.0F, 1.0F) // iron_bandR_front_1
                .texOffs(0, 122).addBox(-9.0F, -9.0F, 6.0F, 3.0F, 8.0F, 1.0F) // iron_bandfillR_front
                .texOffs(62, 43).addBox(-8.0F, -10.0F, 7.0F, 1.0F, 10.0F, 0.5F) // band_midR_front
                .texOffs(124, 188).addBox(-6.0F, -3.0F, 7.0F, 1.0F, 1.0F, 1.0F) // iron_notchR_front_2_a
                .texOffs(0, 190).addBox(-10.0F, -3.0F, 7.0F, 1.0F, 1.0F, 1.0F) // iron_notchR_front_2_b
                .texOffs(4, 190).addBox(-6.0F, -6.0F, 7.0F, 1.0F, 1.0F, 1.0F) // iron_notchR_front_5_a
                .texOffs(8, 190).addBox(-10.0F, -6.0F, 7.0F, 1.0F, 1.0F, 1.0F) // iron_notchR_front_5_b
                .texOffs(48, 43).addBox(-7.0F, -10.0F, -8.0F, 1.0F, 10.0F, 1.0F) // iron_bandR_back_0
                .texOffs(52, 43).addBox(-9.0F, -10.0F, -8.0F, 1.0F, 10.0F, 1.0F) // iron_bandR_back_1
                .texOffs(8, 122).addBox(-9.0F, -9.0F, -7.0F, 3.0F, 8.0F, 1.0F) // iron_bandfillR_back
                .texOffs(65, 43).addBox(-8.0F, -10.0F, -7.5F, 1.0F, 10.0F, 0.5F) // band_midR_back
                .texOffs(20, 190).addBox(-6.0F, -3.0F, -8.0F, 1.0F, 1.0F, 1.0F) // iron_notchR_back_2_a
                .texOffs(24, 190).addBox(-10.0F, -3.0F, -8.0F, 1.0F, 1.0F, 1.0F) // iron_notchR_back_2_b
                .texOffs(28, 190).addBox(-6.0F, -6.0F, -8.0F, 1.0F, 1.0F, 1.0F) // iron_notchR_back_5_a
                .texOffs(32, 190).addBox(-10.0F, -6.0F, -8.0F, 1.0F, 1.0F, 1.0F) // iron_notchR_back_5_b
                .texOffs(110, 186).addBox(10.0F, -9.0F, 7.0F, 3.0F, 1.0F, 1.0F) // iron_scallop_l
                .texOffs(76, 190).addBox(11.0F, -8.0F, 7.0F, 1.0F, 1.0F, 1.0F) // iron_scallop_l_tip
                .texOffs(118, 186).addBox(-13.0F, -9.0F, 7.0F, 3.0F, 1.0F, 1.0F) // iron_scallop_r
                .texOffs(80, 190).addBox(-12.0F, -8.0F, 7.0F, 1.0F, 1.0F, 1.0F) // iron_scallop_r_tip
                .texOffs(82, 180).addBox(-1.0F, -6.0F, 6.5F, 2.0F, 2.0F, 1.0F) // iron_drop_stem
                .texOffs(70, 159).addBox(13.0F, -11.5F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lf0
                .texOffs(84, 190).addBox(13.5F, -12.5F, 4.5F, 1.0F, 1.0F, 1.0F) // bonelt_tooth_lf0_tip
                .texOffs(78, 159).addBox(9.0F, -11.5F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lf1
                .texOffs(88, 167).addBox(9.5F, -14.0F, 4.5F, 1.0F, 2.5F, 1.0F) // bonelt_tooth_lf1_tip
                .texOffs(86, 159).addBox(5.0F, -11.5F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lf2
                .texOffs(106, 180).addBox(5.5F, -13.5F, 4.5F, 1.0F, 2.0F, 1.0F) // bonelt_tooth_lf2_tip
                .texOffs(94, 159).addBox(1.0F, -11.5F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lf3
                .texOffs(88, 190).addBox(1.5F, -12.5F, 4.5F, 1.0F, 1.0F, 1.0F) // bonelt_tooth_lf3_tip
                .texOffs(102, 159).addBox(-3.0F, -11.5F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lf4
                .texOffs(92, 190).addBox(-2.5F, -12.5F, 4.5F, 1.0F, 1.0F, 1.0F) // bonelt_tooth_lf4_tip
                .texOffs(110, 159).addBox(-7.0F, -11.5F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lf5
                .texOffs(110, 180).addBox(-6.5F, -13.5F, 4.5F, 1.0F, 2.0F, 1.0F) // bonelt_tooth_lf5_tip
                .texOffs(118, 159).addBox(-11.0F, -11.5F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lf6
                .texOffs(92, 167).addBox(-10.5F, -14.0F, 4.5F, 1.0F, 2.5F, 1.0F) // bonelt_tooth_lf6_tip
                .texOffs(0, 163).addBox(-15.0F, -11.5F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lf7
                .texOffs(96, 190).addBox(-14.5F, -12.5F, 4.5F, 1.0F, 1.0F, 1.0F) // bonelt_tooth_lf7_tip
                .texOffs(8, 163).addBox(13.0F, -11.5F, -1.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_ll
                .texOffs(100, 190).addBox(13.5F, -12.5F, -0.5F, 1.0F, 1.0F, 1.0F) // bonelt_tooth_ll_tip
                .texOffs(16, 163).addBox(-15.0F, -11.5F, -1.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_lr
                .texOffs(104, 190).addBox(-14.5F, -12.5F, -0.5F, 1.0F, 1.0F, 1.0F); // bonelt_tooth_lr_tip
        PartDefinition part = root.addOrReplaceChild("bottom", CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 8.0F));
        PartDefinition turned = part.addOrReplaceChild("body", body,
                PartPose.rotation(0.0F, 0.0F, TURN));
        CubeListBuilder turn0 = CubeListBuilder.create()
                .texOffs(68, 174).addBox(-14.0F, -0.707F, -1.0F, 28.0F, 1.414F, 1.0F); // steel_bev_plinth_front
        turned.addOrReplaceChild("turn0", turn0, PartPose.offsetAndRotation(
                0.0F, -1.5F, 6.5F, deg(45), 0.0F, 0.0F));
        CubeListBuilder turn1 = CubeListBuilder.create()
                .texOffs(0, 177).addBox(-14.0F, -0.707F, 0.0F, 28.0F, 1.414F, 1.0F); // steel_bev_plinth_back
        turned.addOrReplaceChild("turn1", turn1, PartPose.offsetAndRotation(
                0.0F, -1.5F, -6.5F, deg(-45), 0.0F, 0.0F));
        CubeListBuilder turn2 = CubeListBuilder.create()
                .texOffs(58, 177).addBox(-14.0F, -0.707F, -1.0F, 28.0F, 1.414F, 1.0F); // steel_bev_rim_front
        turned.addOrReplaceChild("turn2", turn2, PartPose.offsetAndRotation(
                0.0F, -8.5F, 6.5F, deg(-45), 0.0F, 0.0F));
        CubeListBuilder turn3 = CubeListBuilder.create()
                .texOffs(0, 180).addBox(-14.0F, -0.707F, 0.0F, 28.0F, 1.414F, 1.0F); // steel_bev_rim_back
        turned.addOrReplaceChild("turn3", turn3, PartPose.offsetAndRotation(
                0.0F, -8.5F, -6.5F, deg(45), 0.0F, 0.0F));
        CubeListBuilder turn4 = CubeListBuilder.create()
                .texOffs(92, 16).addBox(0.0F, -0.707F, -5.0F, 1.0F, 1.414F, 10.0F); // steel_bev_plinth_end_r
        turned.addOrReplaceChild("turn4", turn4, PartPose.offsetAndRotation(
                -15.5F, -1.5F, 0.0F, 0.0F, 0.0F, deg(45)));
        CubeListBuilder turn5 = CubeListBuilder.create()
                .texOffs(0, 31).addBox(-1.0F, -0.707F, -5.0F, 1.0F, 1.414F, 10.0F); // steel_bev_plinth_end_l
        turned.addOrReplaceChild("turn5", turn5, PartPose.offsetAndRotation(
                15.5F, -1.5F, 0.0F, 0.0F, 0.0F, deg(-45)));
        CubeListBuilder turn6 = CubeListBuilder.create()
                .texOffs(22, 31).addBox(0.0F, -0.707F, -5.0F, 1.0F, 1.414F, 10.0F); // steel_bev_rim_end_r
        turned.addOrReplaceChild("turn6", turn6, PartPose.offsetAndRotation(
                -15.5F, -8.5F, 0.0F, 0.0F, 0.0F, deg(-45)));
        CubeListBuilder turn7 = CubeListBuilder.create()
                .texOffs(44, 31).addBox(-1.0F, -0.707F, -5.0F, 1.0F, 1.414F, 10.0F); // steel_bev_rim_end_l
        turned.addOrReplaceChild("turn7", turn7, PartPose.offsetAndRotation(
                15.5F, -8.5F, 0.0F, 0.0F, 0.0F, deg(45)));
        CubeListBuilder turn8 = CubeListBuilder.create()
                .texOffs(88, 180).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F); // iron_drop_gem
        turned.addOrReplaceChild("turn8", turn8, PartPose.offsetAndRotation(
                0.0F, -3.0F, 7.0F, 0.0F, 0.0F, deg(45)));
    }

    private static void doubleLid(PartDefinition root) {
        CubeListBuilder body = CubeListBuilder.create()
                .texOffs(104, 167).addBox(7.0F, -3.0F, 14.0F, 1.0F, 3.0F, 0.5F) // band_lidmidL_front
                .texOffs(6, 192).addBox(7.0F, -3.207F, 14.0F, 1.0F, 0.207F, 0.5F) // bandflat_lidmidfillL_front
                .texOffs(60, 188).addBox(9.0F, -2.0F, 14.0F, 1.0F, 1.0F, 1.0F) // iron_lidnotchL_front_a
                .texOffs(64, 188).addBox(5.0F, -2.0F, 14.0F, 1.0F, 1.0F, 1.0F) // iron_lidnotchL_front_b
                .texOffs(107, 167).addBox(7.0F, -3.0F, -0.5F, 1.0F, 3.0F, 0.5F) // band_lidmidL_back
                .texOffs(9, 192).addBox(7.0F, -3.207F, -0.5F, 1.0F, 0.207F, 0.5F) // bandflat_lidmidfillL_back
                .texOffs(84, 188).addBox(9.0F, -2.0F, -1.0F, 1.0F, 1.0F, 1.0F) // iron_lidnotchL_back_a
                .texOffs(88, 188).addBox(5.0F, -2.0F, -1.0F, 1.0F, 1.0F, 1.0F) // iron_lidnotchL_back_b
                .texOffs(56, 167).addBox(8.0F, -3.0F, 14.0F, 1.0F, 3.0F, 1.0F) // iron_lidbandL0_front
                .texOffs(92, 188).addBox(8.0F, -3.414F, 14.0F, 1.0F, 0.414F, 1.0F) // iron_lidbandL0_frontfill
                .texOffs(102, 104).addBox(8.0F, -7.0F, 3.0F, 1.0F, 1.0F, 8.0F) // iron_lidbandL0_top
                .texOffs(108, 190).addBox(8.0F, -7.0F, 11.0F, 1.0F, 1.0F, 0.414F) // iron_lidbandL0_topfill_f
                .texOffs(111, 190).addBox(8.0F, -7.0F, 2.586F, 1.0F, 1.0F, 0.414F) // iron_lidbandL0_topfill_b
                .texOffs(60, 167).addBox(8.0F, -3.0F, -1.0F, 1.0F, 3.0F, 1.0F) // iron_lidbandL0_back
                .texOffs(96, 188).addBox(8.0F, -3.414F, -1.0F, 1.0F, 0.414F, 1.0F) // iron_lidbandL0_backfill
                .texOffs(64, 167).addBox(6.0F, -3.0F, 14.0F, 1.0F, 3.0F, 1.0F) // iron_lidbandL1_front
                .texOffs(100, 188).addBox(6.0F, -3.414F, 14.0F, 1.0F, 0.414F, 1.0F) // iron_lidbandL1_frontfill
                .texOffs(0, 113).addBox(6.0F, -7.0F, 3.0F, 1.0F, 1.0F, 8.0F) // iron_lidbandL1_top
                .texOffs(114, 190).addBox(6.0F, -7.0F, 11.0F, 1.0F, 1.0F, 0.414F) // iron_lidbandL1_topfill_f
                .texOffs(117, 190).addBox(6.0F, -7.0F, 2.586F, 1.0F, 1.0F, 0.414F) // iron_lidbandL1_topfill_b
                .texOffs(68, 167).addBox(6.0F, -3.0F, -1.0F, 1.0F, 3.0F, 1.0F) // iron_lidbandL1_back
                .texOffs(104, 188).addBox(6.0F, -3.414F, -1.0F, 1.0F, 0.414F, 1.0F) // iron_lidbandL1_backfill
                .texOffs(18, 113).addBox(7.0F, -6.5F, 3.0F, 1.0F, 0.5F, 8.0F) // band_midL_top
                .texOffs(12, 192).addBox(7.0F, -6.5F, 11.0F, 1.0F, 0.5F, 0.207F) // bandflat_midL_topfill_f
                .texOffs(15, 192).addBox(7.0F, -6.5F, 2.793F, 1.0F, 0.5F, 0.207F) // bandflat_midL_topfill_b
                .texOffs(108, 188).addBox(9.0F, -7.0F, 5.0F, 1.0F, 1.0F, 1.0F) // iron_topnotchL_-2_a
                .texOffs(112, 188).addBox(5.0F, -7.0F, 5.0F, 1.0F, 1.0F, 1.0F) // iron_topnotchL_-2_b
                .texOffs(116, 188).addBox(9.0F, -7.0F, 8.0F, 1.0F, 1.0F, 1.0F) // iron_topnotchL_1_a
                .texOffs(120, 188).addBox(5.0F, -7.0F, 8.0F, 1.0F, 1.0F, 1.0F) // iron_topnotchL_1_b
                .texOffs(110, 167).addBox(-8.0F, -3.0F, 14.0F, 1.0F, 3.0F, 0.5F) // band_lidmidR_front
                .texOffs(18, 192).addBox(-8.0F, -3.207F, 14.0F, 1.0F, 0.207F, 0.5F) // bandflat_lidmidfillR_front
                .texOffs(12, 190).addBox(-6.0F, -2.0F, 14.0F, 1.0F, 1.0F, 1.0F) // iron_lidnotchR_front_a
                .texOffs(16, 190).addBox(-10.0F, -2.0F, 14.0F, 1.0F, 1.0F, 1.0F) // iron_lidnotchR_front_b
                .texOffs(113, 167).addBox(-8.0F, -3.0F, -0.5F, 1.0F, 3.0F, 0.5F) // band_lidmidR_back
                .texOffs(21, 192).addBox(-8.0F, -3.207F, -0.5F, 1.0F, 0.207F, 0.5F) // bandflat_lidmidfillR_back
                .texOffs(36, 190).addBox(-6.0F, -2.0F, -1.0F, 1.0F, 1.0F, 1.0F) // iron_lidnotchR_back_a
                .texOffs(40, 190).addBox(-10.0F, -2.0F, -1.0F, 1.0F, 1.0F, 1.0F) // iron_lidnotchR_back_b
                .texOffs(72, 167).addBox(-7.0F, -3.0F, 14.0F, 1.0F, 3.0F, 1.0F) // iron_lidbandR0_front
                .texOffs(44, 190).addBox(-7.0F, -3.414F, 14.0F, 1.0F, 0.414F, 1.0F) // iron_lidbandR0_frontfill
                .texOffs(36, 113).addBox(-7.0F, -7.0F, 3.0F, 1.0F, 1.0F, 8.0F) // iron_lidbandR0_top
                .texOffs(120, 190).addBox(-7.0F, -7.0F, 11.0F, 1.0F, 1.0F, 0.414F) // iron_lidbandR0_topfill_f
                .texOffs(123, 190).addBox(-7.0F, -7.0F, 2.586F, 1.0F, 1.0F, 0.414F) // iron_lidbandR0_topfill_b
                .texOffs(76, 167).addBox(-7.0F, -3.0F, -1.0F, 1.0F, 3.0F, 1.0F) // iron_lidbandR0_back
                .texOffs(48, 190).addBox(-7.0F, -3.414F, -1.0F, 1.0F, 0.414F, 1.0F) // iron_lidbandR0_backfill
                .texOffs(80, 167).addBox(-9.0F, -3.0F, 14.0F, 1.0F, 3.0F, 1.0F) // iron_lidbandR1_front
                .texOffs(52, 190).addBox(-9.0F, -3.414F, 14.0F, 1.0F, 0.414F, 1.0F) // iron_lidbandR1_frontfill
                .texOffs(54, 113).addBox(-9.0F, -7.0F, 3.0F, 1.0F, 1.0F, 8.0F) // iron_lidbandR1_top
                .texOffs(0, 192).addBox(-9.0F, -7.0F, 11.0F, 1.0F, 1.0F, 0.414F) // iron_lidbandR1_topfill_f
                .texOffs(3, 192).addBox(-9.0F, -7.0F, 2.586F, 1.0F, 1.0F, 0.414F) // iron_lidbandR1_topfill_b
                .texOffs(84, 167).addBox(-9.0F, -3.0F, -1.0F, 1.0F, 3.0F, 1.0F) // iron_lidbandR1_back
                .texOffs(56, 190).addBox(-9.0F, -3.414F, -1.0F, 1.0F, 0.414F, 1.0F) // iron_lidbandR1_backfill
                .texOffs(72, 113).addBox(-8.0F, -6.5F, 3.0F, 1.0F, 0.5F, 8.0F) // band_midR_top
                .texOffs(24, 192).addBox(-8.0F, -6.5F, 11.0F, 1.0F, 0.5F, 0.207F) // bandflat_midR_topfill_f
                .texOffs(27, 192).addBox(-8.0F, -6.5F, 2.793F, 1.0F, 0.5F, 0.207F) // bandflat_midR_topfill_b
                .texOffs(60, 190).addBox(-6.0F, -7.0F, 5.0F, 1.0F, 1.0F, 1.0F) // iron_topnotchR_-2_a
                .texOffs(64, 190).addBox(-10.0F, -7.0F, 5.0F, 1.0F, 1.0F, 1.0F) // iron_topnotchR_-2_b
                .texOffs(68, 190).addBox(-6.0F, -7.0F, 8.0F, 1.0F, 1.0F, 1.0F) // iron_topnotchR_1_a
                .texOffs(72, 190).addBox(-10.0F, -7.0F, 8.0F, 1.0F, 1.0F, 1.0F) // iron_topnotchR_1_b
                .texOffs(32, 167).addBox(15.0F, -3.0F, 14.0F, 2.0F, 3.0F, 1.0F) // iron_lidedge_l_front
                .texOffs(0, 188).addBox(15.0F, -3.414F, 14.0F, 2.0F, 0.414F, 1.0F) // iron_lidedge_l_frontfill
                .texOffs(62, 104).addBox(15.0F, -7.0F, 3.0F, 2.0F, 1.0F, 8.0F) // iron_lidedge_l_top
                .texOffs(24, 188).addBox(15.0F, -7.0F, 11.0F, 2.0F, 1.0F, 0.414F) // iron_lidedge_l_topfill_f
                .texOffs(29, 188).addBox(15.0F, -7.0F, 2.586F, 2.0F, 1.0F, 0.414F) // iron_lidedge_l_topfill_b
                .texOffs(38, 167).addBox(15.0F, -3.0F, -1.0F, 2.0F, 3.0F, 1.0F) // iron_lidedge_l_back
                .texOffs(6, 188).addBox(15.0F, -3.414F, -1.0F, 2.0F, 0.414F, 1.0F) // iron_lidedge_l_backfill
                .texOffs(44, 167).addBox(-17.0F, -3.0F, 14.0F, 2.0F, 3.0F, 1.0F) // iron_lidedge_r_front
                .texOffs(12, 188).addBox(-17.0F, -3.414F, 14.0F, 2.0F, 0.414F, 1.0F) // iron_lidedge_r_frontfill
                .texOffs(82, 104).addBox(-17.0F, -7.0F, 3.0F, 2.0F, 1.0F, 8.0F) // iron_lidedge_r_top
                .texOffs(34, 188).addBox(-17.0F, -7.0F, 11.0F, 2.0F, 1.0F, 0.414F) // iron_lidedge_r_topfill_f
                .texOffs(39, 188).addBox(-17.0F, -7.0F, 2.586F, 2.0F, 1.0F, 0.414F) // iron_lidedge_r_topfill_b
                .texOffs(50, 167).addBox(-17.0F, -3.0F, -1.0F, 2.0F, 3.0F, 1.0F) // iron_lidedge_r_back
                .texOffs(18, 188).addBox(-17.0F, -3.414F, -1.0F, 2.0F, 0.414F, 1.0F) // iron_lidedge_r_backfill
                .texOffs(0, 159).addBox(-16.0F, -1.0F, 11.0F, 32.0F, 1.0F, 3.0F) // iron_lid_trim_front
                .texOffs(24, 163).addBox(16.0F, -1.0F, 11.0F, 1.0F, 1.0F, 3.0F) // iron_lidbottom_edge_front_l
                .texOffs(32, 163).addBox(-17.0F, -1.0F, 11.0F, 1.0F, 1.0F, 3.0F) // iron_lidbottom_edge_front_r
                .texOffs(0, 174).addBox(-16.0F, -1.0F, 0.0F, 32.0F, 1.0F, 2.0F) // iron_lid_trim_back
                .texOffs(94, 180).addBox(16.0F, -1.0F, 0.0F, 1.0F, 1.0F, 2.0F) // iron_lidbottom_edge_back_l
                .texOffs(100, 180).addBox(-17.0F, -1.0F, 0.0F, 1.0F, 1.0F, 2.0F) // iron_lidbottom_edge_back_r
                .texOffs(0, 145).addBox(-17.0F, -3.0F, 11.0F, 34.0F, 2.0F, 3.0F) // steel_lid_front
                .texOffs(16, 150).addBox(-17.0F, -3.0F, 0.0F, 34.0F, 2.0F, 2.0F) // steel_lid_back
                .texOffs(96, 84).addBox(16.0F, -1.0F, 2.0F, 1.0F, 1.0F, 9.0F) // iron_lidbottom_edge_end_l
                .texOffs(48, 84).addBox(13.0F, -1.0F, 2.0F, 3.0F, 1.0F, 9.0F) // iron_lid_trim_end_l
                .texOffs(66, 31).addBox(13.0F, -3.0F, 2.0F, 4.0F, 2.0F, 9.0F) // steel_lid_cap1_l
                .texOffs(64, 74).addBox(13.0F, -4.0F, 2.0F, 4.0F, 1.0F, 9.0F) // steel_lid_cap2_l
                .texOffs(0, 94).addBox(15.0F, -6.0F, 3.0F, 2.0F, 2.0F, 8.0F) // steel_lid_cap3_l
                .texOffs(20, 94).addBox(-17.0F, -1.0F, 2.0F, 1.0F, 1.0F, 9.0F) // iron_lidbottom_edge_end_r
                .texOffs(72, 84).addBox(-16.0F, -1.0F, 2.0F, 3.0F, 1.0F, 9.0F) // iron_lid_trim_end_r
                .texOffs(92, 31).addBox(-17.0F, -3.0F, 2.0F, 4.0F, 2.0F, 9.0F) // steel_lid_cap1_r
                .texOffs(90, 74).addBox(-17.0F, -4.0F, 2.0F, 4.0F, 1.0F, 9.0F) // steel_lid_cap2_r
                .texOffs(40, 94).addBox(-17.0F, -6.0F, 3.0F, 2.0F, 2.0F, 8.0F) // steel_lid_cap3_r
                .texOffs(0, 54).addBox(-15.0F, -6.0F, 3.0F, 30.0F, 2.0F, 8.0F) // steel_lid_top
                .texOffs(40, 163).addBox(11.0F, 0.0F, 11.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_uf0
                .texOffs(114, 180).addBox(11.5F, 1.5F, 11.5F, 1.0F, 1.5F, 1.0F) // bonelt_tooth_uf0_tip
                .texOffs(48, 163).addBox(7.0F, 0.0F, 11.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_uf1
                .texOffs(96, 167).addBox(7.5F, 1.5F, 11.5F, 1.0F, 2.5F, 1.0F) // bonelt_tooth_uf1_tip
                .texOffs(56, 163).addBox(3.0F, 0.0F, 11.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_uf2
                .texOffs(118, 180).addBox(3.5F, 1.5F, 11.5F, 1.0F, 2.0F, 1.0F) // bonelt_tooth_uf2_tip
                .texOffs(64, 163).addBox(-1.0F, 0.0F, 11.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_uf3
                .texOffs(122, 180).addBox(-0.5F, 1.5F, 11.5F, 1.0F, 1.5F, 1.0F) // bonelt_tooth_uf3_tip
                .texOffs(72, 163).addBox(-5.0F, 0.0F, 11.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_uf4
                .texOffs(0, 183).addBox(-4.5F, 1.5F, 11.5F, 1.0F, 2.0F, 1.0F) // bonelt_tooth_uf4_tip
                .texOffs(80, 163).addBox(-9.0F, 0.0F, 11.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_uf5
                .texOffs(100, 167).addBox(-8.5F, 1.5F, 11.5F, 1.0F, 2.5F, 1.0F) // bonelt_tooth_uf5_tip
                .texOffs(88, 163).addBox(-13.0F, 0.0F, 11.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_uf6
                .texOffs(4, 183).addBox(-12.5F, 1.5F, 11.5F, 1.0F, 1.5F, 1.0F) // bonelt_tooth_uf6_tip
                .texOffs(96, 163).addBox(13.0F, 0.0F, 8.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_ul1
                .texOffs(8, 183).addBox(13.5F, 1.5F, 8.5F, 1.0F, 1.5F, 1.0F) // bonelt_tooth_ul1_tip
                .texOffs(104, 163).addBox(13.0F, 0.0F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_ul-3
                .texOffs(12, 183).addBox(13.5F, 1.5F, 4.5F, 1.0F, 1.5F, 1.0F) // bonelt_tooth_ul-3_tip
                .texOffs(112, 163).addBox(-15.0F, 0.0F, 8.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_ur1
                .texOffs(16, 183).addBox(-14.5F, 1.5F, 8.5F, 1.0F, 1.5F, 1.0F) // bonelt_tooth_ur1_tip
                .texOffs(120, 163).addBox(-15.0F, 0.0F, 4.0F, 2.0F, 1.5F, 2.0F) // bone_tooth_ur-3
                .texOffs(20, 183).addBox(-14.5F, 1.5F, 4.5F, 1.0F, 1.5F, 1.0F) // bonelt_tooth_ur-3_tip
                .texOffs(74, 145).addBox(-2.0F, -7.0F, 5.0F, 4.0F, 1.0F, 4.0F) // horn_mid_root
                .texOffs(0, 167).addBox(-1.0F, -9.0F, 6.0F, 2.0F, 2.0F, 2.0F) // horn_mid_1
                .texOffs(24, 183).addBox(-0.5F, -10.5F, 6.5F, 1.0F, 1.5F, 1.0F) // horntip_mid_2
                .texOffs(16, 122).addBox(17.0F, -5.0F, 5.0F, 1.0F, 4.0F, 4.0F) // horn_l_root
                .texOffs(26, 122).addBox(-18.0F, -5.0F, 5.0F, 1.0F, 4.0F, 4.0F); // horn_r_root
        PartDefinition part = root.addOrReplaceChild("lid", CubeListBuilder.create(),
                PartPose.offset(0.0F, 10.0F, 1.0F));
        PartDefinition turned = part.addOrReplaceChild("body", body,
                PartPose.rotation(0.0F, 0.0F, TURN));
        CubeListBuilder turn0 = CubeListBuilder.create()
                .texOffs(18, 138).addBox(8.0F, -2.536F, 0.0F, 1.0F, 5.072F, 1.0F) // iron_lidbandL0_bev_front
                .texOffs(26, 138).addBox(6.0F, -2.536F, 0.0F, 1.0F, 5.072F, 1.0F) // iron_lidbandL1_bev_front
                .texOffs(50, 138).addBox(7.0F, -2.328F, 0.0F, 1.0F, 4.656F, 0.5F) // bandflat_midL_bev_front
                .texOffs(34, 138).addBox(-7.0F, -2.536F, 0.0F, 1.0F, 5.072F, 1.0F) // iron_lidbandR0_bev_front
                .texOffs(42, 138).addBox(-9.0F, -2.536F, 0.0F, 1.0F, 5.072F, 1.0F) // iron_lidbandR1_bev_front
                .texOffs(56, 138).addBox(-8.0F, -2.328F, 0.0F, 1.0F, 4.656F, 0.5F) // bandflat_midR_bev_front
                .texOffs(120, 131).addBox(15.0F, -2.536F, 0.0F, 2.0F, 5.072F, 1.0F) // iron_lidedge_l_bev_front
                .texOffs(6, 138).addBox(-17.0F, -2.536F, 0.0F, 2.0F, 5.072F, 1.0F) // iron_lidedge_r_bev_front
                .texOffs(88, 131).addBox(15.0F, -2.121F, -2.0F, 1.98F, 4.242F, 2.0F) // steel_lid_bevend_front_l
                .texOffs(104, 131).addBox(-16.98F, -2.121F, -2.0F, 1.98F, 4.242F, 2.0F) // steel_lid_bevend_front_r
                .texOffs(36, 122).addBox(-15.0F, -2.121F, -2.0F, 30.0F, 4.242F, 2.0F); // steel_lid_bev_front
        turned.addOrReplaceChild("turn0", turn0, PartPose.offsetAndRotation(
                0.0F, -4.5F, 12.5F, deg(45), 0.0F, 0.0F));
        CubeListBuilder turn1 = CubeListBuilder.create()
                .texOffs(22, 138).addBox(8.0F, -2.536F, -1.0F, 1.0F, 5.072F, 1.0F) // iron_lidbandL0_bev_back
                .texOffs(30, 138).addBox(6.0F, -2.536F, -1.0F, 1.0F, 5.072F, 1.0F) // iron_lidbandL1_bev_back
                .texOffs(53, 138).addBox(7.0F, -2.328F, -0.5F, 1.0F, 4.656F, 0.5F) // bandflat_midL_bev_back
                .texOffs(38, 138).addBox(-7.0F, -2.536F, -1.0F, 1.0F, 5.072F, 1.0F) // iron_lidbandR0_bev_back
                .texOffs(46, 138).addBox(-9.0F, -2.536F, -1.0F, 1.0F, 5.072F, 1.0F) // iron_lidbandR1_bev_back
                .texOffs(59, 138).addBox(-8.0F, -2.328F, -0.5F, 1.0F, 4.656F, 0.5F) // bandflat_midR_bev_back
                .texOffs(0, 138).addBox(15.0F, -2.536F, -1.0F, 2.0F, 5.072F, 1.0F) // iron_lidedge_l_bev_back
                .texOffs(12, 138).addBox(-17.0F, -2.536F, -1.0F, 2.0F, 5.072F, 1.0F) // iron_lidedge_r_bev_back
                .texOffs(96, 131).addBox(15.0F, -2.121F, 0.0F, 1.98F, 4.242F, 2.0F) // steel_lid_bevend_back_l
                .texOffs(112, 131).addBox(-16.98F, -2.121F, 0.0F, 1.98F, 4.242F, 2.0F) // steel_lid_bevend_back_r
                .texOffs(0, 131).addBox(-15.0F, -2.121F, 0.0F, 30.0F, 4.242F, 2.0F); // steel_lid_bev_back
        turned.addOrReplaceChild("turn1", turn1, PartPose.offsetAndRotation(
                0.0F, -4.5F, 1.5F, deg(-45), 0.0F, 0.0F));
        CubeListBuilder turn2 = CubeListBuilder.create()
                .texOffs(64, 131).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 4.0F, 3.0F); // horn_l_1
        turned.addOrReplaceChild("turn2", turn2, PartPose.offsetAndRotation(
                17.5F, -3.0F, 7.0F, 0.0F, 0.0F, deg(55)));
        CubeListBuilder turn3 = CubeListBuilder.create()
                .texOffs(90, 145).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F); // horn_l_2
        turned.addOrReplaceChild("turn3", turn3, PartPose.offsetAndRotation(
                19.5479F, -4.434F, 7.0F, 0.0F, 0.0F, deg(35)));
        CubeListBuilder turn4 = CubeListBuilder.create()
                .texOffs(98, 145).addBox(-1.0F, -3.0F, -0.95F, 2.0F, 3.0F, 1.9F); // horn_l_3
        turned.addOrReplaceChild("turn4", turn4, PartPose.offsetAndRotation(
                20.9818F, -6.482F, 7.0F, 0.0F, 0.0F, deg(12)));
        CubeListBuilder turn5 = CubeListBuilder.create()
                .texOffs(106, 145).addBox(-1.0F, -2.5F, -0.9F, 2.0F, 2.5F, 1.8F); // horntip_l_4
        turned.addOrReplaceChild("turn5", turn5, PartPose.offsetAndRotation(
                21.5016F, -8.927F, 7.0F, 0.0F, 0.0F, deg(-10)));
        CubeListBuilder turn6 = CubeListBuilder.create()
                .texOffs(28, 183).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F); // horntip_l_5
        turned.addOrReplaceChild("turn6", turn6, PartPose.offsetAndRotation(
                21.1543F, -10.897F, 7.0F, 0.0F, 0.0F, deg(-25)));
        CubeListBuilder turn7 = CubeListBuilder.create()
                .texOffs(76, 131).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 4.0F, 3.0F); // horn_r_1
        turned.addOrReplaceChild("turn7", turn7, PartPose.offsetAndRotation(
                -17.5F, -3.0F, 7.0F, 0.0F, 0.0F, deg(-55)));
        CubeListBuilder turn8 = CubeListBuilder.create()
                .texOffs(114, 145).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F); // horn_r_2
        turned.addOrReplaceChild("turn8", turn8, PartPose.offsetAndRotation(
                -19.5479F, -4.434F, 7.0F, 0.0F, 0.0F, deg(-35)));
        CubeListBuilder turn9 = CubeListBuilder.create()
                .texOffs(0, 150).addBox(-1.0F, -3.0F, -0.95F, 2.0F, 3.0F, 1.9F); // horn_r_3
        turned.addOrReplaceChild("turn9", turn9, PartPose.offsetAndRotation(
                -20.9818F, -6.482F, 7.0F, 0.0F, 0.0F, deg(-12)));
        CubeListBuilder turn10 = CubeListBuilder.create()
                .texOffs(8, 150).addBox(-1.0F, -2.5F, -0.9F, 2.0F, 2.5F, 1.8F); // horntip_r_4
        turned.addOrReplaceChild("turn10", turn10, PartPose.offsetAndRotation(
                -21.5016F, -8.927F, 7.0F, 0.0F, 0.0F, deg(10)));
        CubeListBuilder turn11 = CubeListBuilder.create()
                .texOffs(32, 183).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F); // horntip_r_5
        turned.addOrReplaceChild("turn11", turn11, PartPose.offsetAndRotation(
                -21.1543F, -10.897F, 7.0F, 0.0F, 0.0F, deg(25)));
    }

    private static void doubleLock(PartDefinition root) {
        CubeListBuilder body = CubeListBuilder.create()
                .texOffs(70, 186).addBox(-2.0F, 3.0F, 13.5F, 4.0F, 1.0F, 1.0F) // iron_plate_row0
                .texOffs(108, 183).addBox(-3.0F, 2.0F, 13.5F, 6.0F, 1.0F, 1.0F) // iron_plate_row1
                .texOffs(36, 183).addBox(-4.0F, 1.0F, 13.5F, 8.0F, 1.0F, 1.0F) // iron_plate_row2
                .texOffs(54, 183).addBox(-4.0F, 0.0F, 13.5F, 8.0F, 1.0F, 1.0F) // iron_plate_row3
                .texOffs(72, 183).addBox(-4.0F, -1.0F, 13.5F, 8.0F, 1.0F, 1.0F) // iron_plate_row4
                .texOffs(90, 183).addBox(-4.0F, -2.0F, 13.5F, 8.0F, 1.0F, 1.0F) // iron_plate_row5
                .texOffs(0, 186).addBox(-3.0F, -3.0F, 13.5F, 6.0F, 1.0F, 1.0F) // iron_plate_row6
                .texOffs(80, 186).addBox(-2.0F, -4.0F, 13.5F, 4.0F, 1.0F, 1.0F) // iron_plate_row7
                .texOffs(90, 186).addBox(-2.0F, 2.0F, 14.0F, 4.0F, 1.0F, 1.0F) // eye_row0
                .texOffs(14, 186).addBox(-3.0F, 1.0F, 14.0F, 6.0F, 1.0F, 1.0F) // eye_row1
                .texOffs(28, 186).addBox(-3.0F, 0.0F, 14.0F, 6.0F, 1.0F, 1.0F) // eye_row2
                .texOffs(42, 186).addBox(-3.0F, -1.0F, 14.0F, 6.0F, 1.0F, 1.0F) // eye_row3
                .texOffs(56, 186).addBox(-3.0F, -2.0F, 14.0F, 6.0F, 1.0F, 1.0F) // eye_row4
                .texOffs(100, 186).addBox(-2.0F, -3.0F, 14.0F, 4.0F, 1.0F, 1.0F); // eye_row5
        PartDefinition part = root.addOrReplaceChild("lock", CubeListBuilder.create(),
                PartPose.offset(0.0F, 10.0F, 1.0F));
        PartDefinition turned = part.addOrReplaceChild("body", body,
                PartPose.rotation(0.0F, 0.0F, TURN));
    }
}
