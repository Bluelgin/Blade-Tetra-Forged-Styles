(()=>{
 const fs=require('fs'),root=globalThis.DIVINE_ART_ROOT,data={};
 for(const name of ['purification_blade','three_sword_anchor','final_binding','divine_mark']){
  ModelProject.all.filter(p=>p.name==='mikage_'+name).at(-1).select();
  data[name]=Outliner.elements.filter(e=>e instanceof Mesh).map(e=>e.getSaveCopy());
 }
 newProject(Formats.free);Project.name='divine_final_binding_assembled_preview';
 Project.texture_width=256;Project.texture_height=256;
 const tex=new Texture({name:'palette',render_sides:'double'}).fromDataURL('data:image/png;base64,'+fs.readFileSync(root+'/src/main/resources/assets/blade_tetra/textures/divine/palette.png').toString('base64')).add(false);
 function place(name,s,off,rx=0,ry=0,rz=0){
  const group=new Group({name}).init();
  for(const saved of data[name]){
   const copy=JSON.parse(JSON.stringify(saved));delete copy.uuid;
   const mesh=new Mesh(copy);
   for(const v of Object.values(mesh.vertices)){
    const point=new THREE.Vector3(v[0]*s,v[1]*s,v[2]*s).applyEuler(new THREE.Euler(rx,ry,rz,'YXZ'));
    v[0]=point.x+off[0]*16;v[1]=point.y+off[1]*16;v[2]=point.z+off[2]*16;
   }
   for(const face of Object.values(mesh.faces))face.texture=tex.uuid;
   mesh.addTo(group).init();
  }
 }
 place('final_binding',6,[0,.07,0],Math.PI/2);
 place('divine_mark',4,[0,4,0],Math.PI/2);
 for(let i=0;i<6;i++){const a=i*Math.PI/3;place('purification_blade',2.4,[Math.cos(a)*5.1,4.7,Math.sin(a)*5.1],0,a,Math.PI);}
 Canvas.updateAll();Preview.selected.camera.position.set(210,160,270);Preview.selected.controls.target.set(0,28,0);Preview.selected.controls.update();
 return {preview:Project.name,meshes:Outliner.elements.length};
})()
