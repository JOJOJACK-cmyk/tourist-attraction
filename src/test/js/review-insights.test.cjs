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

const overall=api.aggregate(all,pilot);
assert.equal(overall.total,30);assert.equal(overall.praiseCount,26);assert.equal(overall.complaintCount,12);
for(const year of [2025,2026]){
 const selected=api.select(annual(year),pilot);assert.equal(selected.reviews.length,15);
 assert.ok(selected.experiences.every(e=>e.published_at.startsWith(String(year))));
 const overview=api.aggregate(annual(year),pilot);assert.equal(overview.total,15);assert.equal(overview.praiseCount,13);assert.equal(overview.complaintCount,6);
}
assert.ok(overall.issues.some(i=>i.title==='가게별 주차권 제공 차이'&&i.count===1));
assert.ok(!api.aggregate(annual(2026),pilot).issues.some(i=>i.title.includes('주차')));
assert.ok(overall.issues.find(i=>i.title.includes('호불호')).text.includes('만족 3건'));
// One post can mention several positives and negatives, yet each sentiment counts once.
const e=pilot.reviews[0].analysis.experiences[0];
const synthetic={reviews:[{source_id:'pilot-1',analysis:{experiences:[{...e,sentiment:'positive'},{...e,sentiment:'positive',item:'다른 상품'},{...e,sentiment:'negative'},{...e,sentiment:'negative',item:'다른 상품'}]}}]};
const counted=api.aggregate({...raw,sources:[raw.sources[0]]},synthetic);assert.equal(counted.praiseCount,1);assert.equal(counted.complaintCount,1);
const walk=el=>[el,...el.children.flatMap(walk)];
const target=new Element();api.render(all,pilot,target);
assert.ok(walk(target).some(el=>el.textContent==='주요 이슈'));
assert.ok(walk(target).some(el=>el.textContent==='칭찬 언급'));
assert.ok(walk(target).some(el=>el.textContent==='불만 언급'));
assert.ok(!walk(target).some(el=>['a','details'].includes(el.tagName)));
assert.ok(!walk(target).some(el=>/익명 후기|원문 보기|근거 요약|프롬프트|블로그/.test(el.textContent)));
const empty=new Element();api.render({...raw,sources:[]},pilot,empty);
assert.ok(walk(empty).some(el=>el.textContent.includes('종합할 만한')));
console.log('Review overview: annual isolation, deduplicated praise/complaint counts, overlapping sentiments, issue scope and summary-only UI passed');
const ids=[...fs.readFileSync(path.join(root,'templates/index.html'),'utf8').matchAll(/id="([^"]+)"/g)].map(m=>m[1]);
const elements=Object.fromEntries(ids.map(id=>[id,new Element()]));
ctx.document.getElementById=id=>{assert.ok(elements[id],'Unknown homepage ID '+id);return elements[id];};
ctx.document.querySelectorAll=()=>[];ctx.raw=raw;ctx.pilot=pilot;
vm.runInContext(fs.readFileSync(path.join(root,'static/js/app.js'),'utf8').replace(/initialize\(\);\s*$/,''),ctx);
vm.runInContext('renderReviewPilot({...selectReviewYear(raw,2026),promptPilot:pilot})',ctx);
assert.equal(elements['result-kind'].textContent,'종합 후기 15건');
assert.ok(walk(elements.result).some(el=>el.textContent==='주요 이슈'));
assert.ok(!walk(elements.result).some(el=>['a','details'].includes(el.tagName)));
console.log('Homepage composition: overview and no per-blog analysis UI passed');
