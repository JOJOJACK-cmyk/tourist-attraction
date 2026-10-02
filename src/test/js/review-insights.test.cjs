const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../main/resources');
const raw=JSON.parse(fs.readFileSync(path.join(root,'static/data/sokcho-review-pilot.json'),'utf8'));
const pilot=JSON.parse(fs.readFileSync(path.join(root,'static/data/sokcho-prompt-pilot.json'),'utf8'));
class Element{
  constructor(tag='div'){this.tagName=tag;this.children=[];this.value='';this.textContent='';this.listeners={};this.attrs={};this.open=false;}
  append(...nodes){this.children.push(...nodes);}replaceChildren(...nodes){this.children=nodes;}setAttribute(key,value){this.attrs[key]=value;}addEventListener(name,fn){(this.listeners[name]??=[]).push(fn);}click(){for(const fn of this.listeners.click||[])fn();}
}
const ctx=vm.createContext({window:{},document:{createElement:tag=>new Element(tag)},URL});
vm.runInContext(fs.readFileSync(path.join(root,'static/js/review-insights.js'),'utf8'),ctx);
const api=ctx.window.ReviewInsights;
const annual=year=>({...raw,sources:raw.sources.filter(s=>s.date.startsWith(String(year))),promptPilot:pilot});
const all={...raw,promptPilot:pilot};
assert.equal(api.select(all,pilot).reviews.length,30);
assert.equal(api.select(all,pilot).experiences.length,57);
for(const year of [2025,2026]){
 const selected=api.select(annual(year),pilot);assert.equal(selected.reviews.length,15);
 assert.ok(selected.experiences.every(e=>e.published_at.startsWith(String(year))));
}
assert.equal(api.select(annual(2026),pilot).coverage.find(c=>c.category==='parking').count,0);
assert.equal(api.select(annual(2025),pilot).coverage.find(c=>c.category==='parking').count,1);
assert.equal(api.select(all,pilot).coverage.find(c=>c.category==='shopping').count,1); // Two experiences from one post count once.
assert.equal(api.select(all,pilot).coverage.find(c=>c.category==='lodging').count,0);
const center=api.group(api.select(all,pilot).experiences).find(g=>g.item==='회'&&g.place==='시장 지하 회센터');
assert.equal(center.count,2);assert.ok(!center.experiences.some(e=>e.source_id==='pilot-11'));
assert.equal(api.questions(api.select(all,pilot).experiences).length,4);
assert.equal(api.questions(api.select(annual(2026),pilot).experiences).length,3);
assert.ok(api.questions(api.select(all,pilot).experiences).find(q=>q.title.includes('포장')).description.includes('요약도'));
const walk=el=>[el,...el.children.flatMap(walk)];
const target=new Element();api.render(annual(2026),pilot,target);
const parkingButton=walk(target).find(el=>el.tagName==='button'&&el.textContent==='주차 0');parkingButton.click();
assert.ok(walk(target).some(el=>el.textContent.includes('주차 판단에 쓸 근거가 없어요')));
assert.ok(walk(target).some(el=>el.tagName==='details'&&el.open));
const links=walk(target).filter(el=>el.tagName==='a');
assert.ok(links.every(el=>annual(2026).sources.some(s=>new URL(s.url).href===el.href)));
const empty=new Element();api.render({...raw,sources:[],promptPilot:pilot},pilot,empty);
assert.ok(walk(empty).some(el=>el.textContent.includes('구체적인 단서를 찾지 못했어요')));
console.log('Review insights: annual isolation, deduplicated counts, facility scope, conflicting opinions, filters, evidence links and empty state passed');
// Compose the saved analysis with the existing annual homepage renderer.
const ids=[...fs.readFileSync(path.join(root,'templates/index.html'),'utf8').matchAll(/id="([^"]+)"/g)].map(m=>m[1]);
const elements=Object.fromEntries(ids.map(id=>[id,new Element()]));
ctx.document.getElementById=id=>{assert.ok(elements[id],'Unknown homepage ID '+id);return elements[id];};
ctx.document.querySelectorAll=()=>[];ctx.raw=raw;ctx.pilot=pilot;
vm.runInContext(fs.readFileSync(path.join(root,'static/js/app.js'),'utf8').replace(/initialize\(\);\s*$/,''),ctx);
vm.runInContext('renderReviewPilot({...selectReviewYear(raw,2026),promptPilot:pilot})',ctx);
assert.equal(elements['result-kind'].textContent,'후기 15건 · 프롬프트 적용 예시');
assert.ok(walk(elements.result).some(el=>el.textContent==='방문 전에 먼저 정할 것'));
assert.ok(walk(elements.result).some(el=>el.textContent==='기존 주제별 검토 보기'));
assert.ok(walk(elements.result).filter(el=>el.tagName==='a').every(el=>annual(2026).sources.some(s=>new URL(s.url).href===el.href)));
console.log('Homepage composition: annual prompt pilot, collapsed original themes and year-specific links passed');
