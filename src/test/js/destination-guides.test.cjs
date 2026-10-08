const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../main/resources');
const script=fs.readFileSync(path.join(root,'static/js/app.js'),'utf8');
const guides=JSON.parse(fs.readFileSync(path.join(root,'data/destination-guides.json'),'utf8'));
const destinations=[{slug:'sokcho',name:'속초시장',region:'강원',categories:'시장'},...guides.map(g=>({...g,categories:'관광'}))];
class Element {
 constructor(){this.children=[];this.value='';this.textContent='';this.attrs={};this.listeners={};this.classList={add(){},toggle(){}};}
 append(...nodes){this.children.push(...nodes);}replaceChildren(...nodes){this.children=nodes;}
 setAttribute(k,v){this.attrs[k]=v;}addEventListener(k,fn){this.listeners[k]=fn;}scrollIntoView(){}
 get options(){return this.children;}querySelector(){return this.children.find(x=>x.value==='all');}
}
const settle=async()=>{for(let i=0;i<8;i++)await new Promise(r=>setImmediate(r));};
async function run(slug){
 const ids=[...fs.readFileSync(path.join(root,'templates/index.html'),'utf8').matchAll(/id="([^"]+)"/g)].map(m=>m[1]);
 const elements=Object.fromEntries(ids.map(id=>[id,new Element()]));
 const sample={value:'sample',checked:true,addEventListener(){}},live={value:'live',checked:false,addEventListener(){}};
 const ctx=vm.createContext({URL,URLSearchParams,AbortController,Date,location:{search:'?destination='+slug,hash:'#report'},matchMedia:()=>({matches:true}),document:{getElementById:id=>elements[id],createElement:()=>new Element(),querySelector:()=>sample,querySelectorAll:()=>[sample,live]},fetch:async url=>({ok:true,json:async()=>url==='/api/destinations'?destinations:url==='/api/status'?{liveAvailable:false}:url.startsWith('/data/')?{sources:[],themes:[]}:{kind:'empty',title:'자료 없음',summary:'검토 준비 중',sources:[]}})});
 vm.runInContext(script,ctx);await settle();return {elements,ctx};
}
(async()=>{
 for(const guide of guides){
  const {elements,ctx}=await run(guide.slug);
  assert.equal(elements.destination.value,guide.slug);
  assert.ok(elements['report-period'].textContent.startsWith(guide.name));
  const ownLink=elements.result.children.find(e=>e.href);
  assert.equal(ownLink.href,'/destinations/'+guide.slug);
  assert.equal(elements.cards.children.length,9);
  for(const [i,card] of elements.cards.children.entries())assert.equal(card.children.at(-1).children[0].href,'/destinations/'+destinations[i].slug);
  elements.period.value='all';await vm.runInContext('analyze(false)',ctx);
  assert.equal(elements.result.children.find(e=>e.href).href,'/destinations/'+guide.slug);
 }
 const invalid=await run('unknown');assert.equal(invalid.elements.destination.value,'sokcho');
 console.log('Destination guides: all 8 direct selections, 9 introduction links, own-guide fallback, all-year fallback and invalid selection passed');
})().catch(e=>{console.error(e);process.exitCode=1});
