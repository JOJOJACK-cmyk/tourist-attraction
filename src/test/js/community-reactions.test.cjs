const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../main/resources');
class Element {
 constructor(){this.children=[];this.value='';this.textContent='';this.attrs={};this.listeners={};this.files=[];this.hidden=true;this.disabled=false;}
 append(...nodes){this.children.push(...nodes);}replaceChildren(...nodes){this.children=nodes;}setAttribute(k,v){this.attrs[k]=v;}addEventListener(k,fn){this.listeners[k]=fn;}cloneNode(){return new Element();}scrollIntoView(){}reset(){}
}
const html=fs.readFileSync(path.join(root,'templates/community.html'),'utf8');
const code=fs.readFileSync(path.join(root,'static/js/community.js'),'utf8');
const settle=async()=>{for(let i=0;i<8;i++)await new Promise(r=>setImmediate(r));};
const walk=e=>[e,...e.children.flatMap(walk)];
function harness(authenticated=true,search=''){
 const elements=Object.fromEntries([...html.matchAll(/id="([^"]+)"/g)].map(m=>[m[1],new Element()]));
 const requests=[];let loggedIn=authenticated;
 const post={id:1,kind:'GENERAL',category:'FOOD',title:'<script>후기</script>',body:'서문시장 방문 경험',author:'닉네임',createdAt:'2026-10-13',helpfulCount:0,helpful:false,saved:false};
 vm.runInContext(code,vm.createContext({URL,URLSearchParams,FormData,location:{href:'http://localhost/community'+search,search},history:{replaceState(){}},document:{getElementById:id=>elements[id],createElement:()=>new Element()},fetch:async(url,options)=>{
  requests.push({url,...options});let data;
  if(url==='/api/auth/me')data={authenticated:loggedIn,nickname:'닉네임'};
  else if(url==='/api/auth/csrf')data={headerName:'X-CSRF-TOKEN',token:'test-token'};
  else if(url==='/api/auth/social/providers')data={naver:false,google:false};
  else if(url==='/api/destinations')data=[];
  else if(url==='/api/auth/logout'){loggedIn=false;data={};}
  else if(/\/(helpful|saved)$/.test(url)){
   assert.equal(options.headers['X-CSRF-TOKEN'],'test-token');
   const type=url.split('/').at(-1);post[type]=options.method==='PUT';if(type==='helpful')post.helpfulCount=post.helpful?1:0;data={...post};
  }else if(url.endsWith('/comments'))data=[];
  else if(url==='/api/community/posts/1')data={...post};
  else{
   const query=new URL(url,'http://localhost').searchParams;
   const content=query.get('saved')==='true'&&!post.saved?[]:[{...post}];data={content,totalPages:content.length?1:0,totalElements:content.length};
  }
  return {ok:true,status:200,json:async()=>data};
 }}));
 return {elements,requests,post};
}
(async()=>{
 const h=harness();await settle();
 const click=async(label,container='post-list')=>{const button=walk(h.elements[container]).find(e=>e.textContent===label&&e.listeners.click);assert.ok(button,label);await button.listeners.click();};
 await click('도움됐어요 · 0');assert.equal(h.post.helpful,true);await click('도움 취소 · 1');assert.equal(h.post.helpfulCount,0);
 await click('저장');assert.equal(h.post.saved,true);await h.elements['board-saved'].listeners.click();assert.ok(h.requests.at(-1).url.includes('saved=true'));assert.ok(!h.requests.at(-1).url.includes('kind='));assert.equal(h.elements['board-title'].textContent,'저장한 글');
 await click(h.post.title);h.elements['comment-body'].value='작성 중인 댓글';
 await click('도움됐어요 · 0','post-content');assert.equal(h.elements['comment-body'].value,'작성 중인 댓글');
 await click('저장됨 · 취소','post-content');assert.equal(h.post.saved,false);assert.ok(walk(h.elements['post-list']).some(e=>e.textContent.startsWith('저장한 글이 없어요')));
 await h.elements['account-toggle'].listeners.click();assert.equal(h.elements['board-kind'].value,'GENERAL');assert.ok(!h.requests.at(-1).url.includes('saved=true'));
 const anonymous=harness(false);await settle();const button=walk(anonymous.elements['post-list']).find(e=>e.textContent==='저장');await button.listeners.click();assert.equal(anonymous.elements['account-panel'].hidden,false);assert.ok(!anonymous.requests.some(r=>r.method==='PUT'));
 const direct=harness(false,'?saved=1');await settle();assert.equal(direct.elements['board-title'].textContent,'저장한 글');assert.equal(direct.elements['account-panel'].hidden,false);assert.ok(!direct.requests.some(r=>r.url.includes('saved=true')));
 assert.ok(fs.readFileSync(path.join(root,'templates/login.html'),'utf8').includes('/community?saved=1#board'));
 console.log('Community reactions: helpful/save/cancel CSRF, private saved tab, safe title, preserved draft, logout reset and anonymous login guidance passed');
})().catch(e=>{console.error(e);process.exitCode=1;});
