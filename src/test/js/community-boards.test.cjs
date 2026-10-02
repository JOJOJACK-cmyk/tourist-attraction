const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../main/resources');
class Element{
 constructor(){this.children=[];this.value='';this.textContent='';this.attrs={};this.listeners={};this.files=[];this.hidden=true;}
 append(...nodes){this.children.push(...nodes);}replaceChildren(...nodes){this.children=nodes;}setAttribute(k,v){this.attrs[k]=v;}addEventListener(k,fn){this.listeners[k]=fn;}cloneNode(){const el=new Element();el.value=this.value;el.textContent=this.textContent;return el;}scrollIntoView(){}reset(){}
}
const ids=[...fs.readFileSync(path.join(root,'templates/community.html'),'utf8').matchAll(/id="([^"]+)"/g)].map(m=>m[1]);
const elements=Object.fromEntries(ids.map(id=>[id,new Element()]));
const requests=[];let saved;
const base={id:99,kind:'GENERAL',category:'OTHER',destination:null,destinationId:null,title:'여행 이야기',body:'여행 준비 이야기',createdAt:'2026-10-02',mine:true};
const ctx=vm.createContext({URL,URLSearchParams,FormData,location:{href:'http://localhost/community?board=question',search:'?board=question'},history:{replaceState(){}},document:{getElementById:id=>{assert.ok(elements[id],id);return elements[id];},createElement:()=>new Element()},fetch:async(url,options)=>{
 requests.push(url);let data;
 if(url==='/api/auth/csrf')data={headerName:'X-CSRF-TOKEN',token:'token'};
 else if(url==='/api/auth/me')data={authenticated:true,loginId:'alice'};
 else if(url==='/api/destinations')data=[{slug:'sokcho',name:'속초시장'}];
 else if(options.method==='POST'){saved=JSON.parse(options.body);assert.equal(options.headers['X-CSRF-TOKEN'],'token');data={...base,...saved};}
 else if(url.endsWith('/comments'))data=[];
 else if(url==='/api/community/posts/99')data={...base,...saved};
 else {const query=new URL(url,'http://localhost').searchParams;assert.ok(['GENERAL','QUESTION'].includes(query.get('kind')));data={content:[{...base,kind:query.get('kind')}],totalPages:2,totalElements:13};}
 return {ok:true,status:200,json:async()=>data};
}});
vm.runInContext(fs.readFileSync(path.join(root,'static/js/community.js'),'utf8'),ctx);
const settle=async()=>{for(let i=0;i<8;i++)await new Promise(r=>setImmediate(r));};
(async()=>{
 await settle();assert.equal(elements['board-kind'].value,'QUESTION');assert.equal(elements['board-question'].attrs['aria-pressed'],'true');assert.equal(elements['write-post'].textContent,'질문하기');
 elements['write-post'].listeners.click();assert.equal(elements['post-kind'].value,'QUESTION');assert.equal(elements['post-destination'].value,'');assert.equal(elements['post-category'].value,'OTHER');
 await elements['page-next'].listeners.click();assert.ok(requests.at(-1).includes('page=1'));
 await elements['board-general'].listeners.click();assert.equal(elements['board-kind'].value,'GENERAL');assert.equal(elements['board-general'].attrs['aria-pressed'],'true');assert.ok(requests.at(-1).includes('page=0'));assert.ok(requests.at(-1).includes('kind=GENERAL'));
 elements['write-post'].listeners.click();assert.equal(elements['post-kind'].value,'GENERAL');
 elements['post-title'].value='여행 이야기';elements['post-body'].value='여행 준비 이야기';elements['post-kind'].value='QUESTION';
 await elements['post-form'].listeners.submit({preventDefault(){}});
 assert.equal(saved.kind,'QUESTION');assert.equal(saved.destinationId,'');assert.equal(elements['board-kind'].value,'QUESTION');assert.equal(elements['board-error'].hidden,true);
 console.log('Community boards: direct link, tabs, pagination reset, optional tag, CSRF save and moved-board refresh passed');
})().catch(e=>{console.error(e);process.exitCode=1});
