const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../main/resources');
class Element{constructor(){this.children=[];this.textContent='';this.attrs={};this.value='';this.classList={add(){},toggle(){}};}append(...n){this.children.push(...n);}replaceChildren(...n){this.children=n;}setAttribute(k,v){this.attrs[k]=v;}addEventListener(){}set innerHTML(v){throw Error('Unsafe HTML');}}
const ids=[...fs.readFileSync(path.join(root,'templates/index.html'),'utf8').matchAll(/id="([^"]+)"/g)].map(m=>m[1]),els=Object.fromEntries(ids.map(id=>[id,new Element()]));
const requests=[],ctx=vm.createContext({URL,URLSearchParams,AbortController,document:{getElementById:id=>els[id],createElement:()=>new Element(),querySelectorAll:()=>[]},fetch:async url=>{requests.push(url);return {ok:true,json:async()=>({total:2,praiseCount:1,complaintCount:1,aspects:[{key:'food_cost',label:'식비',total:2,good:1,bad:1,neutral:0},{key:'lodging_cost',label:'숙박비',total:0,good:0,bad:0,neutral:0},{key:'crowding',label:'혼잡·대기',total:1,good:0,bad:0,neutral:1}]})};}});
vm.runInContext(fs.readFileSync(path.join(root,'static/js/app.js'),'utf8').replace(/initialize\(\);\s*$/,''),ctx);
const walk=e=>[e,...e.children.flatMap(walk)];
(async()=>{
 const original=new Element();original.textContent='외부 후기 6건';els.result.append(original);
 vm.runInContext('requestId=1',ctx);await vm.runInContext('loadVisitOverview("seomun",2026,1,new AbortController().signal)',ctx);
 assert.ok(requests[0].includes('destination=seomun')&&requests[0].includes('year=2026'));
 assert.equal(els.result.children[0],original);
 assert.ok(walk(els['visit-result']).some(e=>e.textContent.includes('승인된 후기 2건')));
 assert.ok(walk(els['visit-result']).some(e=>e.textContent.includes('가격 대비 만족 1건')));
 assert.ok(walk(els['visit-result']).some(e=>e.textContent.includes('혼잡·대기 부담 0건 · 보통 1건')));
 assert.ok(walk(els['visit-result']).some(e=>e.textContent.includes('평가한 방문 후기가 아직 없')));
 let resolveFirst;ctx.fetch=async url=>({ok:true,json:async()=>url.includes('destination=sokcho')?new Promise(r=>resolveFirst=r):{total:0,aspects:[]}});
 vm.runInContext('requestId=2',ctx);const pending=vm.runInContext('loadVisitOverview("sokcho",null,2,new AbortController().signal)',ctx);await new Promise(r=>setImmediate(r));
 vm.runInContext('requestId=3',ctx);await vm.runInContext('loadVisitOverview("seomun",null,3,new AbortController().signal)',ctx);resolveFirst({total:99,aspects:[]});await pending;
 assert.ok(!walk(els['visit-result']).some(e=>e.textContent.includes('99건')));
 assert.equal(walk(els['visit-result']).find(e=>e.href).href,'/visit-reviews?destination=seomun');
 assert.equal(els['visit-result'].attrs['aria-busy'],'false');
 ctx.fetch=async()=>({ok:false,json:async()=>({error:'잠시 후 다시 시도해주세요.'})});await vm.runInContext('loadVisitOverview("seomun",null,3,new AbortController().signal)',ctx);
 assert.ok(walk(els['visit-result']).some(e=>e.textContent.includes('다시 시도')));assert.equal(els.result.children[0],original);
 console.log('Homepage visit overview: separate source counts, year filtering, neutral crowding, missing evaluations, stale response exclusion and isolated API errors passed');
})().catch(e=>{console.error(e);process.exitCode=1});
