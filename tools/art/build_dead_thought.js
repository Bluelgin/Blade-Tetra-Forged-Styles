(async () => {
  const fs = require('fs');
  const root = globalThis.DEAD_THOUGHT_ROOT;
  if (!root) throw new Error('Set globalThis.DEAD_THOUGHT_ROOT to the repository path before running this authoring script.');
  const art = root + '/art/blockbench/dead_thought';
  const models = root + '/src/main/resources/assets/blade_tetra/models/effect/dead_thought';
  const textures = root + '/src/main/resources/assets/blade_tetra/textures/effect/dead_thought';
  [art, models, textures].forEach(p => fs.mkdirSync(p, {recursive:true}));
  const canvas = document.createElement('canvas'); canvas.width=256; canvas.height=256;
  const ctx=canvas.getContext('2d');
  const palette=['#160f19','#351320','#64182c','#9d243b','#dc3f4c','#f57970','#ffd1b7','#08080e'];
  palette.forEach((c,i)=>{
    ctx.fillStyle=c;ctx.fillRect(i*32,0,32,256);
    for(let y=12;y<256;y+=29){ctx.fillStyle=i<4?'#ffffff0b':'#ffffff12';ctx.fillRect(i*32+6,y,18,1);}
  });
  fs.writeFileSync(textures+'/dead_thought_palette.png',Buffer.from(canvas.toDataURL().split(',')[1],'base64'));
  const summary=[];
  for (const family of ['final_wheel','rifts','domain','branch_cage','life_remnant','scars','execution_line']) {
    newProject(Formats.free);Project.name='dead_thought_'+family;Project.texture_width=256;Project.texture_height=256;
    const tex=new Texture({name:'dead_thought_palette',render_mode:'default',render_sides:'double'}).fromDataURL(canvas.toDataURL()).add(false);
    let groups={}, objGroups={};let obj=['# Blade Tetra Dead Thought - authored in Blockbench; units are blocks'];let vi=1,ti=1;let triangles=0;
    function prism(name,poly,depth,color,z=0){
      const group=groups[name]||(groups[name]=new Group({name,origin:[0,0,0]}).init());
      const mesh=new Mesh({name:name+'_piece',vertices:{},faces:{},origin:[0,0,0]});
      const vs=poly.map(p=>[p[0],p[1],z+depth/2]).concat(poly.map(p=>[p[0],p[1],z-depth/2]));
      let keys=mesh.addVertices(...vs.map(p=>p.map(v=>v*16)));const n=poly.length;let faces=[];
      const front=THREE.ShapeUtils.triangulateShape(poly.map(p=>new THREE.Vector2(...p)),[]);
      front.forEach(f=>{faces.push(f);faces.push(f.map(k=>k+n).reverse());});
      for(let i=0;i<n;i++){let j=(i+1)%n;faces.push([i,n+i,n+j]);faces.push([i,n+j,j]);}
      const groupFaces=objGroups[name]||(objGroups[name]=[]);
      vs.forEach(v=>obj.push('v '+v.map(x=>x.toFixed(6)).join(' ')));
      faces.forEach((f,index)=>{
        const c=index>=2*(n-2)?Math.max(0,color-1):color;
        let uv={};f.forEach((k,j)=>uv[keys[k]]=[c*32+8+j*5,80+j*31]);
        mesh.addFaces(new MeshFace(mesh,{vertices:f.map(k=>keys[k]),uv,texture:tex.uuid}));
        f.forEach((k,j)=>obj.push('vt '+((c*32+8+j*5)/256).toFixed(6)+' '+(1-(80+j*31)/256).toFixed(6)));
        groupFaces.push('f '+f.map((k,j)=>(vi+k)+'/'+(ti+j)).join(' '));ti+=3;triangles++;
      });vi+=vs.length;mesh.addTo(group).init();
    }
    const rotate=(poly,a)=>poly.map(([x,y])=>[x*Math.cos(a)-y*Math.sin(a),x*Math.sin(a)+y*Math.cos(a)]);
    function band(name,r,width,a,b,color,z=0){for(let k=0;k<Math.ceil((b-a)*7);k++){let count=Math.ceil((b-a)*7),t=a+(b-a)*k/count,u=a+(b-a)*(k+1)/count;prism(name,[[r*Math.cos(t),r*Math.sin(t)],[r*Math.cos(u),r*Math.sin(u)],[(r-width)*Math.cos(u),(r-width)*Math.sin(u)],[(r-width)*Math.cos(t),(r-width)*Math.sin(t)]],.045,color,z);}}
    function blade(name,a,length,width,color,z=0){prism(name,rotate([[.23,0],[length*.65,-width],[length,0],[length*.72,width*.52]],a),.06,color,z);}
    if(family==='final_wheel'){
      for(let i=0;i<5;i++){
        let a=i*Math.PI*2/5+.23;
        band('outer',.99,.038,a+.09,a+.88,2);
        band('edge',1.005,.008,a+.14,a+.80,5,.027);
        let petal=[[.34,0],[.58,-.15],[.93,-.115],[.78,.025],[.86,.11],[.55,.16]];
        prism('petals',rotate(petal,a),.09,2,.018);
        prism('veins',rotate([[.38,0],[.65,-.03],[.88,-.10],[.65,.007]],a),.018,4,.08);
        blade('shards',a+.47,.87,.03,1,-.04);
      }
      band('inner',.32,.02,0,5.9,3,.07);band('inner',.26,.009,.4,5.4,5,.09);
      prism('core',[[0,-.095],[.045,-.02],[.035,.055],[0,.12],[-.045,.025]],.13,7,.05);
      for(let i=0;i<3;i++)blade('core_edges',i*2.094,.17,.014,5,.10);
    }
    if(family==='rifts'){
      for(let side of ['left','right']){
        let sign=side==='left'?1:-1;
        const p=[[-1,0],[-.58,-.105],[-.23,-.09],[.14,-.17],[.43,-.1],[1,.035],[.47,.065],[.20,.14],[-.18,.085],[-.51,.12]].map(([x,y])=>[x,y*sign]);
        prism('rift_'+side,p,.12,0);
        prism('rim_'+side,p.map(([x,y])=>[x*.94,y*.73]),.13,3);
        prism('void_'+side,p.map(([x,y])=>[x*.91,y*.51]),.145,7);
        prism('vein_'+side,[[-.88,0],[-.15,-.008],[.30,.019],[.88,.03],[.23,.033],[-.12,.006]].map(([x,y])=>[x,y*sign]),.151,4);
      }
    }
    if(family==='domain'){
      for(let i=0;i<7;i++){
        let a=i*Math.PI*2/7;
        band('outer',1,.012,a+.035,a+.69,3);band('inner',.72,.009,a+.12,a+.64,2);
        blade('cuts',a+.27,.93,.011,4);blade('cuts',a+.51,.58,.018,2);
        prism('petals',rotate([[.74,0],[.84,-.04],[.94,.02],[.79,.029]],a+.05),.01,3);
      }
      band('center',.24,.012,.15,5.6,4);
    }
    if(family==='branch_cage'){
      for(let i=0;i<3;i++){
        let x=(i-1)*.22,h=.65+i*.12;
        prism('branch',[[x-.035,0],[x+.02,0],[x+.06,h*.4],[x-.07,h*.72],[x+.015,h],[x-.12,h*.72],[x-.015,h*.36]],.07,0);
        prism('edge',[[x-.015,.05],[x+.025,h*.4],[x-.085,h*.72],[x-.01,h*.86],[x-.07,h*.7],[x+.045,h*.4]],.075,3);
        prism('thorns',[[x+.01,h*.4],[x+.22,h*.66],[x+.09,h*.44]],.03,2);
      }
    }
    if(family==='life_remnant'){
      const shapes=[[[0,.95],[.11,.85],[.08,.72],[-.07,.70],[-.12,.82]],[[0,.69],[.17,.61],[.13,.37],[-.11,.35],[-.18,.59]],[[.1,.59],[.3,.29],[.21,.35]],[[ -.12,.58],[-.3,.31],[-.23,.28]],[[.02,.36],[.13,.32],[.16,0],[.04,.08]],[[0,.36],[-.12,.33],[-.18,.01],[-.06,.08]]];
      shapes.forEach((p,i)=>prism('remnant_'+i,p,.045,i===0?4:2));
    }
    if(family==='scars'){
      for(let i=0;i<3;i++){
        let y=(i-1)*.16;prism('scar_'+i,[[-.46,y-.19],[-.18,y-.03],[.03,y+.015],[.41,y+.20],[.05,y+.035],[-.15,y+.008]],.04,0);
        prism('edge_'+i,[[-.38,y-.14],[-.17,y-.018],[.04,y+.024],[.33,y+.16],[.03,y+.028],[-.16,y-.005]],.046,4);
      }
      band('halo',.7,.025,.12,1.25,2);band('halo',.7,.02,2.8,4.1,3);
    }
    if(family==='execution_line'){
      prism('black',[[0,-1],[-.018,-.60],[-.025,.45],[0,1],[.018,.5],[.025,-.6]],.035,0);
      prism('red',[[0,-1],[-.009,-.6],[-.012,.45],[0,1],[.009,.5],[.012,-.6]],.04,4);
      prism('light',[[0,-1],[-.003,-.6],[-.004,.45],[0,1],[.003,.5],[.004,-.6]],.045,6);
    }
    Canvas.updateAll();
    for(const [name,faces] of Object.entries(objGroups)) obj.push('g '+name,...faces);
    fs.writeFileSync(models+'/'+family+'.obj',obj.join('\n')+'\n');
    fs.writeFileSync(art+'/dead_thought_'+family+'.bbmodel',Codecs.project.compile());
    summary.push({family,triangles,groups:Object.keys(groups),project:Project.uuid});
  }
  ModelProject.all.filter(p=>p.name==='dead_thought_final_wheel').at(-1).select();
  Preview.selected.camera.position.set(13,8,65);Preview.selected.controls.target.set(0,0,0);Preview.selected.controls.update();
  return summary;
})()
