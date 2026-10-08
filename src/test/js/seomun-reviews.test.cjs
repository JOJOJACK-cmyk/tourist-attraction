const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../main/resources');
const read=name=>JSON.parse(fs.readFileSync(path.join(root,'static/data/'+name+'.json'),'utf8'));
const raw=read('seomun-review-pilot'),pilot=read('seomun-prompt-pilot');
class Element{
 constructor(tag='div'){this.tagName=tag;this.children=[];this.textContent='';this.value='';this.attrs={};this.classList={add(){},toggle(){}};}
 append(...n){this.children.push(...n);}replaceChildren(...n){this.children=n;}setAttribute(k,v){this.attrs[k]=v;}addEventListener(){}scrollIntoView(){}
 querySelector(){return this.children.find(x=>x.value==='all');}get options(){return this.children;}
}
const walk=e=>[e,...e.children.flatMap(walk)];
const ctx=vm.createContext({window:{},URL,document:{createElement:t=>new Element(t)}});
vm.runInContext(fs.readFileSync(path.join(root,'static/js/review-insights.js'),'utf8'),ctx);
const api=ctx.window.ReviewInsights;
assert.equal(raw.sources.length,9);assert.equal(new Set(raw.sources.map(api.canonicalSource)).size,9);
assert.ok(raw.sources.every(s=>s.evidenceLevel==='public_body_reviewed'&&s.date<='2026-10-08'));
assert.equal(api.aggregate(raw,pilot).total,9);
assert.equal(api.aggregate(raw,pilot).praiseCount,7);assert.equal(api.aggregate(raw,pilot).complaintCount,2);
const annual={...raw,sources:raw.sources.filter(s=>s.date.startsWith('2026'))};
const overview=api.aggregate(annual,pilot);
assert.equal(overview.total,6);assert.equal(overview.praiseCount,4);assert.equal(overview.complaintCount,2);
assert.ok(!overview.praise.some(t=>t.label.includes('계란김밥')));
assert.ok(!overview.issues.some(t=>t.title.includes('술빵')));
const dimensions=api.dimensions(annual,pilot);
assert.equal(dimensions[0].total,2);assert.equal(dimensions[0].good,1);assert.equal(dimensions[0].bad,1);
assert.equal(dimensions[1].total,0);assert.equal(dimensions[2].total,0);
assert.equal(dimensions[3].total,3);assert.equal(dimensions[3].bad,0); // Neutral crowd observations aren't complaints.
assert.equal(api.canonicalSource(raw.sources[4]),api.canonicalSource({url:'https://m.blog.naver.com/wkddbeka/223812341095'}));
const duplicate={...raw,sources:[...raw.sources,{...raw.sources[4],id:99,url:'https://blog.naver.com/wkddbeka/223812341095'}]};
assert.equal(api.aggregate(duplicate,pilot).total,9);
const ids=[...fs.readFileSync(path.join(root,'templates/index.html'),'utf8').matchAll(/id="([^"]+)"/g)].map(m=>m[1]);
const elements=Object.fromEntries(ids.map(id=>[id,new Element()]));
const requests=[],sample={value:'sample',checked:true};
const destinations=[{slug:'sokcho',name:'속초시장',region:'강원',categories:'시장'},{slug:'seomun',name:'대구 서문시장',region:'대구',categories:'시장'}];
Object.assign(ctx,{URLSearchParams,AbortController,Date,location:{search:'?destination=seomun',hash:'#report'},matchMedia:()=>({matches:true}),fetch:async url=>{
 requests.push(url);return {ok:true,json:async()=>url==='/api/destinations'?destinations:url==='/api/status'?{liveAvailable:false}:url.includes('seomun-review-pilot')?raw:url.includes('seomun-prompt-pilot')?pilot:(()=>{throw Error('Unexpected fetch '+url)})()};
}});
Object.assign(ctx.document,{getElementById:id=>elements[id],querySelector:()=>sample,querySelectorAll:()=>[]});
vm.runInContext(fs.readFileSync(path.join(root,'static/js/app.js'),'utf8'),ctx);
(async()=>{
 for(let i=0;i<8;i++)await new Promise(r=>setImmediate(r));
 assert.equal(elements['result-kind'].textContent,'종합 후기 6건');
 assert.ok(requests.every(url=>!url.startsWith('/api/analysis')&&!url.includes('sokcho-')));
 assert.ok(walk(elements.result).some(e=>e.textContent==='2026년 대구 서문시장 후기'));
 assert.ok(!walk(elements.result).some(e=>/술빵|오징어순대/.test(e.textContent)));
 elements.period.value='all';await vm.runInContext('analyze(false)',ctx);
 assert.equal(elements['result-kind'].textContent,'종합 후기 9건');
 elements.period.value='2025';await vm.runInContext('analyze(false)',ctx);
 assert.equal(elements['result-kind'].textContent,'종합 후기 1건');
 elements.period.value='2024';await vm.runInContext('analyze(false)',ctx);
 assert.ok(walk(elements.result).some(e=>e.textContent.includes('서문시장 검토 후기')));
 assert.equal(walk(elements.result).find(e=>e.href).href,'/destinations/seomun');
 assert.equal(elements.error.hidden,true);
 console.log('Seomun: provenance, source deduplication, annual scope, cost vs taste, neutral crowd observations, lodging/service gaps and real homepage data loading passed');
})().catch(e=>{console.error(e);process.exitCode=1});
