const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../main/resources'),html=fs.readFileSync(path.join(root,'templates/visit-reviews.html'),'utf8');
class Element{
 constructor(tag='div'){this.tagName=tag;this.children=[];this.value='';this.textContent='';this.attrs={};this.listeners={};this.hidden=false;this.files=[];this.checked=false;this.disabled=false;}
 append(...n){this.children.push(...n);}replaceChildren(...n){this.children=n;}setAttribute(k,v){this.attrs[k]=v;}scrollIntoView(){}cloneNode(){const e=new Element(this.tagName);e.value=this.value;e.textContent=this.textContent;return e;}
 addEventListener(k,fn){(this.listeners[k]??=[]).push(fn);}async emit(k,event={}){for(const fn of this.listeners[k]||[])await fn({preventDefault(){},...event});}
 get options(){return this.children;}set innerHTML(v){throw Error('Unsafe HTML rendering');}
}
class Form{constructor(){this.items=[];}append(...entry){this.items.push(entry);}}
const walk=e=>[e,...e.children.flatMap(walk)];
const settle=async()=>{for(let i=0;i<10;i++)await new Promise(r=>setImmediate(r));};
async function setup(admin=false,state={reviews:[]}){
 const ids=[...html.matchAll(/id="([^"]+)"/g)].map(m=>m[1]),els=Object.fromEntries(ids.map(id=>[id,new Element()])),requests=[];
 let logged=admin,token=0,fail=false;
 els['visit-filter-status'].value='PENDING';
 const reset=()=>{for(const id of ['visit-date','visit-title','visit-item','visit-spent','visit-wait','visit-good','visit-bad'])els[id].value='';for(const id of ['visit-food','visit-lodging','visit-service','visit-crowding'])els[id].value='NOT_USED';els['visit-photo'].files=[];els['visit-firsthand'].checked=false;els['visit-remove-photo'].checked=false;};
 reset();els['visit-form'].reset=reset;els['visit-account-form'].reset=()=>{};
 const ctx=vm.createContext({URL:{createObjectURL:()=> 'blob:preview',revokeObjectURL(){}},URLSearchParams,Date,FormData:Form,confirm:()=>true,location:{search:'?destination=seomun'},document:{body:{dataset:{moderation:admin?'true':'false'}},getElementById:id=>{assert.ok(els[id],id);return els[id];},createElement:t=>new Element(t)},fetch:async(url,options={})=>{
   const method=options.method||'GET',body=options.body instanceof Form?options.body:options.body?JSON.parse(options.body):null;requests.push({url,method,body,headers:options.headers});
   let data,status=200;
   if(url==='/api/auth/csrf')data={headerName:'X-CSRF-TOKEN',token:'csrf-'+(++token)};
   else if(url==='/api/auth/me')data={authenticated:logged,admin,loginId:'visitowner'};
   else if(url==='/api/auth/login'){logged=true;data={success:true};}
   else if(url==='/api/auth/logout'){logged=false;data={success:true};}
   else if(url==='/api/destinations')data=[{slug:'seomun',name:'대구 서문시장'}];
   else if(url==='/api/community/images'&&method==='POST')data={imageKey:'11111111-1111-1111-1111-111111111111'};
   else if(url.startsWith('/api/visit-reviews')&&['POST','PUT'].includes(method)){
     if(fail){fail=false;data={error:'후기가 변경됐어요. 다시 확인해주세요.'};status=409;}
     else{data={...body,id:1,version:method==='PUT'?state.reviews[0].version+1:0,status:'PENDING',destination:'대구 서문시장',author:'익명',mine:true,imageUrl:body.imageKey?'/api/community/images/'+body.imageKey:null,createdAt:'2026-10-08T17:00:00',reviewNote:null};state.reviews=[data];}
   }else if(url.startsWith('/api/admin/visit-reviews/')&&method==='PATCH'){state.reviews[0]={...state.reviews[0],version:state.reviews[0].version+1,status:body.status,reviewNote:body.note};data=state.reviews[0];}
   else if(method==='GET'&&url.includes('/api/')&&url.includes('visit-reviews')){
     const q=new URLSearchParams(url.split('?')[1]),selected=state.reviews.filter(r=>url.includes('/mine')?logged:url.includes('/admin/')?r.status===q.get('status'):r.status==='APPROVED');data={content:selected,totalPages:selected.length?1:0,totalElements:selected.length};
   }else throw Error('Unexpected request '+method+' '+url);
   return {status,ok:status<400,json:async()=>data};
 }});
 vm.runInContext(fs.readFileSync(path.join(root,'static/js/visit-reviews.js'),'utf8'),ctx);await settle();
 return {els,ctx,state,requests,failNext(){fail=true;}};
}
(async()=>{
 const f=await setup(),e=f.els;
 await e['visit-write'].emit('click');assert.equal(e['visit-account-panel'].hidden,false);assert.ok(e['visit-error'].textContent.includes('로그인'));
 e['visit-account-id'].value='visitowner';e['visit-account-password'].value='password123';await e['visit-account-form'].emit('submit',{submitter:{value:'login'}});
 await e['visit-write'].emit('click');assert.equal(e['visit-destination'].value,'seomun');
 e['visit-date'].value='2026-10-07';e['visit-title'].value='직접 방문 경험';e['visit-good'].value='짧음';
 await e['visit-form'].emit('submit');assert.ok(!f.requests.some(r=>r.method==='POST'&&r.url==='/api/visit-reviews'));
 e['visit-good'].value='맛과 양에 만족했어요 <script>alert(1)</script>';e['visit-item'].value='칼국수';e['visit-spent'].value='12000';e['visit-wait'].value='0';e['visit-food'].value='SATISFIED';e['visit-crowding'].value='NEUTRAL';e['visit-firsthand'].checked=true;
 e['visit-photo'].files=[{type:'image/png',size:10}];await e['visit-photo'].emit('change');assert.equal(e['visit-photo-preview'].hidden,false);
 f.failNext();await e['visit-form'].emit('submit');assert.equal(e['visit-editor'].hidden,false);assert.equal(e['visit-save'].disabled,false);assert.ok(e['visit-good'].value.includes('<script>'));
 await e['visit-form'].emit('submit');assert.equal(f.requests.filter(r=>r.url==='/api/community/images').length,1);
 const posted=f.requests.filter(r=>r.method==='POST'&&r.url==='/api/visit-reviews').at(-1);assert.equal(posted.body.waitingMinutes,0);assert.equal(posted.body.spentWon,12000);assert.equal(posted.body.lodgingCost,'NOT_USED');assert.equal(posted.body.crowding,'NEUTRAL');assert.equal(posted.body.firsthand,true);assert.equal(posted.headers['X-CSRF-TOKEN'],'csrf-2');
 assert.equal(e['visit-tab-mine'].attrs['aria-pressed'],'true');assert.equal(e['visit-tab-admin'].hidden,true);assert.equal(e['visit-editor'].hidden,true);
 assert.ok(walk(e['visit-list']).some(n=>n.textContent.includes('<script>')));
 await e['visit-tab-public'].emit('click');assert.ok(walk(e['visit-list']).some(n=>n.textContent.includes('아직 공개된')));
 f.state.reviews[0].status='APPROVED';f.state.reviews[0].version=1;await e['visit-filter'].emit('submit');
 const edit=walk(e['visit-list']).find(n=>n.tagName==='button'&&n.textContent==='수정');await edit.emit('click');assert.equal(e['visit-wait'].value,0);e['visit-firsthand'].checked=true;
 await e['visit-form'].emit('submit');assert.equal(f.requests.find(r=>r.method==='PUT').body.version,1);assert.equal(f.state.reviews[0].status,'PENDING');
 const a=await setup(true,f.state);assert.equal(a.els['visit-tab-admin'].attrs['aria-pressed'],'true');
 let approve=walk(a.els['visit-list']).find(n=>n.tagName==='button'&&n.textContent==='승인·공개');await approve.emit('click');assert.equal(f.state.reviews[0].status,'APPROVED');assert.ok(a.requests.find(r=>r.method==='PATCH').headers['X-CSRF-TOKEN']);
 a.els['visit-filter-status'].value='APPROVED';await a.els['visit-filter-status'].emit('change');const note=walk(a.els['visit-list']).find(n=>n.tagName==='textarea');note.value='방문 내용을 보완해주세요';await walk(a.els['visit-list']).find(n=>n.tagName==='button'&&n.textContent==='반려').emit('click');assert.equal(f.state.reviews[0].status,'REJECTED');
 await e['visit-filter'].emit('submit');assert.ok(walk(e['visit-list']).some(n=>n.textContent.includes('검토 메모')));
 await e['visit-account-toggle'].emit('click');assert.equal(e['visit-tab-public'].attrs['aria-pressed'],'true');assert.equal(e['visit-editor'].hidden,true);
 console.log('Direct visit UI: login/CSRF rotation, required text, optional cost/zero wait, photo retry, safe text, private/mine/admin tabs, edit version, approve/reject and logout passed');
})().catch(e=>{console.error(e);process.exitCode=1});
