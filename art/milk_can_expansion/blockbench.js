
const TEXTURES = {"milk_can_expansion_side": "C:/Users/vault-boy/Documents/Programming/Minecraft Mod Portierung 26.2/goblin-labour/art/milk_can_expansion/assets/goblinlabour/textures/block/milk_can_expansion_side.png", "milk_can_expansion_top": "C:/Users/vault-boy/Documents/Programming/Minecraft Mod Portierung 26.2/goblin-labour/art/milk_can_expansion/assets/goblinlabour/textures/block/milk_can_expansion_top.png", "milk_can_expansion_bottom": "C:/Users/vault-boy/Documents/Programming/Minecraft Mod Portierung 26.2/goblin-labour/art/milk_can_expansion/assets/goblinlabour/textures/block/milk_can_expansion_bottom.png"};
for (const p of ModelProject.all.filter(p => p.name === 'milk_can_expansion')) p.close(true);
newProject(Formats.java_block);
Project.name = 'milk_can_expansion';
const tex = {};
for (const [name, path] of Object.entries(TEXTURES)) {
  tex[name] = new Texture({name: name + '.png'}).fromPath(path).add(false);
}
const side = {uv: [0, 0, 16, 16], texture: tex['milk_can_expansion_side'].uuid};
const faces = {north: side, south: {...side}, east: {...side}, west: {...side},
  up: {uv: [0, 0, 16, 16], texture: tex['milk_can_expansion_top'].uuid},
  down: {uv: [0, 0, 16, 16], texture: tex['milk_can_expansion_bottom'].uuid}};
new Cube({name: 'milk_can_expansion', from: [0, 0, 0], to: [16, 16, 16], autouv: 0, faces}).init();
Canvas.updateAll();
({cubes: Cube.all.length, textures: Texture.all.map(t => t.name)});
