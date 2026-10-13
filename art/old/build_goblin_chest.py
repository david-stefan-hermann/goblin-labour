# Builds the Goblin Chest concept in Blender from cuboids on a 1/16 block grid.
# 1 Blender unit = 1 block. Front faces -Y. The lid hangs on GoblinChest_LidHinge (keys: 1 closed, 15-35 open, 50 closed).
import bpy, math, random
from mathutils import Vector, Matrix

random.seed(7)
PX = 1/16

for cname in ("GoblinChest", "GoblinChest_Preview"):
    c = bpy.data.collections.get(cname)
    if c:
        for o in list(c.all_objects):
            bpy.data.objects.remove(o, do_unlink=True)
        bpy.data.collections.remove(c)
for me in list(bpy.data.meshes):
    if me.name.startswith("GC_") and me.users == 0:
        bpy.data.meshes.remove(me)
for ld in list(bpy.data.lights):
    if ld.name.startswith("GoblinChest_") and ld.users == 0:
        bpy.data.lights.remove(ld)
for cd in list(bpy.data.cameras):
    if cd.name.startswith("GoblinChest_") and cd.users == 0:
        bpy.data.cameras.remove(cd)

col = bpy.data.collections.new("GoblinChest")
bpy.context.scene.collection.children.link(col)


def lin(h):
    h = h.lstrip('#')
    c = [int(h[i:i+2], 16)/255 for i in (0, 2, 4)]
    return [x/12.92 if x <= 0.04045 else ((x+0.055)/1.055)**2.4 for x in c] + [1.0]


PAL = {
    'wood': '#3d4632', 'wood_dk': '#2f3628', 'moss': '#526040', 'moss_lt': '#5f6c47', 'moss_dk': '#444e36',
    'seam': '#1f231a', 'inner': '#2a1d15', 'iron': '#363434', 'iron_lt': '#5e5c59', 'rust': '#553b3a',
    'bone': '#cdb892', 'bone_lt': '#e2d5b4', 'bone_dk': '#9c8462', 'red': '#8f3326', 'red_dk': '#5e1d16',
    'gold': '#b98a30', 'gold_dk': '#6f4f1c', 'rope': '#6e5132', 'skull': '#6a9a55',
}
mats = {}
for key, hx in PAL.items():
    name = "GC_" + key
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree; nt.nodes.clear(); L = nt.links
    out = nt.nodes.new("ShaderNodeOutputMaterial"); out.location = (900, 0)
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled"); bsdf.location = (600, 0)
    tc = nt.nodes.new("ShaderNodeTexCoord"); tc.location = (-800, 0)
    # per-pixel brightness noise: object coords nudged inward along the normal, snapped to the 1/16 grid
    s1 = nt.nodes.new("ShaderNodeVectorMath"); s1.operation = 'SCALE'; s1.inputs[3].default_value = 0.002
    sub = nt.nodes.new("ShaderNodeVectorMath"); sub.operation = 'SUBTRACT'
    s2 = nt.nodes.new("ShaderNodeVectorMath"); s2.operation = 'SCALE'; s2.inputs[3].default_value = 16.0
    fl = nt.nodes.new("ShaderNodeVectorMath"); fl.operation = 'FLOOR'
    wn = nt.nodes.new("ShaderNodeTexWhiteNoise"); wn.noise_dimensions = '3D'
    mr = nt.nodes.new("ShaderNodeMapRange")
    jit = 0.07 if key in ('bone', 'bone_lt', 'skull', 'gold', 'seam') else 0.2
    mr.inputs[3].default_value = 1 - jit; mr.inputs[4].default_value = 1 + jit
    hs = nt.nodes.new("ShaderNodeHueSaturation")
    hs.inputs["Color"].default_value = lin(hx)
    for i, n in enumerate((s1, sub, s2, fl, wn, mr, hs)):
        n.location = (-600 + i*170, -200)
    L.new(tc.outputs["Normal"], s1.inputs[0]); L.new(tc.outputs["Object"], sub.inputs[0])
    L.new(s1.outputs[0], sub.inputs[1]); L.new(sub.outputs[0], s2.inputs[0])
    L.new(s2.outputs[0], fl.inputs[0]); L.new(fl.outputs[0], wn.inputs["Vector"])
    L.new(wn.outputs["Value"], mr.inputs[0]); L.new(mr.outputs[0], hs.inputs["Value"])
    L.new(hs.outputs["Color"], bsdf.inputs["Base Color"]); L.new(bsdf.outputs[0], out.inputs[0])
    metal = key in ('iron', 'iron_lt')
    bsdf.inputs["Metallic"].default_value = 0.55 if metal else 0.0
    bsdf.inputs["Roughness"].default_value = 0.5 if metal else 0.9
    m.diffuse_color = lin(hx)
    mats[key] = m


class B:
    """Collects cuboids (pixel coords) into one mesh."""
    def __init__(s): s.v = []; s.f = []; s.mi = []; s.mats = []

    def box(s, a, b, m, M=None):
        x0, y0, z0 = [min(p, q) for p, q in zip(a, b)]
        x1, y1, z1 = [max(p, q) for p, q in zip(a, b)]
        cs = [(x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0),(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)]
        if M is not None:
            cs = [tuple(M @ Vector(c)) for c in cs]
        i = len(s.v); s.v += cs
        if m not in s.mats: s.mats.append(m)
        idx = s.mats.index(m)
        for fc in ((0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)):
            s.f.append(tuple(i+k for k in fc)); s.mi.append(idx)


def finish(name, b, parent, origin=(0,0,0), parent_origin=(0,0,0)):
    me = bpy.data.meshes.new("GC_" + name)
    o = Vector(origin)
    me.from_pydata([(Vector(v) - o) * PX for v in b.v], [], b.f)
    for k in b.mats: me.materials.append(mats[k])
    me.polygons.foreach_set("material_index", b.mi)
    me.update()
    ob = bpy.data.objects.new("GoblinChest_" + name, me)
    col.objects.link(ob)
    ob.parent = parent
    ob.location = (o - Vector(parent_origin)) * PX
    return ob


def tooth(b, base, d, s=1.0):
    d = Vector(d).normalized()
    q = Vector((0, 0, 1)).rotation_difference(d)
    R = Matrix.Translation(Vector(base)) @ q.to_matrix().to_4x4() @ Matrix.Rotation(random.uniform(-0.35, 0.35), 4, 'Z')
    for w, z0, z1, m in ((1.0*s, -1.0, 1.4*s, 'bone_dk'), (0.75*s, 1.4*s, 2.8*s, 'bone'),
                         (0.5*s, 2.8*s, 4.0*s, 'bone'), (0.25*s, 4.0*s, 5.0*s, 'bone_lt')):
        b.box((-w, -w*0.85, z0), (w, w*0.85, z1), m, R)


def face_pt(face, u, v, d):
    return {'front': (u, -7.2-d, v), 'back': (-u, 7.2+d, v), 'left': (-15-d, u, v), 'right': (15+d, -u, v)}[face]


def fbox(b, face, u0, u1, v0, v1, d0, d1, m):
    b.box(face_pt(face, u0, v0, d0), face_pt(face, u1, v1, d1), m)


def rope(b, face, x, top, rt=12.5):
    fbox(b, face, x-0.25, x+0.25, top, rt, 0.15, 0.45, 'rope')
    fbox(b, face, x-0.45, x+0.45, rt-0.9, rt-0.3, 0.1, 0.55, 'rope')


def banner(b, face, x, top):
    rope(b, face, x, top)
    fbox(b, face, x-2, x+2, top-0.6, top, 0.1, 0.8, 'rope')
    fbox(b, face, x-1.5, x+1.5, top-4.2, top-0.6, 0.1, 0.5, 'red')
    fbox(b, face, x-1.5, x-0.5, top-5.2, top-4.2, 0.1, 0.5, 'red')
    fbox(b, face, x+0.5, x+1.5, top-5.2, top-4.2, 0.1, 0.5, 'red')
    fbox(b, face, x-0.6, x+0.6, top-3.0, top-1.8, 0.5, 0.6, 'red_dk')


def charm(b, face, x, top):
    rope(b, face, x, top)
    fbox(b, face, x-0.5, x+0.5, top-4, top, 0.2, 0.9, 'bone')
    fbox(b, face, x-1.2, x+1.2, top-0.9, top, 0.2, 0.9, 'bone')
    fbox(b, face, x-1.2, x+1.2, top-4, top-3.1, 0.2, 0.9, 'bone')


def tag(b, face, x, top):
    rope(b, face, x, top)
    fbox(b, face, x-1, x+1, top-3.2, top, 0.1, 0.6, 'gold')
    fbox(b, face, x-0.5, x+0.5, top-4, top-3.2, 0.1, 0.6, 'gold')
    fbox(b, face, x-0.35, x+0.35, top-1.4, top-0.6, 0.6, 0.7, 'gold_dk')


root = bpy.data.objects.new("GoblinChest", None); col.objects.link(root)
root.empty_display_type = 'PLAIN_AXES'; root.empty_display_size = 0.5
HINGE = (0, 7.5, 14)
hinge = bpy.data.objects.new("GoblinChest_LidHinge", None); col.objects.link(hinge)
hinge.empty_display_type = 'SINGLE_ARROW'; hinge.empty_display_size = 0.3
hinge.parent = root; hinge.location = Vector(HINGE) * PX

# ---------- body ----------
body = B()
for sx in (-1, 1):
    for sy in (-1, 1):
        body.box((sx*10.5, sy*2.5, 0), (sx*15.8, sy*7.8, 3.5), 'iron')
        body.box((sx*13.15-0.8, sy*7.8, 1), (sx*13.15+0.8, sy*8.2, 2.6), 'iron_lt')
        body.box((sx*15.8, sy*5.15-0.8, 1), (sx*16.2, sy*5.15+0.8, 2.6), 'iron_lt')
        body.box((sx*13, sy*5, 3.5), (sx*15.5, sy*7.5, 12.5), 'moss')
body.box((-14.5, -6.5, 3), (14.5, 6.5, 4), 'wood_dk')
for z0, z1, m in ((3.5, 6.5, 'wood'), (6.5, 9.5, 'wood_dk'), (9.5, 12.5, 'wood')):
    body.box((-13.5, -7.2, z0), (13.5, -5.5, z1), m)
    body.box((-13.5, 5.5, z0), (13.5, 7.2, z1), m)
    body.box((-15, -5.5, z0), (-13.5, 5.5, z1), m)
    body.box((13.5, -5.5, z0), (15, 5.5, z1), m)
for z in (6.5, 9.5):  # plank seams
    body.box((-13, -7.25, z-0.12), (13, -7.2, z+0.12), 'seam')
    body.box((-13, 7.2, z-0.12), (13, 7.25, z+0.12), 'seam')
    body.box((-15.05, -5, z-0.12), (-15, 5, z+0.12), 'seam')
    body.box((15, -5, z-0.12), (15.05, 5, z+0.12), 'seam')
for x in (-4.5, 4.5):  # staggered board ends
    body.box((x-0.12, -7.25, 6.5), (x+0.12, -7.2, 9.5), 'seam')
    body.box((x-0.12, 7.2, 3.5), (x+0.12, 7.25, 6.5), 'seam')
body.box((-13, -7.5, 3.5), (13, -6, 4.8), 'moss')
body.box((-13, 6, 3.5), (13, 7.5, 4.8), 'moss')
body.box((-15.3, -5, 3.5), (-14, 5, 4.8), 'moss')
body.box((14, -5, 3.5), (15.3, 5, 4.8), 'moss')
body.box((-15.5, -7.5, 12.5), (15.5, -5.5, 14), 'moss')
body.box((-15.5, 5.5, 12.5), (15.5, 7.5, 14), 'moss')
body.box((-15.5, -5.5, 12.5), (-13.5, 5.5, 14), 'moss')
body.box((13.5, -5.5, 12.5), (15.5, 5.5, 14), 'moss')
body.box((-13.5, -5.5, 4), (13.5, 5.5, 4.15), 'inner')
body.box((-13.5, -5.5, 4), (13.5, -5.35, 14), 'inner')
body.box((-13.5, 5.35, 4), (13.5, 5.5, 14), 'inner')
body.box((-13.5, -5.35, 4), (-13.35, 5.35, 14), 'inner')
body.box((13.35, -5.35, 4), (13.5, 5.35, 14), 'inner')
body.box((-1.5, -7.8, 10.5), (1.5, -7.2, 12.5), 'iron_lt')
body.box((-0.5, -7.9, 11), (0.5, -7.75, 12), 'inner')
for sx in (-1, 1):
    body.box((sx*9, 7.2, 3.5), (sx*10.5, 7.7, 12.5), 'iron')
    for z in (5.5, 10.5):
        body.box((sx*9.75-0.4, 7.7, z-0.4), (sx*9.75+0.4, 7.95, z+0.4), 'iron_lt')
finish("Body", body, root)

orn = B()
banner(orn, 'front', -10, 10.5); charm(orn, 'front', -6, 10); tag(orn, 'front', 5, 10.5); banner(orn, 'front', 10, 10.5)
for ux in (-1.2, 1.2):
    fbox(orn, 'front', ux-0.25, ux+0.25, 9, 10.5, 0.15, 0.45, 'rope')
fbox(orn, 'front', -2, 2, 6, 7, 0.15, 0.9, 'bone')
fbox(orn, 'front', -2, -1, 7, 9, 0.15, 0.9, 'bone')
fbox(orn, 'front', 1, 2, 7, 9, 0.15, 0.9, 'bone')
fbox(orn, 'front', -0.6, 0.6, 7, 7.6, 0.3, 0.8, 'bone_lt')
banner(orn, 'back', 0, 10.5); tag(orn, 'back', -6, 10); charm(orn, 'back', 6, 10)
fbox(orn, 'back', -11, 11, 11.3, 11.8, 0.1, 0.5, 'rope')
tag(orn, 'left', -3.2, 10.5); banner(orn, 'left', 3.2, 11)
charm(orn, 'right', -3.2, 10.5); banner(orn, 'right', 3.2, 11)
finish("Body_Ornaments", orn, root)

bt = B()
for x in (-11.5, -7, -3, 3, 7, 11.5):
    tooth(bt, (x, -5.9, 13.5), (0, 0, 1), random.uniform(0.8, 1.0))
for sx in (-1, 1):
    for y in (-2.5, 2.5):
        tooth(bt, (sx*14.0, y, 13.5), (0, 0, 1), 0.7)
    tooth(bt, (sx*11, 5.9, 13.5), (0, 0, 1), 0.75)
    tooth(bt, (sx*15.2, 0, 8), (sx, 0, 0.1), 1.1)
    tooth(bt, (sx*15.3, -6.2, 6), (sx, -0.4, 0), 0.75)
finish("Body_Teeth", bt, root)

# ---------- lid (chest coords, origin at the hinge) ----------
lid = B()
lid.box((-15.5, -7.5, 14), (15.5, -6, 18.5), 'moss_dk')
lid.box((-15.5, 6, 14), (15.5, 7.5, 18.5), 'moss_dk')
lid.box((-15.5, -6, 14), (-14, 6, 18.5), 'moss_dk')
lid.box((14, -6, 14), (15.5, 6, 18.5), 'moss_dk')
lid.box((-15.6, -7.7, 14), (15.6, -6, 15), 'moss')  # rim band, open in the middle
lid.box((-15.6, 6, 14), (15.6, 7.7, 15), 'moss')
lid.box((-15.6, -6, 14), (-14, 6, 15), 'moss')
lid.box((14, -6, 14), (15.6, 6, 15), 'moss')
lid.box((-15.5, -7.5, 18.5), (15.5, 7.5, 20), 'moss')
for y0, y1, m in ((-6.5, -2.2, 'moss_lt'), (-2.2, 2.2, 'moss'), (2.2, 6.5, 'moss_lt')):
    lid.box((-14.5, y0, 20), (14.5, y1, 21.2), m)
for y in (-2.2, 2.2):
    lid.box((-14.5, y-0.12, 21.2), (14.5, y+0.12, 21.25), 'seam')
lid.box((-15, -7.55, 16.2), (15, -7.5, 16.45), 'seam')
lid.box((-14, -6, 18.35), (14, 6, 18.5), 'inner')
lid.box((-14, -6, 14), (14, -5.85, 18.5), 'inner')
lid.box((-14, 5.85, 14), (14, 6, 18.5), 'inner')
lid.box((-14, -5.85, 14), (-13.85, 5.85, 18.5), 'inner')
lid.box((13.85, -5.85, 14), (14, 5.85, 18.5), 'inner')
for c, w, m in ((-12.25, 0.75, 'iron'), (12.25, 0.75, 'iron'), (-6, 1.25, 'rust'), (6, 1.25, 'rust')):
    lid.box((c-w, -7.9, 20), (c+w, 7.9, 21.7), m)
    lid.box((c-w, -7.9, 14.3), (c+w, -7.5, 21.7), m)
    lid.box((c-w, 7.5, 14.3), (c+w, 7.9, 21.7), m)
    for z in (16, 19.5):
        lid.box((c-0.45, -8.2, z-0.45), (c+0.45, -7.9, z+0.45), 'iron_lt')
    for y in (-4, 4):
        lid.box((c-0.45, y-0.45, 21.7), (c+0.45, y+0.45, 22), 'iron_lt')
finish("Lid", lid, hinge, HINGE, HINGE)

lock = B()
lock.box((-2.5, -9, 12), (2.5, -7.9, 19.5), 'iron')
lock.box((-2.7, -9.2, 19.5), (2.7, -7.9, 20.3), 'iron_lt')
lock.box((-1, -8.4, 20.3), (1, -7.5, 21.0), 'iron')
for x in (-1.8, 1.8):
    for z in (12.7, 18.8):
        lock.box((x-0.4, -9.3, z-0.4), (x+0.4, -9, z+0.4), 'iron_lt')
lock.box((-1.5, -9.25, 14.5), (1.5, -9, 17.5), 'skull')
lock.box((-1, -9.25, 13.5), (1, -9, 14.5), 'skull')
lock.box((-2.1, -9.25, 17), (-1.5, -9, 18.2), 'skull')
lock.box((1.5, -9.25, 17), (2.1, -9, 18.2), 'skull')
lock.box((-1.1, -9.35, 15.8), (-0.3, -9.2, 16.8), 'iron')
lock.box((0.3, -9.35, 15.8), (1.1, -9.2, 16.8), 'iron')
lock.box((-0.25, -9.35, 15.0), (0.25, -9.2, 15.5), 'iron')
finish("Lid_Lock", lock, hinge, HINGE, HINGE)

lt = B()
for x, s in ((-13, 0.85), (-8.8, 1.1), (-4, 1.0), (4, 1.0), (8.8, 1.1), (13, 0.85)):
    tooth(lt, (x, -8.4, 15), (0, 0, -1), s)
for sx in (-1, 1):
    for y in (-5, 0, 5):
        tooth(lt, (sx*16, y, 15), (0, 0, -1), 0.7)
    tooth(lt, (sx*14.8, -6.8, 20.5), (sx*0.35, -0.25, 1), 1.3)
    tooth(lt, (sx*14.8, 6.8, 20.5), (sx*0.35, 0.25, 1), 1.15)
    tooth(lt, (sx*15.5, 0, 17), (sx, 0, 0.35), 0.9)
for x in (-12, -8, -4, 0, 4, 8, 12):
    tooth(lt, (x, -5.0, 15), (0, 0, -1), 0.6)
finish("Lid_Teeth", lt, hinge, HINGE, HINGE)

hinge.animation_data_clear()
for fr, ang in ((1, 0), (15, -75), (35, -75), (50, 0)):
    hinge.rotation_euler[0] = math.radians(ang)
    hinge.keyframe_insert("rotation_euler", index=0, frame=fr)

# ---------- preview scene ----------
pcol = bpy.data.collections.new("GoblinChest_Preview")
bpy.context.scene.collection.children.link(pcol)
gm = bpy.data.materials.get("GC_ground") or bpy.data.materials.new("GC_ground")
gm.use_nodes = True
gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = lin('#6e6e6e')
gm.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = 1.0
gme = bpy.data.meshes.new("GC_ground")
gme.from_pydata([(-8,-8,0),(8,-8,0),(8,8,0),(-8,8,0)], [], [(0,1,2,3)]); gme.materials.append(gm)
g = bpy.data.objects.new("GoblinChest_Ground", gme); pcol.objects.link(g)
tgt = bpy.data.objects.new("GoblinChest_CamTarget", None); pcol.objects.link(tgt); tgt.location = (0, 0, 0.7)
camd = bpy.data.cameras.new("GoblinChest_Cam"); camd.lens = 50
cam = bpy.data.objects.new("GoblinChest_Cam", camd); pcol.objects.link(cam)
cam.location = (2.9, -4.4, 2.9)
tcn = cam.constraints.new('TRACK_TO'); tcn.target = tgt; tcn.track_axis = 'TRACK_NEGATIVE_Z'; tcn.up_axis = 'UP_Y'
sund = bpy.data.lights.new("GoblinChest_Key", 'SUN'); sund.energy = 3.5; sund.angle = math.radians(8)
sun = bpy.data.objects.new("GoblinChest_Key", sund); pcol.objects.link(sun)
sun.rotation_euler = (math.radians(50), 0, math.radians(35))
ard = bpy.data.lights.new("GoblinChest_Fill", 'AREA'); ard.energy = 250; ard.size = 4
ar = bpy.data.objects.new("GoblinChest_Fill", ard); pcol.objects.link(ar)
ar.location = (-4, -3, 3); ar.rotation_euler = (math.radians(60), 0, math.radians(-55))
w = bpy.data.worlds.get("GoblinChest_World") or bpy.data.worlds.new("GoblinChest_World")
w.use_nodes = True
bg = w.node_tree.nodes.get("Background")
bg.inputs[0].default_value = lin('#6f6f6f'); bg.inputs[1].default_value = 0.8
sc = bpy.context.scene
sc.world = w; sc.camera = cam
sc.render.resolution_x = 1280; sc.render.resolution_y = 960; sc.render.resolution_percentage = 100
cube = bpy.data.objects.get("Cube")
if cube:
    cube.hide_set(True); cube.hide_render = True
sc.frame_set(1)
