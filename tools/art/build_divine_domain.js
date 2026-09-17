(async()=>{
 const fs=require('fs'), root=globalThis.DIVINE_ART_ROOT;
 if(!root)throw new Error('Set DIVINE_ART_ROOT before authoring.');
 const art=root+'/art/blockbench/divine_domain', models=root+'/src/main/resources/assets/blade_tetra/models/divine';
 const textures=root+'/src/main/resources/assets/blade_tetra/textures/divine', svg=root+'/art/svg/divine_domain';
 [art,models,textures,svg].forEach(p=>fs.mkdirSync(p,{recursive:true}));
 const palette=['#EEE6D5','#B84435','#A3874E','#655741'];
 const canvas=document.createElement('canvas');canvas.width=256;canvas.height=256;
 const ctx=canvas.getContext('2d');palette.forEach((c,i)=>{ctx.fillStyle=c;ctx.fillRect(i*64,0,64,256);});
 fs.writeFileSync(textures+'/palette.png',Buffer.from(canvas.toDataURL().split(',')[1],'base64'));
 const summaries=[];
 for(const name of ['purification_blade','three_sword_anchor','boundary_cut','boundary_shard','guard_barrier','divine_mark','final_binding']){
  newProject(Formats.free);Project.name='mikage_'+name;Project.texture_width=256;Project.texture_height=256;
  const tex=new Texture({name:'divine_palette',render_sides:'double'}).fromDataURL(canvas.toDataURL()).add(false);
  const objs=['# Blockbench divine support VFX; block units'], groups={},facesByGroup={};let vi=1,ti=1,tris=0;
  function prism(group,poly,d,c,z=0){
   const parent=groups[group]||(groups[group]=new Group({name:group}).init());
   const mesh=new Mesh({name:group+'_piece',vertices:{},faces:{}});
   const vs=poly.map(p=>[...p,z+d/2]).concat(poly.map(p=>[...p,z-d/2]));let keys=mesh.addVertices(...vs.map(p=>p.map(v=>v*16)));
   const n=poly.length,faces=[];
   THREE.ShapeUtils.triangulateShape(poly.map(p=>new THREE.Vector2(...p)),[]).forEach(f=>{faces.push(f,f.map(k=>k+n).reverse());});
   for(let i=0;i<n;i++){let j=(i+1)%n;faces.push([i,n+i,n+j],[i,n+j,j]);}
   vs.forEach(v=>objs.push('v '+v.map(x=>x.toFixed(6)).join(' ')));
   const output=facesByGroup[group]||(facesByGroup[group]=[]);
   faces.forEach(f=>{
    let uv={};f.forEach((k,j)=>uv[keys[k]]=[c*64+16+j*7,70+j*20]);
    mesh.addFaces(new MeshFace(mesh,{vertices:f.map(k=>keys[k]),uv,texture:tex.uuid}));
    f.forEach((k,j)=>objs.push('vt '+((c*64+16+j*7)/256).toFixed(6)+' '+(1-(70+j*20)/256).toFixed(6)));
    output.push('f '+f.map((k,j)=>(vi+k)+'/'+(ti+j)).join(' '));ti+=3;tris++;
   });vi+=vs.length;mesh.addTo(parent).init();
  }
  const rotate=(ps,a)=>ps.map(([x,y])=>[x*Math.cos(a)-y*Math.sin(a),x*Math.sin(a)+y*Math.cos(a)]);
  function line(g,a,b,w,c,z=0){let dx=b[0]-a[0],dy=b[1]-a[1],l=Math.hypot(dx,dy),x=-dy/l*w/2,y=dx/l*w/2;prism(g,[[a[0]+x,a[1]+y],[b[0]+x,b[1]+y],[b[0]-x,b[1]-y],[a[0]-x,a[1]-y]],.03,c,z);}
  function ring(g,r,w,c,segments=30){for(let i=0;i<segments;i++){if(i%10===0)continue;let a=i*2*Math.PI/segments,b=(i+1)*2*Math.PI/segments;prism(g,[[Math.cos(a)*r,Math.sin(a)*r],[Math.cos(b)*r,Math.sin(b)*r],[Math.cos(b)*(r-w),Math.sin(b)*(r-w)],[Math.cos(a)*(r-w),Math.sin(a)*(r-w)]],.035,c);}}
  if(name==='purification_blade'){
   prism('body',[[-.06,0],[.06,0],[.07,1.45],[0,1.85],[-.07,1.45]],.075,0);
   prism('red',[[-.017,.15],[.017,.15],[.017,1.45],[0,1.67],[-.017,1.45]],.081,1);
   prism('gold',[[-.12,.28],[.12,.28],[.12,.36],[-.12,.36]],.095,2);
   prism('gold',[[-.035,-.28],[.035,-.28],[.035,0],[-.035,0]],.08,2);
  } else if(name==='three_sword_anchor'){
   const p=[[0,-1],[-.866,.5],[.866,.5]];
   for(let i=0;i<3;i++){line('body',p[i],p[(i+1)%3],.018,0);line('red',p[i].map(v=>v*.91),p[(i+1)%3].map(v=>v*.91),.009,1);}
   ring('gold',.18,.025,2,18);
  } else if(name==='boundary_cut'){
   prism('body',[[-1,-.018],[1,-.018],[1,.018],[-1,.018]],.025,0);
   prism('red',[[-1,-.035],[1,-.035],[1,-.020],[-1,-.020]],.03,1);
   line('gold',[0,-1],[0,1],.012,2);
   for(let i=-4;i<=4;i++) line('gold',[i/5,-.75],[i/5,.75],.006,2);
  } else if(name==='boundary_shard'){
   prism('body',[[0,0],[.16,.15],[.02,.5],[-.04,.17]],.02,0);
   line('gold',[0,.04],[.04,.35],.012,2,.015);
  } else if(name==='guard_barrier'){
   ring('body',1,.035,0);ring('gold',.88,.012,2);
   const p=[[0,1],[-.866,-.5],[.866,-.5]];
   for(let i=0;i<3;i++) line('red',p[i],p[(i+1)%3],.035,1);
   for(let i=0;i<6;i++)prism('gold',rotate([[.92,0],[1.09,-.04],[1.14,0],[1.04,.055]],i*Math.PI/3),.045,2);
  } else {
   ring('body',1,.026,0,name==='final_binding'?42:24);ring('gold',.79,.012,2);
   const p=[[0,.70],[-.606,-.35],[.606,-.35]];
   for(let i=0;i<3;i++)line('red',p[i],p[(i+1)%3],.025,1);
   line('body',[0,-.65],[0,.83],.027,0,.025);line('red',[-.3,-.08],[.3,.08],.02,1,.045);
   if(name==='final_binding')for(let i=0;i<6;i++)line('gold',[0,0],[Math.cos(i*Math.PI/3)*1.2,Math.sin(i*Math.PI/3)*1.2],.01,2);
  }
  for(const [g,faces]of Object.entries(facesByGroup))objs.push('g '+g,...faces);
  Canvas.updateAll();fs.writeFileSync(models+'/'+name+'.obj',objs.join('\n')+'\n');
  fs.writeFileSync(art+'/'+name+'.bbmodel',Codecs.project.compile());summaries.push({name,triangles:tris});
 }
 const icons={mikage_support:'M128 30L36 202H220Z M128 64V157 M73 175L111 110 M183 175L145 110',purification_array:'M128 35L28 214H228Z M128 12V91 M28 160V242 M228 160V242',boundary_cut:'M128 22V234 M36 128H220 M116 62L140 194',divine_guard:'M128 25L215 68V148L128 231L41 148V68Z M128 62L67 172H189Z',divine_mark:'M128 32A96 96 0 1 0 129 32 M128 57L54 187H202Z M128 53V209',final_binding:'M128 24A104 104 0 1 0 129 24 M128 48L59 178H197Z M128 6V81 M26 189L85 157 M230 189L171 157'};
 for(const [name,d]of Object.entries(icons)){
  const xml=`<svg xmlns="http:\u002f\u002fwww.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256"><path d="${d}" fill="none" stroke="#EEE6D5" stroke-width="9" stroke-linejoin="round"/><path d="M128 105V151M105 128H151" stroke="#B84435" stroke-width="8"/><circle cx="128" cy="128" r="7" fill="#A3874E"/></svg>`;
  fs.writeFileSync(svg+'/'+name+'.svg',xml);
  const img=new Image();await new Promise((resolve,reject)=>{img.onload=resolve;img.onerror=reject;img.src='data:image/svg+xml;base64,'+Buffer.from(xml).toString('base64');});
  for(const size of [256,128,64,32]){const c=document.createElement('canvas');c.width=c.height=size;c.getContext('2d').drawImage(img,0,0,size,size);fs.writeFileSync(textures+'/'+name+'_'+size+'.png',Buffer.from(c.toDataURL().split(',')[1],'base64'));}
 }
 ModelProject.all.filter(p=>p.name==='mikage_purification_blade').at(-1).select();
 Preview.selected.camera.position.set(25,15,50);Preview.selected.controls.target.set(0,12,0);Preview.selected.controls.update();
 return summaries;
})()
